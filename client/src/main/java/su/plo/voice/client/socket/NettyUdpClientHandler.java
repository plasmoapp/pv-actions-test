package su.plo.voice.client.socket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.connection.UdpClientPacketReceivedEvent;
import su.plo.voice.api.client.event.socket.UdpClientClosedEvent;
import su.plo.voice.client.BaseVoiceClient;
import su.plo.voice.client.audio.source.VoiceClientSelfSourceInfo;
import su.plo.voice.client.config.ClientConfig;
import su.plo.voice.proto.packets.Packet;
import su.plo.voice.proto.packets.udp.bothbound.CustomPacket;
import su.plo.voice.proto.packets.udp.bothbound.PingPacket;
import su.plo.voice.proto.packets.udp.clientbound.ClientPacketUdpHandler;
import su.plo.voice.proto.packets.udp.clientbound.SelfAudioInfoPacket;
import su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket;
import su.plo.voice.socket.NettyPacketUdp;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public final class NettyUdpClientHandler extends SimpleChannelInboundHandler<NettyPacketUdp> implements ClientPacketUdpHandler {

    private final Logger logger = LogManager.getLogger();

    private final PlasmoVoiceClient voiceClient;
    private final ClientConfig config;
    private final NettyUdpClient client;
    private final ScheduledFuture<?> ticker;

    private long keepAlive = System.currentTimeMillis();

    public NettyUdpClientHandler(@NotNull BaseVoiceClient voiceClient,
                                 @NotNull ClientConfig config,
                                 @NotNull NettyUdpClient client) {
        this.voiceClient = checkNotNull(voiceClient, "voiceClient");
        this.config = checkNotNull(config, "config");
        this.client = checkNotNull(client, "client");

        this.ticker = voiceClient.getBackgroundExecutor().scheduleAtFixedRate(
                this::tick,
                0L,
                1L,
                TimeUnit.SECONDS
        );
    }

    public void close() {
        if (ticker.isDone()) return;
        ticker.cancel(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NettyPacketUdp packetUdp) throws Exception {
        Packet<ClientPacketUdpHandler> packet = packetUdp.getPacketUdp().getPacket();

        UdpClientPacketReceivedEvent event = new UdpClientPacketReceivedEvent(client, packet);
        voiceClient.getEventBus().call(event);
        if (event.isCancelled()) return;

        logger.info("UDP packet received {}", packet);
        packet.handle(this);
    }

    @Override
    public void handle(@NotNull PingPacket packet) {
        client.setTimedOut(false);
        this.keepAlive = System.currentTimeMillis();
        client.sendPacket(new PingPacket());
    }

    @Override
    public void handle(@NotNull CustomPacket packet) {

    }

    @Override
    public void handle(@NotNull SourceAudioPacket packet) {
        if (config.getVoice().getDisabled().value()) return;

        voiceClient.getSourceManager().getSourceById(packet.getSourceId())
                .ifPresent(source -> {
                    if (source.getInfo().getState() != packet.getSourceState()) {
                        LogManager.getLogger().warn("Drop audio packet with bad source state");
                        voiceClient.getSourceManager().sendSourceInfoRequest(packet.getSourceId());
                        return;
                    }

                    source.process(packet);
                });
    }

    @Override
    public void handle(@NotNull SelfAudioInfoPacket packet) {
        if (config.getVoice().getDisabled().value()) return;

        voiceClient.getSourceManager().getSelfSourceInfo(packet.getSourceId())
                .ifPresent((sourceInfo) -> {
                    VoiceClientSelfSourceInfo selfSourceInfo = (VoiceClientSelfSourceInfo) sourceInfo;

                    selfSourceInfo.setSequenceNumber(packet.getSequenceNumber());
                    selfSourceInfo.setDistance(packet.getDistance());
                });
    }

    private void tick() {
        if (!client.getRemoteAddress().isPresent()) return;

        if (!client.isConnected())
            client.sendPacket(new PingPacket());

        // todo: config for max timeout keepalive?
        long diff = System.currentTimeMillis() - keepAlive;
        if (diff > 30_000L) {
            logger.warn("UDP timed out. Disconnecting...");
            client.close(UdpClientClosedEvent.Reason.TIMED_OUT);
        } else if (diff > 7_000L) {
            client.setTimedOut(true);
        }
    }
}

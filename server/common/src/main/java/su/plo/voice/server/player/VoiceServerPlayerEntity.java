package su.plo.voice.server.player;

import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import su.plo.lib.api.server.entity.MinecraftServerPlayerEntity;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.event.connection.TcpPacketSendEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.player.VoicePlayerInfo;
import su.plo.voice.proto.packets.Packet;
import su.plo.voice.proto.packets.tcp.PacketTcpCodec;
import su.plo.voice.server.BaseVoiceServer;

@ToString(doNotUseGetters = true, callSuper = true)
public final class VoiceServerPlayerEntity
        extends BaseVoicePlayer<MinecraftServerPlayerEntity>
        implements VoiceServerPlayer {

    private final PlasmoVoiceServer voiceServer;

    public VoiceServerPlayerEntity(@NotNull PlasmoVoiceServer voiceServer,
                                   @NotNull MinecraftServerPlayerEntity player) {
        super(voiceServer, player);
        this.voiceServer = voiceServer;
    }

    @Override
    public void sendPacket(@NotNull Packet<?> packet) {
        byte[] encoded = PacketTcpCodec.encode(packet);

        TcpPacketSendEvent event = new TcpPacketSendEvent(this, packet);
        if (!voiceServer.getEventBus().call(event)) return;

        instance.sendPacket(BaseVoiceServer.CHANNEL_STRING, encoded);

//        LogManager.getLogger().info("Channel packet {} sent to {}", packet, this);
    }

    @Override
    public boolean hasVoiceChat() {
        return voiceServer.getUdpConnectionManager()
                .getConnectionByPlayerId(instance.getUUID())
                .isPresent();
    }

    @Override
    public VoicePlayerInfo getInfo() {
        if (!hasVoiceChat()) throw new IllegalStateException("Player is not connected to UDP server");

        return new VoicePlayerInfo(
                instance.getUUID(),
                instance.getName(),
                voiceServer.getMuteManager()
                        .getMute(instance.getUUID())
                        .isPresent(),
                isVoiceDisabled(),
                isMicrophoneMuted()
        );
    }
}

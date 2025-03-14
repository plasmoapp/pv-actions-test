package su.plo.voice.api.server.audio.capture;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.lib.api.server.permission.PermissionDefault;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerPlayerSource;
import su.plo.voice.api.server.event.audio.capture.ServerActivationRegisterEvent;
import su.plo.voice.api.server.event.audio.capture.ServerActivationUnregisterEvent;
import su.plo.voice.api.server.event.audio.source.ServerSourcePacketEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.data.audio.line.VoiceSourceLine;
import su.plo.voice.proto.packets.tcp.clientbound.SourceAudioEndPacket;
import su.plo.voice.proto.packets.tcp.clientbound.SourceInfoPacket;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseProximityServerActivation {

    protected final PlasmoVoiceServer voiceServer;
    protected final String activationName;
    protected final UUID activationId;
    protected final UUID sourceLineId;
    protected final PermissionDefault defaultPermission;

    protected final SelfActivationInfo selfActivationInfo;

    public BaseProximityServerActivation(@NotNull PlasmoVoiceServer voiceServer,
                                         @NotNull String activationName,
                                         @NotNull PermissionDefault defaultPermission) {
        this.voiceServer = voiceServer;
        this.activationName = activationName;
        this.activationId = VoiceActivation.generateId(activationName);
        this.sourceLineId = VoiceSourceLine.generateId(activationName);
        this.defaultPermission = defaultPermission;
        this.selfActivationInfo = new SelfActivationInfo(voiceServer.getUdpConnectionManager());
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onActivationRegister(@NotNull ServerActivationRegisterEvent event) {
        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(activationName)) return;

        activation.getPermissions().forEach((permission) ->
                voiceServer.getMinecraftServer()
                        .getPermissionsManager()
                        .register(permission, defaultPermission)
        );
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onActivationUnregister(@NotNull ServerActivationUnregisterEvent event) {
        ServerActivation activation = event.getActivation();
        if (!activation.getName().equals(activationName)) return;

        activation.getPermissions().forEach((permission) ->
                voiceServer.getMinecraftServer()
                        .getPermissionsManager()
                        .unregister(permission)
        );
    }

    @EventSubscribe(priority = EventPriority.HIGHEST)
    public void onSourceSendPacket(@NotNull ServerSourcePacketEvent event) {
        if (!(event.getSource() instanceof ServerPlayerSource)) return;

        ServerPlayerSource source = (ServerPlayerSource) event.getSource();
        if (!selfActivationInfo.getLastPlayerActivationIds()
                .containsKey(source.getPlayer().getInstance().getUUID())
        ) {
            return;
        }

        if (event.getPacket() instanceof SourceInfoPacket) {
            selfActivationInfo.updateSelfSourceInfo(
                    source.getPlayer(),
                    source,
                    ((SourceInfoPacket) event.getPacket()).getSourceInfo()
            );
        } else if (event.getPacket() instanceof SourceAudioEndPacket) {
            source.getPlayer().sendPacket(event.getPacket());
        }
    }

    protected void sendAudioEndPacket(@NotNull ServerPlayerSource source,
                                      @NotNull PlayerAudioEndPacket packet) {
        sendAudioEndPacket(source, packet, packet.getDistance());
    }

    protected void sendAudioEndPacket(@NotNull ServerPlayerSource source,
                                      @NotNull PlayerAudioEndPacket packet,
                                      short distance) {
        SourceAudioEndPacket sourcePacket = new SourceAudioEndPacket(source.getId(), packet.getSequenceNumber());
        source.sendPacket(sourcePacket, distance);
    }

    protected void sendAudioPacket(@NotNull VoiceServerPlayer player,
                                   @NotNull ServerPlayerSource source,
                                   @NotNull PlayerAudioPacket packet) {
        sendAudioPacket(player, source, packet, packet.getDistance());
    }

    protected void sendAudioPacket(@NotNull VoiceServerPlayer player,
                                   @NotNull ServerPlayerSource source,
                                   @NotNull PlayerAudioPacket packet,
                                   short distance) {
        SourceAudioPacket sourcePacket = new SourceAudioPacket(
                packet.getSequenceNumber(),
                (byte) source.getState(),
                packet.getData(),
                source.getId(),
                distance
        );

        if (source.sendAudioPacket(sourcePacket, distance, packet.getActivationId())) {
            selfActivationInfo.sendAudioInfo(player, source, packet.getActivationId(), sourcePacket);
        }
    }

    protected Optional<ServerPlayerSource> getPlayerSource(@NotNull VoiceServerPlayer player,
                                                           @NotNull UUID activationId,
                                                           @Nullable Boolean isStereo) {
        if (!activationId.equals(this.activationId)) return Optional.empty();

        Optional<ServerActivation> activation = voiceServer.getActivationManager()
                .getActivationById(activationId);
        if (!activation.isPresent()) return Optional.empty();

        Optional<ServerSourceLine> sourceLine = voiceServer.getSourceLineManager()
                .getLineById(sourceLineId);
        if (!sourceLine.isPresent()) return Optional.empty();

        ServerPlayerSource source = voiceServer.getSourceManager().createPlayerSource(
                voiceServer,
                player,
                sourceLine.get(),
                "opus",
                false
        );
        source.setLine(sourceLine.get());
        if (isStereo != null) source.setStereo(isStereo && activation.get().isStereoSupported());

        return Optional.of(source);
    }
}

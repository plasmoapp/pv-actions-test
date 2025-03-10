package su.plo.voice.api.client.audio.source;

import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.audio.source.AudioSourceManager;
import su.plo.voice.proto.data.audio.source.SelfSourceInfo;
import su.plo.voice.proto.data.audio.source.SourceInfo;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ClientSourceManager extends AudioSourceManager<ClientAudioSource<?>> {

    @NotNull LoopbackSource createLoopbackSource(boolean relative);

    Optional<ClientAudioSource<?>> getSourceById(@NotNull UUID sourceId, boolean request);

    Collection<ClientAudioSource<?>> getSourcesByLineId(@NotNull UUID lineId);

    void update(@NotNull SourceInfo sourceInfo);

    void sendSourceInfoRequest(@NotNull UUID sourceId);

    void updateSelfSourceInfo(@NotNull SelfSourceInfo selfSourceInfo);

    Optional<ClientSelfSourceInfo> getSelfSourceInfo(@NotNull UUID sourceId);

    Collection<? extends ClientSelfSourceInfo> getSelfSourceInfos();
}

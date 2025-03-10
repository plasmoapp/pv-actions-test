package su.plo.voice.api.server.audio.source;

import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.proto.data.audio.source.PlayerSourceInfo;

public interface ServerPlayerSource extends ServerPositionalSource<PlayerSourceInfo> {

    @NotNull VoiceServerPlayer getPlayer();
}

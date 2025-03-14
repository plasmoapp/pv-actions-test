package su.plo.voice.api.server.event;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.event.Event;
import su.plo.voice.api.server.PlasmoVoiceServer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This event is fired when the Plasmo Voice server is initializing
 */
public final class VoiceServerInitializeEvent implements Event {

    @Getter
    private final PlasmoVoiceServer server;

    public VoiceServerInitializeEvent(@NotNull PlasmoVoiceServer server) {
        this.server = checkNotNull(server, "server cannot be null");
    }
}

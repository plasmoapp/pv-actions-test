package su.plo.voice.api.client.event.audio.capture;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.event.EventCancellableBase;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This event is fired once a samples was captured by {@link AudioCapture}
 */
public class AudioCaptureEvent extends EventCancellableBase {

    @Getter
    private final AudioCapture capture;

    @Getter
    @Setter
    private byte[] samples;

    public AudioCaptureEvent(@NotNull AudioCapture capture, byte[] samples) {
        this.capture = checkNotNull(capture, "capture");
        this.samples = checkNotNull(samples, "samples");
    }
}

package su.plo.voice.client.audio.capture;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.audio.codec.CodecException;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.capture.ClientActivationManager;
import su.plo.voice.api.client.audio.device.AudioDevice;
import su.plo.voice.api.client.audio.device.DeviceManager;
import su.plo.voice.api.client.audio.device.DeviceType;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.connection.ServerConnection;
import su.plo.voice.api.client.connection.ServerInfo;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureEvent;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureInitializeEvent;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureStartEvent;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureStopEvent;
import su.plo.voice.api.client.socket.UdpClient;
import su.plo.voice.api.encryption.Encryption;
import su.plo.voice.api.encryption.EncryptionException;
import su.plo.voice.api.util.AudioUtil;
import su.plo.voice.api.util.Params;
import su.plo.voice.client.audio.filter.NoiseSuppressionFilter;
import su.plo.voice.client.audio.filter.StereoToMonoFilter;
import su.plo.voice.client.config.ClientConfig;
import su.plo.voice.proto.data.audio.capture.CaptureInfo;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.data.audio.codec.CodecInfo;
import su.plo.voice.proto.data.player.VoicePlayerInfo;
import su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import javax.sound.sampled.AudioFormat;
import java.util.*;

public final class VoiceAudioCapture implements AudioCapture {

    private static final Logger LOGGER = LogManager.getLogger();

    private final PlasmoVoiceClient voiceClient;
    private final DeviceManager devices;
    private final ClientActivationManager activations;
    private final ClientConfig config;

    private final Set<UUID> activationStreams = Sets.newHashSet();
    private final Map<UUID, Long> activationSequenceNumbers = Maps.newHashMap();

    @Setter
    private volatile AudioEncoder monoEncoder;
    @Setter
    private volatile AudioEncoder stereoEncoder;
    @Setter
    private volatile Encryption encryption;

    private Thread thread;

    public VoiceAudioCapture(@NotNull PlasmoVoiceClient voiceClient,
                             @NotNull ClientConfig config) {
        this.voiceClient = voiceClient;
        this.devices = voiceClient.getDeviceManager();
        this.activations = voiceClient.getActivationManager();
        this.config = config;
    }

    @Override
    public Optional<AudioEncoder> getMonoEncoder() {
        return Optional.ofNullable(monoEncoder);
    }

    @Override
    public Optional<AudioEncoder> getStereoEncoder() {
        return Optional.ofNullable(stereoEncoder);
    }

    @Override
    public Optional<Encryption> getEncryption() {
        return Optional.ofNullable(encryption);
    }

    private Optional<InputDevice> getDevice() {
        Collection<AudioDevice> devices = this.devices.getDevices(DeviceType.INPUT);
        return Optional.ofNullable((InputDevice) devices.stream().findFirst().orElse(null));
    }

    @Override
    public void initialize(@NotNull ServerInfo serverInfo) {
        AudioCaptureInitializeEvent event = new AudioCaptureInitializeEvent(this);
        voiceClient.getEventBus().call(event);
        if (event.isCancelled()) return;

        // initialize input device
        AudioFormat format = serverInfo.getVoiceInfo().getFormat(
                config.getVoice().getStereoCapture().value()
        );

        if (!getDevice().isPresent()) {
            try {
                InputDevice device = voiceClient.getDeviceManager().openInputDevice(format, Params.EMPTY);
                devices.replace(null, device);
            } catch (Exception e) {
                LOGGER.error("Failed to open input device", e);
            }
        }

        // initialize encoder
        CaptureInfo capture = serverInfo.getVoiceInfo().getCapture();
        if (capture.getCodec() != null) {
            CodecInfo codec = capture.getCodec();

            Params.Builder params = Params.builder();
            codec.getParams().forEach(params::set);

            this.monoEncoder = voiceClient.getCodecManager().createEncoder(
                    codec.getName(),
                    capture.getSampleRate(),
                    false,
                    serverInfo.getVoiceInfo().getBufferSize(),
                    capture.getMtuSize(),
                    params.build()
            );

            this.stereoEncoder = voiceClient.getCodecManager().createEncoder(
                    codec.getName(),
                    capture.getSampleRate(),
                    true,
                    serverInfo.getVoiceInfo().getBufferSize(),
                    capture.getMtuSize(),
                    params.build()
            );
        }

        // initialize encryption
        if (serverInfo.getEncryption().isPresent()) {
            this.encryption = serverInfo.getEncryption().get();
        }

        LOGGER.info("Audio capture initialized");
    }

    @Override
    public void start() {
        AudioCaptureStartEvent event = new AudioCaptureStartEvent(this);
        voiceClient.getEventBus().call(event);
        if (event.isCancelled()) return;

        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                return;
            }
        }

        this.thread = new Thread(this::run);
        thread.setName("Voice Audio Capture");
        thread.start();
    }

    @Override
    public void stop() {
        AudioCaptureStopEvent event = new AudioCaptureStopEvent(this);
        voiceClient.getEventBus().call(event);
        if (event.isCancelled()) return;

        if (thread != null) thread.interrupt();
    }

    @Override
    public boolean isActive() {
        return thread != null;
    }

    @Override
    public boolean isServerMuted() {
        return voiceClient.getServerConnection()
                .map(connection -> connection.getClientPlayer()
                        .map(VoicePlayerInfo::isMuted)
                        .orElse(false))
                .orElse(false);
    }

    private void run() {
        while (!thread.isInterrupted()) {
            try {
                Optional<InputDevice> device = getDevice();
                Optional<ServerInfo> serverInfo = voiceClient.getServerInfo();

                if (!device.isPresent()
                        || !device.get().isOpen()
                        || !voiceClient.getUdpClientManager().isConnected()
                        || !serverInfo.isPresent()
                        || !activations.getParentActivation().isPresent()
                ) {
                    Thread.sleep(1_000L);
                    continue;
                }

                device.get().start();
                short[] samples = device.get().read();
                if (samples == null) {
                    Thread.sleep(5L);
                    continue;
                }

                AudioCaptureEvent captureEvent = new AudioCaptureEvent(this, device.get(), samples);
                voiceClient.getEventBus().call(captureEvent);
                if (captureEvent.isCancelled()) continue;

                ClientActivation parentActivation = activations.getParentActivation().get();

                if (captureEvent.isSendEnd()
                        || config.getVoice().getMicrophoneDisabled().value()
                        || config.getVoice().getDisabled().value()
                        || isServerMuted()
                ) {
                    if (parentActivation.isActivated()) {
                        parentActivation.reset();
                        sendVoiceEndPacket(parentActivation);
                    }

                    activations.getActivations().forEach((activation) -> {
                        if (activation.isActivated()) {
                            activation.reset();
                            sendVoiceEndPacket(activation);
                        }
                    });
                    continue;
                }

                ClientActivation.Result parentResult = parentActivation.process(samples, null);

                EncodedCapture encoded = new EncodedCapture();
                boolean transitiveReached = false;

                for (ClientActivation activation : activations.getActivations()) {
                    if ((activation.isDisabled() && !activation.isActivated()) ||
                            activation.equals(parentActivation)
                    ) continue;

                    if (transitiveReached) {
                        activation.reset();
                        continue;
                    }

                    ClientActivation.Result activationResult = activation.process(samples, parentResult);

                    if (activation.getType() == ClientActivation.Type.INHERIT) {
                        processActivation(device.get(), activation, activationResult, samples, encoded);
                    } else if (activation.getType() == ClientActivation.Type.VOICE) {
                        processActivation(device.get(), activation, activationResult, samples, encoded);
                    } else {
                        processActivation(device.get(), activation, activationResult, samples, encoded);
                    }

                    if (activationResult.isActivated() && !activation.isTransitive()) {
                        transitiveReached = true;
                    }
                }

                if (parentActivation.getId().equals(VoiceActivation.PROXIMITY_ID)) {
                    if (!transitiveReached) {
                        processActivation(device.get(), parentActivation, parentResult, samples, encoded);
                    } else if (activationStreams.remove(parentActivation.getId())) {
                        processActivation(device.get(), parentActivation, ClientActivation.Result.END, null, encoded);
                    }
                }
            } catch (InterruptedException ignored) {
                break;
            }
        }

        cleanup();
    }

    private void cleanup() {
        activationSequenceNumbers.clear();
        if (monoEncoder != null) monoEncoder.close();
        if (stereoEncoder != null) stereoEncoder.close();

        Optional<InputDevice> device = getDevice();
        if (device.isPresent() && device.get().isOpen()) {
            device.get().close();
            devices.remove(device.get());
        }

        this.thread = null;
    }

    private void processActivation(@NotNull InputDevice device,
                                   @NotNull ClientActivation activation,
                                   @NotNull ClientActivation.Result result,
                                   short[] samples,
                                   @NotNull EncodedCapture encoded) {
        boolean isStereo = config.getVoice().getStereoCapture().value() && activation.isStereoSupported();

        if (result.isActivated() && samples != null) {
            if (isStereo && encoded.stereo == null) {
                short[] processedSamples = new short[samples.length];
                System.arraycopy(samples, 0, processedSamples, 0, samples.length);

                processedSamples = device.processFilters(
                        processedSamples,
                        (filter) -> (filter instanceof StereoToMonoFilter) ||
                                (filter instanceof NoiseSuppressionFilter)
                );
                encoded.stereo = encode(stereoEncoder, processedSamples);
            } else if (!isStereo && encoded.mono == null) {
                short[] processedSamples = new short[samples.length];
                System.arraycopy(samples, 0, processedSamples, 0, samples.length);

                processedSamples = device.processFilters(processedSamples);
                encoded.mono = encode(monoEncoder, processedSamples);
            }
        }

        byte[] encodedData = isStereo ? encoded.stereo : encoded.mono;

        if (result == ClientActivation.Result.ACTIVATED) {
            sendVoicePacket(activation, isStereo, encodedData);
            activationStreams.add(activation.getId());
        } else if (result == ClientActivation.Result.END) {
            if (encodedData != null) sendVoicePacket(activation, isStereo, encodedData);
            sendVoiceEndPacket(activation);
            activationStreams.remove(activation.getId());
        }
    }

    private byte[] encode(@Nullable AudioEncoder encoder, short[] samples) {
        byte[] encoded;
        if (encoder != null) {
            try {
                encoded = encoder.encode(samples);
            } catch (CodecException e) {
                LOGGER.error("Failed to encode audio data", e);
                return null;
            }
        } else {
            encoded = AudioUtil.shortsToBytes(samples);
        }

        if (encryption != null) {
            try {
                encoded = encryption.encrypt(encoded);
            } catch (EncryptionException e) {
                LOGGER.error("Failed to encrypt audio data", e);
                return null;
            }
        }

        return encoded;
    }

    private void sendVoicePacket(@NotNull ClientActivation activation,
                                 boolean isStereo,
                                 byte[] encoded) {
        if (activation.getTranslation().equals("pv.activation.parent")) return;

        Optional<UdpClient> udpClient = voiceClient.getUdpClientManager().getClient();
        if (!udpClient.isPresent()) return;

        udpClient.get().sendPacket(new PlayerAudioPacket(
                getSequenceNumber(activation),
                encoded,
                activation.getId(),
                (short) activation.getDistance(),
                isStereo
        ));
    }

    private void sendVoiceEndPacket(ClientActivation activation) {
        if (activation.getTranslation().equals("pv.activation.parent")) return;

        if (monoEncoder != null) monoEncoder.reset();
        if (stereoEncoder != null) stereoEncoder.reset();

        Optional<ServerConnection> connection = voiceClient.getServerConnection();
        if (!connection.isPresent()) return;

        connection.get().sendPacket(new PlayerAudioEndPacket(
                getSequenceNumber(activation),
                activation.getId(),
                (short) activation.getDistance()
        ));
    }

    private long getSequenceNumber(@NotNull ClientActivation activation) {
        long sequenceNumber = activationSequenceNumbers.getOrDefault(activation.getId(), 0L) + 1;
        activationSequenceNumbers.put(activation.getId(), sequenceNumber);
        return sequenceNumber;
    }

    static class EncodedCapture {

        private byte[] mono;
        private byte[] stereo;
    }
}

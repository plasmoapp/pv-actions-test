package su.plo.voice.addon;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.BaseVoice;
import su.plo.voice.api.PlasmoVoice;
import su.plo.voice.api.addon.AddonContainer;
import su.plo.voice.api.addon.AddonScope;

import java.nio.file.Path;
import java.util.Optional;

@RequiredArgsConstructor
public final class PlasmoVoiceAddon implements AddonContainer {

    private final @NotNull PlasmoVoice object;
    private final @NotNull AddonScope scope;

    @Override
    public String getId() {
        return "plasmovoice";
    }

    @Override
    public String getName() {
        return "PlasmoVoice";
    }

    @Override
    public @NotNull AddonScope getScope() {
        return scope;
    }

    @Override
    public String getVersion() {
        return object.getVersion();
    }

    @Override
    public String[] getAuthors() {
        return new String[]{"Apehum"};
    }

    @Override
    public Class<?> getMainClass() {
        return BaseVoice.class;
    }

    @Override
    public Path getPath() {
        return null;
    }

    @Override
    public Optional<?> getInstance() {
        return Optional.of(object);
    }
}

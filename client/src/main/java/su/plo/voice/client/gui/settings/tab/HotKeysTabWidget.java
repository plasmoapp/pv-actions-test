package su.plo.voice.client.gui.settings.tab;

import com.google.common.collect.Lists;
import su.plo.lib.api.chat.MinecraftTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.capture.ClientActivationManager;
import su.plo.voice.client.audio.capture.VoiceClientActivation;
import su.plo.voice.client.config.ClientConfig;
import su.plo.voice.client.config.keybind.ConfigKeyBindings;
import su.plo.voice.client.config.keybind.KeyBindingConfigEntry;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class HotKeysTabWidget extends AbstractHotKeysTabWidget {

    private final ClientActivationManager activations;

    public HotKeysTabWidget(VoiceSettingsScreen parent,
                            PlasmoVoiceClient voiceClient,
                            ClientConfig config) {
        super(parent, voiceClient, config);

        this.activations = voiceClient.getActivationManager();
    }

    @Override
    public void init() {
        super.init();

        ((ConfigKeyBindings) hotKeys)
                .getCategoryEntries()
                .asMap()
                .forEach(this::createCategory);

        List<ClientActivation> activations = Lists.newArrayList(this.activations.getActivations());
        Collections.reverse(activations);
        activations.forEach(this::createActivation);
    }

    private void createActivation(ClientActivation activation) {
        if (activation.getDistances().size() == 0) return;

        addEntry(new CategoryEntry(MinecraftTextComponent.translatable("key.plasmovoice.distance", MinecraftTextComponent.translatable(activation.getTranslation()))));

        VoiceClientActivation clientActivation = (VoiceClientActivation) activation;

        addEntry(createHotKey(
                "key.plasmovoice.distance.increase",
                null,
                clientActivation.getDistanceIncreaseConfigEntry()
        ));
        addEntry(createHotKey(
                "key.plasmovoice.distance.decrease",
                null,
                clientActivation.getDistanceDecreaseConfigEntry()
        ));
    }

    private void createCategory(String category, Collection<KeyBindingConfigEntry> list) {
        if (category.equals("hidden")) return;

        addEntry(new CategoryEntry(MinecraftTextComponent.translatable(category)));

        list.forEach(entry ->
                addEntry(createHotKey(
                        entry.value().getName(),
                        null,
                        entry
                ))
        );
    }
}

package su.plo.voice.client.gui.settings.tab;

import com.google.common.collect.Lists;
import gg.essential.universal.UGraphics;
import gg.essential.universal.UMatrixStack;
import gg.essential.universal.UMinecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import su.plo.config.entry.ConfigEntry;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.lib.api.chat.MinecraftTextComponent;
import su.plo.lib.api.chat.MinecraftTextStyle;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.lib.mod.client.gui.components.IconButton;
import su.plo.lib.mod.client.gui.components.TextFieldWidget;
import su.plo.lib.mod.client.gui.widget.GuiAbstractWidget;
import su.plo.lib.mod.client.render.RenderUtil;
import su.plo.lib.mod.client.render.texture.ModPlayerSkins;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.line.ClientSourceLine;
import su.plo.voice.api.client.audio.line.ClientSourceLineManager;
import su.plo.voice.api.client.event.connection.VoicePlayerConnectedEvent;
import su.plo.voice.api.client.event.connection.VoicePlayerDisconnectedEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.client.config.ClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.widget.UpdatableWidget;
import su.plo.voice.client.gui.settings.widget.VolumeSliderWidget;
import su.plo.voice.proto.data.player.VoicePlayerInfo;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class VolumeTabWidget extends TabWidget {

    private final PlasmoVoiceClient voiceClient;
    private final ClientSourceLineManager sourceLines;

    private String currentSearch = "";

    public VolumeTabWidget(@NotNull VoiceSettingsScreen parent,
                           @NotNull PlasmoVoiceClient voiceClient,
                           @NotNull ClientConfig config) {
        super(parent, voiceClient, config);

        this.voiceClient = voiceClient;
        this.sourceLines = voiceClient.getSourceLineManager();
    }

    @Override
    public void init() {
        super.init();

        addEntry(new CategoryEntry(MinecraftTextComponent.translatable("gui.plasmovoice.volume.sources"), 24));

        List<ClientSourceLine> sourceLines = Lists.newArrayList(this.sourceLines.getLines());
        Collections.reverse(sourceLines);
        sourceLines.forEach(this::createSourceLineVolume);

        addEntry(new CategoryEntry(MinecraftTextComponent.translatable("gui.plasmovoice.volume.players"), 24));
        createPlayersSearch();
        refreshPlayerEntries();
    }

    @EventSubscribe
    public void onPlayerConnected(@NotNull VoicePlayerConnectedEvent event) {
        refreshPlayerEntries();
    }

    @EventSubscribe
    public void onPlayerDisconnected(@NotNull VoicePlayerDisconnectedEvent event) {
        refreshPlayerEntries();
    }

    private void createSourceLineVolume(@NotNull ClientSourceLine sourceLine) {
        DoubleConfigEntry volumeEntry = config.getVoice().getVolumes().getVolume(sourceLine.getName());
        ConfigEntry<Boolean> muteEntry = config.getVoice().getVolumes().getMute(sourceLine.getName());

        List<Button> buttons = Lists.newArrayList();
        Runnable updateButtons = createMuteButtonAction(buttons, muteEntry);

        addEntry(new SourceLineVolumeEntry<>(
                MinecraftTextComponent.translatable(sourceLine.getTranslation()),
                createVolumeSlider(volumeEntry),
                createMuteButton(buttons, updateButtons, muteEntry),
                volumeEntry,
                muteEntry,
                Collections.emptyList(),
                new ResourceLocation(sourceLine.getIcon()),
                (button, element) -> updateButtons.run()
        ));
    }

    private void createPlayersSearch() {
        TextFieldWidget textField = new TextFieldWidget(
                0,
                0,
                0,
                20,
                MinecraftTextComponent.translatable("gui.plasmovoice.volume.players_search").withStyle(MinecraftTextStyle.GRAY)
        );

        textField.setResponder((value) -> {
            this.currentSearch = value.toLowerCase();
            refreshPlayerEntries();
        });

        addEntry(new FullWidthEntry<>(
                textField,
                26
        ));
    }

    private void refreshPlayerEntries() {
        List<Entry> entries = this.entries.stream()
                .filter((entry) -> !(entry instanceof PlayerVolumeEntry))
                .collect(Collectors.toList());

        clearEntries();
        entries.forEach(this::addEntry);

        voiceClient.getServerConnection()
                .ifPresent((connection) -> {
                    connection.getPlayers()
                            .stream()
                            .filter(player -> player.getPlayerNick().toLowerCase().contains(currentSearch))
                            .filter(player -> !UMinecraft.getPlayer().getUUID().equals(player.getPlayerId()))
                            .sorted(Comparator.comparing(VoicePlayerInfo::getPlayerNick))
                            .forEach(this::createPlayerVolume);
                });
    }

    private void createPlayerVolume(@NotNull VoicePlayerInfo player) {
        DoubleConfigEntry volumeEntry = config.getVoice().getVolumes().getVolume("source_" + player.getPlayerId().toString());
        ConfigEntry<Boolean> muteEntry = config.getVoice().getVolumes().getMute("source_" + player.getPlayerId().toString());

        List<Button> buttons = Lists.newArrayList();
        Runnable updateButtons = createMuteButtonAction(buttons, muteEntry);

        addEntry(new PlayerVolumeEntry<>(
                createVolumeSlider(volumeEntry),
                createMuteButton(buttons, updateButtons, muteEntry),
                volumeEntry,
                muteEntry,
                Collections.emptyList(),
                player,
                (button, element) -> updateButtons.run()
        ));
    }

    private VolumeSliderWidget createVolumeSlider(DoubleConfigEntry volumeEntry) {
        return new VolumeSliderWidget(
                voiceClient.getKeyBindings(),
                volumeEntry,
                "%",
                0,
                0,
                ELEMENT_WIDTH - 24,
                20
        );
    }

    private Runnable createMuteButtonAction(@NotNull List<Button> buttons,
                                            @NotNull ConfigEntry<Boolean> muteEntry) {
        return () -> {
            buttons.get(0).setVisible(!muteEntry.value());
            buttons.get(1).setVisible(muteEntry.value());
        };
    }

    private List<Button> createMuteButton(@NotNull List<Button> buttons,
                                          @NotNull Runnable updateButtons,
                                          @NotNull ConfigEntry<Boolean> muteEntry) {
        Button.OnPress buttonClick = (button) -> {
            muteEntry.set(!muteEntry.value());
            updateButtons.run();
        };

        IconButton muteButton = new IconButton(
                0,
                0,
                20,
                20,
                buttonClick,
                Button.NO_TOOLTIP,
                new ResourceLocation("plasmovoice:textures/icons/speaker_menu.png"),
                true
        );

        IconButton unmuteButton = new IconButton(
                0,
                0,
                20,
                20,
                buttonClick,
                Button.NO_TOOLTIP,
                new ResourceLocation("plasmovoice:textures/icons/speaker_menu_disabled.png"),
                true
        );

        muteButton.setVisible(!muteEntry.value());
        unmuteButton.setVisible(muteEntry.value());

        buttons.add(muteButton);
        buttons.add(unmuteButton);

        return buttons;
    }

    class SourceLineVolumeEntry<W extends GuiAbstractWidget> extends ButtonOptionEntry<W> {

        private final ResourceLocation iconLocation;
        private final ConfigEntry<Boolean> muteEntry;

        public SourceLineVolumeEntry(@NotNull MinecraftTextComponent text,
                                     @NotNull W widget,
                                     @NotNull List<Button> buttons,
                                     @NotNull ConfigEntry<?> entry,
                                     @NotNull ConfigEntry<Boolean> muteEntry,
                                     @NotNull List<MinecraftTextComponent> tooltip,
                                     @NotNull ResourceLocation iconLocation,
                                     @NotNull OptionResetAction<W> resetAction) {
            super(text, widget, buttons, entry, tooltip, resetAction);

            this.muteEntry = muteEntry;
            this.iconLocation = iconLocation;
        }

        @Override
        protected void renderText(@NotNull UMatrixStack stack, int index, int x, int y, int entryWidth, int mouseX, int mouseY, boolean hovered, float delta) {
            UGraphics.bindTexture(0, iconLocation);
            UGraphics.color4f(1F, 1F, 1F, 1F);
            RenderUtil.blit(stack, x, y + height / 2 - 8, 0, 0, 16, 16, 16, 16);

            RenderUtil.drawString(
                    stack,
                    text,
                    x + 20,
                    y + height / 2 - UGraphics.getFontHeight() / 2,
                    0xFFFFFF
            );
        }

        @Override
        protected boolean isDefault() {
            return entry.isDefault() && muteEntry.isDefault();
        }

        @Override
        protected void onReset(@NotNull Button button) {
            entry.reset();
            muteEntry.reset();

            if (element instanceof UpdatableWidget)
                ((UpdatableWidget) element).updateValue();

            if (resetAction != null)
                resetAction.onReset(resetButton, element);
        }
    }

    class PlayerVolumeEntry<W extends GuiAbstractWidget> extends ButtonOptionEntry<W> {

        private final VoicePlayerInfo player;
        private final ConfigEntry<Boolean> muteEntry;

        public PlayerVolumeEntry(@NotNull W widget,
                                 @NotNull List<Button> buttons,
                                 @NotNull ConfigEntry<?> entry,
                                 @NotNull ConfigEntry<Boolean> muteEntry,
                                 @NotNull List<MinecraftTextComponent> tooltip,
                                 @NotNull VoicePlayerInfo player,
                                 @NotNull OptionResetAction<W> resetAction) {
            super(MinecraftTextComponent.literal(player.getPlayerNick()), widget, buttons, entry, tooltip, resetAction, 30);

            this.muteEntry = muteEntry;
            this.player = player;
        }

        @Override
        protected void renderText(@NotNull UMatrixStack stack, int index, int x, int y, int entryWidth, int mouseX, int mouseY, boolean hovered, float delta) {
            UGraphics.bindTexture(0, loadSkin());
            UGraphics.color4f(1F, 1F, 1F, 1F);

            int helmY = y + height / 2 - 12;

            // render helm
            RenderUtil.blit(stack, x, helmY, 24, 24, 8F, 8F, 8, 8, 64, 64);
            UGraphics.enableBlend();
            RenderUtil.blit(stack, x, helmY, 24, 24, 40F, 8F, 8, 8, 64, 64);
            UGraphics.disableBlend();

            RenderUtil.drawString(
                    stack,
                    text,
                    x + 30,
                    y + height / 2 - UGraphics.getFontHeight() / 2,
                    0xFFFFFF
            );
        }

        @Override
        protected boolean isDefault() {
            return entry.isDefault() && muteEntry.isDefault();
        }

        @Override
        protected void onReset(@NotNull Button button) {
            entry.reset();
            muteEntry.reset();

            if (element instanceof UpdatableWidget)
                ((UpdatableWidget) element).updateValue();

            if (resetAction != null)
                resetAction.onReset(resetButton, element);
        }

        private ResourceLocation loadSkin() {
            ModPlayerSkins.loadSkin(
                    player.getPlayerId(),
                    player.getPlayerNick(),
                    null
            );
            return ModPlayerSkins.getSkin(player.getPlayerId(), player.getPlayerNick());
        }
    }
}

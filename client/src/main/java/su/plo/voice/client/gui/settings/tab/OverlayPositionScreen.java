package su.plo.voice.client.gui.settings.tab;

import gg.essential.universal.UScreen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.config.entry.EnumConfigEntry;
import su.plo.lib.api.chat.MinecraftTextComponent;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.lib.mod.client.gui.screen.GuiScreen;
import su.plo.lib.mod.client.gui.screen.ScreenWrapper;
import su.plo.voice.client.config.overlay.OverlayPosition;
import su.plo.voice.client.gui.settings.HudPositionScreen;

public final class OverlayPositionScreen extends HudPositionScreen<OverlayPosition> {

    private final OverlayPosition dislabledPosition;

    public OverlayPositionScreen(@NotNull GuiScreen parent,
                                 @NotNull EnumConfigEntry<OverlayPosition> entry,
                                 @Nullable OverlayPosition disabledPosition) {
        super(parent, entry, MinecraftTextComponent.translatable("gui.plasmovoice.overlay.position.choose"));

        this.dislabledPosition = disabledPosition;
    }

    @Override
    public void init() {
        super.init();

        int width = screen.width;
        int height = screen.height;

        addRenderWidget(createPositionButton(
                BUTTON_OFFSET,
                BUTTON_OFFSET,
                OverlayPosition.TOP_LEFT
        ));


        addRenderWidget(createPositionButton(
                width - BUTTON_OFFSET - BUTTON_WIDTH,
                BUTTON_OFFSET,
                OverlayPosition.TOP_RIGHT
        ));


        addRenderWidget(createPositionButton(
                BUTTON_OFFSET,
                height - 20 - BUTTON_OFFSET,
                OverlayPosition.BOTTOM_LEFT
        ));

        addRenderWidget(createPositionButton(
                width - BUTTON_OFFSET - BUTTON_WIDTH,
                height - 20 - BUTTON_OFFSET,
                OverlayPosition.BOTTOM_RIGHT
        ));
    }

    @Override
    protected Button createPositionButton(int x, int y, OverlayPosition iconPosition) {
        Button button = new Button(
                x,
                y,
                BUTTON_WIDTH,
                20,
                MinecraftTextComponent.translatable(iconPosition.getTranslation()),
                (btn) -> {
                    entry.set(iconPosition);
                    ScreenWrapper.openScreen(null);
                },
                Button.NO_TOOLTIP
        );

        button.setActive(iconPosition != dislabledPosition);
        return button;
    }
}

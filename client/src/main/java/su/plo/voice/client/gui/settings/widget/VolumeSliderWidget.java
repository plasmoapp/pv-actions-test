package su.plo.voice.client.gui.settings.widget;

import gg.essential.universal.UGraphics;
import gg.essential.universal.UMatrixStack;
import org.jetbrains.annotations.NotNull;
import su.plo.config.entry.DoubleConfigEntry;
import su.plo.lib.api.chat.MinecraftTextComponent;
import su.plo.lib.mod.client.gui.components.AbstractSlider;
import su.plo.lib.mod.client.render.RenderUtil;
import su.plo.voice.api.client.config.keybind.KeyBinding;
import su.plo.voice.api.client.config.keybind.KeyBindings;

public final class VolumeSliderWidget extends AbstractSlider implements UpdatableWidget {

    private final KeyBindings keyBindings;
    private final DoubleConfigEntry entry;
    private final String suffix;

    public VolumeSliderWidget(@NotNull KeyBindings keyBindings,
                              @NotNull DoubleConfigEntry entry,
                              @NotNull String suffix,
                              int x,
                              int y,
                              int width,
                              int height) {
        super(x, y, width, height);

        this.keyBindings = keyBindings;
        this.entry = entry;
        this.suffix = suffix;

        updateValue();
    }

    @Override
    protected void updateText() {
        this.text = MinecraftTextComponent.literal(Math.round(value * (entry.getMax() * 100D)) + suffix);
    }

    @Override
    protected void applyValue() {
        if (!keyBindings.getPressedKeys().contains(KeyBinding.Type.KEYSYM.getOrCreate(340))) { // GLFW_KEY_LEFT_SHIFT
            this.value = calculateValue(value);
        }
        entry.set(value * entry.getMax());
    }

    @Override
    public void updateValue() {
        this.value = entry.value() / entry.getMax();
        updateText();
    }

    @Override
    protected void renderTrack(@NotNull UMatrixStack stack, int mouseX, int mouseY) {
        UGraphics.bindTexture(0, WIDGETS_LOCATION);
        UGraphics.color4f(1F, 1F, 1F, 1F);
        int k = (isHoveredOrFocused() ? 2 : 1) * 20;

        RenderUtil.blit(stack, x + (int) (value * (double) (getSliderWidth() - 8)), y, 0, 46 + k, 4, 20);
        RenderUtil.blit(stack, x + (int) (value * (double) (getSliderWidth() - 8)) + 4, y, 196, 46 + k, 4, 20);
    }

    private double calculateValue(double value) {
        return (Math.round((value * entry.getMax() * 100D) / 5) * 5) / (entry.getMax() * 100D);
    }
}

package su.plo.lib.mod.client.gui.widget;

import gg.essential.universal.UKeyboard;
import org.jetbrains.annotations.Nullable;

public interface GuiWidgetListener {

    default void mouseMoved(double mouseX, double mouseY) {
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean keyPressed(int keyCode, char typedChar, @Nullable UKeyboard.Modifiers modifiers) {
        return false;
    }

    default boolean keyReleased(int keyCode, char typedChar, @Nullable UKeyboard.Modifiers modifiers) {
        return false;
    }

    default boolean changeFocus(boolean lookForwards) {
        return false;
    }

    default boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }
}

package su.plo.lib.mod.client.gui.components;

import gg.essential.universal.UGraphics;
import gg.essential.universal.UMatrixStack;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import su.plo.lib.api.chat.MinecraftTextComponent;
import su.plo.lib.mod.client.render.RenderUtil;
import su.plo.lib.mod.client.render.shader.SolidColorShader;

public final class IconButton extends Button {

    private final boolean shadow;
    private final int shadowColor;
    @Getter
    @Setter
    private ResourceLocation iconLocation;
    @Getter
    @Setter
    private int iconColor;

    public IconButton(int x,
                      int y,
                      int width,
                      int height,
                      @NotNull OnPress pressAction,
                      @NotNull OnTooltip tooltipAction,
                      @NotNull ResourceLocation iconLocation,
                      boolean shadow) {
        this(x, y, width, height, pressAction, tooltipAction, iconLocation, shadow, -0x1);
    }

    public IconButton(int x,
                      int y,
                      int width,
                      int height,
                      @NotNull OnPress pressAction,
                      @NotNull OnTooltip tooltipAction,
                      @NotNull ResourceLocation iconLocation,
                      boolean shadow,
                      int shadowColor) {
        super(x, y, width, height, MinecraftTextComponent.empty(), pressAction, tooltipAction);

        this.iconLocation = iconLocation;
        this.shadow = shadow;
        this.shadowColor = shadowColor;
    }

    @Override
    public void renderButton(@NotNull UMatrixStack stack, int mouseX, int mouseY, float delta) {
        super.renderButton(stack, mouseX, mouseY, delta);

        UGraphics.bindTexture(0, iconLocation);
        UGraphics.enableDepth();

        if (hasShadow()) {
            int shadowColor = active ? this.shadowColor : -6250336;

            try {
                SolidColorShader.bind();

                RenderUtil.blitWithActiveShader(
                        stack,
                        UGraphics.CommonVertexFormats.POSITION_TEXTURE_COLOR,
                        x + 2,
                        x + 2 + 16,
                        y + 3,
                        y + 3 + 16,
                        0,
                        0, 1F,
                        0, 1F,
                        (int) ((shadowColor >> 16 & 255) * 0.25),
                        (int) ((shadowColor >> 8 & 255) * 0.25),
                        (int) ((shadowColor & 255) * 0.25),
                        shadowColor >> 24 & 255
                );

                SolidColorShader.unbind();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (iconColor != 0) {
            float alpha = (float) (iconColor >> 24 & 255) / 255F;
            float red = (float) (iconColor >> 16 & 255) / 255F;
            float green = (float) (iconColor >> 8 & 255) / 255F;
            float blue = (float) (iconColor & 255) / 255F;

            UGraphics.color4f(red, green, blue, alpha);
        } else {
            UGraphics.color4f(1F, 1F, 1F, 1F);
        }
        RenderUtil.blit(stack, x + 2, y + 2, 0, 0, 16, 16, 16, 16);

        UGraphics.disableDepth();
    }

    public boolean hasShadow() {
        return shadow;
    }
}

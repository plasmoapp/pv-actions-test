package su.plo.lib.mod.client.render.particle;

import gg.essential.universal.UGraphics;
import gg.essential.universal.UMatrixStack;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public abstract class BillboardParticle2D extends Particle2D {
    protected float scale;

    protected BillboardParticle2D(double x, double y) {
        super(x, y);
        this.scale = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
    }

    protected BillboardParticle2D(double x, double y, double velocityX, double velocityY) {
        super(x, y, velocityX, velocityY);
        this.scale = 0.1F * (this.random.nextFloat() * 0.5F + 0.5F) * 2.0F;
    }

    public void buildGeometry(UMatrixStack stack, UGraphics buffer, float tickDelta) {
        float f = (float)(Mth.lerp((double)tickDelta, this.prevPosX, this.x));
        float g = (float)(Mth.lerp((double)tickDelta, this.prevPosY, this.y));

        Vector3f vec3f = new Vector3f(-1.0F, -1.0F, 0.0F);
        Vector3f[] vec3fs = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
        float j = this.getSize(tickDelta);

        for(int k = 0; k < 4; ++k) {
            Vector3f vec3f2 = vec3fs[k];
            vec3f2.mul(j);
            vec3f2.add(f, g, 0);
        }

        float l = this.getMinU();
        float m = this.getMaxU();
        float n = this.getMinV();
        float o = this.getMaxV();

        buffer.pos(stack, (double)vec3fs[0].x(), (double)vec3fs[0].y(), (double)vec3fs[0].z())
                .tex(m, o)
                .color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .endVertex();

        buffer.pos(stack, (double)vec3fs[1].x(), (double)vec3fs[1].y(), (double)vec3fs[1].z())
                .tex(m, n)
                .color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .endVertex();

        buffer.pos(stack, (double)vec3fs[2].x(), (double)vec3fs[2].y(), (double)vec3fs[2].z())
                .tex(l, n)
                .color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .endVertex();

        buffer.pos((double)vec3fs[3].x(), (double)vec3fs[3].y(), (double)vec3fs[3].z())
                .tex(l, o)
                .color(this.colorRed, this.colorGreen, this.colorBlue, this.colorAlpha)
                .endVertex();
    }

    public float getSize(float tickDelta) {
        return this.scale;
    }

    public Particle2D scale(float scale) {
        this.scale *= scale;
        return super.scale(scale);
    }

    protected abstract float getMinU();

    protected abstract float getMaxU();

    protected abstract float getMinV();

    protected abstract float getMaxV();
}

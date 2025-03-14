package su.plo.lib.mod.entity;

import lombok.RequiredArgsConstructor;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import su.plo.lib.api.entity.MinecraftEntity;
import su.plo.lib.api.entity.MinecraftPlayerEntity;
import su.plo.voice.proto.data.pos.Pos3d;

import java.util.UUID;

@RequiredArgsConstructor
public class ModEntity<E extends Entity> implements MinecraftEntity {

    protected final E instance;

    private final Pos3d position = new Pos3d();
    private final Pos3d lookAngle = new Pos3d();

    @Override
    public int getId() {
        return instance.getId();
    }

    @Override
    public @NotNull UUID getUUID() {
        return instance.getUUID();
    }

    @Override
    public @NotNull Pos3d getPosition() {
        return getPosition(position);
    }

    @Override
    public @NotNull Pos3d getPosition(@NotNull Pos3d position) {
        position.setX(instance.position().x());
        position.setY(instance.position().y());
        position.setZ(instance.position().z());

        return position;
    }

    @Override
    public @NotNull Pos3d getLookAngle() {
        return getLookAngle(lookAngle);
    }

    @Override
    public @NotNull Pos3d getLookAngle(@NotNull Pos3d lookAngle) {
        lookAngle.setX(instance.getLookAngle().x());
        lookAngle.setY(instance.getLookAngle().y());
        lookAngle.setZ(instance.getLookAngle().z());

        return lookAngle;
    }

    @Override
    public double getEyeHeight() {
        return instance.getEyeHeight();
    }

    @Override
    public float getHitBoxWidth() {
        return instance.getBbWidth();
    }

    @Override
    public float getHitBoxHeight() {
        return instance.getBbHeight();
    }

    @Override
    public boolean isInvisibleTo(@NotNull MinecraftPlayerEntity player) {
        return instance.isInvisibleTo(player.getInstance());
    }

    @Override
    public <T> T getInstance() {
        return (T) instance;
    }
}

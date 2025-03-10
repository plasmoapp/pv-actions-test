package su.plo.lib.mod.client.render.texture;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import gg.essential.universal.UMinecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.voice.proto.data.player.MinecraftGameProfile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ModPlayerSkins {

    private static final Cache<String, ResourceLocation> skins = CacheBuilder
            .newBuilder()
            .expireAfterAccess(15L, TimeUnit.SECONDS)
            .build();

    public static synchronized void loadSkin(@NotNull UUID playerId,
                                                           @NotNull String nick,
                                                           @Nullable String fallback) {
        PlayerInfo playerInfo = UMinecraft.getNetHandler().getPlayerInfo(playerId);
        if (playerInfo != null) return;

        ResourceLocation skinLocation = skins.getIfPresent(nick);
        if (skinLocation != null) return;

        if (fallback != null) {
            RenderSystem.recordRenderCall(() -> {
                ResourceLocation fallbackIdentifier = new ResourceLocation(
                        "plasmovoice",
                        "skins/" + Hashing.sha1().hashUnencodedChars(nick.toLowerCase())
                );
                try {
                    UMinecraft.getMinecraft().getTextureManager().register(
                            fallbackIdentifier,
                            new DynamicTexture(NativeImage.fromBase64(fallback))
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // fallback
                skins.put(nick, fallbackIdentifier);
            });
        }

        GameProfile profile = new GameProfile(playerId, nick);

        SkinManager skinManager = UMinecraft.getMinecraft().getSkinManager();

        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = skinManager
                .getInsecureSkinInformation(profile);
        if (textures.isEmpty()) {
            skinManager.registerSkins(
                    profile,
                    (type, identifier, texture) -> {
                        if (type.equals(MinecraftProfileTexture.Type.SKIN)) {
                            skins.put(profile.getName(), identifier);
                        }
                    }, false
            );
        } else {
            String hash = Hashing.sha1().hashUnencodedChars(textures.get(MinecraftProfileTexture.Type.SKIN).getHash()).toString();
            ResourceLocation identifier = new ResourceLocation("skins/" + hash);
            skins.put(profile.getName(), identifier);
        }
    }

    public static synchronized void loadSkin(@NotNull MinecraftGameProfile gameProfile) {
        PlayerInfo playerInfo = UMinecraft.getNetHandler().getPlayerInfo(gameProfile.getId());
        if (playerInfo != null) return;

        ResourceLocation skinLocation = skins.getIfPresent(gameProfile.getName());
        if (skinLocation != null) return;

        GameProfile profile = new GameProfile(
                gameProfile.getId(),
                gameProfile.getName()
        );
        gameProfile.getProperties().forEach((property) -> {
            profile.getProperties().put(property.getName(), new Property(
                    property.getName(),
                    property.getValue(),
                    property.getSignature()
            ));
        });

        skinLocation = UMinecraft.getMinecraft().getSkinManager().getInsecureSkinLocation(profile);
        skins.put(gameProfile.getName(), skinLocation);
    }

    public static synchronized @NotNull ResourceLocation getSkin(@NotNull UUID playerId, @NotNull String nick) {
        PlayerInfo playerInfo = UMinecraft.getNetHandler().getPlayerInfo(playerId);
        if (playerInfo != null) {
            return playerInfo.getSkinLocation();
        }

        ResourceLocation skinLocation = skins.getIfPresent(nick);
        if (skinLocation != null) return skinLocation;

        return getDefaultSkin(playerId);
    }

    public static @NotNull ResourceLocation getDefaultSkin(@NotNull UUID playerId) {
        return DefaultPlayerSkin.getDefaultSkin(playerId);
    }

    private ModPlayerSkins() {
    }
}

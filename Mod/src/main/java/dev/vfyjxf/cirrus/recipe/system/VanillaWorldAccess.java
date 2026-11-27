package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cloudlib.api.util.Games;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Side-safe access helpers for native vanilla non-{@code Recipe} systems.
 */
final class VanillaWorldAccess {

    private VanillaWorldAccess() {}

    static @Nullable RegistryAccess registryAccessOrNull() {
        try {
            return Games.registryAccess();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static @Nullable PotionBrewing potionBrewingOrNull() {
        MinecraftServer server = Games.server();
        if (server != null) {
            return server.potionBrewing();
        }
        if (FMLEnvironment.dist.isClient()) {
            try {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.level != null) {
                    return client.level.potionBrewing();
                }
            } catch (RuntimeException | LinkageError ignored) {}
        }
        try {
            RegistryAccess access = registryAccessOrNull();
            return access != null
                    ? PotionBrewing.bootstrap(FeatureFlags.VANILLA_SET, access)
                    : PotionBrewing.bootstrap(FeatureFlags.VANILLA_SET);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static long worldEpoch() {
        long epoch = RecipeApiImpl.attachEpoch();
        RegistryAccess access = registryAccessOrNull();
        long accessHash = access == null ? 0L : System.identityHashCode(access);
        return (epoch * 31L) ^ accessHash;
    }

    static String sanitizePathSegment(@Nullable ResourceLocation id) {
        if (id == null) {
            return "unknown";
        }
        String raw = "minecraft".equals(id.getNamespace())
                ? id.getPath()
                : id.getNamespace() + "_" + id.getPath();
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.isEmpty() ? "unknown" : out.toString();
    }

    static ResourceLocation vanillaId(String prefix, String... parts) {
        StringBuilder path = new StringBuilder(prefix);
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            path.append('/').append(part.toLowerCase(Locale.ROOT));
        }
        return ResourceLocation.withDefaultNamespace(path.toString());
    }
}

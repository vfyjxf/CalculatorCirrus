package dev.vfyjxf.calculatorcirrus.util;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class CirrusLocations {

    public static <T> ResourceKey<Registry<T>> createKey(ResourceLocation location) {
        return ResourceKey.createRegistryKey(location);
    }

    public static <T> ResourceKey<T> createKey(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation location) {
        return ResourceKey.create(registryKey, location);
    }

    public static ResourceLocation parse(String location) {
        return ResourceLocation.parse(location);
    }

    public static @Nullable ResourceLocation maybe(String location) {
        return ResourceLocation.tryParse(location);
    }

    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation ofMc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    public static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.MOD_ID, path);
    }

    private CirrusLocations() {}
}

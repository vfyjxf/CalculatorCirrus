package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.Constants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class CirrusRegistries {

    public static <T> ResourceKey<Registry<T>> createKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }

    public static <T> ResourceKey<Registry<T>> createKey(String namespace, String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private CirrusRegistries() {
    }
}

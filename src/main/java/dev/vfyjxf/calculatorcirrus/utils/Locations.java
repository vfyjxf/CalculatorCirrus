package dev.vfyjxf.calculatorcirrus.utils;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class Locations {

    public static ResourceLocation parse(String location) {
        return ResourceLocation.parse(location);
    }

    public static @Nullable ResourceLocation tryParse(String location) {
        return ResourceLocation.tryParse(location);
    }

    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation ofDefault(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    public static ResourceLocation create(String path) {
        return ResourceLocation.fromNamespaceAndPath("calculatorcirrus", path);
    }

    private Locations() {
    }
}

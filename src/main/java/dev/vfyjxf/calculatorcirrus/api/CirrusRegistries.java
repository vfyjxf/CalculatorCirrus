package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class CirrusRegistries {

    public static final ResourceKey<Registry<IContentType<?>>> CONTENT_TYPE_KEY = createKey("content_type");

    public static final Registry<IContentType<?>> CONTENT_DEFINITION_REGISTRY = new RegistryBuilder<>(CONTENT_TYPE_KEY).sync(true).create();

    public static <T> ResourceKey<Registry<T>> createKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }

    public static <T> ResourceKey<Registry<T>> createKey(String namespace, String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private CirrusRegistries() {
    }
}

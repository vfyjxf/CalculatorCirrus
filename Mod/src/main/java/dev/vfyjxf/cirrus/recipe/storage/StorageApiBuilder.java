package dev.vfyjxf.cirrus.recipe.storage;

import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.register.StorageSourceRendererRegister;
import dev.vfyjxf.cirrus.api.register.StorageTypeRegister;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.concurrent.ConcurrentHashMap;

public class StorageApiBuilder implements StorageTypeRegister, StorageSourceRendererRegister {

    final ConcurrentHashMap<Namespace, StorageSourceType<?>> storageTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, StorageSourceRenderer<?>> renderers = new ConcurrentHashMap<>();

    @Override
    public void registerStorageType(StorageSourceType<?> type) {
        if (storageTypes.containsKey(type.id())) {
            throw new IllegalArgumentException("Storage type already registered: " + type.id());
        }
        storageTypes.put(type.id(), type);
    }

    @Override
    public void registerRenderer(StorageSourceType<?> type, StorageSourceRenderer<?> renderer) {
        if (renderers.containsKey(type.id())) {
            throw new IllegalArgumentException("Renderer already registered for type: " + type.id());
        }
        renderers.put(type.id(), renderer);
    }
}

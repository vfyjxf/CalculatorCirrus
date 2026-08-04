package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;

public interface StorageSourceRendererRegister {

    void registerRenderer(StorageSourceType<?> type, StorageSourceRenderer<?> renderer);

}

package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;

public interface StorageTypeRegister {

    void registerStorageType(StorageSourceType<?> type);

}

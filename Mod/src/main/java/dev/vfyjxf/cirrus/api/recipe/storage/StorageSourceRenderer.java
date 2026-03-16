package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;

public interface StorageSourceRenderer<T extends StorageSource> {

    Widget fromSource(T source);

}

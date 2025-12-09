package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cloudlib.api.util.Namespace;

public record SystemDataType<T>(Namespace id) {

    public static <T> SystemDataType<T> of(Namespace id) {
        return new SystemDataType<>(id);
    }

}

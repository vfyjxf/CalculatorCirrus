package dev.vfyjxf.calculatorcirrus.api.recipe.system;

import net.minecraft.resources.ResourceLocation;

public record SystemDataType<T>(ResourceLocation id) {

    public static <T> SystemDataType<T> of(ResourceLocation id) {
        return new SystemDataType<>(id);
    }

}

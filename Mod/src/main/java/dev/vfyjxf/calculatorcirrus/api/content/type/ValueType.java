package dev.vfyjxf.calculatorcirrus.api.content.type;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public non-sealed interface ValueType<T> extends ContentKind {

    @Override
    ResourceLocation typeId();

    long getValue(T content);

    T fromValue(long value);

    T copy(T content);

    default String toString(T content) {
        return Objects.toString(content);
    }

}

package dev.vfyjxf.calculatorcirrus.api.content.type;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Range;

import java.util.Objects;

public interface ValueType<T> {

    /**
     * @return the unique id of this type
     */
    @Contract(pure = true)
    ResourceLocation typeId();

    @Range(from = 0, to = Long.MAX_VALUE)
    long getValue(T content);

    boolean matches(T fist, T second);

    T fromValue(@Range(from = 0, to = Long.MAX_VALUE)long value);

    T copy(T content);

    default String toString(T content) {
        return Objects.toString(content);
    }

}

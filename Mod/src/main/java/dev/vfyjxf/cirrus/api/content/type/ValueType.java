package dev.vfyjxf.cirrus.api.content.type;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Range;

import java.util.Objects;

public interface ValueType<T> {

    /**
     * @return the unique id of this type
     */
    @Contract(pure = true)
    Namespace typeId();

    @Range(from = 0, to = Long.MAX_VALUE)
    long getValue(T content);

    boolean matches(T fist, T second);

    T fromValue(@Range(from = 0, to = Long.MAX_VALUE) long value);

    T copy(T content);

    default String toString(T content) {
        return Objects.toString(content);
    }

}

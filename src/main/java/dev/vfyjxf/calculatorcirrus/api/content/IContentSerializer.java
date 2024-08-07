package dev.vfyjxf.calculatorcirrus.api.content;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface IContentSerializer<T> {

    default boolean isValid(CompoundTag tag) {
        return true;
    }

    /**
     * Serializes the given object into a {@link CompoundTag}.
     */
    CompoundTag save(IContentStack<T> stack, T value);

    /**
     * Deserializes the given tag into an object.
     *
     * @param tag the tag to deserialize
     * @return the deserialized object
     */
    @Nullable
    T read(CompoundTag tag);

}

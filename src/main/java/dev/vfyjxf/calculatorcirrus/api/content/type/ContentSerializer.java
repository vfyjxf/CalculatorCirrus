package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface ContentSerializer<T> {

    default boolean isValid(CompoundTag tag) {
        return true;
    }

    /**
     * Serializes the given object into a {@link CompoundTag}.
     */
    CompoundTag save(ContentStack<T> stack, T value);

    /**
     * Deserializes the given tag into an object.
     *
     * @param tag the tag to deserialize
     * @return the deserialized object
     */
    @Nullable
    T read(CompoundTag tag);

}

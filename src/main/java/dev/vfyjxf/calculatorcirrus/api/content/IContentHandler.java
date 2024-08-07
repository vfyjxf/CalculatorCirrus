package dev.vfyjxf.calculatorcirrus.api.content;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface IContentHandler<T> {

    /**
     * @return null if the content is not serializable
     */
    @Nullable
    CompoundTag serialize(T content);

    T deserialize(CompoundTag tag);

    @Nullable
    default T merge(T value1, T value2, boolean fuzzy) {
        return null;
    }


}

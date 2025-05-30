package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

public interface ContentType<T> {

    //region Constants

    String namespace = "content_type";

    //endregion

    //region type info

    @Contract(pure = true)
    ResourceLocation typeId();

    @Contract(pure = true)
    Class<T> valueType();

    @Contract(pure = true)
    @Nullable
    TagType<?, T> tagType();

    @Contract(pure = true)
    default boolean supportTags() {
        return tagType() != null;
    }

    //endregion

    //region content info


    /**
     * @return the owner's namespace from the content
     */
    @Contract(pure = true)
    @Nullable
    default String getNamespace(T value) {
        ResourceLocation identifier = getLocation(value);
        if (identifier == null) return null;
        else return identifier.getNamespace();
    }

    @Contract(pure = true)
    @Nullable
    ResourceLocation getLocation(T value);

    //endregion

    //region content operations

    /**
     * @param value the value to check
     * @return true if the value is empty, false otherwise.
     * @implNote This method should be used to check if the value is empty, such as {@link ItemStack#isEmpty()} or {@link FluidStack#isEmpty()}.
     */
    boolean isEmpty(T value);

    T copy(T value);

    T normalize(T value);

    T wildcard(T value);

    default @Nullable DataComponentMap getComponents(T value) {
        return null;
    }

    default DataComponentPatch getComponentsPatch(T value) {
        return DataComponentPatch.EMPTY;
    }


    /**
     * @param value1   value to match against
     * @param value2   value to match with
     * @param strategy the strategy to match the two values, it can be used to ignore amount or components
     * @return true if the two values match according to the strategy, false otherwise.
     * @implNote handle value1 == value2 as a special case, it should return true.
     */
    @Contract(pure = true)
    boolean matches(T value1, T value2, MatchStrategy strategy);

    @Nullable
    ContentSerializer<T> getSerializer();

    /**
     * <b>Note:The hash function should ignore {@link MatchStrategy#withIdentity()}</b>
     *
     * @param value    the value to hash
     * @param strategy the match strategy to use for hashing
     * @return the hash code of the value based on the match strategy
     */
    int hash(T value, MatchStrategy strategy);

    //endregion

    //region tags

    Stream<? extends TagKey<?>> getTags(T value);

    //endregion

    //region utils

    ItemStack cheatStack(T value);

    @SuppressWarnings("unchecked")
    default <C extends ContentType<R>, R> C cast() {
        return (C) this;
    }

    default String asString(T value) {
        return Objects.toString(value);
    }

    //endregion
}

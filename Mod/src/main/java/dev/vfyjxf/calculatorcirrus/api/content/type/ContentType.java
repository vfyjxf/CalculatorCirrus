package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.checkerframework.dataflow.qual.Deterministic;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * @param <T> the type content
 * @see StackContentType
 * @see TagType
 */
public interface ContentType<T> {

    //region CirrusConstants

    String namespace = "content_type";

    //endregion

    //region type info

    @Contract(pure = true)
    ResourceLocation typeId();

    @Contract(pure = true)
    Class<T> valueType();

    @Contract(pure = true)
    @Nullable
    @Deterministic
    TagType<?, ?> tagType();

    Optional<? extends TagType<?, ?>> getTgType();

    @Contract(pure = true)
    @Deterministic
    default boolean hasTag() {
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
     * Checks if the value is empty.
     *
     * @param value the value to check
     * @return true if the value is empty, false otherwise.
     * @implNote This method should be used to check if the value is empty, such as {@link ItemStack#isEmpty()} or {@link FluidStack#isEmpty()}.
     */
    boolean isEmpty(T value);

    /**
     * <b>Note:it maybe not create new instance</b>
     *
     * @param value the value to copy
     * @return a copy of the value, it should be a new instance with the same properties as the original value.
     * @implNote if value is empty, it should return the empty instance of the value type.
     */
    T copy(T value);

    T normalize(T value);

    T wildcard(T value);

    /**
     * Returns the components of the value, if the type supports components.
     * <p>
     * Use {@link DataComponentMap#EMPTY} if the value have no components.
     *
     * @param value the value to get components from
     * @return the components of the value, or null if the type does not support components.
     */
    default @Nullable DataComponentMap getComponents(T value) {
        return null;
    }

    /**
     * @param value the value to get components patch from
     * @return the components patch of the value, or an empty patch if the type does not support components or the value has no components.
     */
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

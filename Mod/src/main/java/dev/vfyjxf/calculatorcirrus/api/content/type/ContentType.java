package dev.vfyjxf.calculatorcirrus.api.content.type;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.checkerframework.dataflow.qual.Deterministic;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @param <T> the type content
 * @see TagType
 */
public interface ContentType<T> {

    //region CirrusConstants

    String namespace = "content_type";

    //endregion

    //region type info

    /**
     * @return the unique id of this type
     */
    @Contract(pure = true)
    ResourceLocation typeId();

    @Contract(pure = true)
    Class<T> valueType();

    @Contract(pure = true)
    @Nullable
    @Deterministic
    TagType<T> tagType();

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

    default boolean supportComponents() {
        return false;
    }

    default @Nullable DataComponentMap getComponents(T value) {return null;}

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
     * @param value1 value to match against
     * @param value2 value to match with
     * @return true if the two values match according to the strategy, false otherwise.
     * @implNote handle value1 == value2 as a special case, it should return true.
     */
    @Contract(pure = true)
    boolean matches(T value1, T value2);

    /**
     * @param value the value to hash
     * @return the hashcode of given value.
     */
    int hash(T value);

    @Nullable Codec<T> contentCodec();

    //endregion

    //region util

    default ItemStack toItemStack(T value) {
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    default <C extends ContentType<R>, R> C cast() {
        return (C) this;
    }

    default String asString(T value) {
        return Objects.toString(value);
    }

    //endregion
}

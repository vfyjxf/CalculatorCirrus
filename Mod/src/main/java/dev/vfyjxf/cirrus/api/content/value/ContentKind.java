package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public sealed interface ContentKind<T> extends ContentVariant permits EmptyContentKind, TypedContentKind {

    //region factory and constant

    static <T> ContentKind<T> fromContent(ContentType<T> type, T value) {
        return new TypedContentKind<>(type, value);
    }

    @SuppressWarnings("unchecked")
    static <T> ContentKind<T> empty() {
        return EmptyContentKind.instance;
    }

    //endregion

    //region content info

    ContentType<T> type();

    T value();

    @Contract(pure = true)
    @Nullable
    default String getNamespace() {
        return type().getNamespace(value());
    }

    @Contract(pure = true)
    @Nullable
    default ResourceLocation getLocation() {
        return type().getLocation(value());
    }

    @Nullable
    default DataComponentMap components() {
        return type().getComponents(value());
    }

    /**
     * Check if the content kind is empty.
     * <p>
     * The {@link ContentKind#value()} of empty content kind shouldn't be read ,and all empty value will be treated as {@link ContentKind#empty()}.
     *
     * @return true if the value is empty, false otherwise
     */
    default boolean isEmpty() {
        return type().isEmpty(value());
    }

    /**
     * @see #isEmpty()
     */
    default boolean notEmpty() {
        return !isEmpty();
    }

    //endregion

    //region match

    /**
     *
     * @param other the other content kind to match against
     * @return whether this content kind matches the other content kind according to the given strategy
     */
    @SuppressWarnings("unchecked")
    default boolean matches(ContentKind<?> other) {
        if (this == other) return true;
        if (other.isEmpty()) return isEmpty();
        if (type() != other.type()) return false;
        return type().matches(this.value(), (T) other.value());
    }

    @SuppressWarnings("unchecked")
    default boolean matchesStackKind(ContentStack<?, ?> stack) {
        if (stack == ContentStack.empty()) return isEmpty();
        if (stack.contentType() != type()) return false;
        return type().matches(value(), (T) stack.content());
    }

    //endregion

    //region util

    default boolean isItem() {
        return type() == CirrusTypes.Contents.ITEM;
    }

    default boolean isFluid() {
        return type() == CirrusTypes.Contents.FLUID;
    }

    @SuppressWarnings("unchecked")
    default <R> ContentKind<R> cast() {
        return (ContentKind<R>) this;
    }

    //endregion

}

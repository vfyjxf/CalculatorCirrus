package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public sealed interface ContentObject<T> extends ContentVariant permits EmptyContentObject, TypedContentObject {

    //region factory and constant

    static <T> ContentObject<T> fromContent(ContentType<T> type, T value) {
        return new TypedContentObject<>(type, value);
    }

    @SuppressWarnings("unchecked")
    static <T> ContentObject<T> empty() {
        return EmptyContentObject.instance;
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
     * Check if the content object is empty.
     * <p>
     * The {@link ContentObject#value()} of empty content object shouldn't be read ,and all empty value will be treated as {@link ContentObject#empty()}.
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
     * @param other    the other content object to match against
     * @param strategy the match strategy
     * @return whether this content object matches the other content object according to the given strategy
     */
    @SuppressWarnings("unchecked")
    default boolean matches(ContentObject<?> other, MatchStrategy strategy) {
        if (this == other) return true;
        if (other.isEmpty()) return isEmpty();
        if (type() != other.type()) return false;
        return type().matches(this.value(), (T) other.value());
    }

    //endregion

    //region tooltip


    /**
     * Append tooltips to the content object.
     *
     * @param configurator the builder to append tooltip to this content object
     * @return this content object
     */
    @Contract("_ -> this")
    ContentObject<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator);

    /**
     * Same as {@link #appendTooltip(BiConsumer)} .
     *
     * @see #appendTooltip(BiConsumer)
     */
    @Contract("_ -> this")
    default ContentObject<T> withTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        return appendTooltip(configurator);
    }

    /**
     * Configure the tooltip for this content object.
     *
     * @param tooltip the rich tooltip to configure.
     * @see #getTooltip(TooltipContext)
     */
    void configureTooltip(RichTooltip tooltip, TooltipContext context);

    /**
     * @param context the tooltip context.
     * @return the rich tooltip for this content object, or null if tooltip is not available or called from server side.
     */
    @Nullable
    RichTooltip getTooltip(TooltipContext context);

    //endregion

    //region util

    @SuppressWarnings("unchecked")
    default <R> ContentObject<R> cast() {
        return (ContentObject<R>) this;
    }

    //endregion

}

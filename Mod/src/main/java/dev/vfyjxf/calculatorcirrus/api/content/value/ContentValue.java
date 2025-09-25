package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public sealed interface ContentValue<T> extends ContentVariant permits EmptyContentValue, TypedContentValue {

    //region factory and constant

    static <T> ContentValue<T> fromContent(ContentType<T> type, T value) {
        return new TypedContentValue<>(type, value);
    }

    @SuppressWarnings("unchecked")
    static <T> ContentValue<T> empty() {
        return EmptyContentValue.instance;
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

    /**
     * Check if the content value is empty.
     * <p>
     * The {@link ContentValue#value()} of empty content value shouldn't be read ,and all empty value will be treated as {@link ContentValue#empty()}.
     *
     * @return true if the value is empty, false otherwise
     */
    default boolean isEmpty() {
        return type().isEmpty(value());
    }

    default boolean notEmpty() {
        return !isEmpty();
    }

    //endregion

    //region match

    @SuppressWarnings("unchecked")
    default <C> boolean matches(ContentValue<C> other, MatchStrategy strategy) {
        if (this == other) return true;
        if (other.isEmpty()) return isEmpty();
        if (type() != other.type()) return false;
        return type().matches(this.value(), (T) other.value());
    }

    //endregion

    //region tooltip


    /**
     * Append tooltips to the content value.
     *
     * @param configurator the builder to append tooltip to this content value
     * @return this content value
     */
    @Contract("_ -> this")
    ContentValue<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator);

    /**
     * Same as {@link #appendTooltip(BiConsumer)} .
     *
     * @see #appendTooltip(BiConsumer)
     */
    @Contract("_ -> this")
    default ContentValue<T> withTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        return appendTooltip(configurator);
    }

    /**
     * Configure the tooltip for this content value.
     *
     * @param tooltip the rich tooltip to configure.
     * @see #getTooltip(TooltipContext)
     */
    void configureTooltip(RichTooltip tooltip, TooltipContext context);

    /**
     * @param context the tooltip context.
     * @return the rich tooltip for this content value, or null if tooltip is not available or called from server side.
     */
    @Nullable
    RichTooltip getTooltip(TooltipContext context);

    //endregion

    //region util

    @SuppressWarnings("unchecked")
    default <R> ContentValue<R> cast() {
        return (ContentValue<R>) this;
    }

    //endregion

}

package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Represents a content value and its type.
 *
 * @param <T> the type of the content value,it can be nullable type.
 */
public sealed interface ContentValue<T>
        permits BasicContentValue, ContentStack, TypedContentValue {

    static <T> ContentValue<T> create(ContentType<T> contentType, T value) {
        return new TypedContentValue<>(contentType, value);
    }

    static ContentValue<?> empty() {
        return EmptyStackContentType.empty();
    }

    /**
     * <b>If the content is empty, it should not be read!</b>
     *
     * @return the value of the content.
     */
    T value();

    ContentType<T> contentType();

    @Contract(pure = true)
    @Nullable
    default String getNamespace() {
        return contentType().getNamespace(value());
    }

    @Contract(pure = true)
    @Nullable
    default ResourceLocation getLocation() {
        return contentType().getLocation(value());
    }

    @Contract(" -> new")
    default ContentValue<T> normalize() {
        var contentType = contentType();
        return create(contentType, contentType.normalize(value()));
    }

    default ContentValue<T> copy() {
        var type = contentType();
        return create(type, type.copy(value()));
    }

    /**
     * Check if the content value is empty.
     * <p>
     * The {@link ContentValue#value()} of empty content value shouldn't be read ,and all empty value will be treated as {@link ContentValue#empty()}.
     *
     * @return true if the value is empty, false otherwise
     */
    default boolean isEmpty() {
        return contentType().isEmpty(value());
    }

    default <C> boolean matches(ContentValue<C> other) {
        return matches(other, MatchStrategy.ALL);
    }

    @SuppressWarnings("unchecked")
    default <C> boolean matches(ContentValue<C> other, MatchStrategy strategy) {
        if (this == other) return true;
        if (other.isEmpty()) return isEmpty();
        if (contentType() != other.contentType()) return false;
        return contentType().matches(this.value(), (T) other.value(), strategy);
    }

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
     * @return the rich tooltip for this content stack, or null if tooltip is not available or called from server side.
     */
    @Nullable
    RichTooltip getTooltip(TooltipContext context);

    default Class<T> getTypeClass() {
        return contentType().valueType();
    }

    @SuppressWarnings("unchecked")
    default <R> ContentValue<R> cast() {
        return (ContentValue<R>) this;
    }

}

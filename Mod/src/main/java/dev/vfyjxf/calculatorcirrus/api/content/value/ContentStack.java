package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public sealed interface ContentStack<T>
        extends ContentValue<T>
        permits BasicContentStack,
                EmptyContentStack,
                TagStack {

    //region factories

    ContentStack<?> EMPTY = empty();

    static ContentStack<?> empty() {
        return EmptyStackContentType.empty();
    }

    static <T> ContentStack<T> create(StackContentType<T> type, T value) {
        return new TypedContentStack<>(type, type.copy(value), 1);
    }

    static <T> ContentStack<T> create(StackContentType<T> type, T value, long amount) {
        return new TypedContentStack<>(type, type.copy(value), amount);
    }

    @Contract("null-> null")
    static @Nullable <T> ContentStack<T> create(@Nullable T content) {
        if (content == null) return null;
        ContentApi contentApi = ContentApi.getInstance();
        ContentType<?> detectedType = contentApi.allContentTypes()
                .detect(contentType -> contentType.valueType().isAssignableFrom(content.getClass()));
        if (detectedType != null) {
            return create(detectedType.cast(), content);
        }
        return null;
    }

    //endregion

    //region builtin types factories
    static ContentStack<ItemStack> create(@Nullable ItemStack stack) {
        if (stack == null) stack = ItemStack.EMPTY;
        return create(CirrusContentTypes.ITEM, stack, stack.getCount());
    }

    static ContentStack<ItemStack> create(@Nullable ItemStack stack, long amount) {
        if (stack == null) stack = ItemStack.EMPTY;
        return create(CirrusContentTypes.ITEM, stack, amount);
    }

    static ContentStack<FluidStack> create(@Nullable FluidStack stack) {
        if (stack == null) stack = FluidStack.EMPTY;
        return create(CirrusContentTypes.FLUID, stack);
    }

    static ContentStack<FluidStack> create(@Nullable FluidStack stack, long amount) {
        if (stack == null) stack = FluidStack.EMPTY;
        return create(CirrusContentTypes.FLUID, stack, amount);
    }

    //endregion

    //region tag factories

    static TagStack<Item, ItemStack> createItemTag(TagKey<Item> tagKey) {
        return TagStack.create(CirrusContentTypes.ITEM_TAG, tagKey);
    }

    static TagStack<Item, ItemStack> createItemTag(TagKey<Item> tagKey, long amount) {
        return TagStack.create(CirrusContentTypes.ITEM_TAG, tagKey, amount);
    }

    static TagStack<Fluid, FluidStack> createFluidTag(TagKey<Fluid> tagKey) {
        return TagStack.create(CirrusContentTypes.FLUID_TAG, tagKey, 1);
    }

    static TagStack<Fluid, FluidStack> createFluidTag(TagKey<Fluid> tagKey, long amount) {
        return TagStack.create(CirrusContentTypes.FLUID_TAG, tagKey, amount);
    }

    //endregion


    StackContentType<T> contentType();

    /**
     * For Stack Contents, the value become identity with {@link DataComponentMap}
     *
     * @return the normalized value of the content stack.
     */
    @Override
    T value();

    default Object identity() {
        return contentType().getIdentity(value());
    }

    default long amount() {
        return contentType().getAmount(this, value());
    }

    @Contract("_ -> this")
    ContentStack<T> setAmount(long amount);

    default ContentStack<T> addAmount(long amount) {
        return setAmount(Math.addExact(amount(), amount));
    }

    default ContentStack<T> multiply(double multiplier) {
        double result = amount() * multiplier;
        if (result > Long.MAX_VALUE) {
            throw new ArithmeticException("The result of the multiplication is too large to be stored in a long.");
        }
        setAmount((long) Math.ceil(result));
        return this;
    }

    default ContentStack<T> multiply(long multiplier) {
        return multiply((double) multiplier);
    }

    default ContentStack<T> reverse() {
        return multiply(-1D);
    }

    @Contract(" -> new")
    @Override
    default ContentStack<T> normalize() {
        return contentType().normalize(this, value());
    }

    @Override
    default ContentStack<T> copy() {
        return contentType().copy(this, value());
    }

    default ContentStack<T> copyWithAmount(long amount) {
        return contentType().copy(this, value()).setAmount(amount);
    }

    /**
     * Check if the content value is empty.
     * <p>
     * The {@link ContentValue#value()} of empty content value shouldn't be read ,and all empty value will be treated as {@link ContentValue#empty()}.
     *
     * @return true if the value is empty, false otherwise
     */
    @Override
    default boolean isEmpty() {
        return contentType().isEmpty(this, value());
    }

    @Nullable
    default DataComponentMap getComponents() {
        return contentType().getComponents(value());
    }

    default DataComponentPatch getComponentsPatch() {
        return contentType().getComponentsPatch(value());
    }

    @Override
    default <C> boolean matches(ContentValue<C> other, MatchStrategy strategy) {
        if (!(other instanceof ContentStack<C> stack)) return false;
        return matches(stack, strategy);
    }

    /**
     * @param other other content stack to match with
     * @param <C>   the type of the content stack
     * @return the strict match of the content stack, it will check the content type and value.
     */
    default <C> boolean matches(ContentStack<C> other) {
        return matches(other, MatchStrategy.ALL);
    }

    default <C> boolean matches(ContentStack<C> other, MatchStrategy strategy) {
        if (strategy.withIdentity() && contentType() != other.contentType()) return false;
        return contentType().matches(this, other.cast(), strategy);
    }

    default Class<T> getTypeClass() {
        return contentType().valueType();
    }

    @SuppressWarnings("unchecked")
    default <R extends T> R castValue() {
        return (R) value();
    }

    /**
     * Append tooltips to the content stack.
     * <p>
     * <b>Only available on client side. Calling from server side will have no effect.</b>
     *
     * @param configurator the builder to append tooltip to this content stack
     * @return this content stack
     */
    @Contract("_ -> this")
    ContentStack<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator);

    /**
     * Same as {@link ContentValue#appendTooltip(BiConsumer)} .
     *
     * @see ContentValue#appendTooltip(BiConsumer)
     */
    @Contract("_ -> this")
    default ContentStack<T> withTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        return appendTooltip(configurator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable RichTooltip getTooltip(TooltipContext context);

    /**
     * {@inheritDoc}
     */

    @Override
    void configureTooltip(RichTooltip tooltip, TooltipContext context);

    ContentStack<ItemStack> cheatsStack();

    @SuppressWarnings("unchecked")
    default <O> ContentStack<O> cast() {
        return (ContentStack<O>) this;
    }

    default boolean isItem() {
        return contentType() == CirrusContentTypes.ITEM;
    }

    default boolean isFluid() {
        return contentType() == CirrusContentTypes.FLUID;
    }

    default boolean is(StackContentType<?> type) {
        return contentType() == type;
    }

    default String asString() {
        return contentType().asString(value());
    }

}

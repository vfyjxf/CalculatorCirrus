package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public sealed interface ContentStack<T, S> extends ContentVariant permits EmptyContentStack, TypedContentStack {

    @SuppressWarnings("unchecked")
    static <C, R> ContentStack<C, R> empty() {
        return EmptyContentStack.instance;
    }


    //region factory

    /**
     * @param type           the stack type
     * @param stack          the identity stack.
     * @param amount         the amount of content stack.
     * @param componentPatch the data components
     * @param <T>            the type of Content
     * @param <S>            the type of Stack
     * @return the created ContentStack,it may be {@link ContentStack#empty()}.
     */
    static <T, S> ContentStack<T, S> fromStack(StackType<T, S> type, S stack, long amount, DataComponentPatch componentPatch) {
        stack = type.fromStack(stack, type.quantity(), componentPatch);
        if (type.isEmpty(stack)) return empty();
        return new TypedContentStack<>(type, stack, amount);
    }

    static <T, S> ContentStack<T, S> fromStack(StackType<T, S> type, S stack, long amount) {
        return fromStack(type, stack, amount, type.getComponentsPatch(stack));
    }

    static <T, S> ContentStack<T, S> fromStack(StackType<T, S> type, S stack) {
        return fromStack(type, stack, type.quantity());
    }

    static <T, S> ContentStack<T, S> fromStack(ResourceLocation typeId, S stack) {
        StackType<T, S> stackType = ContentApi.getInstance().getStackType(typeId);
        if (stackType == null) {
            throw new IllegalArgumentException("No StackType found for type ID: " + typeId);
        }
        return fromStack(stackType, stack);
    }

    static <T, S> ContentStack<T, S> fromContent(StackType<T, S> type, T content, long amount, DataComponentPatch componentPatch) {
        S identityStack = type.fromContent(content, type.quantity(), componentPatch);
        return fromStack(type, identityStack, amount);
    }

    static <T, S> ContentStack<T, S> fromContent(StackType<T, S> type, T content, long amount) {
        S stack = type.fromContent(content, type.quantity());
        return fromStack(type, stack, amount);
    }

    //endregion

    //region builtin type factory

    //region item

    static ContentStack<Item, ItemStack> fromStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.itemStack;
        return fromStack(type, type.copy(stack), type.getAmount(stack));
    }

    static ContentStack<Item, ItemStack> fromStack(@Nullable ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.itemStack;
        return fromStack(type, type.copy(stack), amount);
    }

    static ContentStack<Item, ItemStack> fromContent(Item item, long amount, DataComponentPatch componentPatch) {
        return fromContent(CirrusTypes.itemStack, item, amount, componentPatch);
    }

    static ContentStack<Item, ItemStack> fromContent(Item item, long amount) {
        return fromContent(CirrusTypes.itemStack, item, amount);
    }

    static ContentStack<Item, ItemStack> fromContent(Item item) {
        return fromContent(CirrusTypes.itemStack, item, CirrusTypes.itemStack.quantity());
    }

    //endregion

    //region fluid

    static ContentStack<Fluid, FluidStack> fromStack(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.fluidStack;
        return fromStack(type, type.copy(stack), type.getAmount(stack));
    }

    static ContentStack<Fluid, FluidStack> fromStack(@Nullable FluidStack stack, long amount) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.fluidStack;
        return fromStack(type, type.copy(stack), amount);
    }

    static ContentStack<Fluid, FluidStack> fromContent(Fluid fluid, long amount, DataComponentPatch componentPatch) {
        return fromContent(CirrusTypes.fluidStack, fluid, amount, componentPatch);
    }

    static ContentStack<Fluid, FluidStack> fromContent(Fluid fluid, long amount) {
        return fromContent(CirrusTypes.fluidStack, fluid, amount);
    }

    static ContentStack<Fluid, FluidStack> fromContent(Fluid fluid) {
        return fromContent(CirrusTypes.fluidStack, fluid, CirrusTypes.fluidStack.quantity());
    }

    //endregion

    //endregion

    //region stack info

    StackType<T, S> type();

    S value();

    default T content() {
        return type().getContent(value());
    }

    default boolean isEmpty() {
        return this == empty() || amount() <= 0 || type().isEmpty(value());
    }

    long amount();

    @Contract("_ -> this")
    ContentStack<T, S> setAmount(long amount);

    default long maxStackSize() {
        return type().getMaxStackSize(value());
    }

    @Nullable
    default DataComponentMap components() {
        return type().getComponents(value());
    }

    default DataComponentPatch componentsPatch() {
        return type().getComponentsPatch(value());
    }

    default boolean matches(ContentStack<?, ?> other, MatchStrategy strategy) {
        if (type() != other.type()) return false;
        return (!strategy.withAmount() || amount() == other.amount()) &&
                type().matches(value(), other.<T, S>cast().value(), strategy);

    }

    default boolean matches(ContentStack<?, ?> other) {
        return matches(other, MatchStrategy.IDENTITY_COMPONENT);
    }

    //endregion

    //region copy

    @Contract(" -> new")
    default ContentStack<T, S> copy() {
        if (isEmpty()) return empty();
        return fromStack(type(), value(), amount(), componentsPatch());
    }

    default ContentStack<T, S> copyWithAmount(long amount) {
        if (isEmpty()) return empty();
        return fromStack(type(), value(), amount, componentsPatch());
    }

    @Contract(" -> new")
    default ContentStack<T, S> normalize() {
        if (isEmpty()) return empty();
        return fromStack(type(), value(), type().quantity(), componentsPatch());
    }

    default ContentStack<T, S> wildcard() {
        if (isEmpty()) return empty();
        return fromStack(type(), type().wildcard(value()), type().quantity());
    }

    //endregion

    //region tooltip

    /**
     * Append tooltips to the content stack.
     *
     * @param configurator the builder to append tooltip to this content stack
     * @return this content stack
     */
    @Contract("_ -> this")
    ContentStack<T, S> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator);

    /**
     * Same as {@link #appendTooltip(BiConsumer)} .
     *
     * @see #appendTooltip(BiConsumer)
     */
    @Contract("_ -> this")
    default ContentStack<T, S> withTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        return appendTooltip(configurator);
    }

    /**
     * Configure the tooltip for this content stack.
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

    //endregion

    //region util

    default boolean isItem() {
        return type() == CirrusTypes.itemStack;
    }

    default boolean isFluid() {
        return type() == CirrusTypes.fluidStack;
    }

    default boolean is(StackType<?, ?> type) {
        return type() == type;
    }

    @SuppressWarnings("unchecked")
    default <C, R> ContentStack<C, R> cast() {
        return (ContentStack<C, R>) this;
    }

    ContentStack<Item, ItemStack> cheatsStack();

    //endregion

    //region amount util

    default ContentStack<T, S> addAmount(long amount) {
        return setAmount(Math.addExact(amount(), amount));
    }

    default ContentStack<T, S> decrease(long amount) {
        return setAmount(Math.addExact(amount(), -amount));
    }

    default ContentStack<T, S> multiply(double multiplier) {
        double result = amount() * multiplier;
        if (result > Long.MAX_VALUE) {
            throw new ArithmeticException("The result of the multiplication is too large to be stored in a long.");
        }
        setAmount((long) Math.ceil(result));
        return this;
    }

    default ContentStack<T, S> multiply(long multiplier) {
        return multiply((double) multiplier);
    }

    default ContentStack<T, S> reverse() {
        return multiply(-1D);
    }

    //endregion

}

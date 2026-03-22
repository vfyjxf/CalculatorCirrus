package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public sealed interface ContentStack<T, S> extends ContentVariant, ContentResource permits EmptyContentStack, TypedContentStack {

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
        return new TypedContentStack<>(type, amount, stack);
    }

    static <T, S> ContentStack<T, S> fromStack(StackType<T, S> type, S stack, long amount) {
        return fromStack(type, stack, amount, type.getComponentsPatch(stack));
    }

    static <T, S> ContentStack<T, S> fromStack(StackType<T, S> type, S stack) {
        return fromStack(type, stack, type.quantity());
    }

    static <T, S> ContentStack<T, S> fromStack(Namespace typeId, S stack) {
        StackType<T, S> stackType = ContentApi.instance().getStackType(typeId);
        return fromStack(stackType, stack);
    }

    static <T, S> ContentStack<T, S> fromContent(StackType<T, S> type, T content, long amount, DataComponentPatch componentPatch) {
        S stack = type.fromContent(content, type.quantity(), componentPatch);
        return fromStack(type, stack, amount);
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
        var type = CirrusTypes.Contents.ITEM_STACK;
        return fromStack(type, type.copy(stack), type.getAmount(stack));
    }

    static ContentStack<Item, ItemStack> fromStack(@Nullable ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.Contents.ITEM_STACK;
        return fromStack(type, type.copy(stack), amount);
    }

    static ContentStack<Item, ItemStack> fromContent(Item item, long amount, DataComponentPatch componentPatch) {
        return fromContent(CirrusTypes.Contents.ITEM_STACK, item, amount, componentPatch);
    }

    static ContentStack<Item, ItemStack> fromContent(Item item, long amount) {
        return fromContent(CirrusTypes.Contents.ITEM_STACK, item, amount);
    }

    static ContentStack<Item, ItemStack> fromContent(Item item) {
        return fromContent(CirrusTypes.Contents.ITEM_STACK, item, CirrusTypes.Contents.ITEM_STACK.quantity());
    }

    //endregion

    //region fluid

    static ContentStack<Fluid, FluidStack> fromStack(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.Contents.FLUID_STACK;
        return fromStack(type, type.copy(stack), type.getAmount(stack));
    }

    static ContentStack<Fluid, FluidStack> fromStack(@Nullable FluidStack stack, long amount) {
        if (stack == null || stack.isEmpty()) return empty();
        var type = CirrusTypes.Contents.FLUID_STACK;
        return fromStack(type, type.copy(stack), amount);
    }

    static ContentStack<Fluid, FluidStack> fromContent(Fluid fluid, long amount, DataComponentPatch componentPatch) {
        return fromContent(CirrusTypes.Contents.FLUID_STACK, fluid, amount, componentPatch);
    }

    static ContentStack<Fluid, FluidStack> fromContent(Fluid fluid, long amount) {
        return fromContent(CirrusTypes.Contents.FLUID_STACK, fluid, amount);
    }

    static ContentStack<Fluid, FluidStack> fromContent(Fluid fluid) {
        return fromContent(CirrusTypes.Contents.FLUID_STACK, fluid, CirrusTypes.Contents.FLUID_STACK.quantity());
    }

    //endregion

    //endregion

    //region stack info

    StackType<T, S> type();

    ContentType<T> contentType();

    @Contract(pure = true)
    VariantType<T, S> variantType();

    S value();

    default T content() {
        return type().getContent(value());
    }

    default ContentKind<T> kind() {
        return ContentKind.fromContent(contentType(), content());
    }

    default boolean isEmpty() {
        return this == empty() || amount() <= 0 || type().isEmpty(value());
    }

    default boolean notEmpty() {
        return !isEmpty();
    }

    default boolean isValueEmpty() {
        return this == empty() || type().isEmpty(value());
    }

    default boolean valueNotEmpty() {
        return !isValueEmpty();
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

    default boolean hasComponent(DataComponentType<?> componentType) {
        return type().hasComponent(value(), componentType);
    }

    default <C> @UnknownNullability C getComponent(DataComponentType<? extends C> componentType) throws IllegalArgumentException {
        if (!hasComponent(componentType)) {
            ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(componentType);
            throw new IllegalArgumentException("Component: " + key + " not found");
        }
        return type().getComponent(value(), componentType);
    }

    //endregion

    //region match

    default boolean matches(ContentStack<?, ?> other, MatchStrategy strategy) {
        if (this == other) return true;
        if (this == empty() || other == empty()) return false;
        if (type() != other.type()) return false;

        return (!strategy.withAmount() || amount() == other.amount()) &&
               type().matches(value(), other.<T, S>cast().value(), variantType(), strategy);
    }

    default boolean matches(ContentStack<?, ?> other) {
        return matches(other, MatchStrategy.identityAndComponent);
    }

    default boolean matchesContent(ContentKind<?> content) {
        if (this == empty() || content == ContentKind.empty()) {
            return this == empty() && content == ContentKind.empty();
        }
        return contentType() == content.type() &&
               contentType().matches(content(), content.<T>cast().value());
    }

    @Override
    default boolean matchesResource(ContentResource resource) {
        return resource instanceof ContentStack<?, ?> stack && matches(stack);
    }

    //endregion

    //region copy

    @Contract(" -> new")
    default ContentStack<T, S> copy() {
        if (isEmpty()) return empty();
        return fromStack(type(), value(), amount(), componentsPatch());
    }

    @Contract("_ -> new")
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

    //region type util

    default boolean isItem() {
        return type() == CirrusTypes.Contents.ITEM_STACK;
    }

    default boolean isFluid() {
        return type() == CirrusTypes.Contents.FLUID_STACK;
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

    default ContentStack<T, S> increase(long amount) {
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

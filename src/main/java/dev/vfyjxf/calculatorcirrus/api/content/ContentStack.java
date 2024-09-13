package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.content.EmptyContentStack;
import dev.vfyjxf.calculatorcirrus.content.TagContentStack;
import dev.vfyjxf.calculatorcirrus.content.TypedContentStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public interface ContentStack<T> {

    @SuppressWarnings("unchecked")
    static <T> ContentStack<T> empty() {
        return EmptyContentStack.INSTANCE;
    }

    static <T> ContentStack<T> create(ContentType<T> type, T value) {
        return new TypedContentStack<>(type, value, 1);
    }

    static <T> ContentStack<T> create(ContentType<T> type, T value, long amount) {
        return new TypedContentStack<>(type, value, amount);
    }

    @SuppressWarnings("unchecked")
    static @Nullable <T> ContentStack<T> create(T content) {
        ContentApi contentApi = ContentApi.getInstance();
        ContentType<T> detect = (ContentType<T>) contentApi.allContentTypes()
                .detect(contentType -> contentType.getTypeClass() == content);
        if (detect != null) {
            return create(detect, content);
        }
        return null;
    }

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

    static <
            STACK, VALUE> TagStack<STACK, VALUE> create(TagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey) {
        return new TagContentStack<>(tagType, tagKey, 1);
    }

    static <
            STACK, VALUE> TagStack<STACK, VALUE> create(
            TagType<STACK, VALUE> tagType, TagKey<VALUE> tagKey,
            long amount
    ) {
        return new TagContentStack<>(tagType, tagKey, amount);
    }

    static TagStack<ItemStack, Item> createItemTag(TagKey<Item> tagKey) {
        return create(CirrusContentTypes.ITEM_TAG, tagKey);
    }

    static TagStack<ItemStack, Item> createItemTag(TagKey<Item> tagKey, long amount) {
        return create(CirrusContentTypes.ITEM_TAG, tagKey, amount);
    }

    static TagStack<FluidStack, Fluid> createFluidTag(TagKey<Fluid> tagKey) {
        return create(CirrusContentTypes.FLUID_TAG, tagKey, 1);
    }

    static TagStack<FluidStack, Fluid> createFluidTag(TagKey<Fluid> tagKey, long amount) {
        return create(CirrusContentTypes.FLUID_TAG, tagKey, amount);
    }

    T value();

    ContentType<T> contentType();

    default Object getBase() {
        return contentType().getBase(value());
    }

    default long getAmount() {
        return contentType().getAmount(this, value());
    }

    @Contract("_ -> this")
    ContentStack<T> setAmount(long amount);

    default ContentStack<T> addAmount(long amount) {
        return setAmount(Math.addExact(getAmount(), amount));
    }

    default ContentStack<T> multiply(double multiplier) {
        double result = getAmount() * multiplier;
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
    default ContentStack<T> normalize() {
        if (isNormalized()) return this;
        ContentType<T> contentType = contentType();
        return create(contentType, contentType.normalize(this, value()));
    }

    default ContentStack<T> copy() {
        var type = contentType();
        return create(type, type.copy(this, value()));
    }

    default ContentStack<T> copyWithAmount(long amount) {
        var type = contentType();
        return create(type, type.copy(this, value()), amount);
    }

    default boolean isEmpty() {
        return contentType().isEmpty(this, value());
    }

    default boolean isNormalized() {
        return getAmount() == 1;
    }

    default <C> boolean matches(ContentStack<C> other) {
        return matches(other, MatchContext.EXACT);
    }

    default <C> boolean matches(ContentStack<C> other, MatchContext matchContext) {
        if (this == other) return true;
        if (other.isEmpty()) return isEmpty();
        if (contentType() != other.contentType()) return false;
        return contentType().matches(this, other.cast(), matchContext);
    }

    default Class<T> getTypeClass() {
        return contentType().getTypeClass();
    }

    @SuppressWarnings("unchecked")
    default <R> R castValue() {
        return (R) value();
    }

    default CompoundTag save() {
        if (supportSaving()) {
            ContentSerializer<T> serializer = contentType().getSerializer();
            assert serializer != null;
            return serializer.save(this, value());
        }
        throw new UnsupportedOperationException(contentType() + " does not support saving");
    }

    default boolean supportSaving() {
        ContentSerializer<T> serializer = contentType().getSerializer();
        return serializer != null;
    }

    @Nullable
    Tooltip getTooltip(TooltipContext context);

    default ContentStack<T> appendTooltip(String... tooltips) {
        return appendTooltip(Arrays.stream(tooltips).map(Component::translatable).toArray(Component[]::new));
    }

    default ContentStack<T> appendTooltip(Component... tooltips) {
        return appendTooltip(Arrays.stream(tooltips).map(Tooltip::entry).toList());
    }

    ContentStack<T> appendTooltip(List<Tooltip.Entry> tooltips);

    ContentStack<T> tooltip(Tooltip tooltip);

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

    default boolean is(ContentType<?> type) {
        return contentType() == type;
    }

    default String getAsString() {
        return contentType().getAsString(value());
    }

}

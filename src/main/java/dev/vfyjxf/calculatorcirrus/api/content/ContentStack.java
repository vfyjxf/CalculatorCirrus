package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.content.EmptyContentStack;
import dev.vfyjxf.calculatorcirrus.content.TypedContentStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public interface ContentStack<T> {

    static ContentStack<Unit> empty() {
        return EmptyContentStack.INSTANCE;
    }

    static <T> ContentStack<T> of(ContentType<T> type, T value) {
        return new TypedContentStack<>(type, value);
    }

    static ContentStack<ItemStack> of(ItemStack stack) {
        return of(CirrusContentTypes.ITEM, stack);
    }

    static ContentStack<FluidStack> of(FluidStack stack) {
        return of(CirrusContentTypes.FLUID, stack);
    }

    T value();

    ContentType<T> contentType();

    default boolean isEmpty() {
        return contentType().isEmpty(this, value());
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

    default <O> ContentStack<O> cast() {
        return (ContentStack<O>) this;
    }

}

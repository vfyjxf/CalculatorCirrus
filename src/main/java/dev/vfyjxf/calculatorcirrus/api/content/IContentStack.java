package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
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

public interface IContentStack<T> {

    static IContentStack<Unit> empty() {
        return EmptyContentStack.INSTANCE;
    }

    static <T> IContentStack<T> of(IContentType<T> type, T value) {
        return new TypedContentStack<>(type, value);
    }

    static IContentStack<ItemStack> of( ItemStack stack) {
        return of(CirrusContentTypes.ITEM, stack);
    }

    static IContentStack<FluidStack> of(FluidStack stack) {
        return of(CirrusContentTypes.FLUID, stack);
    }

    T value();

    IContentType<T> type();

    default boolean isEmpty() {
        return type().isEmpty(this, value());
    }

    default Class<T> getTypeClass() {
        return type().getTypeClass();
    }

    @SuppressWarnings("unchecked")
    default <R> R castValue() {
        return (R) value();
    }

    default CompoundTag save() {
        if (supportSaving()) {
            IContentSerializer<T> serializer = type().getSerializer();
            assert serializer != null;
            return serializer.save(this, value());
        }
        throw new UnsupportedOperationException(type() + " does not support saving");
    }

    default boolean supportSaving() {
        IContentSerializer<T> serializer = type().getSerializer();
        return serializer != null;
    }

    @Nullable
    ITooltip getTooltip(TooltipContext context);

    default IContentStack<T> appendTooltip(String... tooltips) {
        return appendTooltip(Arrays.stream(tooltips).map(Component::translatable).toArray(Component[]::new));
    }

    default IContentStack<T> appendTooltip(Component... tooltips) {
        return appendTooltip(Arrays.stream(tooltips).map(ITooltip::entry).toList());
    }

    IContentStack<T> appendTooltip(List<ITooltip.Entry> tooltips);

    IContentStack<T> tooltip(ITooltip tooltip);

    IContentStack<ItemStack> cheatsStack();

    default <O> IContentStack<O> cast() {
        return (IContentStack<O>) this;
    }

}

package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.EnergyValue;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.TimeValue;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public sealed interface OutputContent
        extends RecipeContent
        permits EmptyRecipeContent,
                StackContent,
                ValueContent {

    //region factory

    //region value factory

    static <T> OutputContent fromValue(ValueType<T> type, T value, float chance) {
        return new ValueContent<>(type, value, chance);
    }

    static <T> OutputContent fromValue(ValueType<T> type, T value) {
        return fromValue(type, value, MAX_CHANCE);
    }

    //region time value factory

    static OutputContent fromTime(TimeValue timeValue) {
        return fromValue(CirrusTypes.time, timeValue);
    }

    static OutputContent fromTimeTick(int ticks) {
        return fromValue(CirrusTypes.time, TimeValue.fromTick(ticks));
    }

    static OutputContent fromTimeSeconds(int seconds) {
        return fromValue(CirrusTypes.time, TimeValue.fromSecond(seconds));
    }

    static OutputContent fromTimeMinutes(int minutes) {
        return fromValue(CirrusTypes.time, TimeValue.fromMinute(minutes));
    }

    //endregion

    //region forge energy value factory

    static OutputContent fromEnergy(EnergyValue energyValue) {
        return fromValue(CirrusTypes.energy, energyValue);
    }

    static OutputContent fromEnergy(long energy) {
        return fromValue(CirrusTypes.energy, EnergyValue.from(energy));
    }

    //endregion

    //endregion

    //region stack factory

    static <T, S> OutputContent fromStack(StackType<T, S> type, S stack, long amount, float chance) {
        if (amount <= 0) return empty;
        ContentStack<T, S> contentStack = ContentStack.fromStack(type, stack, amount);
        if (contentStack.isEmpty()) return empty;
        return new StackContent<>(type, contentStack, chance);
    }

    static <T, S> OutputContent fromStack(StackType<T, S> type, S stack, long amount) {
        return fromStack(type, stack, amount, MAX_CHANCE);
    }

    static <T, S> OutputContent fromStack(StackType<T, S> type, S stack, float chance) {
        return fromStack(type, stack, type.getAmount(stack), chance);
    }

    static <T, S> OutputContent fromStack(StackType<T, S> type, S stack) {
        return fromStack(type, stack, type.getAmount(stack), MAX_CHANCE);
    }

    static OutputContent fromItemStack(ItemStack stack, long amount, float chance) {
        return fromStack(CirrusTypes.itemStack, stack, amount, chance);
    }

    static OutputContent fromItemStack(ItemStack stack, long amount) {
        return fromItemStack(stack, amount, MAX_CHANCE);
    }

    static OutputContent fromItemStack(ItemStack stack) {
        return fromItemStack(stack, stack.getCount(), MAX_CHANCE);
    }

    static OutputContent fromFluidStack(FluidStack stack, long amount, float chance) {
        return fromStack(CirrusTypes.fluidStack, stack, amount, chance);
    }

    static OutputContent fromFluidStack(FluidStack stack, long amount) {
        return fromFluidStack(stack, amount, MAX_CHANCE);
    }

    static OutputContent fromFluidStack(FluidStack stack) {
        return fromFluidStack(stack, stack.getAmount(), MAX_CHANCE);
    }

    //endregion

    //endregion


}

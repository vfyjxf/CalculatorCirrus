package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.value.EnergyValue;
import dev.vfyjxf.cirrus.api.recipe.value.TimeValue;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public sealed interface OutputContent
    extends RecipeContent
    permits EmptyRecipeContent,
            StackContent,
            ValueContent {

    //region factory

    //region value factory

    static <T> OutputContent fromValue(ContentValue<T> value, float chance) {
        return new ValueContent<>(value.type(), value.content(), chance);
    }

    static <T> OutputContent fromValue(ContentValue<T> value) {
        return fromValue(value, MAX_CHANCE);
    }

    static <T> OutputContent fromValue(ValueType<T> type, T value, float chance) {
        return new ValueContent<>(type, value, chance);
    }

    static <T> OutputContent fromValue(ValueType<T> type, T value) {
        return fromValue(type, value, MAX_CHANCE);
    }

    //region time value factory

    static OutputContent fromTime(TimeValue timeValue) {
        return fromValue(CirrusTypes.Contents.TIME, timeValue);
    }

    static OutputContent fromTimeTick(int ticks) {
        return fromValue(CirrusTypes.Contents.TIME, TimeValue.fromTick(ticks));
    }

    static OutputContent fromTimeSeconds(int seconds) {
        return fromValue(CirrusTypes.Contents.TIME, TimeValue.fromSecond(seconds));
    }

    static OutputContent fromTimeMinutes(int minutes) {
        return fromValue(CirrusTypes.Contents.TIME, TimeValue.fromMinute(minutes));
    }

    //endregion

    //region forge energy value factory

    static OutputContent fromEnergy(EnergyValue energyValue) {
        return fromValue(CirrusTypes.Contents.ENERGY, energyValue);
    }

    static OutputContent fromEnergy(long energy) {
        return fromValue(CirrusTypes.Contents.ENERGY, EnergyValue.from(energy));
    }

    //endregion

    //endregion

    //region stack factory

    static <T, S> OutputContent fromStack(ContentStack<T, S> stack, float chance) {
        if (stack.isEmpty()) return empty;
        return new StackContent<>(stack.type(), stack.copy(), chance);
    }

    static <T, S> OutputContent fromStack(ContentStack<T, S> stack, MatchStrategy matchStrategy) {
        if (stack.isEmpty()) return empty;
        return new StackContent<>(stack.type(), stack.copy(), MAX_CHANCE, matchStrategy);
    }

    static <T, S> OutputContent fromStack(ContentStack<T, S> stack, float chance, MatchStrategy matchStrategy) {
        if (stack.isEmpty()) return empty;
        return new StackContent<>(stack.type(), stack.copy(), chance, matchStrategy);
    }

    static <T, S> OutputContent fromStack(ContentStack<T, S> stack) {
        return fromStack(stack, MAX_CHANCE);
    }

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
        return fromStack(CirrusTypes.Contents.ITEM_STACK, stack, amount, chance);
    }

    static OutputContent fromItemStack(ItemStack stack, long amount) {
        return fromItemStack(stack, amount, MAX_CHANCE);
    }

    static OutputContent fromItemStack(ItemStack stack) {
        return fromItemStack(stack, stack.getCount(), MAX_CHANCE);
    }

    static OutputContent fromFluidStack(FluidStack stack, long amount, float chance) {
        return fromStack(CirrusTypes.Contents.FLUID_STACK, stack, amount, chance);
    }

    static OutputContent fromFluidStack(FluidStack stack, long amount) {
        return fromFluidStack(stack, amount, MAX_CHANCE);
    }

    static OutputContent fromFluidStack(FluidStack stack) {
        return fromFluidStack(stack, stack.getAmount(), MAX_CHANCE);
    }

    //endregion

    //endregion

    //region copy

    @Override
    OutputContent copyWithAmount(long amount);

    @Override
    OutputContent copyWithChance(float chance);

    @Override
    OutputContent copyWithAmountAndChance(long amount, float chance);

    @Override
    OutputContent copy();

    //endregion

    //region match

    boolean is(ContentVariant variant);

    //endregion

    //region util

    default ContentResource asResource() {
        return switch (this) {
            case EmptyRecipeContent ignored -> ContentStack.empty();
            case StackContent<?, ?> stack -> stack.content();
            case ValueContent<?> value -> value.content();
        };
    }

    //endregion

}
package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentVariant;
import dev.vfyjxf.calculatorcirrus.helper.TagHelper;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;

import static dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes.*;


public sealed interface InputContent
        extends RecipeContent
        permits EmptyRecipeContent,
                StackContent,
                ValueContent,
                StackListContent,
                TypedStackListContent,
                TagStackContent {


    //region stack factory

    static <T, S> InputContent fromStack(StackType<T, S> type, S stack, long amount, float chance) {
        if (amount <= 0) return empty;
        ContentStack<T, S> contentStack = ContentStack.fromStack(type, stack, amount);
        if (contentStack.isEmpty()) return empty;
        return new StackContent<>(type, contentStack, chance);
    }

    static <T, S> InputContent fromStack(StackType<T, S> type, S stack, long amount) {
        return fromStack(type, stack, amount, MAX_CHANCE);
    }

    static <T, S> InputContent fromStack(StackType<T, S> type, S stack, float chance) {
        return fromStack(type, stack, type.getAmount(stack), chance);
    }

    static <T, S> InputContent fromStack(StackType<T, S> type, S stack) {
        return fromStack(type, stack, type.getAmount(stack), MAX_CHANCE);
    }

    //endregion

    //region item stack factory
    static InputContent fromItemStack(ItemStack stack, long amount, float chance) {
        return fromStack(itemStack, stack, amount, chance);
    }

    static InputContent fromItemStack(ItemStack stack, long amount) {
        return fromItemStack(stack, amount, MAX_CHANCE);
    }

    static InputContent fromItemStack(ItemStack stack, float chance) {
        return fromItemStack(stack, stack.getCount(), chance);
    }

    static InputContent fromItemStack(ItemStack stack) {
        return fromItemStack(stack, stack.getCount(), MAX_CHANCE);
    }

    //endregion

    //region fluid stack factory
    static InputContent fromFluidStack(FluidStack stack, long amount, float chance) {
        return fromStack(fluidStack, stack, amount, chance);
    }

    static InputContent fromFluidStack(FluidStack stack, long amount) {
        return fromFluidStack(stack, amount, MAX_CHANCE);
    }

    static InputContent fromFluidStack(FluidStack stack, float chance) {
        return fromFluidStack(stack, stack.getAmount(), chance);
    }

    static InputContent fromFluidStack(FluidStack stack) {
        return fromFluidStack(stack, stack.getAmount(), MAX_CHANCE);
    }

    //endregion

    //region tag factory

    static <T> InputContent fromTag(TagType<T> type, TagKey<T> tag, long amount, float chance) {
        return new TagStackContent<>(type, tag, amount, chance);
    }

    static <T> InputContent fromTag(TagType<T> type, TagKey<T> tag, long amount) {
        return new TagStackContent<>(type, tag, amount, MAX_CHANCE);
    }

    //item tag

    static InputContent fromItemTag(TagKey<Item> tag, long amount, float chance) {
        return fromTag(itemTag, tag, amount, chance);
    }

    static InputContent fromItemTag(TagKey<Item> tag, long amount) {
        return fromItemTag(tag, amount, MAX_CHANCE);
    }

    static InputContent fromItemTag(TagKey<Item> tag, float chance) {
        return fromItemTag(tag, itemStack.quantity(), chance);
    }

    static InputContent fromItemTag(TagKey<Item> tag) {
        return fromItemTag(tag, itemStack.quantity(), MAX_CHANCE);
    }

    //fluid tag

    static InputContent fromFluidTag(TagKey<Fluid> tag, long amount, float chance) {
        return fromTag(fluidTag, tag, amount, chance);
    }

    static InputContent fromFluidTag(TagKey<Fluid> tag, long amount) {
        return fromTag(fluidTag, tag, amount);
    }

    static InputContent fromFluidTag(TagKey<Fluid> tag, float chance) {
        return fromTag(fluidTag, tag, fluidStack.quantity(), chance);
    }

    static InputContent fromFluidTag(TagKey<Fluid> tag) {
        return fromFluidTag(tag, fluidStack.quantity(), MAX_CHANCE);
    }

    //endregion

    //region ingredient factory

    //internal helper
    private static <T, S> InputContent fromStackArray(StackType<T, S> stackType, TagType<T> tagType, S[] stacks, long amount, float chance) {
        var adapter = ArrayAdapter.adapt(stacks);
        TagKey<T> matchedTag = TagHelper.findTagMatchStacks(tagType, stackType, adapter);
        if (matchedTag == null) {
            return new TypedStackListContent<>(
                    stackType,
                    adapter.collect(stack -> ContentStack.fromStack(stackType, stack)),
                    amount,
                    chance
            );
        } else return new TagStackContent<>(tagType, matchedTag, amount, chance);
    }
    //endregion

    //region item ingredient

    //todo:provide custom ingredient adapter api.
    //todo:find a better way to handle ICustomIngredient
    //why so many fucking custom ingredient!!!
    static InputContent fromItemIngredient(Ingredient ingredient, long amount, float chance) {
        if (ingredient.isEmpty()) return empty;
        Checks.checkNotNull(ingredient, "ingredient");
        if (!ingredient.isCustom()) {
            Ingredient.Value[] values = ingredient.getValues();
            if (values.length == 1) {
                Ingredient.Value value = values[0];
                return switch (value) {
                    case Ingredient.ItemValue(var stack) -> fromItemStack(stack, amount);
                    case Ingredient.TagValue(var tag) -> fromItemTag(tag, amount);
                    default -> throw new IllegalArgumentException("An ingredient isn't custom but have a custom value");
                };
            }

        }
        //try to identify which tag the ingredient is.
        ItemStack[] items = ingredient.getItems();
        return switch (items.length) {
            case 0 -> empty;
            case 1 -> {
                //specially,when items only contains one item,we handle them as ItemStack.
                ItemStack stack = items[0];
                if (stack.isEmpty()) yield empty;
                else yield fromItemStack(stack, amount, chance);
            }
            default -> fromStackArray(itemStack, itemTag, items, amount, chance);
        };
    }

    static InputContent fromItemIngredient(Ingredient ingredient, float chance) {
        return fromItemIngredient(ingredient, 1, chance);
    }

    static InputContent fromItemIngredient(Ingredient ingredient) {
        return fromItemIngredient(ingredient, 1, MAX_CHANCE);
    }

    static InputContent fromItemIngredient(SizedIngredient ingredient, float chance) {
        return fromItemIngredient(ingredient.ingredient(), ingredient.count(), chance);
    }

    static InputContent fromItemIngredient(SizedIngredient ingredient) {
        return fromItemIngredient(ingredient.ingredient(), ingredient.count(), MAX_CHANCE);
    }

    //endregion

    //region fluid ingredient

    static InputContent fromFluidIngredient(FluidIngredient ingredient, long amount, float chance) {
        if (ingredient.isEmpty()) return empty;

        if (ingredient instanceof TagFluidIngredient tagFluid) {
            return new TagStackContent<>(fluidTag, tagFluid.tag(), amount, chance);
        }

        FluidStack[] stacks = ingredient.getStacks();
        return switch (stacks.length) {
            case 0 -> empty;
            case 1 -> {
                FluidStack stack = stacks[0];
                if (stack.isEmpty()) yield empty;
                else yield fromFluidStack(stack, amount, chance);
            }
            default -> fromStackArray(fluidStack, fluidTag, stacks, amount, chance);
        };
    }

    static InputContent fromFluidIngredient(SizedFluidIngredient ingredient, float chance) {
        return fromFluidIngredient(ingredient.ingredient(), ingredient.amount(), chance);
    }

    static InputContent fromFluidIngredient(SizedFluidIngredient ingredient) {
        return fromFluidIngredient(ingredient.ingredient(), ingredient.amount(), MAX_CHANCE);
    }

    //endregion



    //region match

    MutableList<? extends ContentVariant> equivalents();

    //endregion

}

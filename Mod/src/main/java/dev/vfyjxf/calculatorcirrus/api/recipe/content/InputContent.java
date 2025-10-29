package dev.vfyjxf.calculatorcirrus.api.recipe.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.helper.TagHelper;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;
import org.eclipse.collections.impl.collector.Collectors2;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;

import java.util.SequencedCollection;

import static dev.vfyjxf.calculatorcirrus.api.CirrusTypes.Contents;
import static dev.vfyjxf.calculatorcirrus.api.CirrusTypes.Contents.FLUID_STACK;
import static dev.vfyjxf.calculatorcirrus.api.CirrusTypes.Contents.FLUID_TAG;
import static dev.vfyjxf.calculatorcirrus.api.CirrusTypes.IngredientResolvers;


public sealed interface InputContent
    extends RecipeContent
    permits EmptyRecipeContent,
            StackContent,
            ValueContent,
            StackListContent,
            TypedStackListContent,
            TagStackContent,
            ComplexContent {


    //region stack factory

    static <T, S> InputContent fromStack(ContentStack<T, S> stack, float chance) {
        if (stack.isEmpty()) return empty;
        return new StackContent<>(stack.type(), stack.copy(), chance);
    }

    static <T, S> InputContent fromStack(ContentStack<T, S> stack) {
        return fromStack(stack, MAX_CHANCE);
    }

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
        return fromStack(Contents.ITEM_STACK, stack, amount, chance);
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
        return fromStack(FLUID_STACK, stack, amount, chance);
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
        if (amount <= 0) return empty;
        return new TagStackContent<>(type, tag, amount, chance);
    }

    static <T> InputContent fromTag(TagType<T> type, TagKey<T> tag, long amount) {
        if (amount <= 0) return empty;
        return new TagStackContent<>(type, tag, amount, MAX_CHANCE);
    }

    //item tag

    static InputContent fromItemTag(TagKey<Item> tag, long amount, float chance) {
        return fromTag(Contents.ITEM_TAG, tag, amount, chance);
    }

    static InputContent fromItemTag(TagKey<Item> tag, long amount) {
        return fromItemTag(tag, amount, MAX_CHANCE);
    }

    static InputContent fromItemTag(TagKey<Item> tag, float chance) {
        return fromItemTag(tag, Contents.ITEM_STACK.quantity(), chance);
    }

    static InputContent fromItemTag(TagKey<Item> tag) {
        return fromItemTag(tag, Contents.ITEM_STACK.quantity(), MAX_CHANCE);
    }

    //fluid tag

    static InputContent fromFluidTag(TagKey<Fluid> tag, long amount, float chance) {
        return fromTag(FLUID_TAG, tag, amount, chance);
    }

    static InputContent fromFluidTag(TagKey<Fluid> tag, long amount) {
        return fromTag(FLUID_TAG, tag, amount);
    }

    static InputContent fromFluidTag(TagKey<Fluid> tag, float chance) {
        return fromTag(FLUID_TAG, tag, FLUID_STACK.quantity(), chance);
    }

    static InputContent fromFluidTag(TagKey<Fluid> tag) {
        return fromFluidTag(tag, FLUID_STACK.quantity(), MAX_CHANCE);
    }

    //endregion

    //region value factory

    static <T> InputContent fromValue(ValueType<T> type, T value, float chance) {
        return new ValueContent<>(type, value, chance);
    }

    static <T> InputContent fromValue(ValueType<T> type, T value) {
        return fromValue(type, value, MAX_CHANCE);
    }

    //endregion

    //region stack list factory

    static <T, S> InputContent fromTypedStackList(StackType<T, S> type, SequencedCollection<? extends S> stacks, long amount, float chance) {
        if (stacks.isEmpty() || amount <= 0) return empty;
        var contentStacks = stacks.stream()
                                  .map(stack -> ContentStack.fromStack(type, stack))
                                  .filter(ContentStack::notEmpty)
                                  .collect(Collectors2.toList());
        if (contentStacks.isEmpty()) return empty;
        return new TypedStackListContent<>(type, contentStacks, amount, chance);
    }

    static <T, S> InputContent fromTypedStackList(StackType<T, S> type, SequencedCollection<? extends S> stacks, long amount) {
        return InputContent.fromTypedStackList(type, stacks, amount, MAX_CHANCE);
    }

    static <T, S> InputContent fromContentStackList(StackType<T, S> type, SequencedCollection<? extends ContentStack<T, S>> contentStacks, long amount, float chance) {
        if (contentStacks.isEmpty() || amount <= 0) return empty;
        return new TypedStackListContent<>(type, contentStacks, amount, chance);
    }

    static <T, S> InputContent fromContentStackList(StackType<T, S> type, SequencedCollection<? extends ContentStack<T, S>> contentStacks, long amount) {
        if (contentStacks.isEmpty() || amount <= 0) return empty;
        return new TypedStackListContent<>(type, contentStacks, amount, MAX_CHANCE);
    }

    static InputContent fromStackList(SequencedCollection<? extends ContentStack<?, ?>> stacks, long amount, float chance) {
        if (stacks.isEmpty() || amount <= 0) return empty;
        return new StackListContent(stacks, amount, chance);
    }

    static InputContent fromStackList(SequencedCollection<? extends ContentStack<?, ?>> stacks, long amount) {
        return fromStackList(stacks, amount, MAX_CHANCE);
    }

    //endregion

    //region ingredient factory

    //region internal helper
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
        if (ingredient.isSimple()) {
            ItemStack[] items = ingredient.getItems();
            return switch (items.length) {
                case 0 -> empty;
                case 1 -> {
                    //specially,when items only contains one item,we handle them as ItemStack.
                    ItemStack stack = items[0];
                    if (stack.isEmpty()) yield empty;
                    else yield fromItemStack(stack, amount, chance);
                }
                default -> fromStackArray(Contents.ITEM_STACK, Contents.ITEM_TAG, items, amount, chance);
            };
        } else if (ingredient.isCustom()) {
            ICustomIngredient customIngredient = ingredient.getCustomIngredient();
            assert customIngredient != null;
            RecipeApi recipeApi = RecipeApi.instance();
            var resolver = recipeApi.findIngredientResolver(IngredientResolvers.CUSTOM, customIngredient);
            if (resolver != null) {
                return resolver.resolve(customIngredient, amount, chance);
            }
        }
        return empty;
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
            return new TagStackContent<>(FLUID_TAG, tagFluid.tag(), amount, chance);
        }

        if (ingredient.isSimple()) {
            FluidStack[] stacks = ingredient.getStacks();
            return switch (stacks.length) {
                case 0 -> empty;
                case 1 -> {
                    FluidStack stack = stacks[0];
                    if (stack.isEmpty()) yield empty;
                    else yield fromFluidStack(stack, amount, chance);
                }
                default -> fromStackArray(FLUID_STACK, FLUID_TAG, stacks, amount, chance);
            };
        } else {
            RecipeApi recipeApi = RecipeApi.instance();
            var resolver = recipeApi.findIngredientResolver(IngredientResolvers.FLUID, ingredient);
            if (resolver != null) {
                return resolver.resolve(ingredient, amount, chance);
            }
        }
        return empty;
    }

    static InputContent fromFluidIngredient(SizedFluidIngredient ingredient, float chance) {
        return fromFluidIngredient(ingredient.ingredient(), ingredient.amount(), chance);
    }

    static InputContent fromFluidIngredient(SizedFluidIngredient ingredient) {
        return fromFluidIngredient(ingredient.ingredient(), ingredient.amount(), MAX_CHANCE);
    }

    //endregion

    //region copy

    @Override
    InputContent copyWithAmount(long amount);

    @Override
    InputContent copyWithChance(float chance);

    @Override
    InputContent copyWithAmountAndChance(long amount, float chance);

    @Override
    InputContent copy();

    //endregion

    //region input info

    default boolean consumed() {
        return chance() > MIN_CHANCE;
    }

    default boolean notConsumed() {
        return chance() == MIN_CHANCE;
    }

    //endregion

}

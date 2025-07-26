package dev.vfyjxf.calculatorcirrus.recipe.system;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusContentTypes;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.calculatorcirrus.helper.RecipeHelper;
import dev.vfyjxf.calculatorcirrus.recipe.BasicRecipeData;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.eclipse.collections.impl.factory.Multimaps;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.function.Predicate;

public class CraftingRecipeSystem implements VanillaRecipeSystem<CraftingRecipe> {

    public static final ResourceLocation SYSTEM_ID = RecipeSystem.createId(Constants.MOD_ID, "crafting");

    private final RecipeManager recipeManager;

    public CraftingRecipeSystem() {
        this.recipeManager = VanillaRecipeSystem.recipeManager();
    }

    @Override
    public RecipeType<CraftingRecipe> recipeType() {
        return RecipeType.CRAFTING;
    }

    @Override
    public ResourceLocation systemId() {
        return SYSTEM_ID;
    }

    @Nullable
    @Override
    public ResourceLocation getRecipeId(RecipeHolder<CraftingRecipe> recipeHolder) {
        return recipeHolder.id();
    }

    @Override
    public boolean contains(RecipeHolder<CraftingRecipe> recipe) {
        return recipeManager.byKey(recipe.id()).isPresent();
    }

    @Override
    public RecipeData parseRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        MutableListMultimap<IngredientType, IngredientStack> recipeStableData = Multimaps.mutable.list.empty();
        for (Ingredient ingredient : recipe.getIngredients()) {
            IngredientStack stack = IngredientStack.create(ingredient);
            recipeStableData.put(IngredientType.INPUT, stack);
        }
        recipeStableData.put(IngredientType.OUTPUT, IngredientStack.create(RecipeHelper.getOutput(recipe)));
        return BasicRecipeData.create(recipeStableData.toImmutable(), recipeHolder.id().toString());
    }

    @Override
    public MutableList<ContentStack<ItemStack>> getMachines() {
        return RecipeSystem.getMachinesFromJei(RecipeTypes.CRAFTING);
    }

    @Override
    public RecipeLookup<RecipeHolder<CraftingRecipe>> createLookup() {
        return new CraftingRecipeLookup();
    }

    @Override
    public @Unmodifiable OrderedIterable<RecipeHolder<CraftingRecipe>> allRecipes() {
        var allRecipeForType = recipeManager.byType(RecipeType.CRAFTING);
        return Lists.immutable.ofAll(allRecipeForType);
    }

    @Override
    public String toString() {
        return "CraftingRecipeSystem";
    }

    private class CraftingRecipeLookup implements RecipeLookup<RecipeHolder<CraftingRecipe>> {

        private Predicate<RecipeHolder<CraftingRecipe>> predicate = recipeHolder -> true;

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> filter(Predicate<RecipeHolder<CraftingRecipe>> predicate) {
            this.predicate = this.predicate.and(predicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withInput(ContentStack<?> input, MatchStrategy strategy) {
            if (!input.isItem()) return this;
            Predicate<RecipeHolder<CraftingRecipe>> inputPredicate = recipeHolder -> {
                CraftingRecipe recipe = recipeHolder.value();
                ContentStack<ItemStack> inputStack = input.cast();
                return recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(inputStack.value()));
            };
            this.predicate = this.predicate.and(inputPredicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withInput(Collection<? extends ContentStack<?>> inputs, MatchStrategy strategy) {
            inputs.removeIf(input -> !input.isItem() || input.isEmpty());
            if (inputs.isEmpty()) return this;
            Predicate<RecipeHolder<CraftingRecipe>> inputPredicate = recipeHolder -> {
                CraftingRecipe recipe = recipeHolder.value();
                return inputs.stream().allMatch(input -> {
                    ContentStack<ItemStack> inputStack = input.cast();
                    return recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(inputStack.value()));
                });
            };
            this.predicate = this.predicate.and(inputPredicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withOutput(ContentStack<?> output, MatchStrategy strategy) {
            if (!output.isItem()) return this;
            Predicate<RecipeHolder<CraftingRecipe>> outputPredicate = recipeHolder -> {
                CraftingRecipe recipe = recipeHolder.value();
                return CirrusContentTypes.ITEM.matches(RecipeHelper.getOutput(recipe), output.<ItemStack>cast().value(), strategy);
            };
            this.predicate = this.predicate.and(outputPredicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withOutput(Collection<? extends ContentStack<?>> outputs, MatchStrategy strategy) {
            if (outputs.size() > 1) return this;//fail fast
            outputs.removeIf(output -> !output.isItem() || output.isEmpty());
            if (outputs.isEmpty()) return this;
            ContentStack<ItemStack> outputStack = outputs.iterator().next().cast();
            withOutput(outputStack, strategy);
            return this;
        }

        @Override
        public RichIterable<RecipeHolder<CraftingRecipe>> get() {
            return allRecipes().select(t -> predicate.test(t)).asLazy();
        }
    }
}

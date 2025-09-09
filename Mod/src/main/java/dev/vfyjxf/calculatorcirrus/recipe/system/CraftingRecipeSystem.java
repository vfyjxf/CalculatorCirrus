package dev.vfyjxf.calculatorcirrus.recipe.system;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.data.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.calculatorcirrus.helper.RecipeHelper;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.function.Predicate;

public class CraftingRecipeSystem implements VanillaRecipeSystem<CraftingRecipe> {

    public static final ResourceLocation SYSTEM_ID = RecipeSystem.systemId(CirrusConstants.MOD_ID, "crafting");

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

    @Override
    public boolean contains(RecipeHolder<CraftingRecipe> recipe) {
        return recipeManager.byKey(recipe.id()).isPresent();
    }

    @Override
    public RecipeData getData(RecipeHolder<CraftingRecipe> recipeHolder) {
//        CraftingRecipe recipe = recipeHolder.value();
//        MutableListMultimap<ContentGroup, IngredientStack> recipeStableData = Multimaps.mutable.list.empty();
//        for (Ingredient ingredient : recipe.getIngredients()) {
//            IngredientStack stack = IngredientStack.create(ingredient);
//            recipeStableData.put(ContentGroup.input, stack);
//        }
//        recipeStableData.put(ContentGroup.output, IngredientStack.create(RecipeHelper.getOutput(recipe)));
//        return BasicRecipeData.create(recipeStableData.toImmutable(), recipeHolder.id().toString());
        //TODO:
        throw new UnsupportedOperationException("Not Implemented");
    }

    @Override
    public MutableList<ContentStack<Item, ItemStack>> getMachines() {
        return RecipeSystem.getMachinesFromJei(RecipeTypes.CRAFTING);
    }

    @Override
    public RecipeLookup<RecipeHolder<CraftingRecipe>> createLookup() {
        return new CraftingRecipeLookup();
    }

    @Override
    public @Unmodifiable RichIterable<RecipeHolder<CraftingRecipe>> allRecipes() {
        return CollectionAdapter.adapt(recipeManager.byType(RecipeType.CRAFTING))
                .asUnmodifiable();
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
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withInput(ContentStack<?, ?> input, MatchStrategy strategy) {
            if (!input.isItem()) return this;
            Predicate<RecipeHolder<CraftingRecipe>> inputPredicate = recipeHolder -> {
                CraftingRecipe recipe = recipeHolder.value();
                ContentStack<Item, ItemStack> inputStack = input.cast();
                return recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(inputStack.value()));
            };
            this.predicate = this.predicate.and(inputPredicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy) {
            inputs.removeIf(input -> !input.isItem() || input.isEmpty());
            if (inputs.isEmpty()) return this;
            Predicate<RecipeHolder<CraftingRecipe>> inputPredicate = recipeHolder -> {
                CraftingRecipe recipe = recipeHolder.value();
                return inputs.stream().allMatch(input -> {
                    ContentStack<Item, ItemStack> inputStack = input.cast();
                    return recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(inputStack.value()));
                });
            };
            this.predicate = this.predicate.and(inputPredicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withOutput(ContentStack<?, ?> output, MatchStrategy strategy) {
            if (!output.isItem()) return this;
            Predicate<RecipeHolder<CraftingRecipe>> outputPredicate = recipeHolder -> {
                CraftingRecipe recipe = recipeHolder.value();
                return CirrusTypes.itemStack.matches(RecipeHelper.getOutput(recipe), output.<Item, ItemStack>cast().value(), MatchStrategy.ALL);
            };
            this.predicate = this.predicate.and(outputPredicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<CraftingRecipe>> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy) {
            if (outputs.size() > 1) return this;//fail fast
            outputs.removeIf(output -> !output.isItem() || output.isEmpty());
            if (outputs.isEmpty()) return this;
            ContentStack<Item, ItemStack> outputStack = outputs.iterator().next().cast();
            withOutput(outputStack, strategy);
            return this;
        }

        @Override
        public RichIterable<RecipeHolder<CraftingRecipe>> get() {
            return allRecipes().select(t -> predicate.test(t)).asLazy();
        }
    }
}

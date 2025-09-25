package dev.vfyjxf.calculatorcirrus.recipe.system;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.OutputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.calculatorcirrus.helper.RecipeHelper;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.util.Locations;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class CraftingRecipeSystem extends BasicVanillaSystem<CraftingRecipe> {

    public static final ResourceLocation SYSTEM_ID = Locations.ofMc("crafting");

    public CraftingRecipeSystem() {
        super(SYSTEM_ID, RecipeType.CRAFTING);
    }

    private CraftingRecipeSystem self() {return this;}

    @Override
    public RecipeView resolveRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        List<InputContent> inputs = MutableLists.empty();
        for (var ingredient : recipe.getIngredients())
            inputs.add(InputContent.fromItemIngredient(ingredient));

        List<OutputContent> output = Collections.singletonList(
            OutputContent.fromItemStack(RecipeHelper.getOutput(recipe))
        );
        return new RecipeView(new RecipeEntry<>(this, recipeHolder), null, null);
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
                return CirrusTypes.ITEM_STACK.matches(RecipeHelper.getOutput(recipe), output.<Item, ItemStack>cast().value(), MatchStrategy.ALL);
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

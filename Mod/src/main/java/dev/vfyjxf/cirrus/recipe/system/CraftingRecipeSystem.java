package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class CraftingRecipeSystem extends BasicVanillaSystem<CraftingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("crafting");

    public CraftingRecipeSystem() {
        super(systemId, RecipeType.CRAFTING);
    }

    @Override
    public RecipeView resolveRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        List<InputContent> inputs = MutableLists.empty();
        for (var ingredient : recipe.getIngredients())
            inputs.add(InputContent.fromItemIngredient(ingredient));

        List<OutputContent> output = Collections.singletonList(
            OutputContent.fromItemStack(RecipeHelper.getOutput(recipe))
        );
        return RecipeView.create(this, recipeHolder, inputs, output);
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
                return CirrusTypes.Contents.ITEM_STACK.matches(RecipeHelper.getOutput(recipe), output.<Item, ItemStack>cast().value(), MatchStrategy.all);
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

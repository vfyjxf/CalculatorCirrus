package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeLookup;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public abstract class VanillaIngredientRecipeSystem<T extends Recipe<?>> extends BasicVanillaSystem<T> {

    protected VanillaIngredientRecipeSystem(Namespace systemId, RecipeType<T> recipeType) {
        super(systemId, recipeType);
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, RecipeHolder<T> recipeHolder) {
        T recipe = recipeHolder.value();
        return VanillaRecipeProjectionSupport.resolve(
            this,
            recipeHolder,
            ingredients(recipe),
            result(recipe),
            projectionSchema(recipe),
            inputSchema(recipe)
        );
    }

    @Override
    public RoutingRecipeView routingView(ResolveContext context, RecipeHolder<T> recipeHolder) {
        T recipe = recipeHolder.value();
        return VanillaRecipeProjectionSupport.routingView(ingredients(recipe), result(recipe));
    }

    protected List<Ingredient> ingredients(T recipe) {
        return recipe.getIngredients();
    }

    protected ItemStack result(T recipe) {
        return RecipeHelper.getOutput(recipe);
    }

    protected ProjectionSchema projectionSchema(T recipe) {
        return ProjectionSchema.empty();
    }

    protected IntFunction<ProjectionSchema> inputSchema(T recipe) {
        return ignored -> ProjectionSchema.empty();
    }

    @Override
    public RecipeLookup<RecipeHolder<T>> createLookup() {
        return new Lookup();
    }

    private final class Lookup implements RecipeLookup<RecipeHolder<T>> {
        private Predicate<RecipeHolder<T>> predicate = ignored -> true;

        @Override
        public RecipeLookup<RecipeHolder<T>> filter(Predicate<RecipeHolder<T>> predicate) {
            this.predicate = this.predicate.and(predicate);
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<T>> withInput(ContentStack<?, ?> input, MatchStrategy strategy) {
            if (!input.isItem()) {
                return this;
            }
            ContentStack<Item, ItemStack> inputStack = input.cast();
            this.predicate = this.predicate.and(holder -> ingredients(holder.value()).stream()
                .anyMatch(ingredient -> ingredient.test(inputStack.value())));
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<T>> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy) {
            ArrayList<ContentStack<Item, ItemStack>> itemInputs = new ArrayList<>();
            for (ContentStack<?, ?> input : inputs) {
                if (input.isItem() && input.notEmpty()) {
                    itemInputs.add(input.cast());
                }
            }
            if (itemInputs.isEmpty()) {
                return this;
            }
            this.predicate = this.predicate.and(holder -> itemInputs.stream().allMatch(input ->
                ingredients(holder.value()).stream().anyMatch(ingredient -> ingredient.test(input.value()))
            ));
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<T>> withOutput(ContentStack<?, ?> output, MatchStrategy strategy) {
            if (!output.isItem()) {
                return this;
            }
            ContentStack<Item, ItemStack> outputStack = output.cast();
            this.predicate = this.predicate.and(holder ->
                CirrusTypes.Contents.ITEM_STACK.matches(
                    result(holder.value()),
                    outputStack.value(),
                    strategy
                )
            );
            return this;
        }

        @Override
        public RecipeLookup<RecipeHolder<T>> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy) {
            ArrayList<ContentStack<Item, ItemStack>> itemOutputs = new ArrayList<>();
            for (ContentStack<?, ?> output : outputs) {
                if (output.isItem() && output.notEmpty()) {
                    itemOutputs.add(output.cast());
                }
            }
            if (itemOutputs.isEmpty()) {
                return this;
            }
            if (itemOutputs.size() > 1) {
                this.predicate = this.predicate.and(holder -> false);
                return this;
            }
            return withOutput(itemOutputs.getFirst(), strategy);
        }

        @Override
        public RichIterable<RecipeHolder<T>> get() {
            return allRecipes().select(holder -> predicate.test(holder)).asLazy();
        }
    }
}

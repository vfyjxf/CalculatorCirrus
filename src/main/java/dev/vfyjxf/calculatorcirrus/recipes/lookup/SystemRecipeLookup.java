package dev.vfyjxf.calculatorcirrus.recipes.lookup;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeSystem;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.impl.lazy.LazyIterableAdapter;

import java.util.Collection;
import java.util.function.Predicate;

public class SystemRecipeLookup<T> implements RecipeLookup<T> {

    private Predicate<T> predicate = t -> true;
    private final RecipeSystem<T> system;
    private final RichIterable<T> recipes;

    public SystemRecipeLookup(RecipeSystem<T> system, Iterable<T> recipes) {
        this.system = system;
        this.recipes = new LazyIterableAdapter<>(recipes);
    }

    @Override
    public RecipeLookup<T> filter(Predicate<T> predicate) {
        this.predicate = this.predicate.and(predicate);
        return this;
    }

    @Override
    public RecipeLookup<T> withInput(ContentStack<?> input, MatchContext context) {
        Predicate<T> inputPredicate = (T recipe) -> {
            RecipeData recipeData = system.parseRecipe(recipe);
            return recipeData.getInputs().anySatisfy(stack -> stack.contains(input, context));
        };
        this.predicate = this.predicate.and(inputPredicate);
        return this;
    }

    @Override
    public RecipeLookup<T> withInput(Collection<? extends ContentStack<?>> inputs, MatchContext context) {
        Predicate<T> inputPredicate = (T recipe) -> {
            RecipeData recipeData = system.parseRecipe(recipe);
            return recipeData.getInputs().allSatisfy(stack -> inputs.stream().anyMatch(input -> stack.contains(input, context)));
        };
        this.predicate = this.predicate.and(inputPredicate);
        return this;
    }

    @Override
    public RecipeLookup<T> withOutput(ContentStack<?> output, MatchContext context) {
        Predicate<T> outputPredicate = (T recipe) -> {
            RecipeData recipeData = system.parseRecipe(recipe);
            return recipeData.getOutputs().anySatisfy(stack -> stack.contains(output, context));
        };
        this.predicate = this.predicate.and(outputPredicate);
        return this;
    }

    @Override
    public RecipeLookup<T> withOutput(Collection<? extends ContentStack<?>> outputs, MatchContext context) {
        Predicate<T> outputPredicate = (T recipe) -> {
            RecipeData recipeData = system.parseRecipe(recipe);
            return recipeData.getOutputs().allSatisfy(stack -> outputs.stream().anyMatch(output -> stack.contains(output, context)));
        };
        this.predicate = this.predicate.and(outputPredicate);
        return this;
    }

    @Override
    public RichIterable<T> get() {
        return this.recipes.select(t -> this.predicate.test(t));
    }
}

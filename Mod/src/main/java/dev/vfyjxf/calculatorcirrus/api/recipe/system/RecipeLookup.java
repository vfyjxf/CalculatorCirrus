package dev.vfyjxf.calculatorcirrus.api.recipe.system;


import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.recipe.lookup.EmptyRecipeLookup;
import dev.vfyjxf.calculatorcirrus.recipe.lookup.SystemRecipeLookup;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Contract;

import java.util.Collection;
import java.util.function.Predicate;

public interface RecipeLookup<T> {

    @SuppressWarnings("unchecked")
    static <T> RecipeLookup<T> empty() {
        return EmptyRecipeLookup.instance;
    }

    static <T> RecipeLookup<T> ofSystem(RecipeSystem<T> system) {
        return new SystemRecipeLookup<>(system, system.allRecipes());
    }

    @Contract("_->this")
    RecipeLookup<T> filter(Predicate<T> predicate);

    default RecipeLookup<T> withInput(ContentStack<?, ?> input) {
        return withInput(input, MatchStrategy.ALL);
    }

    @Contract("_,_->this")
    RecipeLookup<T> withInput(ContentStack<?, ?> input, MatchStrategy strategy);

    @Contract("_,_->this")
    RecipeLookup<T> withInput(Collection<? extends ContentStack<?, ?>> inputs, MatchStrategy strategy);

    default RecipeLookup<T> withOutput(ContentStack<?, ?> output) {
        return withOutput(output, MatchStrategy.ALL);
    }

    @Contract("_,_->this")
    RecipeLookup<T> withOutput(ContentStack<?, ?> output, MatchStrategy strategy);

    @Contract("_,_->this")
    RecipeLookup<T> withOutput(Collection<? extends ContentStack<?, ?>> outputs, MatchStrategy strategy);

    /**
     * @return Lazy evaluation of the recipes.
     */
    RichIterable<T> get();

}

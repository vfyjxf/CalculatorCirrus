package dev.vfyjxf.calculatorcirrus.api.content.recipes;


import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.recipes.lookup.EmptyRecipeLookup;
import dev.vfyjxf.calculatorcirrus.recipes.lookup.SystemRecipeLookup;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Contract;

import java.util.Collection;
import java.util.function.Predicate;

public interface RecipeLookup<T> {

    @SuppressWarnings("unchecked")
    static <T> RecipeLookup<T> empty() {
        return EmptyRecipeLookup.INSTANCE;
    }

    static <T> RecipeLookup<T> ofSystem(RecipeSystem<T> system) {
        return new SystemRecipeLookup<>(system, system.allRecipes());
    }

    @Contract("_->this")
    RecipeLookup<T> filter(Predicate<T> predicate);

    default RecipeLookup<T> withInput(ContentStack<?> input) {
        return withInput(input, MatchContext.EXACT);
    }

    @Contract("_,_->this")
    RecipeLookup<T> withInput(ContentStack<?> input, MatchContext context);

    @Contract("_,_->this")
    RecipeLookup<T> withInput(Collection<? extends ContentStack<?>> inputs, MatchContext context);

    default RecipeLookup<T> withOutput(ContentStack<?> output) {
        return withOutput(output, MatchContext.EXACT);
    }

    @Contract("_,_->this")
    RecipeLookup<T> withOutput(ContentStack<?> output, MatchContext context);

    @Contract("_,_->this")
    RecipeLookup<T> withOutput(Collection<? extends ContentStack<?>> outputs, MatchContext context);

    /**
     * @return Lazy evaluation of the recipes.
     */
    RichIterable<T> get();

}

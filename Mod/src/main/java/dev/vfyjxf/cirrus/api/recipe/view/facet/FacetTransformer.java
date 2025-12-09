package dev.vfyjxf.cirrus.api.recipe.view.facet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import org.jetbrains.annotations.Contract;

import java.util.function.Predicate;

public interface FacetTransformer<T extends RecipeContent, F extends RecipeFacet> {

    //region factory helper

    static <T extends RecipeContent, F extends RecipeFacet> FacetTransformer<T, F> of(SelectFunction selector, TransformFunction<T, F> transformation) {
        return new FacetTransformer<>() {
            @Override
            public boolean select(RecipeContext context, RecipeView view, RecipeContent content, boolean asInput) {
                return selector.select(context, view, content, asInput);
            }

            @Override
            public ActionResult transform(RecipeContext context, RecipeEntry<?> recipe, T content, F facet, boolean asInput, IncrementOps increment, DecrementOps decrement) {
                return transformation.transform(context, recipe, content, facet, asInput, increment, decrement);
            }
        };
    }

    //endregion

    //region core functions

    boolean select(RecipeContext context, RecipeView view, RecipeContent content, boolean asInput);

    ActionResult transform(
        RecipeContext context, RecipeEntry<?> recipe,
        T content, F facet, boolean asInput,
        IncrementOps increment, DecrementOps decrement
    );

    //endregion

    //region helper Functions

    interface SelectFunction {
        boolean select(RecipeContext context, RecipeView view, RecipeContent content, boolean asInput);
    }

    interface TransformFunction<T extends RecipeContent, F extends RecipeFacet> {
        ActionResult transform(
            RecipeContext context, RecipeEntry<?> recipe,
            T content, F facet, boolean asInput,
            IncrementOps increment, DecrementOps decrement
        );

    }

    //endregion

    //region transform ops

    /**
     * Transform action result for given content.
     */
    enum ActionResult {
        /**
         * REMOVE the given content.
         */
        REMOVE,
        /**
         * Retain the given content.
         */
        RETAIN
    }

    interface IncrementOps {

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        IncrementOps injectInput(InputContent content);

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        IncrementOps injectOutput(OutputContent content);
    }

    interface DecrementOps {

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        DecrementOps rejectInput(Predicate<InputContent> predicate);

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        DecrementOps rejectInput(InputContent content);

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        DecrementOps rejectOutput(Predicate<OutputContent> predicate);

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        DecrementOps rejectOutput(OutputContent content);
    }

    //endregion

}

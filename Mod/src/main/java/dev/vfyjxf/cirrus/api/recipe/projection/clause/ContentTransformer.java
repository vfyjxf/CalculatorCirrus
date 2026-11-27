package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.effect.ReplaceMode;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemaKey;
import org.jetbrains.annotations.Contract;

import java.util.function.Predicate;

public interface ContentTransformer<T extends RecipeContent, F extends RecipeClause> {

    //region factory helper

    static <T extends RecipeContent, F extends RecipeClause> ContentTransformer<T, F> of(
        ProjectionPlugin plugin,
        SelectFunction selector,
        TransformFunction<T, F> transformation
    ) {
        return new ContentTransformer<>() {
            @Override
            public ProjectionPlugin plugin() {
                return plugin;
            }

            @Override
            public boolean select(
                ExecutionContext context,
                RecipeProjection projection,
                RecipeContent content,
                boolean asInput
            ) {
                return selector.select(context, projection, content, asInput);
            }

            @Override
            public ActionResult transform(
                ExecutionContext context,
                RecipeEntry<?> recipe,
                T content,
                F facet,
                boolean asInput,
                IncrementOps increment,
                DecrementOps decrement
            ) {
                return transformation.transform(context, recipe, content, facet, asInput, increment, decrement);
            }
        };
    }

    //endregion

    //region core functions

    ProjectionPlugin plugin();

    boolean select(ExecutionContext context, RecipeProjection projection, RecipeContent content, boolean asInput);

    ActionResult transform(
        ExecutionContext context,
        RecipeEntry<?> recipe,
        T content,
        F facet,
        boolean asInput,
        IncrementOps increment,
        DecrementOps decrement
    );

    //endregion

    //region helper Functions

    interface SelectFunction {
        boolean select(ExecutionContext context, RecipeProjection projection, RecipeContent content, boolean asInput);
    }

    interface TransformFunction<T extends RecipeContent, F extends RecipeClause> {
        ActionResult transform(
            ExecutionContext context,
            RecipeEntry<?> recipe,
            T content,
            F facet,
            boolean asInput,
            IncrementOps increment,
            DecrementOps decrement
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
        remove,
        /**
         * Retain the given content.
         */
        retain
    }

    interface IncrementOps {

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        IncrementOps injectInput(InputContent content);

        @CanIgnoreReturnValue
        @Contract("_ -> this")
        IncrementOps injectOutput(OutputContent content);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        IncrementOps replaceInput(InputContent before, InputContent after, ReplaceMode mode);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        IncrementOps replaceOutput(OutputContent before, OutputContent after, ReplaceMode mode);

        @CanIgnoreReturnValue
        @Contract("_, _ -> this")
        <T> IncrementOps putProjectionSchema(ProjectionSchemaKey<T> key, T value);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        <T> IncrementOps putInputSchema(InputContent owner, ProjectionSchemaKey<T> key, T value);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        <T> IncrementOps putOutputSchema(OutputContent owner, ProjectionSchemaKey<T> key, T value);
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

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        DecrementOps replaceInput(InputContent before, InputContent after, ReplaceMode mode);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        DecrementOps replaceOutput(OutputContent before, OutputContent after, ReplaceMode mode);

        @CanIgnoreReturnValue
        @Contract("_, _ -> this")
        <T> DecrementOps putProjectionSchema(ProjectionSchemaKey<T> key, T value);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        <T> DecrementOps putInputSchema(InputContent owner, ProjectionSchemaKey<T> key, T value);

        @CanIgnoreReturnValue
        @Contract("_, _, _ -> this")
        <T> DecrementOps putOutputSchema(OutputContent owner, ProjectionSchemaKey<T> key, T value);
    }

    //endregion

}

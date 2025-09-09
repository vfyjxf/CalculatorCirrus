package dev.vfyjxf.calculatorcirrus.api.recipe.data;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.OutputContent;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public class RecipeData {

    //region factory


    //endregion

    //region internal

    private final RecipeEntry<?> recipe;
    private final ImmutableList<InputContent> inputs;
    private final ImmutableList<OutputContent> outputs;
    private final ContextContainer contexts = new ContextContainer();
    private final ModifierContainer modifiers = new ModifierContainer();

    RecipeData(
            RecipeEntry<?> recipe,
            ImmutableList<InputContent> inputs,
            ImmutableList<OutputContent> outputs
    ) {
        this.recipe = recipe;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    //endregion

    //region recipe and ingredient

    public RecipeEntry<?> recipe() {
        return recipe;
    }

    public ImmutableList<InputContent> inputs() {
        return inputs;
    }

    public ImmutableList<OutputContent> outputs() {
        return outputs;
    }

    //endregion

    //region context

    public boolean hasContext(ContextType<?> type) {return contexts.hasContext(type);}

    public <T> T getOrDefault(ContextType<T> type) {return contexts.getOrDefault(type);}

    public <T> T getOrElse(ContextType<T> type, @UnknownNullability T other) {return contexts.getOrElse(type, other);}

    public <T> @Nullable T getOrNull(ContextType<T> type) {return contexts.getOrNull(type);}

    public <T> @UnknownNullability T getOrThrow(ContextType<T> type) {return contexts.getOrThrow(type);}

    //endregion
}

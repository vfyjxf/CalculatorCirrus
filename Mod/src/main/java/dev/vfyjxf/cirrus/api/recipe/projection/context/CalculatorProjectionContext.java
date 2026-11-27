package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import org.jspecify.annotations.Nullable;

public record CalculatorProjectionContext(@Nullable RecipeEntry<?> recipe, int times, long version) {

    private static final CalculatorProjectionContext none = new CalculatorProjectionContext(null, 1, 0L);

    public CalculatorProjectionContext {
        times = Math.max(1, times);
    }

    public static CalculatorProjectionContext none() {
        return none;
    }

    public boolean hasRecipe() {
        return recipe != null;
    }
}

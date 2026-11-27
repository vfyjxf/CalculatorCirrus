package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;

import java.util.Objects;

public record AutomatedRecipeCapability(
    CompiledRecipeKey recipeKey,
    double passiveSecondsPerBatch,
    int parallelCrafters
) {

    public AutomatedRecipeCapability {
        Objects.requireNonNull(recipeKey, "recipeKey");
        if (!Double.isFinite(passiveSecondsPerBatch) || passiveSecondsPerBatch < 0.0) {
            throw new IllegalArgumentException("passiveSecondsPerBatch must be >= 0");
        }
        if (parallelCrafters <= 0) {
            throw new IllegalArgumentException("parallelCrafters must be > 0");
        }
    }
}

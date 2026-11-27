package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import org.jspecify.annotations.Nullable;

public record JeiTargetSelectionResult(boolean success, @Nullable RecipeEntry<?> recipe, int times) {}

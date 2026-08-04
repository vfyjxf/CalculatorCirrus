package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

/**
 * Coarse routing policy attached to a recipe before planning.
 */
public enum RecipeUsePolicy {
    normal,
    discouraged,
    lastResort,
    disabledByDefault,
    targetOnly
}

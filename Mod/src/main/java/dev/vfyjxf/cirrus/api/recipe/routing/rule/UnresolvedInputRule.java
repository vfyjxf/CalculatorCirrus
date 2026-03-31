package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Decides how the planner should handle an input that remains unresolved after
 * stock/byproduct consumption and candidate expansion.
 */
@FunctionalInterface
public interface UnresolvedInputRule {

    Resolution resolve(UnresolvedInputContext context);

    enum Resolution {
        fail,
        pendingInput
    }
}

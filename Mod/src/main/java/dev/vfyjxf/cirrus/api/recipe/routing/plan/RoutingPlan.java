package dev.vfyjxf.cirrus.api.recipe.routing.plan;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Immutable executable plan produced by the routing engine.
 *
 * <p>A {@code RoutingPlan} is an ordered sequence of {@link PlanAction} instances
 * that, when executed in order, resolves all dependencies for the target recipe.
 * The action order constitutes the execution contract for client-side simulation —
 * replaying the same plan must always produce the same result.
 *
 * @param planVersion        monotonic version stamp identifying this plan
 * @param tagSnapshotVersion the tag snapshot version that was active during planning
 * @param targetRecipe       the top-level recipe this plan was built for
 * @param targetTimes        how many times the target recipe should be executed
 * @param actions            the ordered action sequence
 * @see PlanAction
 * @see PlanBuilder
 */
public record RoutingPlan(
    long planVersion,
    long tagSnapshotVersion,
    RecipeEntry<?> targetRecipe,
    int targetTimes,
    ImmutableList<PlanAction> actions
) {
    public RoutingPlan {
        Checks.checkArgument(planVersion >= 0, "planVersion must be non-negative");
        Checks.checkArgument(tagSnapshotVersion >= 0, "tagSnapshotVersion must be non-negative");
        Checks.checkNotNull(targetRecipe, "targetRecipe");
        Checks.checkNotNull(actions, "actions");
        Checks.checkArgument(targetTimes > 0, "targetTimes must be positive");
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }
}


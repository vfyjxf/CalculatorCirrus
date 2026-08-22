package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only context handed to {@link ContextualRecipeRule} implementations.
 *
 * <p>Unlike the pattern-based recipe screening (which decides once per recipe
 * from its static shape), contextual rules see the live routing request: the
 * target resource, current stock, and where in the plan tree the candidate
 * sits. They answer "is this recipe sensible <em>here</em>", not "is this
 * recipe sensible at all".
 *
 * @param service          the routing service running the plan (exposes the
 *                         resource topology for loop-family checks)
 * @param request          the active routing request (target, stock, budgets)
 * @param candidate        the graph node of the recipe under evaluation
 * @param demandedInput    the input this candidate would supply
 * @param demandResourceId the concrete resource id of the demand, or null for
 *                         tag/list/value demands
 * @param targetResourceId the concrete resource id of the request target, or
 *                         null when the target has no single resource
 * @param depth            current search depth (0 = root demand)
 */
public record ContextualRuleContext(
    RoutingService service,
    RoutingRequest request,
    RecipeGraph.Node candidate,
    InputContent demandedInput,
    @Nullable String demandResourceId,
    @Nullable String targetResourceId,
    int depth
) {
    public ContextualRuleContext {
        Checks.checkNotNull(service, "service");
        Checks.checkNotNull(request, "request");
        Checks.checkNotNull(candidate, "candidate");
        Checks.checkNotNull(demandedInput, "demandedInput");
        Checks.checkArgument(depth >= 0, "depth must be non-negative");
    }
}

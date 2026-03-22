package dev.vfyjxf.cirrus.api.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;

/**
 * Strategy interface for recipe routing planners.
 *
 * <p>A {@code RoutePlanner} receives a frozen {@link PlannerContext} and produces
 * a {@link RoutingOutcome} containing the execution plan, issues, metrics, and
 * optional diagnostic trace.
 *
 * <p>Implementations must be stateless — all mutable state lives inside the
 * {@link PlannerContext} and its associated counters.
 *
 * @see dev.vfyjxf.cirrus.api.recipe.routing.planner.StandardPlanner
 * @see dev.vfyjxf.cirrus.api.recipe.routing.planner.AssuredPlanner
 */
public interface RoutePlanner {

    /**
     * Executes the planning algorithm against the given context.
     *
     * @param context the frozen context for this planning run
     * @return the routing outcome (never {@code null})
     */
    RoutingOutcome plan(PlannerContext context);
}


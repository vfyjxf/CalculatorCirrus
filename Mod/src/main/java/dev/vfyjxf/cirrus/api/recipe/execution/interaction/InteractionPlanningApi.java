package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

/**
 * Optional interaction extension for exposing routing-time planning state.
 */
public interface InteractionPlanningApi extends InteractionApi {

    @Override
    ExecutionPlanningSnapshot planningSnapshot();
}

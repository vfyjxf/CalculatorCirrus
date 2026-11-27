package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;

/** SPI 3: Declares pack/mod/stage-specific Base supply boundaries and price partial orders. */
public interface BaseBoundaryProvider {
    String providerId();

    BaseBoundarySnapshot provide(CompiledRecipeGraph graph, PlanningContext context);
}

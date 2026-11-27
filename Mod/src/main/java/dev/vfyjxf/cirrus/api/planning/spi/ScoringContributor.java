package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

/**
 * SPI 5: Auditable bounded soft-scoring contributor (zero leakage into physical CostVector).
 */
public interface ScoringContributor {
    String contributorId();

    default double recipeSoftBiasSeconds(CompiledRecipe recipe, PlanningContext context) {
        return 0.0;
    }

    default double resourceSoftBiasSeconds(ResourceKey resource, PlanningContext context) {
        return 0.0;
    }
}

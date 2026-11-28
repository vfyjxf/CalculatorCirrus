package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;

/**
 * Auditable bounded soft-scoring contributor (zero leakage into physical CostVector).
 */
public interface ScoringContributor {
    String contributorId();

    default double recipeSoftBiasSeconds(CompiledRecipe recipe, PlanningContext context) {
        return 0.0;
    }

    default double resourceSoftBiasSeconds(ComponentStackKey resource, PlanningContext context) {
        return 0.0;
    }
}

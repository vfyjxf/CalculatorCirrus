package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;

public interface QuerySubgraph {

    List<ResourceKey> activeNodes();

    List<CompiledRecipe> activeRecipes();

    int acquisitionTierOf(ResourceKey resource);

    List<EquivalenceClass> reducedCandidates(int recipeCompactId, int slotIndex);

    List<CoupledComponent> coupledComponents();

    int collapsedOrCandidateCount();

    boolean truncatedByBudget();
}

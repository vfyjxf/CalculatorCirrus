package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.List;

public interface QuerySubgraph {

    List<ComponentStackKey> activeNodes();

    List<CompiledRecipe> activeRecipes();

    int acquisitionTierOf(ComponentStackKey resource);

    List<EquivalenceClass> reducedCandidates(int recipeCompactId, int slotIndex);

    List<CoupledComponent> coupledComponents();

    int collapsedOrCandidateCount();

    boolean truncatedByBudget();
}

package dev.vfyjxf.cirrus.planning.core.cone;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CoupledComponent;
import dev.vfyjxf.cirrus.api.planning.graph.EquivalenceClass;
import dev.vfyjxf.cirrus.api.planning.graph.QuerySubgraph;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.ArrayList;
import java.util.List;

public final class QuerySubgraphImpl implements QuerySubgraph {

    private final ConeModel cone;
    private List<ResourceKey> cachedNodes;
    private List<CompiledRecipe> cachedRecipes;

    public QuerySubgraphImpl(ConeModel cone) {
        this.cone = cone;
    }

    public ConeModel cone() {
        return cone;
    }

    @Override
    public List<ResourceKey> activeNodes() {
        if (cachedNodes == null) {
            List<ResourceKey> list = new ArrayList<>(cone.nodeCount);
            for (int u = 0; u < cone.nodeCount; u++) {
                if (cone.tier[u] < ConeModel.INF_TIER) {
                    list.add(cone.resKeys[u]);
                }
            }
            cachedNodes = List.copyOf(list);
        }
        return cachedNodes;
    }

    @Override
    public List<CompiledRecipe> activeRecipes() {
        if (cachedRecipes == null) {
            List<CompiledRecipe> list = new ArrayList<>(cone.recipeCount);
            for (int r = 0; r < cone.recipeCount; r++) {
                if (cone.isAnyActive(r)) {
                    list.add(cone.recObjects[r]);
                }
            }
            cachedRecipes = List.copyOf(list);
        }
        return cachedRecipes;
    }

    @Override
    public int acquisitionTierOf(ResourceKey resource) {
        int u = cone.localRes(resource);
        return u < 0 ? ConeModel.INF_TIER : cone.tier[u];
    }

    @Override
    public List<EquivalenceClass> reducedCandidates(int recipeCompactId, int slotIndex) {
        int r = cone.localRecipe(recipeCompactId);
        if (r < 0) {
            return List.of();
        }
        int s = cone.inStart[r];
        int e = cone.inStart[r + 1];
        for (int pos = s; pos < e; pos++) {
            if (cone.inSlotIndex[pos] == slotIndex) {
                return cone.reducedCandidatesForLocalSlot(pos);
            }
        }
        return List.of();
    }

    @Override
    public List<CoupledComponent> coupledComponents() {
        return cone.coupledComponents;
    }

    @Override
    public int collapsedOrCandidateCount() {
        return cone.collapsedOrCandidates;
    }

    @Override
    public boolean truncatedByBudget() {
        return cone.truncatedByBudget;
    }
}

package dev.vfyjxf.cirrus.planning.core.cone;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CoupledComponent;
import dev.vfyjxf.cirrus.api.planning.graph.EquivalenceClass;
import dev.vfyjxf.cirrus.api.planning.graph.QuerySubgraph;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class QuerySubgraphImpl implements QuerySubgraph {

    private final ConeModel cone;
    private @Nullable List<ComponentStackKey> cachedNodes;
    private @Nullable List<CompiledRecipe> cachedRecipes;

    public QuerySubgraphImpl(ConeModel cone) {
        this.cone = cone;
    }

    public ConeModel cone() {
        return cone;
    }

    @Override
    public List<ComponentStackKey> activeNodes() {
        if (cachedNodes == null) {
            List<ComponentStackKey> list = new ArrayList<>(cone.nodeCount);
            for (int u = 0; u < cone.nodeCount; u++) {
                if (cone.tier[u] < ConeModel.infTier) {
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
    public int acquisitionTierOf(ComponentStackKey resource) {
        int u = cone.localRes(resource);
        return u < 0 ? ConeModel.infTier : cone.tier[u];
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

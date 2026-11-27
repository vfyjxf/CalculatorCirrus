package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Objects;

public record BatchCouplingComponent(
    ResourceKey targetResource,
    long demand,
    List<CandidateRecipe> candidates,
    double seedUpperBoundCost
) {

    public record CandidateRecipe(
        CompiledRecipeKey recipeKey,
        long outputPerBatch,
        double perBatchCost,
        boolean multiOutput
    ) {
        public CandidateRecipe {
            Objects.requireNonNull(recipeKey, "recipeKey");
            if (outputPerBatch <= 0L) {
                throw new IllegalArgumentException("outputPerBatch must be > 0");
            }
            if (!Double.isFinite(perBatchCost) || perBatchCost < 0.0) {
                throw new IllegalArgumentException("perBatchCost must be finite >= 0");
            }
        }
    }

    public BatchCouplingComponent {
        Objects.requireNonNull(targetResource, "targetResource");
        Objects.requireNonNull(candidates, "candidates");
        if (demand <= 0L) {
            throw new IllegalArgumentException("demand must be > 0");
        }
        candidates = List.copyOf(candidates);
    }
}

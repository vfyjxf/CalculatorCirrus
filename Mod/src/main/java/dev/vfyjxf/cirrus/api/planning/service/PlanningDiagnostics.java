package dev.vfyjxf.cirrus.api.planning.service;

import dev.vfyjxf.cirrus.api.planning.human.ConflictDiagnosis;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record PlanningDiagnostics(
    long elapsedMillis,
    int subgraphNodes,
    int subgraphRecipes,
    int collapsedOrCandidates,
    int coupledComponentsEvaluated,
    boolean truncatedByNodeBudget,
    Optional<ConflictDiagnosis> conflictDiagnosis,
    Map<ResourceKey, String> unmetResources
) {

    public PlanningDiagnostics(
        long elapsedMillis,
        int subgraphNodes,
        int subgraphRecipes,
        int collapsedOrCandidates,
        int coupledComponentsEvaluated,
        boolean truncatedByNodeBudget,
        Optional<ConflictDiagnosis> conflictDiagnosis
    ) {
        this(
            elapsedMillis,
            subgraphNodes,
            subgraphRecipes,
            collapsedOrCandidates,
            coupledComponentsEvaluated,
            truncatedByNodeBudget,
            conflictDiagnosis,
            Map.of()
        );
    }

    public PlanningDiagnostics {
        Objects.requireNonNull(conflictDiagnosis, "conflictDiagnosis");
        Objects.requireNonNull(unmetResources, "unmetResources");
        if (unmetResources.isEmpty()) {
            unmetResources = Map.of();
        } else {
            TreeMap<ResourceKey, String> sorted = new TreeMap<>();
            for (var e : unmetResources.entrySet()) {
                sorted.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
            }
            unmetResources = Collections.unmodifiableMap(sorted);
        }
    }
}

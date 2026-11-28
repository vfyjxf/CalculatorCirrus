package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record CycleLoopStep(
    String stepId,
    List<String> dependsOnStepIds,
    CompiledCycleMacroKey macroKey,
    long iterations,
    Map<ComponentStackKey, Long> workingSeedAmounts,
    List<CraftStep> singleIterationTemplate,
    Map<ComponentStackKey, Long> netDeltaPerIteration,
    CostVector stepCost
) implements PlanStep {

    public CycleLoopStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(macroKey, "macroKey");
        Objects.requireNonNull(workingSeedAmounts, "workingSeedAmounts");
        Objects.requireNonNull(singleIterationTemplate, "singleIterationTemplate");
        Objects.requireNonNull(netDeltaPerIteration, "netDeltaPerIteration");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
        singleIterationTemplate = List.copyOf(singleIterationTemplate);
        workingSeedAmounts = sortedResourceMap(workingSeedAmounts);
        netDeltaPerIteration = sortedResourceMap(netDeltaPerIteration);
    }

    private static Map<ComponentStackKey, Long> sortedResourceMap(Map<ComponentStackKey, Long> source) {
        if (source.isEmpty()) return Map.of();
        TreeMap<ComponentStackKey, Long> sorted = new TreeMap<>();
        for (var e : source.entrySet()) {
            sorted.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
        }
        return Collections.unmodifiableMap(sorted);
    }
}

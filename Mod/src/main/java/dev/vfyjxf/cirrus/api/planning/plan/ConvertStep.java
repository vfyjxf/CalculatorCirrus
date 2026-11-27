package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record ConvertStep(
    String stepId,
    List<String> dependsOnStepIds,
    CompiledRecipeKey conversionRecipeKey,
    long batches,
    Map<Integer, SlotBinding> slotBindings,
    CostVector stepCost
) implements PlanStep {

    public ConvertStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(conversionRecipeKey, "conversionRecipeKey");
        Objects.requireNonNull(slotBindings, "slotBindings");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
        if (slotBindings.isEmpty()) {
            slotBindings = Map.of();
        } else {
            TreeMap<Integer, SlotBinding> sorted = new TreeMap<>();
            for (var e : slotBindings.entrySet()) {
                sorted.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
            }
            slotBindings = Collections.unmodifiableMap(sorted);
        }
    }
}

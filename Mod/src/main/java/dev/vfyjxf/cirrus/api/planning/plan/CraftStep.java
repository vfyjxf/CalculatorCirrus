package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record CraftStep(
    String stepId,
    List<String> dependsOnStepIds,
    CompiledRecipeKey recipeKey,
    long batches,
    Optional<String> chosenMachineId,
    boolean executedByAutomation,
    Map<Integer, SlotBinding> slotBindings,
    Map<Integer, ResourceKey> catalystBindings,
    CostVector stepCost
) implements PlanStep {

    public CraftStep {
        Objects.requireNonNull(stepId, "stepId");
        Objects.requireNonNull(dependsOnStepIds, "dependsOnStepIds");
        Objects.requireNonNull(recipeKey, "recipeKey");
        Objects.requireNonNull(chosenMachineId, "chosenMachineId");
        Objects.requireNonNull(slotBindings, "slotBindings");
        Objects.requireNonNull(catalystBindings, "catalystBindings");
        Objects.requireNonNull(stepCost, "stepCost");
        dependsOnStepIds = List.copyOf(dependsOnStepIds);
        slotBindings = sortedIntMap(slotBindings);
        catalystBindings = sortedIntMap(catalystBindings);
    }

    private static <V> Map<Integer, V> sortedIntMap(Map<Integer, V> source) {
        if (source.isEmpty()) return Map.of();
        TreeMap<Integer, V> sorted = new TreeMap<>();
        for (var e : source.entrySet()) {
            sorted.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
        }
        return Collections.unmodifiableMap(sorted);
    }
}

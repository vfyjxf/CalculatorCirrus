package dev.vfyjxf.cirrus.api.planning.graph;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record RawRecipeRecord(
        String systemId,
        String sourceKind,
        Optional<String> explicitId,
        List<String> machineIds,
        List<RawIngredientSlot> inputs,
        List<RawOutputSlot> outputs,
        Map<String, Double> recipeValues,
        long sourceRef
) {

    public RawRecipeRecord(
            String systemId,
            String sourceKind,
            Optional<String> explicitId,
            List<String> machineIds,
            List<RawIngredientSlot> inputs,
            List<RawOutputSlot> outputs,
            Map<String, Double> recipeValues
    ) {
        this(systemId, sourceKind, explicitId, machineIds, inputs, outputs, recipeValues, 0L);
    }

    public RawRecipeRecord {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(explicitId, "explicitId");
        Objects.requireNonNull(machineIds, "machineIds");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(recipeValues, "recipeValues");
        machineIds = List.copyOf(machineIds);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (recipeValues.isEmpty()) {
            recipeValues = Map.of();
        } else {
            TreeMap<String, Double> sorted = new TreeMap<>();
            for (var e : recipeValues.entrySet()) {
                sorted.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
            }
            recipeValues = Collections.unmodifiableMap(sorted);
        }
    }
}

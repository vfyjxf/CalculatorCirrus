package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record CompiledRecipe(
        int compactId,
        CompiledRecipeKey key,
        RecipeRole role,
        List<InputSlot> inputs,
        List<CatalystSlot> catalysts,
        List<OutputSlot> outputs,
        MachineRequirement machineRequirement,
        Map<String, Double> recipeValues,
        RecipeAdvisory advisory
) {

    public CompiledRecipe(
            int compactId,
            CompiledRecipeKey key,
            RecipeRole role,
            List<InputSlot> inputs,
            List<CatalystSlot> catalysts,
            List<OutputSlot> outputs,
            MachineRequirement machineRequirement,
            Map<String, Double> recipeValues
    ) {
        this(compactId, key, role, inputs, catalysts, outputs, machineRequirement, recipeValues, RecipeAdvisory.NONE);
    }

    public CompiledRecipe {
        if (compactId < 0) {
            throw new IllegalArgumentException("compactId must be >= 0, got " + compactId);
        }
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(catalysts, "catalysts");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(machineRequirement, "machineRequirement");
        Objects.requireNonNull(recipeValues, "recipeValues");
        Objects.requireNonNull(advisory, "advisory");
        inputs = List.copyOf(inputs);
        catalysts = List.copyOf(catalysts);
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

    public boolean isReversibleConversion() {
        return role == RecipeRole.REVERSIBLE_PACKING || role == RecipeRole.REVERSIBLE_RECOLORING;
    }

    public boolean isMultiOutput() {
        return outputs.size() > 1;
    }

    public boolean hasBatchGreaterThanOne() {
        for (OutputSlot out : outputs) {
            if (out.amountPerBatch() > 1L) {
                return true;
            }
        }
        return false;
    }
}

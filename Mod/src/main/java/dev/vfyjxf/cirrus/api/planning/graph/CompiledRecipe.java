package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeSemanticKey;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record CompiledRecipe(
    int compactId,
    CompiledRecipeKey key,
    RecipeRole role,
    List<InputSlot> inputs,
    List<CatalystSlot> catalysts,
    List<OutputSlot> outputs,
    MachineRequirement machineRequirement,
    Map<String, Double> recipeValues,
    RecipeAdvisory advisory,
    Set<String> systemTags,
    Set<CanonicalRecipeTag> canonicalTags,
    RecipeEvidenceTier evidenceTier,
    double structuralFidelity,
    Optional<RecipeSemanticKey> semanticKey
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
        this(
            compactId,
            key,
            role,
            inputs,
            catalysts,
            outputs,
            machineRequirement,
            recipeValues,
            RecipeAdvisory.none,
            Set.of(),
            Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierA,
            1.0,
            Optional.empty()
        );
    }

    public CompiledRecipe(
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
        this(
            compactId,
            key,
            role,
            inputs,
            catalysts,
            outputs,
            machineRequirement,
            recipeValues,
            advisory,
            Set.of(),
            Set.of(CanonicalRecipeTag.normalProduction),
            RecipeEvidenceTier.tierA,
            1.0,
            Optional.empty()
        );
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
        Objects.requireNonNull(systemTags, "systemTags");
        Objects.requireNonNull(canonicalTags, "canonicalTags");
        Objects.requireNonNull(evidenceTier, "evidenceTier");
        Objects.requireNonNull(semanticKey, "semanticKey");
        if (!Double.isFinite(structuralFidelity) || structuralFidelity < 0.0 || structuralFidelity > 1.0) {
            throw new IllegalArgumentException("structuralFidelity must be in [0, 1], got " + structuralFidelity);
        }
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
        systemTags = systemTags.isEmpty() ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(systemTags));
        canonicalTags = canonicalTags.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(canonicalTags));
    }

    public boolean isReversibleConversion() {
        return role == RecipeRole.reversiblePacking || role == RecipeRole.reversibleRecoloring;
    }

    public boolean isSuspiciousJei() {
        return canonicalTags.contains(CanonicalRecipeTag.suspiciousJei) || structuralFidelity < 0.60;
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

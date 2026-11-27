package dev.vfyjxf.cirrus.api.planning.graph;

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

public record RawRecipeRecord(
    String systemId,
    String sourceKind,
    Optional<String> explicitId,
    List<String> machineIds,
    List<RawIngredientSlot> inputs,
    List<RawOutputSlot> outputs,
    Map<String, Double> recipeValues,
    long sourceRef,
    Set<String> systemTags,
    Set<CanonicalRecipeTag> canonicalTags,
    RecipeEvidenceTier evidenceTier,
    double structuralFidelity,
    Optional<RecipeSemanticKey> semanticKey,
    boolean hasOpaqueConstraint
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
        this(
            systemId,
            sourceKind,
            explicitId,
            machineIds,
            inputs,
            outputs,
            recipeValues,
            0L,
            Set.of(),
            Set.of(),
            defaultTierFor(sourceKind),
            defaultFidelityFor(sourceKind),
            Optional.empty(),
            false
        );
    }

    public RawRecipeRecord(
        String systemId,
        String sourceKind,
        Optional<String> explicitId,
        List<String> machineIds,
        List<RawIngredientSlot> inputs,
        List<RawOutputSlot> outputs,
        Map<String, Double> recipeValues,
        long sourceRef
    ) {
        this(
            systemId,
            sourceKind,
            explicitId,
            machineIds,
            inputs,
            outputs,
            recipeValues,
            sourceRef,
            Set.of(),
            Set.of(),
            defaultTierFor(sourceKind),
            defaultFidelityFor(sourceKind),
            Optional.empty(),
            false
        );
    }

    public RawRecipeRecord {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(explicitId, "explicitId");
        Objects.requireNonNull(machineIds, "machineIds");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(recipeValues, "recipeValues");
        Objects.requireNonNull(systemTags, "systemTags");
        Objects.requireNonNull(canonicalTags, "canonicalTags");
        Objects.requireNonNull(evidenceTier, "evidenceTier");
        Objects.requireNonNull(semanticKey, "semanticKey");
        if (!Double.isFinite(structuralFidelity) || structuralFidelity < 0.0 || structuralFidelity > 1.0) {
            throw new IllegalArgumentException("structuralFidelity must be in [0, 1], got " + structuralFidelity);
        }
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
        systemTags = systemTags.isEmpty() ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(systemTags));
        canonicalTags = canonicalTags.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(canonicalTags));
    }

    private static RecipeEvidenceTier defaultTierFor(String sourceKind) {
        if ("vanilla".equals(sourceKind) || "mod_native".equals(sourceKind)) {
            return RecipeEvidenceTier.tierA;
        }
        if ("audited".equals(sourceKind)) {
            return RecipeEvidenceTier.tierB;
        }
        return RecipeEvidenceTier.tierC;
    }

    private static double defaultFidelityFor(String sourceKind) {
        if ("vanilla".equals(sourceKind) || "mod_native".equals(sourceKind)) {
            return 1.0;
        }
        if ("audited".equals(sourceKind)) {
            return 0.85;
        }
        return 0.70;
    }
}

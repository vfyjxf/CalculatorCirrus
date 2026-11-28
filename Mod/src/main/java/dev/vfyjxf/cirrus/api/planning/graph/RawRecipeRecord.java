package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeSemanticKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;

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
    Namespace systemId,
    String sourceKind,
    Optional<ResourceLocation> explicitId,
    List<MachineEntry> machines,
    List<RawIngredientSlot> inputs,
    List<RawOutputSlot> outputs,
    Map<Namespace, Double> recipeValues,
    long sourceRef,
    Set<Namespace> systemTags,
    Set<CanonicalRecipeTag> canonicalTags,
    RecipeEvidenceTier evidenceTier,
    double structuralFidelity,
    Optional<RecipeSemanticKey> semanticKey,
    boolean hasOpaqueConstraint
) {

    public RawRecipeRecord(
        Namespace systemId,
        String sourceKind,
        Optional<ResourceLocation> explicitId,
        List<MachineEntry> machines,
        List<RawIngredientSlot> inputs,
        List<RawOutputSlot> outputs,
        Map<Namespace, Double> recipeValues
    ) {
        this(
            systemId,
            sourceKind,
            explicitId,
            machines,
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
        Namespace systemId,
        String sourceKind,
        Optional<ResourceLocation> explicitId,
        List<MachineEntry> machines,
        List<RawIngredientSlot> inputs,
        List<RawOutputSlot> outputs,
        Map<Namespace, Double> recipeValues,
        long sourceRef
    ) {
        this(
            systemId,
            sourceKind,
            explicitId,
            machines,
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
        Objects.requireNonNull(machines, "machines");
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
        machines = List.copyOf(machines);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (recipeValues.isEmpty()) {
            recipeValues = Map.of();
        } else {
            TreeMap<Namespace, Double> sorted = new TreeMap<>();
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

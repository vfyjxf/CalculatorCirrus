package dev.vfyjxf.cirrus.planning.normalize;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative Stage J4 JEI Normalization Patch protocol (pack/user reusable JSON/Codec patch).
 */
public record JeiNormalizationPatch(
    String patchId,
    Set<String> matchSystemIds,
    String matchRecipeIdSubstring,
    Set<String> systemTagsToAdd,
    List<String> requiredMachineIdsToAdd,
    Set<Integer> catalystSlotIndices,
    Set<CanonicalRecipeTag> canonicalTagsToAdd,
    Set<CanonicalRecipeTag> canonicalTagsToRemove,
    RecipeEvidenceTier evidenceTierOverride,
    Double fidelityOverride
) {
    public JeiNormalizationPatch {
        Objects.requireNonNull(patchId, "patchId");
        matchSystemIds = matchSystemIds == null ? Set.of() : Set.copyOf(matchSystemIds);
        matchRecipeIdSubstring = matchRecipeIdSubstring == null ? "" : matchRecipeIdSubstring;
        systemTagsToAdd = systemTagsToAdd == null ? Set.of() : Set.copyOf(systemTagsToAdd);
        requiredMachineIdsToAdd = requiredMachineIdsToAdd == null ? List.of() : List.copyOf(requiredMachineIdsToAdd);
        catalystSlotIndices = catalystSlotIndices == null ? Set.of() : Set.copyOf(catalystSlotIndices);
        canonicalTagsToAdd = canonicalTagsToAdd == null ? Set.of() : Set.copyOf(canonicalTagsToAdd);
        canonicalTagsToRemove = canonicalTagsToRemove == null ? Set.of() : Set.copyOf(canonicalTagsToRemove);
    }

    public boolean matches(RawRecipeRecord record) {
        if (!matchSystemIds.isEmpty() && !matchSystemIds.contains(record.systemId())) {
            return false;
        }
        if (!matchRecipeIdSubstring.isEmpty()) {
            String rid = record.explicitId().orElse("");
            return rid.contains(matchRecipeIdSubstring);
        }
        return !matchSystemIds.isEmpty();
    }
}

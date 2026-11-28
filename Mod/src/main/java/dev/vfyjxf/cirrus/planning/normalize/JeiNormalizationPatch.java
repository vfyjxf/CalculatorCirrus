package dev.vfyjxf.cirrus.planning.normalize;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative JEI Normalization Patch protocol (pack/user reusable patch).
 */
public record JeiNormalizationPatch(
    String patchId,
    Set<Namespace> matchSystemIds,
    String matchRecipeIdSubstring,
    Set<Namespace> systemTagsToAdd,
    List<MachineEntry> requiredMachinesToAdd,
    Set<Integer> catalystSlotIndices,
    Set<CanonicalRecipeTag> canonicalTagsToAdd,
    Set<CanonicalRecipeTag> canonicalTagsToRemove,
    @Nullable RecipeEvidenceTier evidenceTierOverride,
    @Nullable Double fidelityOverride
) {
    public JeiNormalizationPatch {
        Objects.requireNonNull(patchId, "patchId");
        matchSystemIds = matchSystemIds == null ? Set.of() : Set.copyOf(matchSystemIds);
        matchRecipeIdSubstring = matchRecipeIdSubstring == null ? "" : matchRecipeIdSubstring;
        systemTagsToAdd = systemTagsToAdd == null ? Set.of() : Set.copyOf(systemTagsToAdd);
        requiredMachinesToAdd = requiredMachinesToAdd == null ? List.of() : List.copyOf(requiredMachinesToAdd);
        catalystSlotIndices = catalystSlotIndices == null ? Set.of() : Set.copyOf(catalystSlotIndices);
        canonicalTagsToAdd = canonicalTagsToAdd == null ? Set.of() : Set.copyOf(canonicalTagsToAdd);
        canonicalTagsToRemove = canonicalTagsToRemove == null ? Set.of() : Set.copyOf(canonicalTagsToRemove);
    }

    public boolean matches(RawRecipeRecord record) {
        if (!matchSystemIds.isEmpty() && !matchSystemIds.contains(record.systemId())) {
            return false;
        }
        if (!matchRecipeIdSubstring.isEmpty()) {
            String rid = record.explicitId().map(ResourceLocation::toString).orElse("");
            return rid.contains(matchRecipeIdSubstring);
        }
        return !matchSystemIds.isEmpty();
    }
}

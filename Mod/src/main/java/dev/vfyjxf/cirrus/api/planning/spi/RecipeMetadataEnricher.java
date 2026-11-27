package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;

import java.util.Set;

/** SPI 2: Enriches recipes with canonical tags, fidelity, evidence tier, or operational metadata. */
public interface RecipeMetadataEnricher {
    String enricherId();

    record EnrichmentDelta(
        Set<String> systemTagsToAdd,
        Set<CanonicalRecipeTag> canonicalTagsToAdd,
        Set<CanonicalRecipeTag> canonicalTagsToRemove,
        RecipeEvidenceTier evidenceTierOverride,
        Double fidelityOverride
    ) {
        public static final EnrichmentDelta none = new EnrichmentDelta(Set.of(), Set.of(), Set.of(), null, null);
    }

    EnrichmentDelta enrich(RawRecipeRecord record);
}

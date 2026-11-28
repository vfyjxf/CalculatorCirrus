package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Enriches recipes with canonical tags, fidelity, evidence tier, or operational metadata. */
public interface RecipeMetadataEnricher {
    String enricherId();

    record EnrichmentDelta(
        Set<Namespace> systemTagsToAdd,
        Set<CanonicalRecipeTag> canonicalTagsToAdd,
        Set<CanonicalRecipeTag> canonicalTagsToRemove,
        @Nullable RecipeEvidenceTier evidenceTierOverride,
        @Nullable Double fidelityOverride
    ) {
        public static final EnrichmentDelta none = new EnrichmentDelta(Set.of(), Set.of(), Set.of(), null, null);
    }

    EnrichmentDelta enrich(RawRecipeRecord record);
}

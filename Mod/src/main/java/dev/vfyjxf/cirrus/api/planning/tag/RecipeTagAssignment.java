package dev.vfyjxf.cirrus.api.planning.tag;

import java.util.Objects;

public record RecipeTagAssignment(
    CanonicalRecipeTag tag,
    TagProvenance provenance,
    double confidence,
    String reasonKey
) {
    public RecipeTagAssignment {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(reasonKey, "reasonKey");
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1], got " + confidence);
        }
    }
}

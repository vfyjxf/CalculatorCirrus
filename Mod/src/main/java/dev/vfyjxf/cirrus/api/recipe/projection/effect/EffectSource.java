package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public record EffectSource(
    Namespace clauseId,
    String phase
) {
    public EffectSource {
        Checks.checkNotNull(clauseId, "clauseId");
        Checks.checkNotNull(phase, "phase");
    }
}

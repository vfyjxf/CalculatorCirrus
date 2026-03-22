package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import dev.vfyjxf.cirrus.api.recipe.projection.effect.EffectMeta;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

public record ResolveGuardFinding(
    Namespace guardId,
    boolean passed,
    @Nullable String reasonCode,
    EffectMeta meta
) {
    public ResolveGuardFinding {
        Checks.checkNotNull(guardId, "guardId");
        Checks.checkNotNull(meta, "meta");
    }
}

package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import dev.vfyjxf.cirrus.api.recipe.projection.effect.EffectMeta;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

public record ResolveGuardResult(
    boolean passed,
    @Nullable String reasonCode,
    EffectMeta meta
) {
    public ResolveGuardResult {
        Checks.checkNotNull(meta, "meta");
    }

    //region factory

    public static ResolveGuardResult pass() {
        return new ResolveGuardResult(true, null, EffectMeta.empty());
    }

    public static ResolveGuardResult pass(EffectMeta meta) {
        return new ResolveGuardResult(true, null, meta);
    }

    public static ResolveGuardResult fail(@Nullable String reasonCode) {
        return new ResolveGuardResult(false, reasonCode, EffectMeta.empty());
    }

    public static ResolveGuardResult fail(@Nullable String reasonCode, EffectMeta meta) {
        return new ResolveGuardResult(false, reasonCode, meta);
    }

    //endregion
}

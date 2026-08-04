package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import org.jetbrains.annotations.Nullable;

public record ResolveGuardResult(
    boolean passed,
    @Nullable String reasonCode
) {
    //region factory

    public static ResolveGuardResult pass() {
        return new ResolveGuardResult(true, null);
    }

    public static ResolveGuardResult fail(@Nullable String reasonCode) {
        return new ResolveGuardResult(false, reasonCode);
    }

    //endregion
}

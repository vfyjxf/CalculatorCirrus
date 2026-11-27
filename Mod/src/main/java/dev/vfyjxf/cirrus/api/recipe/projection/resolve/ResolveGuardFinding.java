package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

public record ResolveGuardFinding(Namespace guardId, boolean passed, @Nullable String reasonCode) {
    public ResolveGuardFinding {
        Checks.checkNotNull(guardId, "guardId");
    }
}

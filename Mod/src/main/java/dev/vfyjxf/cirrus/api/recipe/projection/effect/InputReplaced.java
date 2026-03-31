package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

public record InputReplaced(
    ProjectionEffectSource source,
    int index,
    InputContent before,
    InputContent after,
    ReplaceMode mode
) implements InputEffect {
    public InputReplaced {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(before, "before");
        Checks.checkNotNull(after, "after");
        Checks.checkNotNull(mode, "mode");
    }
}

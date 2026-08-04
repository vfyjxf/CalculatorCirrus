package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cloudlib.util.Checks;

public record OutputReplaced(
    ProjectionEffectSource source,
    int index,
    OutputContent before,
    OutputContent after,
    ReplaceMode mode
) implements OutputEffect {
    public OutputReplaced {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(before, "before");
        Checks.checkNotNull(after, "after");
        Checks.checkNotNull(mode, "mode");
    }
}

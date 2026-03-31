package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

public record InputRejected(
    ProjectionEffectSource source,
    int index,
    InputContent before
) implements InputEffect {
    public InputRejected {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(before, "before");
    }

    @Override
    public @Nullable InputContent after() {
        return null;
    }
}

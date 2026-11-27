package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

public record OutputRejected(ProjectionEffectSource source, int index, OutputContent before) implements OutputEffect {
    public OutputRejected {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(before, "before");
    }

    @Override
    public @Nullable OutputContent after() {
        return null;
    }
}

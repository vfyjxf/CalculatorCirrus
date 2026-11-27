package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

public record OutputInjected(ProjectionEffectSource source, int index, OutputContent after) implements OutputEffect {
    public OutputInjected {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(after, "after");
    }

    @Override
    public @Nullable OutputContent before() {
        return null;
    }
}

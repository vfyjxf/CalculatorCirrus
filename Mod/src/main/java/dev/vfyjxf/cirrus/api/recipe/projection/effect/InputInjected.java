package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

public record InputInjected(ProjectionEffectSource source, int index, InputContent after) implements InputEffect {
    public InputInjected {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(after, "after");
    }

    @Override
    public @Nullable InputContent before() {
        return null;
    }
}

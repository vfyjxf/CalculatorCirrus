package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public record LongProjectionContextOption(
    Namespace id,
    ProjectionPlugin plugin,
    ExecutionContextKey<Long> key,
    String labelKey,
    String descriptionKey,
    long min,
    long max,
    long step
) implements ProjectionContextOption<Long> {
    public LongProjectionContextOption {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(plugin, "plugin");
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(labelKey, "labelKey");
        Checks.checkNotNull(descriptionKey, "descriptionKey");
        Checks.checkArgument(min <= max, "min must be <= max");
        step = Math.max(1L, step);
    }

    @Override
    public Long normalize(Object value) {
        long raw = value instanceof Number number ? number.longValue() : defaultValue();
        return Math.max(min, Math.min(max, raw));
    }
}

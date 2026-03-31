package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemaKey;
import dev.vfyjxf.cloudlib.util.Checks;

public record OutputSchemaPut<T>(
    ProjectionEffectSource source,
    int index,
    ProjectionSchemaKey<T> key,
    T value
) implements SchemaEffect {
    public OutputSchemaPut {
        Checks.checkNotNull(source, "source");
        Checks.checkArgument(index >= 0, "index must be non-negative");
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(value, "value");
        key.cast(value);
    }
}

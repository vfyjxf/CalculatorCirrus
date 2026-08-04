package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemaKey;
import dev.vfyjxf.cloudlib.util.Checks;

public record ProjectionSchemaPut<T>(
    ProjectionEffectSource source,
    ProjectionSchemaKey<T> key,
    T value
) implements SchemaEffect {
    public ProjectionSchemaPut {
        Checks.checkNotNull(source, "source");
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(value, "value");
        key.cast(value);
    }
}

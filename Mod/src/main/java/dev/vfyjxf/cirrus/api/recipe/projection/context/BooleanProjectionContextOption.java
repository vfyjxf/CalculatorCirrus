package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

public record BooleanProjectionContextOption(
    Namespace id,
    ProjectionPlugin plugin,
    ExecutionContextKey<Boolean> key,
    String labelKey,
    String descriptionKey
) implements ProjectionContextOption<Boolean> {
    public BooleanProjectionContextOption {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(plugin, "plugin");
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(labelKey, "labelKey");
        Checks.checkNotNull(descriptionKey, "descriptionKey");
    }

    @Override
    public Boolean normalize(@Nullable Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : defaultValue();
    }
}

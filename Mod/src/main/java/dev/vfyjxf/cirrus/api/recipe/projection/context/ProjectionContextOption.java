package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public sealed interface ProjectionContextOption<T>
    permits BooleanProjectionContextOption, ChoiceProjectionContextOption, LongProjectionContextOption {

    Namespace id();

    ProjectionPlugin plugin();

    ExecutionContextKey<T> key();

    String labelKey();

    String descriptionKey();

    default T defaultValue() {
        return key().defaultValue();
    }

    T normalize(Object value);
}

package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cloudlib.util.Checks;

public record ProjectionContextChoice<T>(T value, String labelKey) {
    public ProjectionContextChoice {
        Checks.checkNotNull(value, "value");
        Checks.checkNotNull(labelKey, "labelKey");
    }
}

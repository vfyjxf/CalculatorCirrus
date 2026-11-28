package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Typed key for an attribute in {@link ProjectionAttributes}.
 */
public record ProjectionAttributeKey<T>(Namespace id, Class<T> valueType) {

    public ProjectionAttributeKey {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(valueType, "valueType");
    }

    public static <T> ProjectionAttributeKey<T> create(Namespace id, Class<T> valueType) {
        return new ProjectionAttributeKey<>(id, valueType);
    }

    public T cast(Object value) {
        return valueType.cast(Checks.checkNotNull(value, "value"));
    }
}

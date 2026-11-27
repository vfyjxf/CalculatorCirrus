package dev.vfyjxf.cirrus.api.recipe.projection.schema;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectionSchema {

    private static final ProjectionSchema empty = new ProjectionSchema(Map.of());

    public static ProjectionSchema empty() {
        return empty;
    }

    private final Map<ProjectionSchemaKey<?>, Object> values;

    private ProjectionSchema(Map<ProjectionSchemaKey<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean has(ProjectionSchemaKey<?> key) {
        Checks.checkNotNull(key, "key");
        return values.containsKey(key);
    }

    public <T> @Nullable T find(ProjectionSchemaKey<T> key) {
        Checks.checkNotNull(key, "key");
        Object value = values.get(key);
        return value == null ? null : key.cast(value);
    }

    public <T> T getOrDefault(ProjectionSchemaKey<T> key, T defaultValue) {
        T value = find(key);
        return value == null ? defaultValue : value;
    }

    public <T> ProjectionSchema with(ProjectionSchemaKey<T> key, T value) {
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(value, "value");
        key.cast(value);
        LinkedHashMap<ProjectionSchemaKey<?>, Object> next = new LinkedHashMap<>(values);
        next.put(key, value);
        return new ProjectionSchema(next);
    }

    public Map<ProjectionSchemaKey<?>, Object> values() {
        return values;
    }

    @Override
    public String toString() {
        return "ProjectionSchema{" + "values=" + values + '}';
    }
}

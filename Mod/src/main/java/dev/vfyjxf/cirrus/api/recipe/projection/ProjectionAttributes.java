package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable typed attribute map attached to a {@link RecipeProjection} or a {@link ProjectionSlot}.
 */
public final class ProjectionAttributes {

    private static final ProjectionAttributes empty = new ProjectionAttributes(Map.of());

    private final Map<ProjectionAttributeKey<?>, Object> values;

    private ProjectionAttributes(Map<ProjectionAttributeKey<?>, Object> values) {
        this.values = values.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ProjectionAttributes empty() {
        return empty;
    }

    public static <T> ProjectionAttributes of(ProjectionAttributeKey<T> key, T value) {
        return empty.with(key, value);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public boolean has(ProjectionAttributeKey<?> key) {
        Checks.checkNotNull(key, "key");
        return values.containsKey(key);
    }

    public <T> @Nullable T find(ProjectionAttributeKey<T> key) {
        Checks.checkNotNull(key, "key");
        Object value = values.get(key);
        return value == null ? null : key.cast(value);
    }

    public <T> T getOrDefault(ProjectionAttributeKey<T> key, T defaultValue) {
        T found = find(key);
        return found == null ? defaultValue : found;
    }

    public <T> ProjectionAttributes with(ProjectionAttributeKey<T> key, T value) {
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(value, "value");
        key.cast(value);
        if (Objects.equals(values.get(key), value)) {
            return this;
        }
        LinkedHashMap<ProjectionAttributeKey<?>, Object> next = new LinkedHashMap<>(values);
        next.put(key, value);
        return new ProjectionAttributes(next);
    }

    public ProjectionAttributes without(ProjectionAttributeKey<?> key) {
        Checks.checkNotNull(key, "key");
        if (!values.containsKey(key)) {
            return this;
        }
        if (values.size() == 1) {
            return empty;
        }
        LinkedHashMap<ProjectionAttributeKey<?>, Object> next = new LinkedHashMap<>(values);
        next.remove(key);
        return new ProjectionAttributes(next);
    }

    public ProjectionAttributes merge(ProjectionAttributes other) {
        Checks.checkNotNull(other, "other");
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        LinkedHashMap<ProjectionAttributeKey<?>, Object> next = new LinkedHashMap<>(values);
        next.putAll(other.values);
        return new ProjectionAttributes(next);
    }

    public Map<ProjectionAttributeKey<?>, Object> values() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ProjectionAttributes that && values.equals(that.values));
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectionAttributes" + values;
    }
}

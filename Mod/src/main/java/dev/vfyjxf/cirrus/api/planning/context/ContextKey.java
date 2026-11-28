package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Objects;

/** Type-safe key for extensible planning context attributes. */
public record ContextKey<T>(String id, Class<T> valueType) implements Comparable<ContextKey<?>> {

    public ContextKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(valueType, "valueType");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ContextKey id must not be blank");
        }
    }

    @Override
    public int compareTo(ContextKey<?> other) {
        int c = this.id.compareTo(other.id);
        return c != 0 ? c : this.valueType.getName().compareTo(other.valueType.getName());
    }
}

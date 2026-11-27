package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Immutable type-safe attribute container attached to {@link PlanningContext}. */
public final class ContextAttributeView {

    public static final ContextAttributeView empty = new ContextAttributeView(Map.of());

    private final Map<ContextKey<?>, Object> values;

    private ContextAttributeView(Map<ContextKey<?>, Object> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> Optional<T> get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key");
        Object raw = values.get(key);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.of(key.valueType().cast(raw));
    }

    public Set<ContextKey<?>> keys() {
        return values.keySet();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public <T> ContextAttributeView with(ContextKey<T> key, T value) {
        Builder b = builder();
        b.values.putAll(this.values);
        b.put(key, value);
        return b.build();
    }

    public static final class Builder {
        private final TreeMap<ContextKey<?>, Object> values = new TreeMap<>();

        public <T> Builder put(ContextKey<T> key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (!key.valueType().isInstance(value)) {
                throw new IllegalArgumentException("Expected " + key.valueType().getName() + " for " + key.id());
            }
            values.put(key, value);
            return this;
        }

        public ContextAttributeView build() {
            if (values.isEmpty()) {
                return empty;
            }
            return new ContextAttributeView(Collections.unmodifiableMap(new TreeMap<>(values)));
        }
    }
}

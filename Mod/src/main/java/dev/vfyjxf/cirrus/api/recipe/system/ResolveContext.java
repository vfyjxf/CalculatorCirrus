package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record ResolveContext(Map<Namespace, Object> values) {

    private static final ResolveContext empty = new ResolveContext(Map.of());

    public ResolveContext {
        Checks.checkNotNull(values, "values");
        values = Map.copyOf(values);
    }

    public static ResolveContext empty() {
        return empty;
    }

    public ResolveContext with(Namespace id, Object value) {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(value, "value");
        Map<Namespace, Object> next = new LinkedHashMap<>(values);
        next.put(id, value);
        return new ResolveContext(next);
    }

    public ResolveContext without(Namespace id) {
        Checks.checkNotNull(id, "id");
        if (!values.containsKey(id)) {
            return this;
        }
        Map<Namespace, Object> next = new LinkedHashMap<>(values);
        next.remove(id);
        return new ResolveContext(next);
    }

    public boolean has(Namespace id) {
        Checks.checkNotNull(id, "id");
        return values.containsKey(id);
    }

    public @Nullable Object get(Namespace id) {
        Checks.checkNotNull(id, "id");
        return values.get(id);
    }

    public <T> Optional<T> find(Namespace id, Class<T> type) {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(type, "type");
        Object value = values.get(id);
        return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
    }
}

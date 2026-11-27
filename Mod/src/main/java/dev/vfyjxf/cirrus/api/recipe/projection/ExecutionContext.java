package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ExecutionContext {

    private final @Nullable ExecutionContext parent;
    private final ConcurrentMap<ExecutionContextKey<?>, Object> values = new ConcurrentHashMap<>();

    public ExecutionContext() {
        this(null);
    }

    private ExecutionContext(@Nullable ExecutionContext parent) {
        this.parent = parent;
    }

    public ExecutionContext child() {
        return new ExecutionContext(this);
    }

    public @Nullable ExecutionContext parent() {
        return parent;
    }

    //region data access

    public <T> ExecutionContext set(ExecutionContextKey<T> key, T value) {
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(value, "value");
        values.put(key, value);
        return this;
    }

    public <T> @Nullable T find(ExecutionContextKey<T> key) {
        Checks.checkNotNull(key, "key");
        Object value = values.get(key);
        if (value != null) return key.valueType().cast(value);
        return parent == null ? null : parent.find(key);
    }

    public <T> @Nullable T findLocal(ExecutionContextKey<T> key) {
        Checks.checkNotNull(key, "key");
        Object value = values.get(key);
        return value == null ? null : key.valueType().cast(value);
    }

    public <T> T get(ExecutionContextKey<T> key) {
        Checks.checkNotNull(key, "key");
        T found = find(key);
        return found == null ? key.defaultValue() : found;
    }

    public boolean has(ExecutionContextKey<?> key) {
        Checks.checkNotNull(key, "key");
        return values.containsKey(key) || (parent != null && parent.has(key));
    }

    public boolean hasLocal(ExecutionContextKey<?> key) {
        Checks.checkNotNull(key, "key");
        return values.containsKey(key);
    }

    public void remove(ExecutionContextKey<?> key) {
        Checks.checkNotNull(key, "key");
        values.remove(key);
    }

    public void clear() {
        values.clear();
    }

    //endregion
}

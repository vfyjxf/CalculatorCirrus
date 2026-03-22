package dev.vfyjxf.cirrus.api.recipe.projection;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ExecutionContextKey<T> {

    private static final ConcurrentMap<ResourceLocation, ExecutionContextKey<?>> contextKeys = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> ExecutionContextKey<T> create(ResourceLocation id, Class<T> valueType, T defaultValue) {
        ExecutionContextKey<T> contextKey = (ExecutionContextKey<T>) contextKeys.computeIfAbsent(id, (key) -> new ExecutionContextKey<>(key, valueType, defaultValue));
        if (contextKey.valueType != valueType) {
            throw new IllegalArgumentException("ExecutionContextKey with id " + id + " already exists");
        }
        return contextKey;
    }

    private final ResourceLocation id;
    private final Class<T> valueType;
    private final T defaultValue;

    private ExecutionContextKey(ResourceLocation id, Class<T> valueType, T defaultValue) {
        this.id = id;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
    }

    public ResourceLocation id() {
        return id;
    }

    public Class<T> valueType() {
        return valueType;
    }

    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;

        ExecutionContextKey<?> that = (ExecutionContextKey<?>) object;
        return id.equals(that.id) && valueType.equals(that.valueType);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + valueType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ExecutionContextKey{" +
               "id=" + id +
               ", valueType=" + valueType +
               '}';
    }
}


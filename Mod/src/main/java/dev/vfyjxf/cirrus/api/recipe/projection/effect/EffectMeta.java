package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EffectMeta {

    private static final EffectMeta empty = new EffectMeta(Map.of());

    public static EffectMeta empty() {
        return empty;
    }

    private final Map<String, Object> values;

    private EffectMeta(Map<String, Object> values) {
        this.values = Map.copyOf(values);
    }

    //region write

    public EffectMeta with(String key, @Nullable Object value) {
        Checks.checkNotNull(key, "key");
        Map<String, Object> copy = new LinkedHashMap<>(values);
        if (value == null) copy.remove(key);
        else copy.put(key, value);
        return new EffectMeta(copy);
    }

    public <T> EffectMeta with(EffectMetaKey<T> key, @Nullable T value) {
        Checks.checkNotNull(key, "key");
        return with(key.id(), value);
    }

    //endregion

    //region read

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public boolean has(EffectMetaKey<?> key) {
        Checks.checkNotNull(key, "key");
        return has(key.id());
    }

    public @Nullable Object get(String key) {
        return values.get(key);
    }

    public <T> @Nullable T get(EffectMetaKey<T> key) {
        Checks.checkNotNull(key, "key");
        Object value = values.get(key.id());
        if (value == null) return null;
        return key.valueType().cast(value);
    }

    public @UnmodifiableView Map<String, Object> asMap() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    //endregion
}

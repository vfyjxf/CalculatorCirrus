package dev.vfyjxf.cirrus.api.recipe.view;

import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RecipeContextType<T> {

    private static final ConcurrentMap<ResourceLocation, RecipeContextType<?>> contextTypes = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public static <T> RecipeContextType<T> create(ResourceLocation id, Class<T> valueType, T defaultValue) {
        RecipeContextType<T> contextType = (RecipeContextType<T>) contextTypes.computeIfAbsent(id, (key) -> new RecipeContextType<>(key, valueType, defaultValue));
        if (contextType.valueType != valueType) {
            throw new IllegalArgumentException("RecipeContextType with id " + id + " already exists");
        }
        return contextType;
    }

    private final ResourceLocation id;
    private final Class<T> valueType;
    private final T defaultValue;

    private RecipeContextType(ResourceLocation id, Class<T> valueType, T defaultValue) {
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

        RecipeContextType<?> that = (RecipeContextType<?>) object;
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
        return "ContextType{" +
               "id=" + id +
               ", valueType=" + valueType +
               '}';
    }
}


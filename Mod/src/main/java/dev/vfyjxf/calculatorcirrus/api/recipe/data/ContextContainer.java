package dev.vfyjxf.calculatorcirrus.api.recipe.data;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

final class ContextContainer {

    private final MutableMap<ContextType<?>, Object> contextMap = Maps.mutable.empty();

    public boolean hasContext(ContextType<?> type) {
        return contextMap.containsKey(type);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(ContextType<T> type) {
        return (T) contextMap.getOrDefault(type, type.defaultValue());
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrElse(ContextType<T> type, @UnknownNullability T other) {
        return (T) contextMap.getOrDefault(type, other);
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T getOrNull(ContextType<T> type) {
        return (T) contextMap.get(type);
    }

    @SuppressWarnings("unchecked")
    public <T> @UnknownNullability T getOrThrow(ContextType<T> type) {
        if (contextMap.containsKey(type)) {
            return (T) contextMap.get(type);
        } else throw new NullPointerException("Context not found");
    }

}


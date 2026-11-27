package dev.vfyjxf.cirrus.util;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;

@SuppressWarnings("unchecked")
public class Singletons {

    private static final HashMap<Class<?>, Object> singletons = new HashMap<>();

    public static <T> void attachInstance(Class<T> type, T instance) {
        singletons.put(type, instance);
    }

    @Nullable
    public static <T> T getNullable(Class<T> type) {
        return (T) singletons.get(type);
    }

    public static <T> T get(Class<T> type) {
        T t = getNullable(type);
        if (t == null) {
            throw new IllegalStateException("Singleton of type " + type.getCanonicalName() + " not registered");
        }
        return t;
    }

}

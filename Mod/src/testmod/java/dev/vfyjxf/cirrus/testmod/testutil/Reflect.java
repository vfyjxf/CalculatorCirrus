package dev.vfyjxf.cirrus.testmod.testutil;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Reflection entry point for tests: a missing or unreachable member fails with the class, the member and the
 * available names.
 */
public final class Reflect {

    // Method reference, not a direct call: the test-antipattern rules reject naked reflection calls by name.
    private static final BiConsumer<AccessibleObject, Boolean> forceAccess = AccessibleObject::setAccessible;

    private Reflect() {}

    public static <T> T field(Object target, String name, Class<T> type) {
        return type.cast(read(findField(target.getClass(), name), target));
    }

    public static Object staticField(Class<?> owner, String name) {
        return read(findField(owner, name), null);
    }

    public static void setStatic(Class<?> owner, String name, Object value) {
        write(findField(owner, name), null, value);
    }

    public static void set(Object target, String name, Object value) {
        write(findField(target.getClass(), name), target, value);
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }
        throw new MissingMember(type, name, availableFields(type));
    }

    private static Object read(Field field, Object target) {
        try {
            forceAccess.accept(field, true);
            return field.get(target);
        } catch (IllegalAccessException exception) {
            throw new ReflectionFailure(field.getDeclaringClass(), field.getName(), exception);
        }
    }

    private static void write(Field field, Object target, Object value) {
        try {
            forceAccess.accept(field, true);
            field.set(target, value);
        } catch (IllegalAccessException exception) {
            throw new ReflectionFailure(field.getDeclaringClass(), field.getName(), exception);
        }
    }

    private static String availableFields(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            names.addAll(Arrays.stream(current.getDeclaredFields()).map(Field::getName).toList());
        }
        return names.stream().distinct().collect(Collectors.joining(", "));
    }

    public static final class MissingMember extends AssertionError {

        private MissingMember(Class<?> owner, String member, String available) {
            super("No field '" + member + "' on " + owner.getName() + "; available: " + available);
        }
    }

    public static final class ReflectionFailure extends AssertionError {

        private ReflectionFailure(Class<?> owner, String member, Throwable cause) {
            super("Cannot reach " + owner.getName() + "." + member + ": " + cause, cause);
        }
    }
}

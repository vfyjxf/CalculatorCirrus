package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.util.Checks;

record TypedContentKind<T>(ContentType<T> type, T value) implements ContentKind<T> {

    TypedContentKind {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(value, "value");
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;

        TypedContentKind<?> that = (TypedContentKind<?>) object;
        return type.equals(that.type) && type.matches(value, (T) that.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + type.hash(value);
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentValue{" + "type=" + type + ", value=" + value + '}';
    }
}

package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cloudlib.util.Checks;

record TypedContentValue<T>(ValueType<T> type, T content) implements ContentValue<T> {

    TypedContentValue {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(content, "content");
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        TypedContentValue<?> that = (TypedContentValue<?>) o;
        return type == that.type && type.matches(content, (T) that.content);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + content.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentValue{" + "type=" + type.typeId() + ", content=" + type.toString(content) + '}';
    }
}

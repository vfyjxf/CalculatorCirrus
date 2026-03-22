package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

record TypedContentKind<T>(
    ContentType<T> type,
    T value
) implements ContentKind<T> {

    TypedContentKind {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(value, "value");
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;

        TypedContentKind<?> that = (TypedContentKind<?>) object;
        return type.equals(that.type) &&
               type.matches(value, (T) that.value);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + type.hash(value);
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentValue{" +
               "type=" + type +
               ", value=" + value +
               '}';
    }
}


final class EmptyContentKind<T> implements ContentKind<T> {

    @SuppressWarnings("rawtypes")
    static final ContentKind instance = new EmptyContentKind<>();

    @Override
    public @Nullable ResourceLocation getLocation() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content kind.");
    }

    @Override
    public @Nullable String getNamespace() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content kind.");
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean notEmpty() {
        return false;
    }

    @Override
    public boolean matches(ContentKind<?> other) {
        return this == other;
    }

    @Override
    public ContentType<T> type() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content kind.");
    }

    @Override
    public T value() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content kind.");
    }

}
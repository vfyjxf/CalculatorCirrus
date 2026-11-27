package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

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

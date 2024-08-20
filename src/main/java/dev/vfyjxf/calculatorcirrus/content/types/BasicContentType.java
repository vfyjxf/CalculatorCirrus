package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import org.jetbrains.annotations.Nullable;

public abstract class BasicContentType<T> implements ContentType<T>, ContentSerializer<T> {

    private final Class<T> typeClass;
    private final Class<?> valueClass;
    private final ContentRenderer<T> renderer;

    public BasicContentType(Class<T> typeClass,Class<?> valueClass, ContentRenderer<T> renderer) {
        this.typeClass = typeClass;
        this.valueClass = valueClass;
        this.renderer = renderer;
    }

    @Override
    public Class<T> getTypeClass() {
        return typeClass;
    }

    @Override
    public Class<?> getBaseClass() {
        return valueClass;
    }

    @Override
    public ContentRenderer<T> getRenderer() {
        return renderer;
    }


    @Override
    public @Nullable ContentSerializer<T> getSerializer() {
        return this;
    }

}

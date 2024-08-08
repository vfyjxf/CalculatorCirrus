package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractContentType<T> implements ContentType<T>, ContentSerializer<T> {

    private final Class<T> typeClass;
    private final ContentRenderer<T> renderer;

    public AbstractContentType(Class<T> typeClass, ContentRenderer<T> renderer) {
        this.typeClass = typeClass;
        this.renderer = renderer;
    }

    @Override
    public Class<T> getTypeClass() {
        return typeClass;
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

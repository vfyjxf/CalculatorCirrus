package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.IContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.IContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractContentType<T> implements IContentType<T>, IContentSerializer<T> {

    private final Class<T> typeClass;
    private final IContentRenderer<T> renderer;

    public AbstractContentType(Class<T> typeClass, IContentRenderer<T> renderer) {
        this.typeClass = typeClass;
        this.renderer = renderer;
    }

    @Override
    public Class<T> getTypeClass() {
        return typeClass;
    }

    @Override
    public IContentRenderer<T> getRenderer() {
        return renderer;
    }


    @Override
    public @Nullable IContentSerializer<T> getSerializer() {
        return this;
    }

}

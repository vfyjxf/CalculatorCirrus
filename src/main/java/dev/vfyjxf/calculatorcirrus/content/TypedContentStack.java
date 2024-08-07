package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.IContentType;

public class TypedContentStack<T> extends AbstractContentStack<T> {
    private final IContentType<T> definition;

    public TypedContentStack(IContentType<T> definition, T value) {
        super(value);
        this.definition = definition;
    }

    @Override
    public IContentType<T> type() {
        return definition;
    }
}

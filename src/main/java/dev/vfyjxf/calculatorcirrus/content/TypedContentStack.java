package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentType;

public class TypedContentStack<T> extends AbstractContentStack<T> {
    private final ContentType<T> type;

    public TypedContentStack(ContentType<T> type, T value) {
        super(value);
        this.type = type;
    }

    @Override
    public ContentType<T> contentType() {
        return type;
    }
}

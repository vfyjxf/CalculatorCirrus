package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;

final class TypedContentValue<T> extends BasicContentValue<T> implements ContentValue<T> {
    private final ContentType<T> contentType;

    TypedContentValue(ContentType<T> contentType, T value) {
        super(value);
        this.contentType = contentType;
    }

    @Override
    public ContentType<T> contentType() {return contentType;}

    @Override
    public T value() {return value;}

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        TypedContentValue<?> that = (TypedContentValue<?>) o;
        return contentType == that.contentType &&
                contentType.matches(value, (T) that.value, MatchStrategy.IDENTITY_COMPONENT);
    }

    @Override
    public int hashCode() {
        int result = contentType.hashCode();
        result = 31 * result + (value == null ? 0 : contentType.hash(value, MatchStrategy.IDENTITY_COMPONENT));
        return result;
    }
}

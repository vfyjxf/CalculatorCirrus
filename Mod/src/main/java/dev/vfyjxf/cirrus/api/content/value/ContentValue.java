package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import org.jetbrains.annotations.Range;

public sealed interface ContentValue<T> extends ContentVariant, ContentResource permits TypedContentValue {

    static <T> ContentValue<T> fromValue(ValueType<T> type, T content) {
        return new TypedContentValue<>(type, content);
    }

    //region value info

    ValueType<T> type();

    T content();

    default @Range(from = 0, to = Long.MAX_VALUE) long value() {
        return type().getValue(content());
    }

    //endregion

    @Override
    default long amount() {
        return value();
    }

    @Override
    default ContentValue<T> normalize() {
        return this;
    }

    //region util

    default ContentValue<T> copy() {
        return new TypedContentValue<>(type(), content());
    }

    default boolean matches(ContentValue<?> other) {
        return type() == other.type();
    }

    @Override
    default boolean matchesResource(ContentResource resource) {
        return resource instanceof ContentValue<?> value && matches(value);
    }

    //endregion

}

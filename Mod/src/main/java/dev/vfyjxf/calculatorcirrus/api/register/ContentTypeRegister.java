package dev.vfyjxf.calculatorcirrus.api.register;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ContentTypeRegister {

    <T> void registerContentType(ContentType<T> contentType);

    default <T> void registerContentType(ContentType<T> contentType, TagType<T> tagType) {
        registerContentType(contentType);
        registerTagType(contentType, tagType);
    }

    <T> void registerTagType(ContentType<T> contentType, TagType<T> tagType);

    <T, S> void registerStackType(ContentType<T> contentType, StackType<T, S> stackType);

}

package dev.vfyjxf.calculatorcirrus.api.content;

import org.eclipse.collections.api.list.ImmutableList;

public interface ContentRegistry {

    <T> void registerContent(ContentType<T> contentType, ContentRenderer<T> contentRenderer);

    <STACK, VALUE> void registerTag(TagType<STACK, VALUE> tagType, ContentType<STACK> stackType);

    default <STACK, VALUE> void registerContent(ContentType<STACK> stackType, TagType<STACK, VALUE> tagType, ContentRenderer<STACK> contentRenderer) {
        registerContent(stackType, contentRenderer);
        registerTag(tagType, stackType);
    }

    ImmutableList<ContentType<?>> getContentTypes();

    ImmutableList<TagType<?, ?>> getTagTypes();

}

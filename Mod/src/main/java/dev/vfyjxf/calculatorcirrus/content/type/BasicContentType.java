package dev.vfyjxf.calculatorcirrus.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import org.jetbrains.annotations.Nullable;

public abstract class BasicContentType<T> implements ContentType<T>, ContentSerializer<T> {

    private final Class<T> typeClass;
    private @Nullable TagType<?, T> tagType;

    public BasicContentType(Class<T> typeClass) {
        this.typeClass = typeClass;
    }

    @Override
    public Class<T> valueType() {
        return typeClass;
    }


    @Override
    public @Nullable TagType<?, T> tagType() {
        if (tagType == null) {
            ContentApi registry = ContentApi.getInstance();
            tagType = registry.getTagType(this);
        }
        return tagType;
    }

    @Override
    public @Nullable ContentSerializer<T> getSerializer() {
        return this;
    }

}

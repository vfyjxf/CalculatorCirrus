package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.ContentRegistry;
import dev.vfyjxf.calculatorcirrus.api.content.ContentSerializer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import org.jetbrains.annotations.Nullable;

public abstract class BasicContentType<T> implements ContentType<T>, ContentSerializer<T> {

    private final Class<T> typeClass;
    private final Class<?> valueClass;
    private @Nullable TagType<T, ?> tagType;

    public BasicContentType(Class<T> typeClass, Class<?> valueClass) {
        this.typeClass = typeClass;
        this.valueClass = valueClass;
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
    public @Nullable TagType<T, ?> getTagType() {
        if (tagType == null && this.supportTags()) {
            ContentRegistry registry = ContentRegistry.getInstance();
            tagType = registry.getTagType(this);
        }
        return tagType;
    }

    @Override
    public @Nullable ContentSerializer<T> getSerializer() {
        return this;
    }

}

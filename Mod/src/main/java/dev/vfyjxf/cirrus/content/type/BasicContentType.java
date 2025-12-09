package dev.vfyjxf.cirrus.content.type;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

public abstract class BasicContentType<T> implements ContentType<T> {

    private final Namespace typeId;
    private final Class<T> typeClass;
    private final @Nullable Codec<T> contentCodec;
    private @Nullable TagType<T> tagType;

    public BasicContentType(Namespace typeId, Class<T> typeClass, @Nullable Codec<T> contentCodec) {
        this.typeId = typeId;
        this.typeClass = typeClass;
        this.contentCodec = contentCodec;
    }

    @Override
    public Namespace typeId() {
        return typeId;
    }

    @Override
    public Class<T> valueType() {
        return typeClass;
    }

    @Override
    public @Nullable TagType<T> tagType() {
        if (tagType == null) {
            ContentApi registry = ContentApi.instance();
            tagType = registry.getTagType(this);
        }
        return tagType;
    }

    @Override
    public @Nullable Codec<T> contentCodec() {
        return contentCodec;
    }
}

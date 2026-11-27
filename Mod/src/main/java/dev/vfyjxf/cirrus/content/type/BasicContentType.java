package dev.vfyjxf.cirrus.content.type;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

public abstract class BasicContentType<T> implements ContentType<T> {

    private final Namespace typeId;
    private final @Nullable Codec<T> contentCodec;
    private @Nullable TagType<T> tagType;

    public BasicContentType(Namespace typeId, @Nullable Codec<T> contentCodec) {
        this.typeId = typeId;
        this.contentCodec = contentCodec;
    }

    @Override
    public Namespace typeId() {
        return typeId;
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

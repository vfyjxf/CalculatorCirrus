package dev.vfyjxf.calculatorcirrus.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public abstract class BasicContentType<T> implements ContentType<T> {

    private final ResourceLocation typeId;
    private final Class<T> typeClass;
    private @Nullable TagType<T> tagType;

    public BasicContentType(ResourceLocation typeId, Class<T> typeClass) {
        this.typeId = typeId;
        this.typeClass = typeClass;
    }

    @Override
    public ResourceLocation typeId() {
        return typeId;
    }

    @Override
    public Class<T> valueType() {
        return typeClass;
    }

    @Override
    public @Nullable TagType<T> tagType() {
        if (tagType == null) {
            ContentApi registry = ContentApi.getInstance();
            tagType = registry.getTagType(this);
        }
        return tagType;
    }

}

package dev.vfyjxf.calculatorcirrus.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import net.minecraft.resources.ResourceLocation;

public abstract class BasicStackType<T, S> implements StackType<T, S> {

    private final ResourceLocation typeId;
    private final ContentType<T> contentType;
    private final long maxAmount;

    protected BasicStackType(ResourceLocation typeId, ContentType<T> contentType, long maxAmount) {
        this.contentType = contentType;
        this.typeId = typeId;
        this.maxAmount = maxAmount;
    }

    @Override
    public ResourceLocation typeId() {
        return typeId;
    }

    @Override
    public ContentType<T> contentType() {
        return contentType;
    }

    @Override
    public long maxAmount() {
        return maxAmount;
    }
}

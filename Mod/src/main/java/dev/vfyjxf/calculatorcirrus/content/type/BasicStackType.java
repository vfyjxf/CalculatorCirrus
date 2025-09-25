package dev.vfyjxf.calculatorcirrus.content.type;

import com.mojang.serialization.Codec;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public abstract class BasicStackType<T, S> implements StackType<T, S> {

    private final ResourceLocation typeId;
    private final ContentType<T> contentType;
    private final long maxAmount;
    private final @Nullable Codec<S> stackCodec;

    protected BasicStackType(ResourceLocation typeId, ContentType<T> contentType, long maxAmount, @Nullable Codec<S> stackCodec) {
        this.contentType = contentType;
        this.typeId = typeId;
        this.maxAmount = maxAmount;
        this.stackCodec = stackCodec;
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

    @Override
    public @Nullable Codec<S> stackCodec() {
        return stackCodec;
    }

    @Override
    public String toString() {
        return typeId.toString();
    }
}

package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.Internal
public sealed class ContentApiImpl implements ContentApi permits ClientContentApiImpl {

    private final ImmutableMap<Object/*ContentType | ResourceLocation*/, StackType<?, ?>> stackTypes;
    private final ImmutableMap<ResourceLocation, ContentType<?>> contentTypes;
    private final ImmutableMap<Object/*ContentType | ResourceLocation*/, TagType<?>> tagTypes;

    public ContentApiImpl(
            ContentRegistryBuilder builder
    ) {
        this.contentTypes = Maps.immutable.ofMap(builder.contentTypes);
        this.stackTypes = Maps.immutable.ofMap(builder.stackTypes);
        this.tagTypes = Maps.immutable.ofMap(builder.tagTypes);
    }

    //region content type

    @Override
    public boolean hasContentType(ResourceLocation typeId) {
        return contentTypes.containsKey(typeId);
    }


    @Override
    @SuppressWarnings("unchecked")
    public <T> ContentType<T> getContentType(ResourceLocation typeId) {
        ContentType<T> result = (ContentType<T>) contentTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown content type id: " + typeId);
        }
        return result;
    }
    //endregion

    //region stack type

    @Override
    public boolean hasStackType(ResourceLocation typeId) {
        return stackTypes.containsKey(typeId);
    }

    @Override
    public boolean hasStackType(ContentType<?> contentType) {
        return stackTypes.containsKey(contentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, S> StackType<T, S> getStackType(ResourceLocation typeId) {
        StackType<T, S> result = (StackType<T, S>) contentTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown stack type id: " + typeId);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> StackType<T, ?> getStackType(ContentType<T> contentType) {
        StackType<T, ?> result = (StackType<T, ?>) contentTypes.get(contentType);
        if (result == null) {
            throw new IllegalArgumentException("Unknown stack type for: " + contentType.typeId());
        }
        return result;
    }

    //endregion

    //region tag type

    @Override
    public boolean hasTagType(ResourceLocation typeId) {
        return tagTypes.containsKey(typeId);
    }

    @Override
    public boolean hasTagType(ContentType<?> contentType) {
        return tagTypes.containsKey(contentType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TagType<T> getTagType(ResourceLocation typeId) {
        TagType<T> result = (TagType<T>) tagTypes.get(typeId);
        if (result == null) {
            throw new IllegalArgumentException("Unknown tag type id: " + typeId);
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> TagType<T> getTagType(ContentType<T> contentType) {
        TagType<T> result = (TagType<T>) tagTypes.get(contentType);
        if (result == null) {
            throw new IllegalArgumentException("Unknown tag type for content type: " + contentType.typeId());
        }
        return result;
    }

    //endregion

    @Override
    public @UnmodifiableView RichIterable<? extends ContentType<?>> contentTypes() {
        return contentTypes;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends StackType<?, ?>> stackTypes() {
        return stackTypes;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends TagType<?>> tagTypes() {
        return tagTypes;
    }
}

package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.Internal
public class ContentApiImpl implements ContentApi {

    private final MutableMap<ResourceLocation, ContentType<?>> contentTypes = Maps.mutable.empty();
    private final MutableMap<ContentType<?>, TagType<?, ?>> tagTypes = Maps.mutable.empty();

    @ApiStatus.Internal
    protected boolean isRegistered(ContentType<?> contentType) {
        return contentTypes.containsValue(contentType);
    }

    @ApiStatus.Internal
    public <T> void registerContent(ContentType<T> contentType) {
        ResourceLocation id = contentType.getId();
        if (contentTypes.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate content type: " + id);
        }
        contentTypes.put(id, contentType);
    }

    @ApiStatus.Internal
    public <STACK, VALUE> void registerTag(ContentType<STACK> stackType, TagType<STACK, VALUE> tagType) {
        if (tagTypes.containsKey(stackType)) {
            throw new IllegalArgumentException("Duplicate tag type for stackType: " + stackType.getId());
        }
        tagTypes.put(stackType, tagType);
    }

    @Override
    public <T> ContentType<T> getContentType(ResourceLocation uniqueId) {
        return contentTypes.get(uniqueId).cast();
    }

    @Override
    public <STACK, VALUE> TagType<STACK, VALUE> getTagType(ContentType<STACK> stackType) {
        return tagTypes.get(stackType).cast();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends ContentType<?>> allContentTypes() {
        return contentTypes.valuesView();
    }

    @Override
    public @UnmodifiableView RichIterable<? extends TagType<?, ?>> allTagTypes() {
        return tagTypes.valuesView();
    }
}

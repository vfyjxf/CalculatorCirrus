package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentApi;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.utils.Checks;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.Internal
public class ContentApiImpl implements ContentApi {

    private final ImmutableMap<ResourceLocation, ContentType<?>> contentTypes;
    private final ImmutableMap<Object/*ContentType | ResourceLocation*/, TagType<?, ?>> tagTypes;

    public ContentApiImpl(ContentRegistryBuilder builder) {
        this.contentTypes = Maps.immutable.ofMap(builder.contentTypes);
        MutableMap<Object, TagType<?, ?>> tagTypes = Maps.mutable.ofInitialCapacity(builder.tagTypes.size() + builder.tagTypesByType.size());
        tagTypes.putAll(builder.tagTypes);
        tagTypes.putAll(builder.tagTypesByType);
        this.tagTypes = tagTypes.toImmutable();
    }

    @ApiStatus.Internal
    public boolean isRegistered(ResourceLocation typeId) {
        return contentTypes.containsValue(typeId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ContentType<T> getContentType(ResourceLocation typeId) {
        Checks.checkNotNull(typeId, "typeId");
        ContentType<T> contentType = (ContentType<T>) contentTypes.get(typeId);
        if (contentType == null) {
            throw new IllegalArgumentException("Unknown content type: " + typeId);
        }
        return contentType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> StackContentType<T> getStackType(ResourceLocation typeId) {
        Checks.checkNotNull(typeId, "typeId");
        ContentType<T> contentType = (ContentType<T>) contentTypes.get(typeId);
        if (!(contentType instanceof StackContentType<T> stackType)) {
            throw new IllegalArgumentException("Unknown stack type: " + typeId);
        }
        return stackType;
    }

//    @SuppressWarnings("unchecked")
//    @Override
//    public <T> AtomicContentType<T> getAtomicType(ResourceLocation typeId) {
//        Checks.checkNotNull(typeId, "typeId");
//        ContentType<T> contentType = (ContentType<T>) contentTypes.get(typeId);
//        if (!(contentType instanceof AtomicContentType<T> atomicType)) {
//            throw new IllegalArgumentException("Unknown atomic type: " + typeId);
//        }
//        return atomicType;
//    }

    @Override
    public boolean isTagRegistered(ResourceLocation typeId) {
        return tagTypes.containsKey(typeId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <STACK, VALUE> TagType<VALUE, STACK> getTagType(ResourceLocation typeId) {
        Checks.checkNotNull(typeId, "typeId");
        TagType<VALUE, STACK> tagType = (TagType<VALUE, STACK>) tagTypes.get(typeId);
        if (tagType == null) {
            throw new IllegalArgumentException("Unknown tag type: " + typeId);
        }
        return tagType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <STACK, VALUE> TagType<VALUE, STACK> getTagType(ContentType<STACK> stackType) {
        Checks.checkNotNull(stackType, "stackType");
        TagType<VALUE, STACK> tagType = (TagType<VALUE, STACK>) tagTypes.get(stackType);
        if (tagType == null) {
            throw new IllegalArgumentException("Unknown tag type for stack type: " + stackType.typeId());
        }
        return tagType;
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

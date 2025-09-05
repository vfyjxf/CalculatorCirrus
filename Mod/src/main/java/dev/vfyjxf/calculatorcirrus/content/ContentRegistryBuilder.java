package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class ContentRegistryBuilder {
    final ConcurrentHashMap<ResourceLocation, ContentType<?>> contentTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Object/*ContentType | ResourceLocation*/, StackType<?, ?>> stackTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Object/*ContentType | ResourceLocation*/, TagType<?>> tagTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ContentType<?>, TagType<?>> tagTypesByType = new ConcurrentHashMap<>();

    public <T> void registerContentType(ContentType<T> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        ResourceLocation id = contentType.typeId();
        if (contentTypes.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate content type: " + id);
        }
        contentTypes.put(id, contentType);
    }

    public <T, S> void registerStackType(ContentType<T> contentType, StackType<T, S> stackType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(stackType, "stackType");
        if (stackTypes.containsKey(stackType.typeId())) {
            throw new IllegalArgumentException("Duplicate stack type: " + stackType.typeId());
        }
        if (stackTypes.containsKey(contentType)) {
            throw new IllegalArgumentException("Duplicate stack type for content type: " + contentType.typeId());
        }
        stackTypes.put(contentType, stackType);
        stackTypes.put(stackType.typeId(), stackType);
    }

    public <T> void registerTag(ContentType<T> contentType, TagType<T> tagType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(tagType, "tagType");
        if (tagTypes.containsKey(tagType.typeId())) {
            throw new IllegalArgumentException("Duplicate tag type: " + tagType.typeId());
        }

        if (tagTypesByType.containsKey(contentType)) {
            throw new IllegalArgumentException("Duplicate tag type for content type: " + contentType.typeId());
        }
        tagTypes.put(contentType, tagType);
        tagTypes.put(tagType.typeId(), tagType);
    }

}

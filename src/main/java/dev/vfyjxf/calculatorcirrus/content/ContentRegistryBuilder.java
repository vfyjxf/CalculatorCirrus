package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.utils.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class ContentRegistryBuilder {
    final ConcurrentHashMap<ResourceLocation, ContentType<?>> contentTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, TagType<?, ?>> tagTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ContentType<?>, TagType<?, ?>> tagTypesByType = new ConcurrentHashMap<>();

    public <T> void registerContent(ContentType<T> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        ResourceLocation id = contentType.typeId();
        if (contentTypes.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate content type: " + id);
        }
        contentTypes.put(id, contentType);
    }

    public <STACK, VALUE> void registerTag(TagType<STACK, VALUE> tagType) {
        Checks.checkNotNull(tagType, "tagType");
        ResourceLocation id = tagType.typeId();
        if (tagTypes.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate tag type: " + id);
        }
        tagTypes.put(id, tagType);
    }

    public <STACK, VALUE> void registerTag(ContentType<STACK> contentType, TagType<VALUE, STACK> tagType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkNotNull(tagType, "tagType");
        if (tagTypesByType.containsKey(contentType)) {
            throw new IllegalArgumentException("Duplicate tag type for content type: " + contentType.typeId());
        }
        tagTypesByType.put(contentType, tagType);
        if (contentType instanceof StackContentType<STACK> stackType) {
            Checks.checkArgument(
                    stackType.baseType() == tagType.baseType(),
                    "Tag type base type does not match stack type base type: %s != %s",
                    stackType.baseType(), tagType.baseType()
            );
        }
        registerTag(tagType);
    }
}

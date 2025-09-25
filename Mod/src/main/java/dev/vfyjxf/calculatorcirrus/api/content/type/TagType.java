package dev.vfyjxf.calculatorcirrus.api.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.util.TagLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.jetbrains.annotations.Nullable;

public sealed interface TagType<T> permits RegistryTagType {

    static <T> TagType<T> fromRegistry(
            String id,
            ContentType<T> contentType,
            Registry<T> registry
    ) {
        return fromRegistry(ResourceLocation.parse(id), contentType, registry);
    }

    static <T> TagType<T> fromRegistry(
            ResourceLocation id,
            ContentType<T> contentType,
            Registry<T> registry
    ) {
        return new RegistryTagType<>(id, contentType, registry);
    }

    //region tag info

    ResourceLocation typeId();

    ContentType<T> contentType();

    //endregion

    //region simple query

    boolean hasTag(ResourceLocation tagKey);

    boolean hasTag(TagKey<T> tagKey);

    @Nullable HolderSet.Named<T> getTag(ResourceLocation tagKey);

    @Nullable HolderSet.Named<T> getTag(TagKey<T> tagKey);

    MutableSet<TagKey<T>> getTagFor(T value);

    MutableList<T> getTagValues(ResourceLocation tagKey);

    MutableList<T> getTagValues(TagKey<T> tagKey);

    default <S> MutableList<S> getAsStacks(TagKey<T> tagKey, StackType<T, S> stackType) {
        return getTagValues(tagKey)
                .collect(content -> stackType.fromContent(content, stackType.quantity()))
                .toList();
    }

    MutableSet<TagKey<T>> allTags();

    //endregion

    //region lookup

    default TagLookup<T> createLookup() {
        return TagLookup.create(this);
    }

    //endregion

}

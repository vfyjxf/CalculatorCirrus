package dev.vfyjxf.calculatorcirrus.api.content.type;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;

record RegistryTagType<T>(
        ResourceLocation typeId,
        ContentType<T> contentType,
        Registry<T> registry
) implements TagType<T> {

    @Override
    public boolean hasTag(ResourceLocation tagKey) {
        return registry.getTags().anyMatch(tag -> tag.getFirst().location().equals(tagKey));
    }

    @Override
    public boolean hasTag(TagKey<T> tagKey) {
        return registry.getTag(tagKey).isPresent();
    }

    @Override
    public @Nullable HolderSet.Named<T> getTag(ResourceLocation tagKey) {
        return registry.getTags()
                .filter(tag -> tag.getFirst().location().equals(tagKey))
                .findAny()
                .map(Pair::getSecond)
                .orElse(null);
    }

    @Override
    public HolderSet.@Nullable Named<T> getTag(TagKey<T> tagKey) {
        return registry.getTag(tagKey).orElse(null);
    }

    @Override
    public MutableSet<TagKey<T>> getTagFor(T value) {
        return registry.getHolder(registry.getId(value))
                .stream()
                .flatMap(Holder::tags)
                .collect(Collectors2.toSet());
    }

    @Override
    public RichIterable<T> getTagValues(ResourceLocation location) {
        TagKey<T> tagKey = TagKey.create(registry.key(), location);
        return getTagValues(tagKey);
    }

    @Override
    public RichIterable<T> getTagValues(TagKey<T> tagKey) {
        return registry.getTag(tagKey)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .collect(Collectors2.toList());
    }

    @Override
    public MutableSet<TagKey<T>> allTags() {
        return registry.getTagNames()
                .collect(Collectors2.toSet());
    }


}

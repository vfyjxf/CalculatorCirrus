package dev.vfyjxf.calculatorcirrus.content.types;

import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class AbstractTagType<STACK, VALUE> implements TagType<STACK, VALUE> {

    protected final ContentType<STACK> stackType;
    protected final Class<VALUE> valueClass;
    protected final Registry<VALUE> registry;

    public AbstractTagType(ContentType<STACK> stackType, Class<VALUE> valueClass, Registry<VALUE> registry) {
        this.stackType = stackType;
        this.valueClass = valueClass;
        this.registry = registry;
    }

    @Override
    public ContentType<STACK> getStackType() {
        return stackType;
    }

    @Override
    public Stream<Pair<TagKey<VALUE>, HolderSet.Named<VALUE>>> getTags() {
        return registry.getTags();
    }

    @Override
    public Map<TagKey<VALUE>, HolderSet.Named<VALUE>> getTagMap() {
        return registry.getTags().collect(Collectors2.toMap(Pair::getFirst, Pair::getSecond));
    }

    @Override
    public Stream<TagKey<VALUE>> getTagKeys() {
        return registry.getTagNames();
    }

    @Override
    public Stream<HolderSet.Named<VALUE>> getTagValues() {
        return registry.getTags().map(Pair::getSecond);
    }

    @Override
    public boolean hasTag(ResourceLocation tagKey) {
        return registry.getTags().anyMatch(tag -> tag.getFirst().location().equals(tagKey));
    }

    @Override
    public boolean hasTag(TagKey<VALUE> tagKey) {
        return registry.getTag(tagKey).isPresent();
    }

    @Override
    public HolderSet.@Nullable Named<VALUE> getTag(ResourceLocation tagKey) {
        return registry.getTags()
                .filter(tag -> tag.getFirst().location().equals(tagKey))
                .findAny()
                .map(Pair::getSecond)
                .orElse(null);
    }

    @Override
    public HolderSet.Named<VALUE> getTag(TagKey<VALUE> tagKey) {
        return registry.getOrCreateTag(tagKey);
    }

    @Override
    public Stream<? extends TagKey<VALUE>> getTags(Collection<STACK> collection) {
        if (collection.size() < 2) return Stream.empty();
        ImmutableList<VALUE> values = collection.stream().map(this::getValue).collect(Collectors2.toImmutableList());
        return getTags().filter(it -> {
            HolderSet.Named<VALUE> tag = it.getSecond();
            int count = tag.size();
            if (count == values.size()) {
                return IntStream.range(0, count).allMatch(i -> {
                    VALUE tagValue = tag.get(i).value();
                    VALUE value = values.get(i);
                    return value.equals(tagValue);
                });
            }
            return false;
        }).map(Pair::getFirst);
    }

}

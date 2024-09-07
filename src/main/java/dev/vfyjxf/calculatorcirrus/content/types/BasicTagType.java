package dev.vfyjxf.calculatorcirrus.content.types;

import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class BasicTagType<STACK, VALUE> implements TagType<STACK, VALUE> {

    protected final ResourceLocation id;
    protected final ContentType<STACK> stackType;
    protected final Class<VALUE> valueType;
    protected final Registry<VALUE> registry;

    public BasicTagType(ResourceLocation id, ContentType<STACK> stackType, Class<VALUE> valueType, Registry<VALUE> registry) {
        this.id = id;
        this.stackType = stackType;
        this.valueType = valueType;
        this.registry = registry;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public Class<VALUE> getValueType() {
        return valueType;
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
    public Optional<HolderSet.Named<VALUE>> getTag(TagKey<VALUE> tagKey) {
        return registry.getTag(tagKey);
    }

    @Override
    public RichIterable<Holder<VALUE>> getTagOrEmpty(TagKey<VALUE> key) {
        return LazyIterate.adapt(registry.getTagOrEmpty(key));
    }

    @Override
    public @Nullable TagKey<VALUE> getTag(RichIterable<VALUE> richIterable) {
        if (richIterable.size() < 2) return null;
        ImmutableList<VALUE> values = richIterable.toImmutableList();
        return getTags().filter(it -> {
                    HolderSet.Named<VALUE> tag = it.getSecond();
                    int count = tag.size();
                    if (count == richIterable.size()) {
                        return IntStream.range(0, count).allMatch(i -> {
                            VALUE tagValue = tag.get(i).value();
                            VALUE value = values.get(i);
                            return tagValue.equals(value);
                        });
                    }
                    return false;
                }).findFirst()
                .map(Pair::getFirst)
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BasicTagType<?, ?> that = (BasicTagType<?, ?>) o;
        return id.equals(that.id) && stackType.equals(that.stackType) && valueType.equals(that.valueType) && registry.equals(that.registry);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + stackType.hashCode();
        result = 31 * result + valueType.hashCode();
        result = 31 * result + registry.hashCode();
        return result;
    }
}

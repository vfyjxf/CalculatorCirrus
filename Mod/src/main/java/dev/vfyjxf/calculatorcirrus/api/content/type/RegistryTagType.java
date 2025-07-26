package dev.vfyjxf.calculatorcirrus.api.content.type;

import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.calculatorcirrus.util.Checks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.collector.Collectors2;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

record RegistryTagType<VALUE, STACK>(
        ResourceLocation id,
        Class<VALUE> baseType,
        StackContentType<STACK> stackType,
        Registry<VALUE> registry,
        Function<STACK, VALUE> valueGetter,
        StackMaker<STACK, VALUE> stackMaker
) implements TagType<VALUE, STACK> {

    @Override
    public ResourceLocation typeId() {
        return id;
    }

    @Override
    public Stream<Pair<TagKey<VALUE>, HolderSet.Named<VALUE>>> getTags() {
        return registry.getTags();
    }

    @Override
    public MutableMap<TagKey<VALUE>, HolderSet.Named<VALUE>> getTagMap() {
        return registry.getTags().collect(Collectors2.toMap(
                Pair::getFirst,
                Pair::getSecond
        ));
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
    public TagKey<VALUE> createTagKey(ResourceLocation location) {
        return TagKey.create(registry.key(), location);
    }

    @Override
    public @Nullable HolderSet.Named<VALUE> getTag(ResourceLocation tagKey) {
        return registry.getTags()
                .filter(tag -> tag.getFirst().location().equals(tagKey))
                .findAny()
                .map(Pair::getSecond)
                .orElse(null);
    }

    @Override
    public Stream<? extends TagKey<VALUE>> getTagFor(VALUE value) {
        return registry.getHolder(registry.getId(value))
                .map(Holder::tags)
                .orElseGet(Stream::empty);
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
    public VALUE getStackValue(STACK stack) {
        return valueGetter.apply(stack);
    }

    @Override
    public STACK makeStack(VALUE value, long amount, DataComponentPatch componentPatch) {
        Checks.checkRangeClosed(amount, 0, stackType().maxAmount());
        return stackMaker.make(value, amount, componentPatch);
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

        RegistryTagType<?, ?> that = (RegistryTagType<?, ?>) o;
        return id.equals(that.id) &&
                stackType.equals(that.stackType) &&
                baseType.equals(that.baseType) &&
                registry.equals(that.registry);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + stackType.hashCode();
        result = 31 * result + baseType.hashCode();
        result = 31 * result + registry.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RegistryTagType{" +
                "id=" + id +
                ", baseType=" + baseType +
                ", registry=" + registry +
                ", stackType=" + stackType +
                '}';
    }
}

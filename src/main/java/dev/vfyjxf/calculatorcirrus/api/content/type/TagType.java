package dev.vfyjxf.calculatorcirrus.api.content.type;

import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public sealed interface TagType<VALUE, STACK> permits RegistryTagType {

    static <STACK, VALUE> TagType<VALUE, STACK> fromRegistry(
            String id,
            StackContentType<STACK> stackType,
            Class<VALUE> valueClass,
            Registry<VALUE> registry,
            Function<STACK, VALUE> valueGetter,
            StackMaker<STACK, VALUE> stackMaker
    ) {
        return fromRegistry(ResourceLocation.parse(id), valueClass, stackType, registry, valueGetter, stackMaker);
    }

    static <STACK, VALUE> TagType<VALUE, STACK> fromRegistry(
            ResourceLocation id,
            Class<VALUE> valueClass, StackContentType<STACK> stackType,
            Registry<VALUE> registry,
            Function<STACK, VALUE> valueGetter,
            StackMaker<STACK, VALUE> stackMaker
    ) {
        return new RegistryTagType<>(id, valueClass, stackType, registry, valueGetter, stackMaker);
    }

    ResourceLocation typeId();

    Class<VALUE> baseType();

    StackContentType<STACK> stackType();

    Stream<Pair<TagKey<VALUE>, Named<VALUE>>> getTags();

    MutableMap<TagKey<VALUE>, Named<VALUE>> getTagMap();

    Stream<TagKey<VALUE>> getTagKeys();

    Stream<Named<VALUE>> getTagValues();

    default MutableList<TagKey<VALUE>> tagKeys() {
        return getTagKeys().collect(Collectors2.toList());
    }

    boolean hasTag(ResourceLocation tagKey);

    boolean hasTag(TagKey<VALUE> tagKey);

    default @Nullable Named<VALUE> getTag(ResourceLocation tagKey) {
        return getTagKeys()
                .filter(it -> it.location().equals(tagKey))
                .map(this::getTag)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null);
    }

    Optional<Named<VALUE>> getTag(TagKey<VALUE> tagKey);

    RichIterable<Holder<VALUE>> getTagOrEmpty(TagKey<VALUE> key);

    default MutableList<? extends VALUE> getValues(TagKey<VALUE> tagKey) {
        return getTagOrEmpty(tagKey)
                .collect(Holder::value)
                .toList();
    }

    default MutableList<? extends ContentStack<STACK>> getAsStacks(TagKey<VALUE> tagKey) {
        return getTagOrEmpty(tagKey)
                .collect(holder -> {
                    STACK stack = makeStack(holder.value());
                    return ContentStack.create(stackType(), stack);
                })
                .toList();
    }

    VALUE getValue(STACK stack);

    default STACK makeStack(VALUE value) {
        return makeStack(value, 1, DataComponentPatch.EMPTY);
    }

    default STACK makeStack(VALUE value, long amount) {
        return makeStack(value, amount, DataComponentPatch.EMPTY);
    }

    STACK makeStack(VALUE value, long amount, DataComponentPatch componentPatch);

    @Nullable
    default TagKey<VALUE> getTag(Collection<VALUE> collection) {
        return getTag((RichIterable<VALUE>) new CollectionAdapter<>(collection));
    }

    @Nullable
    TagKey<VALUE> getTag(RichIterable<VALUE> richIterable);

    @Nullable
    default TagKey<VALUE> getTagFrom(RichIterable<? extends ContentStack<STACK>> stacks) {
        return getTag(stacks.collect(c -> getValue(c.value())));
    }

    @SuppressWarnings("unchecked")
    default <S, V> TagType<V, S> cast() {
        return (TagType<V, S>) this;
    }

    @FunctionalInterface
    interface StackMaker<STACK, VALUE> {
        STACK make(VALUE value, long amount, DataComponentPatch componentPatch);
    }

}

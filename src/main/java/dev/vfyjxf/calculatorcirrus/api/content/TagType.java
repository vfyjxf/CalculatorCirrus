package dev.vfyjxf.calculatorcirrus.api.content;

import com.mojang.datafixers.util.Pair;
import dev.vfyjxf.calculatorcirrus.content.types.BasicTagType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public interface TagType<STACK, VALUE> {

    static <STACK, VALUE> TagType<STACK, VALUE> fromRegistry(
            String id,
            ContentType<STACK> stackType,
            Class<VALUE> valueClass,
            StackMaker<STACK, VALUE> stackMaker,
            Function<STACK, VALUE> valueGetter,
            Registry<VALUE> registry
    ) {
        return fromRegistry(ResourceLocation.parse(id), stackType, valueClass, stackMaker, valueGetter, registry);
    }

    static <STACK, VALUE> TagType<STACK, VALUE> fromRegistry(
            ResourceLocation id,
            ContentType<STACK> stackType,
            Class<VALUE> valueClass,
            StackMaker<STACK, VALUE> stackMaker,
            Function<STACK, VALUE> valueGetter,
            Registry<VALUE> registry
    ) {
        return new BasicTagType<>(id, stackType, valueClass, registry) {

            @Override
            public VALUE getValue(STACK stack) {
                return valueGetter.apply(stack);
            }

            @Override
            public STACK makeStack(VALUE value, long amount) {
                return stackMaker.make(value, amount);
            }
        };
    }

    ResourceLocation getId();

    Class<VALUE> getValueType();

    ContentType<STACK> getStackType();

    Stream<Pair<TagKey<VALUE>, Named<VALUE>>> getTags();

    Map<TagKey<VALUE>, Named<VALUE>> getTagMap();

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

    default MutableList<? extends ContentStack<STACK>> getValues(TagKey<VALUE> tagKey) {
        return getTagOrEmpty(tagKey)
                .collect(holder -> {
                    STACK stack = makeStack(holder.value());
                    return ContentStack.create(getStackType(), stack);
                })
                .toList();
    }

    VALUE getValue(STACK stack);

    default STACK makeStack(VALUE value) {
        return makeStack(value, 1);
    }

    STACK makeStack(VALUE value, long amount);

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
    default <S, V> TagType<S, V> cast() {
        return (TagType<S, V>) this;
    }

    @FunctionalInterface
    interface StackMaker<STACK, VALUE> {
        STACK make(VALUE value, long amount);
    }

}

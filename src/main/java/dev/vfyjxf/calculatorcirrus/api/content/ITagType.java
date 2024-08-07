package dev.vfyjxf.calculatorcirrus.api.content;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

public interface ITagType<STACK, VALUE> {

    ResourceLocation getId();

    IContentType<STACK> getStackType();


    Stream<Pair<TagKey<VALUE>, Named<VALUE>>> getTags();

    Map<TagKey<VALUE>, Named<VALUE>> getTagMap();

    Stream<TagKey<VALUE>> getTagKeys();

    Stream<Named<VALUE>> getTagValues();

    default Iterable<TagKey<VALUE>> tagKeys() {
        return getTagKeys().collect(Collectors2.toList());
    }

    boolean hasTag(ResourceLocation tagKey);

    boolean hasTag(TagKey<VALUE> tagKey);

    @Nullable
    default Named<VALUE> getTag(ResourceLocation tagKey) {
        return getTagKeys()
                .filter(it -> it.location().equals(tagKey))
                .map(this::getTag)
                .findFirst()
                .orElse(null);
    }

    Named<VALUE> getTag(TagKey<VALUE> tagKey);

    VALUE getValue(STACK stack);

    STACK makeStack(VALUE value);

    STACK makeStackWithAmount(VALUE value, int amount);

    Stream<? extends TagKey<VALUE>> getTags(Collection<STACK> collection);

    @SuppressWarnings("unchecked")
    default <S, V> ITagType<S, V> cast() {
        return (ITagType<S, V>) this;
    }

}

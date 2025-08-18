package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;

import java.util.Optional;
import java.util.stream.Stream;

public interface TagSupport<T> {


    ImmutableSet<TagKey<T>> allKeys();

    Optional<HolderSet.Named<T>> getTag(TagKey<T> key);

    Stream<Holder<T>> getTagValues(TagKey<T> tagKey);

    HolderSet.Named<T> getOrCreateTag(TagKey<T> key);


}

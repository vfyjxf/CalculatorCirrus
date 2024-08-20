package dev.vfyjxf.calculatorcirrus.helper;

import org.eclipse.collections.api.multimap.ImmutableMultimap;
import org.eclipse.collections.impl.multimap.list.FastListMultimap;

public final class CollectionHelper {

    @SuppressWarnings("rawtypes")
    private static final ImmutableMultimap EMPTY_MULTIMAP = FastListMultimap.newMultimap().toImmutable();

    @SuppressWarnings("unchecked")
    public static <K, V> ImmutableMultimap<K, V> emptyMultimap() {
        return EMPTY_MULTIMAP;
    }

    private CollectionHelper() {
    }
}

package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.planning.core.lang.Fnv1a64;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CandidateTable {

    private final ResourceDictionary dict;
    private final int[][] lists;
    private final List<ResourceKey>[] cachedViews;

    @SuppressWarnings("unchecked")
    CandidateTable(ResourceDictionary dict, int[][] lists) {
        this.dict = dict;
        this.lists = lists.clone();
        this.cachedViews = (List<ResourceKey>[]) new List<?>[lists.length];
    }

    public int count() {
        return lists.length;
    }

    @SuppressWarnings("PMD.MethodReturnsInternalArray") // lists are frozen at construction and only read
    public int[] membersOf(int listId) {
        return lists[listId];
    }

    public int sizeOf(int listId) {
        return lists[listId].length;
    }

    public List<ResourceKey> viewOf(int listId) {
        List<ResourceKey> cached = cachedViews[listId];
        if (cached != null) {
            return cached;
        }
        int[] ids = lists[listId];
        ResourceKey[] arr = new ResourceKey[ids.length];
        for (int i = 0; i < ids.length; i++) {
            arr[i] = dict.keyOf(ids[i]);
        }
        List<ResourceKey> view = List.of(arr);
        cachedViews[listId] = view;
        return view;
    }

    static final class Builder {

        private final Map<IntArrayKey, Integer> table = new HashMap<>(4096);
        private final List<int[]> lists = new ArrayList<>(4096);
        private long[] listHashes = new long[4096];

        int internSortedUnique(int[] sortedUnique, ResourceDictionary dict) {
            IntArrayKey key = new IntArrayKey(sortedUnique);
            Integer existing = table.get(key);
            if (existing != null) {
                return existing;
            }
            int id = lists.size();
            lists.add(sortedUnique);
            table.put(key, id);
            if (id == listHashes.length) {
                listHashes = Arrays.copyOf(listHashes, listHashes.length * 2);
            }
            long h = Fnv1a64.initial();
            for (int i = 0; i < sortedUnique.length; i++) {
                if (i > 0) h = Fnv1a64.mixByte(h, ',');
                h = Fnv1a64.mixString(h, dict.shortStringOf(sortedUnique[i]));
            }
            listHashes[id] = h;
            return id;
        }

        long hash64Of(int listId) {
            return listHashes[listId];
        }

        int[] membersOf(int listId) {
            return lists.get(listId);
        }

        CandidateTable freeze(ResourceDictionary dict) {
            return new CandidateTable(dict, lists.toArray(new int[0][]));
        }
    }

    private static final class IntArrayKey {

        private final int[] data;
        private final int hash;

        @SuppressWarnings("PMD.ArrayIsStoredDirectly") // the key shares the interned, frozen list
        IntArrayKey(int[] data) {
            this.data = data;
            this.hash = Arrays.hashCode(data);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof IntArrayKey other && hash == other.hash && Arrays.equals(data, other.data);
        }
    }
}

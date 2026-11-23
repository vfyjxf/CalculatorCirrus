package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

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
        this.lists = lists;
        this.cachedViews = (List<ResourceKey>[]) new List<?>[lists.length];
    }

    public int count() {
        return lists.length;
    }

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

        int internSortedUnique(int[] sortedUnique) {
            IntArrayKey key = new IntArrayKey(sortedUnique);
            Integer existing = table.get(key);
            if (existing != null) {
                return existing;
            }
            int id = lists.size();
            lists.add(sortedUnique);
            table.put(key, id);
            return id;
        }

        CandidateTable freeze(ResourceDictionary dict) {
            return new CandidateTable(dict, lists.toArray(new int[0][]));
        }
    }

    private static final class IntArrayKey {

        private final int[] data;
        private final int hash;

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

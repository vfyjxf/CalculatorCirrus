package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ResourceDictionary {

    private final ComponentStackKey[] keys;
    private final Map<ComponentStackKey, Integer> idByKey;
    private final String[] canonicalStrings;

    private ResourceDictionary(
        ComponentStackKey[] keys,
        Map<ComponentStackKey, Integer> idByKey,
        String[] canonicalStrings
    ) {
        this.keys = keys;
        this.idByKey = idByKey;
        this.canonicalStrings = canonicalStrings;
    }

    public int size() {
        return keys.length;
    }

    public ComponentStackKey keyOf(int id) {
        return keys[id];
    }

    public String shortStringOf(int id) {
        return canonicalStrings[id];
    }

    public int idOf(ComponentStackKey key) {
        Integer id = idByKey.get(key);
        return id == null ? -1 : id;
    }

    public static @Nullable ComponentStackKey normalizeOrNull(
        @Nullable ComponentStackKey raw,
        int @Nullable [] normalizedCounter
    ) {
        return raw;
    }

    static final class Builder {

        private final Map<ComponentStackKey, Integer> tempIds = new HashMap<>(8192);
        private ComponentStackKey[] tempKeys = new ComponentStackKey[4096];
        private int count;

        int internRaw(@Nullable ComponentStackKey raw, int[] normalizedCounter) {
            if (raw == null) {
                return -1;
            }
            return intern(raw);
        }

        int intern(ComponentStackKey key) {
            Integer existing = tempIds.get(key);
            if (existing != null) {
                return existing;
            }
            int id = count++;
            if (id == tempKeys.length) {
                tempKeys = Arrays.copyOf(tempKeys, tempKeys.length * 2);
            }
            tempKeys[id] = key;
            tempIds.put(key, id);
            return id;
        }

        Built freeze() {
            ComponentStackKey[] sorted = Arrays.copyOf(tempKeys, count);
            Arrays.sort(sorted);
            Map<ComponentStackKey, Integer> finalMap = new HashMap<>((int) (count / 0.7) + 16);
            String[] canonical = new String[count];
            for (int i = 0; i < count; i++) {
                finalMap.put(sorted[i], i);
                canonical[i] = sorted[i].canonicalKey();
            }
            int[] perm = new int[count];
            for (int oldId = 0; oldId < count; oldId++) {
                perm[oldId] = Objects.requireNonNull(finalMap.get(tempKeys[oldId]));
            }
            return new Built(new ResourceDictionary(sorted, finalMap, canonical), perm);
        }
    }

    record Built(ResourceDictionary dictionary, int[] oldToCanonical) {}
}

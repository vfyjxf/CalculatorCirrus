package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ResourceDictionary {

    private final ResourceKey[] keys;
    private final Map<ResourceKey, Integer> idByKey;
    private final String[] shortStrings;

    private ResourceDictionary(ResourceKey[] keys, Map<ResourceKey, Integer> idByKey, String[] shortStrings) {
        this.keys = keys;
        this.idByKey = idByKey;
        this.shortStrings = shortStrings;
    }

    public int size() {
        return keys.length;
    }

    public ResourceKey keyOf(int id) {
        return keys[id];
    }

    public String shortStringOf(int id) {
        return shortStrings[id];
    }

    public int idOf(ResourceKey key) {
        Integer id = idByKey.get(key);
        return id == null ? -1 : id;
    }

    public static @Nullable ResourceKey normalizeOrNull(ResourceKey raw, int @Nullable [] normalizedCounter) {
        if (raw == null) {
            return null;
        }
        String resId = raw.resourceId();
        boolean changed = false;
        int pipe = resId.indexOf('|');
        if (pipe >= 0 && hasBlockstatePlusDigitsSuffix(resId, pipe)) {
            resId = resId.substring(0, pipe);
            changed = true;
        }
        int colon = resId.indexOf(':');
        if (colon < 0) {
            resId = "minecraft:" + resId;
            colon = 9;
            changed = true;
        }
        if (!isValidResourceId(resId, colon)) {
            return null;
        }
        if (changed) {
            if (normalizedCounter != null) {
                normalizedCounter[0]++;
            }
            return new ResourceKey(raw.contentTypeId(), resId, raw.canonicalVariantHash());
        }
        return raw;
    }

    private static boolean hasBlockstatePlusDigitsSuffix(String s, int pipe) {
        int len = s.length();
        if (pipe + 2 >= len || s.charAt(pipe + 1) != '+') {
            return false;
        }
        for (int i = pipe + 2; i < len; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidResourceId(String s, int colon) {
        int len = s.length();
        if (colon <= 0 || colon >= len - 1) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-')) {
                return false;
            }
        }
        for (int i = colon + 1; i < len; i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    static final class Builder {

        private final Map<ResourceKey, Integer> rawCache = new HashMap<>(8192);
        private final Map<ResourceKey, Integer> tempIds = new HashMap<>(8192);
        private ResourceKey[] tempKeys = new ResourceKey[4096];
        private int count;

        int internRaw(ResourceKey raw, int[] normalizedCounter) {
            if (raw == null) {
                return -1;
            }
            Integer cached = rawCache.get(raw);
            if (cached != null) {
                return cached;
            }
            ResourceKey norm = normalizeOrNull(raw, normalizedCounter);
            if (norm == null) {
                rawCache.put(raw, -1);
                return -1;
            }
            int id = intern(norm);
            rawCache.put(raw, id);
            return id;
        }

        int intern(ResourceKey key) {
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
            ResourceKey[] sorted = Arrays.copyOf(tempKeys, count);
            Arrays.sort(sorted);
            Map<ResourceKey, Integer> finalMap = new HashMap<>((int) (count / 0.7) + 16);
            String[] shorts = new String[count];
            for (int i = 0; i < count; i++) {
                finalMap.put(sorted[i], i);
                shorts[i] = sorted[i].shortString();
            }
            int[] perm = new int[count];
            for (int oldId = 0; oldId < count; oldId++) {
                perm[oldId] = Objects.requireNonNull(finalMap.get(tempKeys[oldId]));
            }
            return new Built(new ResourceDictionary(sorted, finalMap, shorts), perm);
        }
    }

    record Built(ResourceDictionary dictionary, int[] oldToCanonical) {}
}

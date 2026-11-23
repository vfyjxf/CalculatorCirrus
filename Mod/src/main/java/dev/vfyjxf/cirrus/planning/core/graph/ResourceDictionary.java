package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ResourceDictionary {

    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern BLOCKSTATE_SUFFIX = Pattern.compile("\\|\\+\\d+$");

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

    public static ResourceKey normalizeOrNull(ResourceKey raw, int[] normalizedCounter) {
        if (raw == null) {
            return null;
        }
        String resId = raw.resourceId();
        boolean changed = false;
        int pipe = resId.indexOf('|');
        if (pipe >= 0 && BLOCKSTATE_SUFFIX.matcher(resId).find()) {
            resId = resId.substring(0, pipe);
            changed = true;
        }
        if (resId.indexOf(':') < 0) {
            resId = "minecraft:" + resId;
            changed = true;
        }
        if (!VALID_ID.matcher(resId).matches()) {
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

    static final class Builder {

        private final Map<ResourceKey, Integer> tempIds = new HashMap<>(4096);
        private ResourceKey[] tempKeys = new ResourceKey[4096];
        private int count;

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
                perm[oldId] = finalMap.get(tempKeys[oldId]);
            }
            return new Built(new ResourceDictionary(sorted, finalMap, shorts), perm);
        }
    }

    record Built(ResourceDictionary dictionary, int[] oldToCanonical) {
    }
}

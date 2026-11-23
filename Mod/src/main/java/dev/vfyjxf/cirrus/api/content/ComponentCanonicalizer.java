package dev.vfyjxf.cirrus.api.content;

import com.google.common.collect.MapMaker;
import net.minecraft.core.component.DataComponentMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Canonicalizes {@link DataComponentMap}s so that component equality — the expensive part of
 * {@link MatchStrategy#identityAndComponent} — can be resolved by reference comparison.
 *
 * <p>Vanilla {@code PatchedDataComponentMap.equals} walks the prototype and patch maps entry by
 * entry on every call (recursing into nested stacks for container components), and its
 * {@code hashCode} is even costlier. This pool amortizes that cost: each map instance pays one
 * full hash/equality pass the first time it is canonicalized, after which all comparisons of
 * logically equal maps collapse to a single {@code ==} on the canonical representative.
 *
 * <p>Structure:
 * <ul>
 *     <li><b>Identity cache</b> — weak-keyed, so lookups never invoke the map's own
 *         {@code hashCode}/{@code equals} and entries evaporate with their stacks.</li>
 *     <li><b>Equality pool</b> — dedupes equal maps to the first-seen representative
 *         ({@code computeIfAbsent} performs the vanilla hash/equality pass once per distinct value).</li>
 * </ul>
 *
 * <p><b>Contract:</b> canonicalized maps must not be mutated afterwards — a mutated map would keep
 * its stale canonical representative. All {@code ContentStack} values in the recipe calculation and
 * planning pipeline are freshly constructed, read-only stacks, satisfying this contract.
 *
 * <p>Cleared on datapack/cache lifecycle by the client runtime; growth is additionally bounded by a
 * soft cap that simply drops the pool (representatives are lazily recomputed, and comparisons stay
 * correct through the {@code equals} fallback).
 */
@ApiStatus.Internal
public final class ComponentCanonicalizer {

    private static final int POOL_SOFT_CAP = 1 << 16;

    private static final ConcurrentMap<DataComponentMap, DataComponentMap> IDENTITY_CACHE =
            new MapMaker().weakKeys().weakValues().concurrencyLevel(4).makeMap();

    private static final ConcurrentHashMap<PoolKey, DataComponentMap> POOL = new ConcurrentHashMap<>(1024);

    private static volatile long generation;

    private ComponentCanonicalizer() {
    }

    /**
     * @param map the components to canonicalize
     * @return a representative instance shared by all maps equal to {@code map}, or {@code null}
     * if {@code map} is {@code null}. Idempotent: equal maps always resolve to the same reference.
     */
    public static @Nullable DataComponentMap canonical(@Nullable DataComponentMap map) {
        if (map == null) return null;
        DataComponentMap cached = IDENTITY_CACHE.get(map);
        if (cached != null) return cached;
        PoolKey key = new PoolKey(map, map.hashCode());
        DataComponentMap canonical = POOL.computeIfAbsent(key, poolKey -> poolKey.map);
        IDENTITY_CACHE.put(map, canonical);
        if (POOL.size() > POOL_SOFT_CAP) {
            clear();
        }
        return canonical;
    }

    /**
     * @return the current pool generation. Within one generation the pool is append-only, so two
     * canonical representatives resolved in the same generation being <b>different instances</b>
     * proves the underlying maps unequal — callers may use this to shortcut negative comparisons.
     * {@link #clear()} bumps the generation, invalidating that proof across generations.
     */
    public static long generation() {
        return generation;
    }

    public static void clear() {
        IDENTITY_CACHE.clear();
        POOL.clear();
        generation++;
    }

    private record PoolKey(DataComponentMap map, int hash) {

        @Override
        public boolean equals(Object o) {
            return o instanceof PoolKey key && hash == key.hash && map.equals(key.map);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}

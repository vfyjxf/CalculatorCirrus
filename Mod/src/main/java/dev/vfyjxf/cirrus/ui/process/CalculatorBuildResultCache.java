package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder.BuildResult;
import dev.vfyjxf.cirrus.ui.process.CalculatorProcessGraphBuilder.RouteMode;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session-scoped LRU cache for calculator build results.
 *
 * <p>Reopening the calculator screen (or re-picking the same target in JEI)
 * rebuilds the {@code OmniCalculatorView} and would otherwise rerun the full
 * build pipeline for a target whose inputs have not changed. Results are keyed
 * by target recipe, craft count, variant preferences, route mode and a settings
 * fingerprint, so any configuration change simply misses the cache.
 *
 * <p>Only stock-free calculations are cached by callers (live stock is
 * volatile); this class is a plain keyed store. Cleared on client unload via
 * {@code CirrusClientRuntime.beforeUnload}.
 */
public final class CalculatorBuildResultCache {

    private static final int maxEntries = 4;
    private static final Object lock = new Object();
    // Access-ordered: eviction has to drop the least recently *used* entry, not
    // the oldest inserted one. With only four slots, a player who keeps coming
    // back to one expensive target while browsing others would otherwise see it
    // evicted by the browsing and pay the full route again.
    private static final LinkedHashMap<Key, BuildResult> results = new LinkedHashMap<>(16, 0.75f, true);

    private CalculatorBuildResultCache() {
    }

    public record Key(
        RecipeEntry<?> recipe,
        int times,
        Map<String, String> preferredVariants,
        RouteMode routeMode,
        long settingsFingerprint
    ) {
        public Key {
            preferredVariants = preferredVariants == null ? Map.of() : Map.copyOf(preferredVariants);
            routeMode = routeMode == null ? RouteMode.calculator : routeMode;
        }
    }

    public static @Nullable BuildResult get(Key key) {
        synchronized (lock) {
            return results.get(key);
        }
    }

    public static void put(Key key, BuildResult result) {
        synchronized (lock) {
            results.put(key, result);
            while (results.size() > maxEntries) {
                var eldest = results.entrySet().iterator().next();
                results.remove(eldest.getKey());
            }
        }
    }

    public static void clear() {
        synchronized (lock) {
            results.clear();
        }
    }

    /** Current entry count; exposed for tests/diagnostics. */
    public static int size() {
        synchronized (lock) {
            return results.size();
        }
    }
}

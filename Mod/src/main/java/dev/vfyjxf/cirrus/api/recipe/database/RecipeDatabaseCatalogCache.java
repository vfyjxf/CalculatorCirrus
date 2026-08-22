package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RecipeDatabaseCatalogCache {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Database");
    private static final Duration defaultTtl = Duration.ofMinutes(5);
    private static volatile long ttlNanos = defaultTtl.toNanos();
    private static CacheEntry cached;

    private RecipeDatabaseCatalogCache() {
    }

    public static RecipeDatabaseIndex getOrBuild(
        List<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        RecipeDatabaseIndex.BuildProgress progress
    ) {
        return getOrBuild(systems, systemTags, progress, null);
    }

    public static RecipeDatabaseIndex getOrBuild(
        List<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        RecipeDatabaseIndex.BuildProgress progress,
        RecipeDatabaseFailureRegistry failureRegistry
    ) {
        long now = System.nanoTime();
        synchronized (RecipeDatabaseCatalogCache.class) {
            // the key is computed under the lock: the registry version only moves
            // while a catalog build (which holds this lock) runs, so a caller that
            // waited on a concurrent build sees the post-build version and hits
            // the freshly stored entry instead of building a second time
            CatalogKey key = CatalogKey.create(
                systems,
                systemTags,
                failureRegistry == null ? -1L : failureRegistry.version()
            );
            if (key.sources().isEmpty()) {
                return RecipeDatabaseIndex.ofRecords(List.of());
            }
            CacheEntry current = cached;
            if (current != null && current.key().equals(key) && now - current.createdNanos() <= ttlNanos) {
                logger.info(
                    "Reused recipe database catalog index: records={} systems={} ageMs={} ttlMs={}",
                    current.index().records().size(),
                    key.sources().size(),
                    nanosToMillis(now - current.createdNanos()),
                    nanosToMillis(ttlNanos)
                );
                return current.index();
            }
            RecipeDatabaseIndex index = buildCatalog(key.sources(), systems, systemTags, progress, failureRegistry);
            // the build records new failures/unsupported into the registry, which
            // bumps its version; store the entry under the post-build key so the
            // next lookup actually hits instead of rebuilding the whole catalog
            CatalogKey storedKey = failureRegistry == null
                ? key
                : CatalogKey.create(systems, systemTags, failureRegistry.version());
            cached = new CacheEntry(storedKey, index, System.nanoTime());
            return index;
        }
    }

    public static void invalidate() {
        synchronized (RecipeDatabaseCatalogCache.class) {
            cached = null;
        }
    }

    public static void configureTtlForTests(Duration ttl) {
        ttlNanos = Math.max(0L, Objects.requireNonNull(ttl, "ttl").toNanos());
        invalidate();
    }

    public static void resetForTests() {
        ttlNanos = defaultTtl.toNanos();
        invalidate();
    }

    private static RecipeDatabaseIndex buildCatalog(
        List<Namespace> sources,
        List<? extends RecipeSystem<?>> systems,
        Map<Namespace, List<Namespace>> systemTags,
        RecipeDatabaseIndex.BuildProgress progress,
        RecipeDatabaseFailureRegistry failureRegistry
    ) {
        DatabaseDefinition catalog = new DatabaseDefinition(
            "system:catalog",
            "System Catalog",
            DatabaseKind.system,
            true,
            0,
            sources
        );
        return RecipeDatabaseIndex.build(
            List.of(catalog),
            Lists.immutable.ofAll(systems),
            systemTags,
            Map.<String, List<RecipeEntry<?>>>of(),
            progress == null ? RecipeDatabaseIndex.BuildProgress.none() : progress,
            failureRegistry
        );
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private record CacheEntry(CatalogKey key, RecipeDatabaseIndex index, long createdNanos) {
    }

    private record CatalogKey(List<SystemFingerprint> systems, List<TagFingerprint> tags, List<Namespace> sources, long failureVersion) {
        private static CatalogKey create(
            List<? extends RecipeSystem<?>> systems,
            Map<Namespace, List<Namespace>> systemTags,
            long failureVersion
        ) {
            ArrayList<SystemFingerprint> systemFingerprints = new ArrayList<>();
            ArrayList<Namespace> sources = new ArrayList<>();
            if (systems != null) {
                for (RecipeSystem<?> system : systems) {
                    if (system == null) {
                        continue;
                    }
                    Namespace source = system.systemId();
                    systemFingerprints.add(new SystemFingerprint(
                        source,
                        System.identityHashCode(system)
                    ));
                    if (!sources.contains(source)) {
                        sources.add(source);
                    }
                }
            }
            systemFingerprints.sort(Comparator
                .comparing((SystemFingerprint fingerprint) -> fingerprint.source().toString())
                .thenComparingInt(SystemFingerprint::identity));
            sources.sort(Comparator.naturalOrder());

            ArrayList<TagFingerprint> tagFingerprints = new ArrayList<>();
            if (systemTags != null && !systemTags.isEmpty()) {
                LinkedHashMap<Namespace, List<Namespace>> normalized = new LinkedHashMap<>();
                for (Map.Entry<Namespace, List<Namespace>> entry : systemTags.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                        continue;
                    }
                    ArrayList<Namespace> tags = new ArrayList<>();
                    for (Namespace tag : entry.getValue()) {
                        if (tag != null && !tags.contains(tag)) {
                            tags.add(tag);
                        }
                    }
                    tags.sort(Comparator.naturalOrder());
                    if (!tags.isEmpty()) {
                        normalized.put(entry.getKey(), List.copyOf(tags));
                    }
                }
                normalized.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> tagFingerprints.add(new TagFingerprint(entry.getKey(), entry.getValue())));
            }
            return new CatalogKey(
                List.copyOf(systemFingerprints),
                List.copyOf(tagFingerprints),
                List.copyOf(sources),
                failureVersion
            );
        }
    }

    private record SystemFingerprint(Namespace source, int identity) {
    }

    private record TagFingerprint(Namespace source, List<Namespace> tags) {
    }
}

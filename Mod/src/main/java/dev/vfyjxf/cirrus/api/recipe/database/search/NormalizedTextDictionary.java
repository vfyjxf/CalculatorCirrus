package dev.vfyjxf.cirrus.api.recipe.database.search;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Build-scope interning dictionary for normalized document text. Every
 * {@link RecipeDatabaseSearchDocument} stores its normalized channel as ids
 * into one dictionary; the catalog build shares a single instance across all
 * parallel record chunks so equal normalized strings (item ids, tag paths,
 * mod namespaces) collapse onto one canonical instance instead of one
 * {@code normalize()} result per document occurrence.
 *
 * <p>Lifecycle: producers call {@link #intern}/{@code canonicalize} freely
 * and concurrently while documents are built; once the owning
 * {@link RecipeDatabaseSearchEngine} finishes collecting documents it calls
 * {@link #seal()}, freezing id-to-string reads into a flat array and dropping
 * the hashing tables. {@link #value(int)} is an array load afterwards, so the
 * search hot path pays no boxing or hashing.
 */
public final class NormalizedTextDictionary {

    private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>(4096);
    private final ConcurrentHashMap<Integer, String> pending = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private volatile String @Nullable [] frozen;

    /** Returns the id of {@code normalized}, registering it on first sight. */
    public int intern(String normalized) {
        Integer existing = ids.get(normalized);
        if (existing != null) {
            return existing;
        }
        // reserve the value before publishing the id so a racing reader of the
        // same id can always resolve its string during the unsealed window
        Integer reserved = nextId.getAndIncrement();
        pending.put(reserved, normalized);
        Integer published = ids.putIfAbsent(normalized, reserved);
        if (published != null) {
            pending.remove(reserved);
            return published;
        }
        return reserved;
    }

    /**
     * Interns {@code normalized} and returns the canonical spelling: the
     * instance first registered for this key. Unknown inputs come back
     * themselves (they were just registered).
     */
    public String canonicalize(String normalized) {
        return value(intern(normalized));
    }

    /** Resolves a normalized-text id returned by {@link #intern}. */
    public String value(int id) {
        String[] frozenValues = frozen;
        if (frozenValues != null) {
            return frozenValues[id];
        }
        String value = pending.get(id);
        return value == null ? "" : value;
    }

    /**
     * Switches {@link #value} to flat-array reads and releases the build
     * maps. Callers must guarantee no producer threads remain; a second call
     * is a no-op.
     */
    public synchronized void seal() {
        if (frozen != null) {
            return;
        }
        String[] sealed = new String[nextId.get()];
        for (Map.Entry<Integer, String> entry : pending.entrySet()) {
            sealed[entry.getKey()] = entry.getValue();
        }
        frozen = sealed;
        pending.clear();
    }
}

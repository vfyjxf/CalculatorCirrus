package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe client execution queue managing multiple queued crafting plans with independent checkpoints and switching.
 */
public final class ExecutionQueueStore {

    public static final int maxEntries = 16;

    public enum Status {
        queued, active, paused, completed
    }

    public record Entry(
        long id,
        RecipeEntry<?> recipe,
        int times,
        @Nullable ProductionPlan pinnedPlan,
        Component displayName,
        int estimatedStages,
        Status status
    ) {
        public Entry withTimes(int nextTimes) {
            return new Entry(id, recipe, Math.max(1, nextTimes), pinnedPlan, displayName, estimatedStages, status);
        }

        public Entry withStatus(Status nextStatus, int nextStages) {
            return new Entry(
                id,
                recipe,
                times,
                pinnedPlan,
                displayName,
                nextStages > 0 ? nextStages : estimatedStages,
                nextStatus
            );
        }
    }

    private static final Object lock = new Object();
    private static final AtomicLong idGenerator = new AtomicLong(1L);
    private static final List<Entry> entries = new ArrayList<>();
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static long activeEntryId = -1L;

    private ExecutionQueueStore() {}

    public static Entry enqueue(
        RecipeEntry<?> recipe,
        int times,
        @Nullable ProductionPlan pinnedPlan,
        int estimatedStages,
        boolean activate
    ) {
        Objects.requireNonNull(recipe, "recipe");
        int normalizedTimes = Math.max(1, times);
        int normalizedStages = Math.max(1, estimatedStages);
        Entry created;
        boolean shouldSyncTarget = false;
        synchronized (lock) {
            // Merge into tail entry if the exact same plan & recipe is re-sent consecutively.
            if (!entries.isEmpty()) {
                Entry tail = entries.get(entries.size() - 1);
                if (tail.recipe().equals(recipe)
                        && Objects.equals(tail.pinnedPlan(), pinnedPlan)
                        && tail.status() != Status.completed) {
                    Entry updated = new Entry(
                        tail.id(),
                        recipe,
                        normalizedTimes,
                        pinnedPlan,
                        ExecutionTargetRecipeState.displayName(recipe),
                        normalizedStages,
                        activate ? Status.active : tail.status()
                    );
                    entries.set(entries.size() - 1, updated);
                    if (activate || activeEntryId < 0L) {
                        markActiveLocked(updated.id());
                        shouldSyncTarget = true;
                    }
                    created = updated;
                } else {
                    created = appendLocked(recipe, normalizedTimes, pinnedPlan, normalizedStages, activate);
                    shouldSyncTarget = activate || activeEntryId == created.id();
                }
            } else {
                created = appendLocked(recipe, normalizedTimes, pinnedPlan, normalizedStages, true);
                shouldSyncTarget = true;
            }
        }
        if (shouldSyncTarget) {
            ExecutionTargetRecipeState.set(created.recipe(), created.times(), created.pinnedPlan());
        }
        notifyListeners();
        return created;
    }

    private static Entry appendLocked(
        RecipeEntry<?> recipe,
        int times,
        @Nullable ProductionPlan pinnedPlan,
        int estimatedStages,
        boolean activate
    ) {
        while (entries.size() >= maxEntries) {
            int dropIdx = findOldestInactiveIndexLocked();
            entries.remove(dropIdx >= 0 ? dropIdx : 0);
        }
        long id = idGenerator.getAndIncrement();
        boolean makeActive = activate || activeEntryId < 0L;
        Entry entry = new Entry(
            id,
            recipe,
            times,
            pinnedPlan,
            ExecutionTargetRecipeState.displayName(recipe),
            estimatedStages,
            makeActive ? Status.active : Status.queued
        );
        entries.add(entry);
        if (makeActive) {
            markActiveLocked(id);
        }
        return entry;
    }

    private static int findOldestInactiveIndexLocked() {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id() != activeEntryId && entries.get(i).status() == Status.completed) {
                return i;
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id() != activeEntryId) {
                return i;
            }
        }
        return 0;
    }

    private static void markActiveLocked(long id) {
        activeEntryId = id;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.id() == id) {
                if (e.status() != Status.active) {
                    entries.set(i, e.withStatus(Status.active, e.estimatedStages()));
                }
            } else if (e.status() == Status.active) {
                entries.set(i, e.withStatus(Status.paused, e.estimatedStages()));
            }
        }
    }

    public static void syncActiveTimesAndPlan(
        RecipeEntry<?> recipe,
        int nextTimes,
        @Nullable ProductionPlan nextPinnedPlan
    ) {
        int normalizedTimes = Math.max(1, nextTimes);
        boolean changed = false;
        synchronized (lock) {
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.id() == activeEntryId && e.recipe().equals(recipe)) {
                    entries.set(
                        i,
                        new Entry(
                            e.id(),
                            recipe,
                            normalizedTimes,
                            nextPinnedPlan,
                            e.displayName(),
                            e.estimatedStages(),
                            e.status()
                        )
                    );
                    changed = true;
                    break;
                }
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    public static void syncFromSelection(ExecutionTargetRecipeState.@Nullable Selection selection) {
        if (selection == null) {
            return;
        }
        boolean changed = false;
        synchronized (lock) {
            for (Entry e : entries) {
                if (e.id() == activeEntryId
                        && e.recipe().equals(selection.recipe())
                        && Objects.equals(e.pinnedPlan(), selection.pinnedPlan())) {
                    if (e.times() != selection.times()) {
                        int idx = entries.indexOf(e);
                        entries.set(idx, e.withTimes(selection.times()));
                        changed = true;
                    }
                    break;
                }
            }
            if (activeEntryId < 0L || entries.stream().noneMatch(e -> e.id() == activeEntryId)) {
                appendLocked(selection.recipe(), selection.times(), selection.pinnedPlan(), 1, true);
                changed = true;
            } else {
                Entry active = activeEntryLocked();
                if (active == null
                        || !active.recipe().equals(selection.recipe())
                        || !Objects.equals(active.pinnedPlan(), selection.pinnedPlan())) {
                    appendLocked(selection.recipe(), selection.times(), selection.pinnedPlan(), 1, true);
                    changed = true;
                }
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    public static List<Entry> entries() {
        synchronized (lock) {
            return List.copyOf(entries);
        }
    }

    public static int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public static int activeIndex() {
        synchronized (lock) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id() == activeEntryId) {
                    return i;
                }
            }
            return entries.isEmpty() ? -1 : 0;
        }
    }

    public static @Nullable Entry activeEntry() {
        synchronized (lock) {
            return activeEntryLocked();
        }
    }

    private static @Nullable Entry activeEntryLocked() {
        for (Entry e : entries) {
            if (e.id() == activeEntryId) {
                return e;
            }
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    public static @Nullable Entry selectIndex(int index) {
        Entry selected;
        synchronized (lock) {
            if (index < 0 || index >= entries.size()) {
                return null;
            }
            selected = entries.get(index);
            markActiveLocked(selected.id());
            selected = entries.get(index);
        }
        ExecutionTargetRecipeState.set(selected.recipe(), selected.times(), selected.pinnedPlan());
        notifyListeners();
        return selected;
    }

    public static @Nullable Entry selectNext() {
        int nextIdx;
        synchronized (lock) {
            if (entries.size() <= 1) {
                return activeEntryLocked();
            }
            int cur = activeIndex();
            nextIdx = (cur + 1) % entries.size();
        }
        return selectIndex(nextIdx);
    }

    public static @Nullable Entry selectPrev() {
        int prevIdx;
        synchronized (lock) {
            if (entries.size() <= 1) {
                return activeEntryLocked();
            }
            int cur = activeIndex();
            prevIdx = (cur - 1 + entries.size()) % entries.size();
        }
        return selectIndex(prevIdx);
    }

    public static void updateActiveStatus(Status status, int stageCount) {
        boolean changed = false;
        synchronized (lock) {
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.id() == activeEntryId) {
                    int nextStages = stageCount > 0 ? stageCount : e.estimatedStages();
                    if (e.status() != status || e.estimatedStages() != nextStages) {
                        entries.set(i, e.withStatus(status, nextStages));
                        changed = true;
                    }
                    break;
                }
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    public static boolean remove(long entryId) {
        Entry nextToActivate = null;
        boolean clearTarget = false;
        boolean removed = false;
        synchronized (lock) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id() == entryId) {
                    entries.remove(i);
                    removed = true;
                    if (activeEntryId == entryId) {
                        if (entries.isEmpty()) {
                            activeEntryId = -1L;
                            clearTarget = true;
                        } else {
                            int nextIdx = Math.min(i, entries.size() - 1);
                            markActiveLocked(entries.get(nextIdx).id());
                            nextToActivate = entries.get(nextIdx);
                        }
                    }
                    break;
                }
            }
        }
        if (!removed) {
            return false;
        }
        if (clearTarget) {
            ExecutionTargetRecipeState.clear();
        } else if (nextToActivate != null) {
            ExecutionTargetRecipeState
                    .set(nextToActivate.recipe(), nextToActivate.times(), nextToActivate.pinnedPlan());
        }
        notifyListeners();
        return true;
    }

    public static void clearCompleted() {
        boolean changed;
        synchronized (lock) {
            changed = entries.removeIf(e -> e.status() == Status.completed && e.id() != activeEntryId);
        }
        if (changed) {
            notifyListeners();
        }
    }

    public static void clearAll() {
        synchronized (lock) {
            entries.clear();
            activeEntryId = -1L;
        }
        notifyListeners();
    }

    public static void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}

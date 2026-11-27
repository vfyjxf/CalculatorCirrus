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
 * Bounded recent-path history for the Execution Overlay, preserving full multi-stage plans and optional resume progress.
 */
public final class ExecutionHistoryStore {

    public static final int maxEntries = 12;

    public record Entry(
        long id,
        RecipeEntry<?> recipe,
        int times,
        @Nullable ProductionPlan pinnedPlan,
        Component displayName,
        int stageCount,
        int savedStepIndex,
        int totalSteps,
        boolean completed,
        long recordedAtEpochMs
    ) {
        public boolean resumable() {
            return !completed && savedStepIndex > 0 && totalSteps > 0 && savedStepIndex < totalSteps;
        }
    }

    private static final Object lock = new Object();
    private static final AtomicLong idGenerator = new AtomicLong(1L);
    private static final List<Entry> entries = new ArrayList<>();
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private ExecutionHistoryStore() {}

    public static @Nullable Entry record(
        ExecutionTargetRecipeState.@Nullable Selection selection,
        int stageCount,
        int savedStepIndex,
        int totalSteps,
        boolean completed
    ) {
        if (selection == null || selection.recipe() == null) {
            return null;
        }
        int normalizedTimes = Math.max(1, selection.times());
        int normalizedStages = Math.max(1, stageCount);
        int normalizedTotal = Math.max(0, totalSteps);
        int normalizedStep = completed || normalizedTotal <= 0
                ? 0
                : Math.max(0, Math.min(savedStepIndex, normalizedTotal - 1));
        Component name = ExecutionTargetRecipeState.displayName(selection.recipe());
        long now = System.currentTimeMillis();
        Entry created;
        synchronized (lock) {
            entries.removeIf(
                existing -> existing.recipe().equals(selection.recipe())
                        && existing.times() == normalizedTimes
                        && Objects.equals(existing.pinnedPlan(), selection.pinnedPlan())
            );
            created = new Entry(
                idGenerator.getAndIncrement(),
                selection.recipe(),
                normalizedTimes,
                selection.pinnedPlan(),
                name,
                normalizedStages,
                normalizedStep,
                normalizedTotal,
                completed,
                now
            );
            entries.add(0, created);
            while (entries.size() > maxEntries) {
                entries.remove(entries.size() - 1);
            }
        }
        notifyListeners();
        return created;
    }

    public static @Nullable Entry restore(long entryId) {
        Entry found = null;
        synchronized (lock) {
            for (Entry entry : entries) {
                if (entry.id() == entryId) {
                    found = entry;
                    break;
                }
            }
        }
        if (found == null) {
            return null;
        }
        ExecutionQueueStore.enqueue(found.recipe(), found.times(), found.pinnedPlan(), found.stageCount(), true);
        ExecutionTargetRecipeState.Selection active = ExecutionTargetRecipeState.current();
        if (active != null) {
            if (found.resumable()) {
                int approxStage = found.totalSteps() > 0
                        ? Math.min(
                            Math.max(0, found.stageCount() - 1),
                            (found.savedStepIndex() * Math.max(1, found.stageCount())) / found.totalSteps()
                        )
                        : 0;
                ExecutionCheckpointStore.saveCheckpoint(
                    active,
                    found.savedStepIndex(),
                    found.totalSteps(),
                    approxStage,
                    found.stageCount()
                );
            } else {
                ExecutionCheckpointStore.clearCheckpoint(active);
            }
        }
        return found;
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

    public static boolean remove(long entryId) {
        boolean removed;
        synchronized (lock) {
            removed = entries.removeIf(entry -> entry.id() == entryId);
        }
        if (removed) {
            notifyListeners();
        }
        return removed;
    }

    public static void clearAll() {
        synchronized (lock) {
            entries.clear();
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

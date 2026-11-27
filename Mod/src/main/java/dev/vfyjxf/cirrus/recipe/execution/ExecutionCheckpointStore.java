package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory checkpoint and stage-breakpoint store so execution progress survives screen close/reopen and supports explicit resume.
 */
public final class ExecutionCheckpointStore {

    public record Checkpoint(
        String key,
        long selectionVersion,
        int stepIndex,
        int totalSteps,
        int stageIndex,
        int totalStages,
        long savedAtEpochMs
    ) {
        public boolean resumable() {
            return stepIndex > 0 && stepIndex < totalSteps;
        }
    }

    private static final Map<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();
    private static final Map<String, Set<Integer>> breakpoints = new ConcurrentHashMap<>();
    private static final Set<Long> completedSelectionVersions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ExecutionCheckpointStore() {}

    public static String selectionKey(ExecutionTargetRecipeState.@Nullable Selection selection) {
        if (selection == null || selection.recipe() == null) {
            return "";
        }
        RecipeEntry<?> recipe = selection.recipe();
        ResourceLocation id = recipe.id();
        String recipeKey = id != null ? id.toString() : recipe.toString();
        return recipeKey + "#" + selection.times();
    }

    public static void saveCheckpoint(
        ExecutionTargetRecipeState.@Nullable Selection selection,
        int stepIndex,
        int totalSteps,
        int stageIndex,
        int totalStages
    ) {
        String key = selectionKey(selection);
        if (key.isEmpty() || selection == null || totalSteps <= 0) {
            return;
        }
        if (completedSelectionVersions.contains(selection.version())) {
            checkpoints.remove(key);
            return;
        }
        int clamped = Math.max(0, Math.min(stepIndex, totalSteps));
        if (clamped >= totalSteps) {
            checkpoints.remove(key);
            return;
        }
        checkpoints.put(
            key,
            new Checkpoint(
                key,
                selection.version(),
                clamped,
                totalSteps,
                Math.max(0, stageIndex),
                Math.max(1, totalStages),
                System.currentTimeMillis()
            )
        );
    }

    public static void markCompleted(ExecutionTargetRecipeState.@Nullable Selection selection) {
        if (selection == null) {
            return;
        }
        completedSelectionVersions.add(selection.version());
        clearCheckpoint(selection);
    }

    public static boolean isCompleted(ExecutionTargetRecipeState.@Nullable Selection selection) {
        return selection != null && completedSelectionVersions.contains(selection.version());
    }

    public static @Nullable Checkpoint checkpointFor(ExecutionTargetRecipeState.@Nullable Selection selection) {
        if (selection == null || completedSelectionVersions.contains(selection.version())) {
            return null;
        }
        String key = selectionKey(selection);
        return key.isEmpty() ? null : checkpoints.get(key);
    }

    public static void clearCheckpoint(ExecutionTargetRecipeState.@Nullable Selection selection) {
        String key = selectionKey(selection);
        if (!key.isEmpty()) {
            checkpoints.remove(key);
        }
    }

    public static boolean toggleBreakpoint(ExecutionTargetRecipeState.@Nullable Selection selection, int stageIndex) {
        String key = selectionKey(selection);
        if (key.isEmpty() || stageIndex < 0) {
            return false;
        }
        Set<Integer> set = breakpoints
                .computeIfAbsent(key, ignored -> Collections.synchronizedSet(new LinkedHashSet<>()));
        synchronized (set) {
            if (set.remove(stageIndex)) {
                return false;
            }
            set.add(stageIndex);
            return true;
        }
    }

    public static boolean hasBreakpoint(ExecutionTargetRecipeState.@Nullable Selection selection, int stageIndex) {
        String key = selectionKey(selection);
        if (key.isEmpty()) {
            return false;
        }
        Set<Integer> set = breakpoints.get(key);
        return set != null && set.contains(stageIndex);
    }

    public static Set<Integer> breakpointsFor(ExecutionTargetRecipeState.@Nullable Selection selection) {
        String key = selectionKey(selection);
        if (key.isEmpty()) {
            return Set.of();
        }
        Set<Integer> set = breakpoints.get(key);
        if (set == null) {
            return Set.of();
        }
        synchronized (set) {
            return Set.copyOf(set);
        }
    }

    public static void clearBreakpoints(ExecutionTargetRecipeState.@Nullable Selection selection) {
        String key = selectionKey(selection);
        if (!key.isEmpty()) {
            breakpoints.remove(key);
        }
    }

    public static void clearAll() {
        checkpoints.clear();
        breakpoints.clear();
        completedSelectionVersions.clear();
    }
}

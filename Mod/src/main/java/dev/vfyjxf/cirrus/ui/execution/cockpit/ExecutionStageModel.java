package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Collapses low-level execution ops into rich, player-facing recipe stages with concrete input grids, upstream dependencies, station labels, and error states.
 */
public record ExecutionStageModel(
    List<Stage> stages,
    int activeStageIndex,
    int completedStageCount,
    int totalRawSteps,
    int currentRawStepIndex,
    boolean completed,
    boolean paused,
    Map<String, Integer> materialFirstStageIndex
) {

    public static final ExecutionStageModel empty = new ExecutionStageModel(
        List.of(),
        -1,
        0,
        0,
        0,
        false,
        false,
        Map.of()
    );

    public enum StageStatus {
        completed, active, paused, pending
    }

    public record Stage(
        int stageIndex,
        int startStepIndex,
        int endStepIndex,
        Component title,
        Component stationLabel,
        List<RecipeContent> inputs,
        List<RecipeContent> rawGridInputs,
        List<RecipeContent> outputs,
        int times,
        StageStatus status,
        boolean breakpoint,
        boolean manual,
        int completedOps,
        int totalOps,
        List<Integer> upstreamStages,
        @Nullable Component phaseHint,
        @Nullable Component errorSummary,
        @Nullable RecipeEntry<?> recipe
    ) {
        public boolean isCompleted() {
            return status == StageStatus.completed;
        }

        public double opProgressRatio() {
            if (status == StageStatus.completed) {
                return 1.0d;
            }
            if (totalOps <= 0) {
                return 0.0d;
            }
            return Math.max(0.0d, Math.min(1.0d, completedOps / (double) totalOps));
        }
    }

    public boolean isEmpty() {
        return stages.isEmpty();
    }

    public int totalStageCount() {
        return stages.size();
    }

    public double progressRatio() {
        if (completed) {
            return 1.0d;
        }
        if (totalRawSteps <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, currentRawStepIndex / (double) totalRawSteps));
    }

    public @Nullable Stage activeStage() {
        if (activeStageIndex >= 0 && activeStageIndex < stages.size()) {
            return stages.get(activeStageIndex);
        }
        return stages.isEmpty() ? null : stages.get(stages.size() - 1);
    }

    public @Nullable Stage stageAt(int index) {
        return index >= 0 && index < stages.size() ? stages.get(index) : null;
    }

    public int stageIndexForStep(int stepIndex) {
        for (Stage stage : stages) {
            if (stepIndex >= stage.startStepIndex() && stepIndex <= stage.endStepIndex()) {
                return stage.stageIndex();
            }
        }
        return -1;
    }

    public int nextSkipTargetStepIndex() {
        Stage current = activeStage();
        if (current == null) {
            return totalRawSteps;
        }
        return Math.min(totalRawSteps, current.endStepIndex() + 1);
    }

    public List<Integer> downstreamStagesOf(int stageIndex) {
        List<Integer> out = new ArrayList<>();
        for (Stage candidate : stages) {
            if (candidate.upstreamStages().contains(stageIndex)) {
                out.add(candidate.stageIndex());
            }
        }
        return out;
    }

    public int firstStageIndexForMaterial(RecipeContent material) {
        String key = ExecutionOverlayContentHelper.recipeContentKey(material);
        return materialFirstStageIndex.getOrDefault(key, Integer.MAX_VALUE);
    }

    public static ExecutionStageModel derive(
        @Nullable ExecutionSession session,
        Set<Integer> breakpoints,
        @Nullable Integer hitBreakpointStage
    ) {
        if (session == null) {
            return empty;
        }
        ImmutableList<InteractionStep> steps = session.plan().steps();
        int totalSteps = steps.size();
        if (totalSteps == 0) {
            return new ExecutionStageModel(List.of(), -1, 0, 0, 0, session.completed(), session.paused(), Map.of());
        }

        List<RawGroup> groups = groupSteps(steps);
        int nextIdx = Math.max(0, Math.min(totalSteps, session.nextStepIndex()));

        // Map each produced output key to the latest stage that produces it for upstream dependency tracking.
        Map<String, Integer> producerByKey = new LinkedHashMap<>();
        Map<String, Integer> firstStageByMaterial = new LinkedHashMap<>();
        List<List<Integer>> upstreamPerStage = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            RawGroup g = groups.get(i);
            Set<Integer> deps = new LinkedHashSet<>();
            for (RecipeContent in : g.inputs) {
                String k = ExecutionOverlayContentHelper.recipeContentKey(in);
                if (!"-".equals(k)) {
                    firstStageByMaterial.putIfAbsent(k, i);
                    Integer prod = producerByKey.get(k);
                    if (prod != null && prod < i) {
                        deps.add(prod);
                    }
                }
            }
            upstreamPerStage.add(List.copyOf(deps));
            for (RecipeContent out : g.outputs) {
                String k = ExecutionOverlayContentHelper.recipeContentKey(out);
                if (!"-".equals(k)) {
                    producerByKey.put(k, i);
                }
            }
        }

        List<Stage> stages = new ArrayList<>(groups.size());
        int activeStage = -1;
        int doneStages = 0;
        boolean sessionPaused = session.paused() || hitBreakpointStage != null;
        for (int i = 0; i < groups.size(); i++) {
            RawGroup g = groups.get(i);
            boolean bp = breakpoints.contains(i);
            int totalOps = Math.max(1, g.endStepIndex - g.startStepIndex + 1);
            int completedOps;
            StageStatus status;
            Component phaseHint = null;
            Component errorSummary = null;
            if (nextIdx > g.endStepIndex) {
                status = StageStatus.completed;
                completedOps = totalOps;
                doneStages++;
            } else if (nextIdx >= g.startStepIndex) {
                activeStage = i;
                completedOps = Math.max(0, nextIdx - g.startStepIndex);
                boolean hitBp = hitBreakpointStage != null && hitBreakpointStage == i;
                if (hitBp) {
                    status = StageStatus.paused;
                    phaseHint = CirrusLangs.Execution.Cockpit.phaseHitBp.get();
                    errorSummary = CirrusLangs.Execution.Cockpit.diagBreakpointTitle.get(i + 1);
                } else if (session.paused()) {
                    status = StageStatus.paused;
                    phaseHint = humanPausePhase(session.pauseReason());
                    errorSummary = resolveErrorSummary(session.pauseReason());
                } else {
                    status = StageStatus.active;
                    phaseHint = humanActivePhase(steps.get(nextIdx));
                }
            } else {
                status = StageStatus.pending;
                completedOps = 0;
            }
            stages.add(
                new Stage(
                    i,
                    g.startStepIndex,
                    g.endStepIndex,
                    g.title,
                    g.stationLabel,
                    g.inputs,
                    g.rawGridInputs,
                    g.outputs,
                    g.times,
                    status,
                    bp,
                    g.manual,
                    completedOps,
                    totalOps,
                    upstreamPerStage.get(i),
                    phaseHint,
                    errorSummary,
                    g.recipe
                )
            );
        }
        return new ExecutionStageModel(
            List.copyOf(stages),
            activeStage,
            doneStages,
            totalSteps,
            nextIdx,
            session.completed(),
            sessionPaused,
            Map.copyOf(firstStageByMaterial)
        );
    }

    private record RawGroup(
        int startStepIndex,
        int endStepIndex,
        Component title,
        Component stationLabel,
        List<RecipeContent> inputs,
        List<RecipeContent> rawGridInputs,
        List<RecipeContent> outputs,
        int times,
        boolean manual,
        @Nullable RecipeEntry<?> recipe
    ) {}

    private static List<RawGroup> groupSteps(ImmutableList<InteractionStep> steps) {
        List<RawGroup> out = new ArrayList<>();
        int total = steps.size();
        int groupStart = 0;
        int i = 0;
        while (i < total) {
            InteractionStep step = steps.get(i);
            if (step instanceof InteractionStep.ExecuteRecipeStep exec) {
                int end = i;
                while (end + 1 < total && isPostCraftOp(steps.get(end + 1), exec.recipe())) {
                    end++;
                }
                List<RecipeContent> rawInputs = extractRawInputs(exec);
                List<RecipeContent> mergedInputs = ExecutionOverlayContentHelper.mergeStackedContents(rawInputs);
                List<RecipeContent> outputs = extractOutputs(exec);
                Component title = resolveCraftTitle(exec, outputs);
                Component station = resolveStationLabel(exec.recipe(), rawInputs.size());
                out.add(
                    new RawGroup(
                        groupStart,
                        end,
                        title,
                        station,
                        mergedInputs,
                        rawInputs,
                        outputs,
                        exec.times(),
                        false,
                        exec.recipe()
                    )
                );
                i = end + 1;
                groupStart = i;
                continue;
            }
            if (step instanceof InteractionStep.ManualCheckpointStep manual) {
                Component title = manual.guidance() != null && manual.guidance().summary() != null
                        ? manual.guidance().summary()
                        : CirrusLangs.Execution.Cockpit.phaseManualStep.get();
                out.add(
                    new RawGroup(
                        groupStart,
                        i,
                        title,
                        CirrusLangs.Execution.Cockpit.stageStationManual.get(),
                        List.of(),
                        List.of(),
                        List.of(),
                        1,
                        true,
                        null
                    )
                );
                i++;
                groupStart = i;
                continue;
            }
            i++;
        }
        if (groupStart < total) {
            if (!out.isEmpty()) {
                RawGroup last = out.remove(out.size() - 1);
                out.add(
                    new RawGroup(
                        last.startStepIndex,
                        total - 1,
                        last.title,
                        last.stationLabel,
                        last.inputs,
                        last.rawGridInputs,
                        last.outputs,
                        last.times,
                        last.manual,
                        last.recipe
                    )
                );
            } else {
                out.add(
                    new RawGroup(
                        0,
                        total - 1,
                        CirrusLangs.Execution.Cockpit.phasePrepareMats.get(),
                        CirrusLangs.Execution.Cockpit.stageStationManual.get(),
                        List.of(),
                        List.of(),
                        List.of(),
                        1,
                        false,
                        null
                    )
                );
            }
        }
        return out;
    }

    private static boolean isPostCraftOp(InteractionStep step, RecipeEntry<?> recipe) {
        if (step instanceof InteractionStep.CollectOutputsStep collect) {
            return recipe.equals(collect.recipe());
        }
        return step instanceof InteractionStep.StoreOutputsStep;
    }

    private static List<RecipeContent> extractRawInputs(InteractionStep.ExecuteRecipeStep exec) {
        List<RecipeContent> raw = new ArrayList<>(exec.inputs().size());
        for (InputBinding binding : exec.inputs()) {
            RecipeContent content = binding.input().copyWithAmount(Math.max(1L, binding.amount()));
            if (content.notEmpty()) {
                raw.add(content);
            }
        }
        return List.copyOf(raw);
    }

    private static List<RecipeContent> extractOutputs(InteractionStep.ExecuteRecipeStep exec) {
        List<RecipeContent> raw = new ArrayList<>(exec.expectedOutputs().size());
        for (OutputBinding binding : exec.expectedOutputs()) {
            RecipeContent content = ExecutionOverlayContentHelper
                    .recipeContentFromResource(binding.resource(), binding.amount(), 1.0d);
            if (content.notEmpty()) {
                raw.add(content);
            }
        }
        return ExecutionOverlayContentHelper.mergeStackedContents(raw);
    }

    private static Component resolveCraftTitle(InteractionStep.ExecuteRecipeStep exec, List<RecipeContent> outputs) {
        if (!outputs.isEmpty()) {
            String label = ExecutionOverlayContentHelper.label(outputs.get(0));
            if (label != null && !label.isBlank() && !"-".equals(label)) {
                return Component.literal(label);
            }
        }
        return ExecutionTargetRecipeState.displayName(exec.recipe());
    }

    private static Component resolveStationLabel(@Nullable RecipeEntry<?> recipe, int rawInputCount) {
        if (recipe != null && recipe.system() != null && recipe.system().systemId() != null) {
            String path = recipe.system().systemId().toString().toLowerCase(Locale.ROOT);
            if (path.contains("smelt") || path.contains("blast") || path.contains("smok") || path.contains("furnace")) {
                return CirrusLangs.Execution.Cockpit.stageStationSmelting.get();
            }
            if (path.contains("craft") || path.contains("workbench")) {
                return CirrusLangs.Execution.Cockpit.stageStationCrafting.get();
            }
        }
        if (rawInputCount >= 1) {
            return CirrusLangs.Execution.Cockpit.stageStationCrafting.get();
        }
        return CirrusLangs.Execution.Cockpit.stageStationMachine.get();
    }

    private static Component resolveErrorSummary(@Nullable PauseReason reason) {
        if (reason == null) {
            return CirrusLangs.Execution.Cockpit.diagGenericTitle.get();
        }
        if (reason.guidance() != null && reason.guidance().summary() != null) {
            String txt = reason.guidance().summary().getString();
            if (!txt.isBlank()) {
                return reason.guidance().summary();
            }
        }
        if (PauseReasons.noAccessibleProvider.equals(reason.key())) {
            return CirrusLangs.Execution.Cockpit.diagMissingTitle.get();
        }
        if (PauseReasons.inventoryCapacityBlocked.equals(reason.key())) {
            return CirrusLangs.Execution.Cockpit.diagInvFullTitle.get();
        }
        if (PauseReasons.noAccessibleProcessor.equals(reason.key())) {
            return CirrusLangs.Execution.Cockpit.diagStationTitle.get();
        }
        if (PauseReasons.routingPlanFailed.equals(reason.key())) {
            return CirrusLangs.Execution.Cockpit.diagPlanTitle.get();
        }
        return CirrusLangs.Execution.Cockpit.diagGenericTitle.get();
    }

    private static Component humanActivePhase(InteractionStep step) {
        return switch (step) {
            case InteractionStep.EnsureAccessibleStep ignored -> CirrusLangs.Execution.Cockpit.phaseGathering.get();
            case InteractionStep.WithdrawResourceStep ignored -> CirrusLangs.Execution.Cockpit.phaseGathering.get();
            case InteractionStep.ReserveCatalystStep ignored -> CirrusLangs.Execution.Cockpit.phaseGathering.get();
            case InteractionStep.OpenInteractionStep ignored -> CirrusLangs.Execution.Cockpit.phaseWaitStation.get();
            case InteractionStep.PrepareInputsStep ignored -> CirrusLangs.Execution.Cockpit.phasePlacing.get();
            case InteractionStep.ExecuteRecipeStep ignored -> CirrusLangs.Execution.Cockpit.phaseCrafting.get();
            case InteractionStep.CollectOutputsStep ignored -> CirrusLangs.Execution.Cockpit.phaseCollecting.get();
            case InteractionStep.StoreOutputsStep ignored -> CirrusLangs.Execution.Cockpit.phaseCollecting.get();
            case InteractionStep.EnsureCapacityStep ignored -> CirrusLangs.Execution.Cockpit.phaseCollecting.get();
            case InteractionStep.ManualCheckpointStep ignored -> CirrusLangs.Execution.Cockpit.phaseWaitManual.get();
        };
    }

    private static Component humanPausePhase(@Nullable PauseReason reason) {
        if (reason == null) {
            return CirrusLangs.Execution.Cockpit.phasePaused.get();
        }
        if (PauseReasons.noAccessibleProvider.equals(reason.key())) {
            return CirrusLangs.Execution.Cockpit.phasePausedMissing.get();
        }
        if (PauseReasons.inventoryCapacityBlocked.equals(reason.key())) {
            return CirrusLangs.Execution.Cockpit.phasePausedFull.get();
        }
        if (PauseReasons.noAccessibleProcessor.equals(reason.key())) {
            String key = reason.guidance() != null
                    && reason.guidance().summary() != null
                    && reason.guidance().summary().getContents() instanceof TranslatableContents tc
                            ? tc.getKey()
                            : null;
            if (key != null && key.contains("no_placements")) {
                return CirrusLangs.Execution.Cockpit.phasePausedMissing.get();
            }
            if (key != null && key.contains("missing_output")) {
                return CirrusLangs.Execution.Cockpit.flowWaitingOutput.get();
            }
            if (key != null && key.contains("click_failed")) {
                return CirrusLangs.Execution.Cockpit.phasePaused.get();
            }
            return CirrusLangs.Execution.Cockpit.phasePausedStation.get();
        }
        return CirrusLangs.Execution.Cockpit.phasePaused.get();
    }
}

package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.ReTranslationHint;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionAuditLog;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionCheckpointStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionHistoryStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionQueueStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableExecutionDriver;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiAsync;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionActiveRecipeWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionCockpitTheme;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionControlDeckWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionDiagnosticsDrawerWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionGraphPanelWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionHeaderWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionMaterialsPanelWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionStageModel;
import dev.vfyjxf.cirrus.ui.execution.page.RecipeActiveContext;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import dev.vfyjxf.taffy.style.Overflow;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * OreUI Execution Cockpit hosting target/queue header, transport/speed/checkpoint deck, concrete active recipe spotlight, searchable/sortable materials panel, interactive dependency execution graph, and diagnostics/telemetry drawer.
 */
public class MainPanel extends CompositeWidget<Widget> implements ExecutionOverlayViewState {

    public static final int minPanelWidth = 172;
    public static final int maxPanelWidth = 280;
    private static final int targetTimesDebounceTicks = 8;

    private final ExecutionDriverHandle driverHandle;
    private final ExecutionHeaderWidget headerWidget;
    private final ExecutionControlDeckWidget controlDeck;
    private final ExecutionActiveRecipeWidget activeRecipeWidget;
    private final ExecutionMaterialsPanelWidget materialsPanel;
    private final ExecutionGraphPanelWidget graphPanel;
    private final ExecutionDiagnosticsDrawerWidget diagnosticsDrawer;
    private final ExecutionAuditLog auditLog = new ExecutionAuditLog();

    private @Nullable ExecutionDriver driver;
    private @Nullable ExecutionDriverSnapshot snapshot;
    private ExecutionStageModel stageModel = ExecutionStageModel.empty;
    private Map<String, OverlayMaterialRequirement> materialMap = Map.of();
    private long appliedSelectionVersion = Long.MIN_VALUE;
    private long failedSelectionVersion = Long.MIN_VALUE;
    private long sessionBuildRequestId = 0L;
    private @Nullable PendingSessionBuild pendingSessionBuild;
    private long activeContextRequestId = 0L;
    private @Nullable ExecutionSession activeContextSession;
    private @Nullable RecipeActiveContext activeRecipeContext;
    private @Nullable PendingActiveContextBuild pendingActiveContextBuild;
    private boolean traceEnabled;
    private double speedRate;
    private @Nullable Integer hitBreakpointStageIndex;
    private @Nullable Integer bypassedBreakpointStageIndex;
    private boolean checkpointBannerDismissed;
    private boolean showingHistory;
    private long recordedCompletedVersion = Long.MIN_VALUE;
    private int inspectedStageIndex = -1;
    private int lastObservedRawStep = 0;
    private int lastObservedCompletedStages = 0;
    private boolean wasSessionPaused;
    private int pendingSingleStageEndStep = -1;
    private int singleStageWaitTicks = 0;
    private final TargetTimesDebouncer<RecipeEntry<?>> targetTimesDebouncer = new TargetTimesDebouncer<>(
        targetTimesDebounceTicks
    );

    public MainPanel(ExecutionDriverHandle driverHandle) {
        this.driverHandle = driverHandle;
        this.traceEnabled = readTraceConfig();
        this.speedRate = readRateConfig();

        useStyle(
            UIStyle.of(
                flexColumn(),
                boxSizingBorderBox(),
                padding(1),
                sizeFull(),
                minWidth(0),
                minHeight(0),
                overflow(Overflow.HIDDEN)
            )
        );
        setTickable(true);
        setFocusable(true);
        onMouseClicked((input, ctx) -> {
            ctx.consume();
            return EventDispatch.consumed;
        });

        headerWidget = addWidget(new ExecutionHeaderWidget(this));
        controlDeck = addWidget(new ExecutionControlDeckWidget(this));
        activeRecipeWidget = addWidget(new ExecutionActiveRecipeWidget(this));
        materialsPanel = addWidget(new ExecutionMaterialsPanelWidget(this));
        graphPanel = addWidget(new ExecutionGraphPanelWidget(this));
        // Reserves the 26px bottom slot for the collapsed drawer while the drawer itself rises upward as a bottom-anchored sheet.
        Widget drawerSpacer = addWidget(new Widget() {});
        drawerSpacer.setInteractive(false);
        drawerSpacer.useStyle(heightOf(ExecutionDiagnosticsDrawerWidget.collapsedHeight), flexShrink(0), minWidth(0));
        diagnosticsDrawer = addWidget(new ExecutionDiagnosticsDrawerWidget(this));
    }

    public static int preferredPanelWidth(int availableWidth) {
        if (availableWidth <= 0) {
            return maxPanelWidth;
        }
        return Math.max(Math.min(minPanelWidth, availableWidth), Math.min(availableWidth, maxPanelWidth));
    }

    public ExecutionHeaderWidget headerWidget() {
        return headerWidget;
    }

    public ExecutionControlDeckWidget controlDeck() {
        return controlDeck;
    }

    public ExecutionActiveRecipeWidget activeRecipeWidget() {
        return activeRecipeWidget;
    }

    public ExecutionMaterialsPanelWidget materialsPanel() {
        return materialsPanel;
    }

    public ExecutionGraphPanelWidget graphPanel() {
        return graphPanel;
    }

    public ExecutionDiagnosticsDrawerWidget diagnosticsDrawer() {
        return diagnosticsDrawer;
    }

    public ExecutionAuditLog auditLog() {
        return auditLog;
    }

    public int panelHeightBudget() {
        return height() > 0 ? height() : 320;
    }

    public int topFixedHeight() {
        int headerH = headerWidget.height() > 0 ? headerWidget.height() : 46;
        int deckH = controlDeck.height() > 0 ? controlDeck.height() : 44;
        return headerH + deckH;
    }

    public boolean isCompactHeight() {
        return height() > 0 && height() < 280;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        headerWidget.syncHeight();
        controlDeck.syncHeight();
        CirrusPalette p = ExecutionCockpitTheme.palette(this);
        OreUiPaint.raisedCard(canvas, 0, 0, w, h, p, false, false);
    }

    //region lifecycle & tick

    @Override
    public void tick() {
        super.tick();
        tickPendingTargetTimes();
        syncDriverSelection();
        if (driver != null) {
            checkBreakpointBeforeTick();
            advancePendingSingleStage();
            driver.tick();
            snapshot = driver.snapshot();
            rebuildStageModel();
            enforceBreakpointAfterTick();
            observeExecutionTelemetry();
            syncDiagnosticsFromSession();
            syncQueueStatus();
            autoPersistProgressCheckpoint();
            consumeReTranslationHint();
            syncActiveRecipeContext();
        } else {
            pendingSingleStageEndStep = -1;
            singleStageWaitTicks = 0;
            snapshot = null;
            stageModel = ExecutionStageModel.empty;
            materialMap = Map.of();
            clearActiveRecipeContext();
        }
    }

    private void observeExecutionTelemetry() {
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        if (session == null) {
            lastObservedRawStep = 0;
            lastObservedCompletedStages = 0;
            return;
        }
        int curStep = session.nextStepIndex();
        if (curStep > lastObservedRawStep) {
            auditLog.recordStepAdvance(curStep - lastObservedRawStep);
        }
        lastObservedRawStep = curStep;

        int completedCount = stageModel.completedStageCount();
        if (completedCount > lastObservedCompletedStages) {
            for (int idx = lastObservedCompletedStages; idx
                    < Math.min(completedCount, stageModel.totalStageCount()); idx++) {
                ExecutionStageModel.Stage st = stageModel.stages().get(idx);
                auditLog.recordStageCompleted(
                    idx,
                    CirrusLangs.Execution.Cockpit.auditStageDone.get(idx + 1, st.title().getString())
                );
            }
        }
        lastObservedCompletedStages = completedCount;
    }

    private void syncDiagnosticsFromSession() {
        if (pendingSingleStageEndStep >= 0 && singleStageWaitTicks > 0) {
            return;
        }
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        if (session == null) {
            return;
        }
        Integer hitBp = hitBreakpointStageIndex;
        if (hitBp != null) {
            wasSessionPaused = true;
            auditLog.setActiveDiagnostic(
                ExecutionAuditLog.Severity.warn,
                ExecutionAuditLog.DiagnosticKind.breakpointHit,
                hitBp,
                CirrusLangs.Execution.Cockpit.diagBreakpointTitle.get(hitBp + 1),
                CirrusLangs.Execution.Cockpit.diagBreakpointDetail.get()
            );
            return;
        }
        if (session.paused()) {
            wasSessionPaused = true;
            int stageIdx = Math.max(0, stageModel.activeStageIndex());
            PauseReason reason = session.pauseReason();
            if (reason != null && PauseReasons.inventoryCapacityBlocked.equals(reason.key())) {
                auditLog.setActiveDiagnostic(
                    ExecutionAuditLog.Severity.error,
                    ExecutionAuditLog.DiagnosticKind.inventoryFull,
                    stageIdx,
                    CirrusLangs.Execution.Cockpit.diagInvFullTitle.get(),
                    CirrusLangs.Execution.Cockpit.diagInvFullDetail.get()
                );
            } else if (reason != null && PauseReasons.noAccessibleProcessor.equals(reason.key())) {
                String gKey = guidanceSummaryKey(reason);
                boolean stationOpen = isStationReady();
                if (gKey != null && gKey.contains("no_placements") && missingMaterialCount() > 0) {
                    auditLog.setActiveDiagnostic(
                        ExecutionAuditLog.Severity.error,
                        ExecutionAuditLog.DiagnosticKind.missingMaterial,
                        stageIdx,
                        CirrusLangs.Execution.Cockpit.diagMissingTitle.get(),
                        CirrusLangs.Execution.Cockpit.diagMissingDetail.get(Math.max(1, missingMaterialCount()))
                    );
                } else if (stationOpen && gKey != null && gKey.contains("missing_output")) {
                    auditLog.setActiveDiagnostic(
                        ExecutionAuditLog.Severity.warn,
                        ExecutionAuditLog.DiagnosticKind.safetyStop,
                        stageIdx,
                        CirrusLangs.Execution.Cockpit.diagWaitingOutputTitle.get(),
                        CirrusLangs.Execution.Cockpit.diagWaitingOutputDetail.get()
                    );
                } else if (stationOpen && reason.guidance() != null && reason.guidance().summary() != null) {
                    auditLog.setActiveDiagnostic(
                        ExecutionAuditLog.Severity.warn,
                        ExecutionAuditLog.DiagnosticKind.safetyStop,
                        stageIdx,
                        reason.guidance().summary(),
                        reason.guidance().detail()
                    );
                } else {
                    auditLog.setActiveDiagnostic(
                        ExecutionAuditLog.Severity.warn,
                        ExecutionAuditLog.DiagnosticKind.wrongStation,
                        stageIdx,
                        CirrusLangs.Execution.Cockpit.diagStationTitle.get(),
                        CirrusLangs.Execution.Cockpit.diagStationDetail.get()
                    );
                }
            } else if (reason != null && PauseReasons.noAccessibleProvider.equals(reason.key())) {
                auditLog.setActiveDiagnostic(
                    ExecutionAuditLog.Severity.error,
                    ExecutionAuditLog.DiagnosticKind.missingMaterial,
                    stageIdx,
                    CirrusLangs.Execution.Cockpit.diagMissingTitle.get(),
                    CirrusLangs.Execution.Cockpit.diagMissingDetail.get(Math.max(1, missingMaterialCount()))
                );
            } else if (reason != null && PauseReasons.routingPlanFailed.equals(reason.key())) {
                auditLog.setActiveDiagnostic(
                    ExecutionAuditLog.Severity.error,
                    ExecutionAuditLog.DiagnosticKind.planUnavailable,
                    stageIdx,
                    CirrusLangs.Execution.Cockpit.diagPlanTitle.get(),
                    CirrusLangs.Execution.Cockpit.diagPlanDetail.get()
                );
            } else {
                Component detail = reason != null && reason.guidance() != null ? reason.guidance().summary() : null;
                auditLog.setActiveDiagnostic(
                    ExecutionAuditLog.Severity.warn,
                    ExecutionAuditLog.DiagnosticKind.safetyStop,
                    stageIdx,
                    CirrusLangs.Execution.Cockpit.diagGenericTitle.get(),
                    detail
                );
            }
            return;
        }
        if (wasSessionPaused) {
            wasSessionPaused = false;
            ExecutionAuditLog.Event currentDiag = auditLog.activeDiagnostic();
            if (currentDiag != null && currentDiag.kind() != ExecutionAuditLog.DiagnosticKind.planUnavailable) {
                auditLog.clearActiveDiagnostic();
            }
        }
    }

    private void syncQueueStatus() {
        if (ExecutionQueueStore.size() <= 0 || stageModel.isEmpty()) {
            return;
        }
        ExecutionQueueStore.Status st = stageModel.completed()
                ? ExecutionQueueStore.Status.completed
                : (stageModel.paused() ? ExecutionQueueStore.Status.paused : ExecutionQueueStore.Status.active);
        ExecutionQueueStore.updateActiveStatus(st, stageModel.totalStageCount());
    }

    private void checkBreakpointBeforeTick() {
        if (driver == null || snapshot == null || !snapshot.autoAdvanceEnabled()) {
            return;
        }
        ExecutionSession session = snapshot.session();
        if (session == null || session.completed() || session.paused()) {
            return;
        }
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            return;
        }
        int stageIdx = stageModel.stageIndexForStep(session.nextStepIndex());
        if (stageIdx < 0) {
            return;
        }
        ExecutionStageModel.Stage stage = stageModel.stages().get(stageIdx);
        if (stage.startStepIndex() == session.nextStepIndex()
                && ExecutionCheckpointStore.hasBreakpoint(selection, stageIdx)
                && (bypassedBreakpointStageIndex == null || bypassedBreakpointStageIndex != stageIdx)) {
            driver.stopAutoAdvance();
            hitBreakpointStageIndex = stageIdx;
        }
    }

    private void enforceBreakpointAfterTick() {
        if (driver == null || snapshot == null) {
            return;
        }
        ExecutionSession session = snapshot.session();
        if (session == null) {
            return;
        }
        int curStage = stageModel.stageIndexForStep(session.nextStepIndex());
        if (bypassedBreakpointStageIndex != null && bypassedBreakpointStageIndex != curStage) {
            bypassedBreakpointStageIndex = null;
        }
        if (snapshot.autoAdvanceEnabled() && !session.completed() && curStage >= 0) {
            ExecutionStageModel.Stage stage = stageModel.stages().get(curStage);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
            if (stage.startStepIndex() == session.nextStepIndex()
                    && ExecutionCheckpointStore.hasBreakpoint(selection, curStage)
                    && (bypassedBreakpointStageIndex == null || bypassedBreakpointStageIndex != curStage)) {
                driver.stopAutoAdvance();
                hitBreakpointStageIndex = curStage;
                snapshot = driver.snapshot();
                rebuildStageModel();
            }
        }
    }

    private void autoPersistProgressCheckpoint() {
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (session == null || selection == null || stageModel.isEmpty()) {
            return;
        }
        if (session.completed() || stageModel.completed()) {
            ExecutionCheckpointStore.markCompleted(selection);
            if (recordedCompletedVersion != selection.version()) {
                recordedCompletedVersion = selection.version();
                ExecutionHistoryStore
                        .record(selection, stageModel.totalStageCount(), 0, session.plan().steps().size(), true);
            }
            return;
        }
        if (session.nextStepIndex() > 0) {
            ExecutionCheckpointStore.saveCheckpoint(
                selection,
                session.nextStepIndex(),
                session.plan().steps().size(),
                Math.max(0, stageModel.activeStageIndex()),
                stageModel.totalStageCount()
            );
        }
    }

    public void autoSaveCheckpointOnClose() {
        autoPersistProgressCheckpoint();
    }

    public void archiveAndClearOnClose() {
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        if (selection != null) {
            boolean done = (session != null && session.completed())
                    || stageModel.completed()
                    || ExecutionCheckpointStore.isCompleted(selection);
            int totalSteps = session == null ? 0 : session.plan().steps().size();
            int savedStep = done || session == null ? 0 : session.nextStepIndex();
            int stages = Math.max(1, stageModel.totalStageCount());
            ExecutionHistoryStore.record(selection, stages, savedStep, totalSteps, done);
            if (done) {
                ExecutionCheckpointStore.markCompleted(selection);
            } else if (savedStep > 0 && savedStep < totalSteps) {
                ExecutionCheckpointStore.saveCheckpoint(
                    selection,
                    savedStep,
                    totalSteps,
                    Math.max(0, stageModel.activeStageIndex()),
                    stages
                );
            }
        }
        cancelPendingSessionBuild();
        targetTimesDebouncer.clear();
        if (driver != null) {
            driver.stopAutoAdvance();
        }
        ExecutionQueueStore.clearAll();
        ExecutionTargetRecipeState.clear();
        appliedSelectionVersion = Long.MIN_VALUE;
        failedSelectionVersion = Long.MIN_VALUE;
        showingHistory = false;
        inspectedStageIndex = -1;
        snapshot = null;
        stageModel = ExecutionStageModel.empty;
        materialMap = Map.of();
        clearActiveRecipeContext();
    }

    public boolean showingHistory() {
        return showingHistory;
    }

    public void setShowingHistory(boolean showing) {
        this.showingHistory = showing;
    }

    public void toggleHistory() {
        this.showingHistory = !this.showingHistory;
    }

    public boolean restoreFromHistory(long historyEntryId) {
        ExecutionHistoryStore.Entry restored = ExecutionHistoryStore.restore(historyEntryId);
        if (restored == null) {
            return false;
        }
        showingHistory = false;
        checkpointBannerDismissed = false;
        appliedSelectionVersion = Long.MIN_VALUE;
        failedSelectionVersion = Long.MIN_VALUE;
        recordedCompletedVersion = Long.MIN_VALUE;
        inspectedStageIndex = -1;
        return true;
    }

    private void rebuildStageModel() {
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        Set<Integer> bps = ExecutionCheckpointStore.breakpointsFor(selection);
        stageModel = ExecutionStageModel.derive(session, bps, hitBreakpointStageIndex);
        if (inspectedStageIndex >= stageModel.totalStageCount()) {
            inspectedStageIndex = -1;
        }
        rebuildMaterialMap();
    }

    private void rebuildMaterialMap() {
        if (snapshot == null || snapshot.materials() == null || snapshot.materials().isEmpty()) {
            materialMap = Map.of();
            return;
        }
        Map<String, OverlayMaterialRequirement> map = new LinkedHashMap<>();
        for (OverlayMaterialRequirement req : snapshot.materials()) {
            String key = ExecutionOverlayContentHelper.recipeContentKey(req.material());
            if (!"-".equals(key)) {
                map.put(key, req);
            }
        }
        materialMap = Map.copyOf(map);
    }
    //endregion

    //region cockpit controls, spotlight, queue & diagnostics

    public boolean hasTarget() {
        return ExecutionTargetRecipeState.current() != null || (snapshot != null && snapshot.session() != null);
    }

    public boolean isStationReady() {
        if (driver instanceof CraftingTableExecutionDriver craftingDriver) {
            return craftingDriver.isStationOpen();
        }
        return driver != null;
    }

    private static @Nullable String guidanceSummaryKey(@Nullable PauseReason reason) {
        if (reason == null || reason.guidance() == null || reason.guidance().summary() == null) {
            return null;
        }
        return reason.guidance().summary().getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
    }

    public boolean canControlExecution() {
        return !calculating()
                && driver != null
                && snapshot != null
                && snapshot.session() != null
                && !stageModel.isEmpty();
    }

    public boolean canSkipStage() {
        if (!canControlExecution()) {
            return false;
        }
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        return session != null && !session.completed() && stageModel.activeStage() != null;
    }

    public boolean isAutoRunning() {
        return snapshot != null && snapshot.autoAdvanceEnabled() && !stageModel.paused() && !stageModel.completed();
    }

    public ExecutionStageModel stageModel() {
        return stageModel;
    }

    public int inspectedStageIndex() {
        return inspectedStageIndex;
    }

    public void inspectStage(int stageIndex) {
        if (stageIndex < 0 || stageIndex >= stageModel.totalStageCount()) {
            inspectedStageIndex = -1;
            return;
        }
        if (stageIndex == stageModel.activeStageIndex()) {
            inspectedStageIndex = -1;
        } else {
            inspectedStageIndex = stageIndex;
        }
    }

    public void clearInspectedStage() {
        inspectedStageIndex = -1;
    }

    public boolean isInspectingDifferentStage() {
        return inspectedStageIndex >= 0
                && inspectedStageIndex < stageModel.totalStageCount()
                && inspectedStageIndex != stageModel.activeStageIndex();
    }

    public ExecutionStageModel.@Nullable Stage spotlightStage() {
        if (inspectedStageIndex >= 0 && inspectedStageIndex < stageModel.totalStageCount()) {
            return stageModel.stages().get(inspectedStageIndex);
        }
        return stageModel.activeStage();
    }

    public Map<String, OverlayMaterialRequirement> materialMap() {
        return materialMap;
    }

    public @Nullable OverlayMaterialRequirement materialRequirementFor(RecipeContent content) {
        String key = ExecutionOverlayContentHelper.recipeContentKey(content);
        return materialMap.get(key);
    }

    public int countMissingInputsForStage(ExecutionStageModel.Stage stage) {
        int missing = 0;
        for (RecipeContent in : stage.inputs()) {
            OverlayMaterialRequirement req = materialRequirementFor(in);
            if (req != null && req.missing() > 0L) {
                missing++;
            }
        }
        return missing;
    }

    public void selectPreviousQueueEntry() {
        autoPersistProgressCheckpoint();
        ExecutionQueueStore.Entry next = ExecutionQueueStore.selectPrev();
        inspectedStageIndex = -1;
        if (next != null) {
            auditLog.record(
                ExecutionAuditLog.Severity.info,
                ExecutionAuditLog.DiagnosticKind.queueChanged,
                -1,
                CirrusLangs.Execution.Cockpit.auditQueueAdded.get(next.displayName().getString()),
                null
            );
        }
    }

    public void selectNextQueueEntry() {
        autoPersistProgressCheckpoint();
        ExecutionQueueStore.Entry next = ExecutionQueueStore.selectNext();
        inspectedStageIndex = -1;
        if (next != null) {
            auditLog.record(
                ExecutionAuditLog.Severity.info,
                ExecutionAuditLog.DiagnosticKind.queueChanged,
                -1,
                CirrusLangs.Execution.Cockpit.auditQueueAdded.get(next.displayName().getString()),
                null
            );
        }
    }

    public void removeActiveQueueEntry() {
        ExecutionQueueStore.Entry active = ExecutionQueueStore.activeEntry();
        if (active != null) {
            ExecutionQueueStore.remove(active.id());
            inspectedStageIndex = -1;
        }
    }

    public void retryCurrentStage() {
        auditLog.clearActiveDiagnostic();
        if (!canControlExecution() || driver == null) {
            replanActiveTarget();
            return;
        }
        if (hitBreakpointStageIndex != null) {
            bypassedBreakpointStageIndex = hitBreakpointStageIndex;
            hitBreakpointStageIndex = null;
        }
        ExecutionSession session = driver.snapshot().session();
        if (session != null && session.paused()) {
            driver.resume();
        }
        snapshot = driver.snapshot();
        rebuildStageModel();
    }

    public void replanActiveTarget() {
        auditLog.clearActiveDiagnostic();
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            return;
        }
        cancelPendingSessionBuild();
        ExecutionDriver recreated = recreateDriver();
        appliedSelectionVersion = Long.MIN_VALUE;
        failedSelectionVersion = Long.MIN_VALUE;
        startSessionBuild(selection, recreated, null);
    }

    public @Nullable Integer hitBreakpointStageIndex() {
        return hitBreakpointStageIndex;
    }

    public double currentSpeedRate() {
        return speedRate;
    }

    public void setSpeedRate(double rate) {
        speedRate = Math.max(0.25d, Math.min(60.0d, rate));
        try {
            ExecutionOverlayClientConfig.setOverlayRate(Math.max(1L, Math.round(speedRate)));
        } catch (RuntimeException | LinkageError ignored) {}
        if (driver != null) {
            driver.setRatePerSecond(speedRate);
            snapshot = driver.snapshot();
        }
    }

    public void toggleAutoExecute() {
        if (!canControlExecution() || driver == null) {
            return;
        }
        pendingSingleStageEndStep = -1;
        singleStageWaitTicks = 0;
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        if (session == null) {
            return;
        }
        if (session.completed()) {
            resetExecution();
        }
        if (hitBreakpointStageIndex != null) {
            bypassedBreakpointStageIndex = hitBreakpointStageIndex;
            hitBreakpointStageIndex = null;
        }
        if (isAutoRunning()) {
            driver.stopAutoAdvance();
        } else {
            if (session.paused()) {
                driver.resume();
            }
            if (!driver.snapshot().autoAdvanceEnabled()) {
                driver.toggleAutoAdvance();
            }
        }
        snapshot = driver.snapshot();
        rebuildStageModel();
        syncDiagnosticsFromSession();
    }

    public void advanceOneStage() {
        if (!canControlExecution() || driver == null) {
            return;
        }
        driver.stopAutoAdvance();
        pendingSingleStageEndStep = -1;
        singleStageWaitTicks = 0;
        if (hitBreakpointStageIndex != null) {
            bypassedBreakpointStageIndex = hitBreakpointStageIndex;
            hitBreakpointStageIndex = null;
        }
        ExecutionSession session = driver.snapshot().session();
        if (session == null || session.completed()) {
            return;
        }
        if (session.paused()) {
            driver.resume();
        }
        ExecutionStageModel.Stage active = stageModel.activeStage();
        int targetEndStep = active == null ? session.nextStepIndex() : active.endStepIndex();
        int safety = 0;
        while (safety++ < 64) {
            ExecutionSession cur = driver.snapshot().session();
            if (cur == null || cur.completed() || cur.paused() || cur.nextStepIndex() > targetEndStep) {
                break;
            }
            InteractionStep before = cur.currentStep();
            driver.advanceSteps(1);
            ExecutionSession after = driver.snapshot().session();
            if (after == null || after.completed() || after.paused() || after.nextStepIndex() > targetEndStep) {
                break;
            }
            if (before instanceof InteractionStep.PrepareInputsStep
                    && after.currentStep() instanceof InteractionStep.ExecuteRecipeStep
                    && driver instanceof CraftingTableExecutionDriver) {
                pendingSingleStageEndStep = targetEndStep;
                singleStageWaitTicks = 12;
                break;
            }
        }
        snapshot = driver.snapshot();
        rebuildStageModel();
        observeExecutionTelemetry();
        syncDiagnosticsFromSession();
        autoPersistProgressCheckpoint();
    }

    private void advancePendingSingleStage() {
        if (pendingSingleStageEndStep < 0 || driver == null) {
            return;
        }
        ExecutionSession cur = driver.snapshot().session();
        if (cur == null || cur.completed() || cur.nextStepIndex() > pendingSingleStageEndStep) {
            pendingSingleStageEndStep = -1;
            singleStageWaitTicks = 0;
            return;
        }
        if (cur.paused()) {
            if (singleStageWaitTicks-- > 0) {
                driver.resume();
                return;
            }
            pendingSingleStageEndStep = -1;
            singleStageWaitTicks = 0;
            return;
        }
        int remainingBudget = Math.max(1, pendingSingleStageEndStep - cur.nextStepIndex() + 1);
        driver.advanceSteps(remainingBudget);
        ExecutionSession after = driver.snapshot().session();
        if (after == null || after.completed() || after.nextStepIndex() > pendingSingleStageEndStep) {
            pendingSingleStageEndStep = -1;
            singleStageWaitTicks = 0;
        } else if (after.paused() && singleStageWaitTicks-- > 0) {
            return;
        } else {
            pendingSingleStageEndStep = -1;
            singleStageWaitTicks = 0;
        }
    }

    public void skipCurrentStage() {
        if (!canSkipStage() || driver == null) {
            return;
        }
        int skippedIdx = Math.max(0, stageModel.activeStageIndex());
        driver.stopAutoAdvance();
        pendingSingleStageEndStep = -1;
        singleStageWaitTicks = 0;
        hitBreakpointStageIndex = null;
        bypassedBreakpointStageIndex = null;
        int nextIndex = stageModel.nextSkipTargetStepIndex();
        driver.jumpToStep(nextIndex);
        snapshot = driver.snapshot();
        rebuildStageModel();
        auditLog.record(
            ExecutionAuditLog.Severity.info,
            ExecutionAuditLog.DiagnosticKind.info,
            skippedIdx,
            CirrusLangs.Execution.Cockpit.auditSkipped.get(skippedIdx + 1),
            null
        );
        syncDiagnosticsFromSession();
        autoPersistProgressCheckpoint();
    }

    public void jumpToStage(int stageIndex) {
        if (!canControlExecution() || driver == null) {
            return;
        }
        if (stageIndex < 0 || stageIndex >= stageModel.totalStageCount()) {
            return;
        }
        driver.stopAutoAdvance();
        hitBreakpointStageIndex = null;
        bypassedBreakpointStageIndex = stageIndex;
        inspectedStageIndex = -1;
        ExecutionStageModel.Stage target = stageModel.stages().get(stageIndex);
        driver.jumpToStep(target.startStepIndex());
        snapshot = driver.snapshot();
        rebuildStageModel();
        auditLog.record(
            ExecutionAuditLog.Severity.info,
            ExecutionAuditLog.DiagnosticKind.info,
            stageIndex,
            CirrusLangs.Execution.Cockpit.auditJumped.get(stageIndex + 1),
            null
        );
        syncDiagnosticsFromSession();
        autoPersistProgressCheckpoint();
    }

    public void resetExecution() {
        if (driver == null) {
            return;
        }
        driver.stopAutoAdvance();
        driver.reset();
        pendingSingleStageEndStep = -1;
        singleStageWaitTicks = 0;
        hitBreakpointStageIndex = null;
        bypassedBreakpointStageIndex = null;
        inspectedStageIndex = -1;
        checkpointBannerDismissed = true;
        ExecutionCheckpointStore.clearCheckpoint(ExecutionTargetRecipeState.current());
        snapshot = driver.snapshot();
        rebuildStageModel();
        lastObservedRawStep = 0;
        lastObservedCompletedStages = 0;
        auditLog.clearActiveDiagnostic();
        auditLog.record(
            ExecutionAuditLog.Severity.info,
            ExecutionAuditLog.DiagnosticKind.info,
            0,
            CirrusLangs.Execution.Cockpit.auditReset.get(),
            null
        );
    }

    public boolean toggleStageBreakpoint(int stageIndex) {
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null || stageIndex < 0 || stageIndex >= stageModel.totalStageCount()) {
            return false;
        }
        boolean enabled = ExecutionCheckpointStore.toggleBreakpoint(selection, stageIndex);
        if (!enabled && hitBreakpointStageIndex != null && hitBreakpointStageIndex == stageIndex) {
            hitBreakpointStageIndex = null;
        }
        rebuildStageModel();
        return enabled;
    }

    public void saveCheckpointNow() {
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (session == null || selection == null || stageModel.isEmpty()) {
            return;
        }
        int activeStage = Math.max(0, stageModel.activeStageIndex());
        ExecutionCheckpointStore.saveCheckpoint(
            selection,
            session.nextStepIndex(),
            session.plan().steps().size(),
            activeStage,
            stageModel.totalStageCount()
        );
        auditLog.record(
            ExecutionAuditLog.Severity.info,
            ExecutionAuditLog.DiagnosticKind.info,
            activeStage,
            CirrusLangs.Execution.Cockpit.auditCheckpointSaved.get(activeStage + 1),
            null
        );
    }

    public ExecutionCheckpointStore.@Nullable Checkpoint activeCheckpoint() {
        return ExecutionCheckpointStore.checkpointFor(ExecutionTargetRecipeState.current());
    }

    public boolean hasResumableCheckpoint() {
        ExecutionCheckpointStore.Checkpoint cp = activeCheckpoint();
        if (cp == null || !cp.resumable()) {
            return false;
        }
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        return session != null && cp.stepIndex() < session.plan().steps().size();
    }

    public boolean isCheckpointBannerVisible() {
        if (checkpointBannerDismissed || stageModel.completed() || !hasResumableCheckpoint()) {
            return false;
        }
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (ExecutionCheckpointStore.isCompleted(selection)) {
            return false;
        }
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        ExecutionCheckpointStore.Checkpoint cp = activeCheckpoint();
        return session != null
                && !session.completed()
                && cp != null
                && session.nextStepIndex() == 0
                && cp.stepIndex() > 0;
    }

    public void resumeFromCheckpoint() {
        if (driver == null || !hasResumableCheckpoint()) {
            return;
        }
        ExecutionCheckpointStore.Checkpoint cp = activeCheckpoint();
        if (cp == null) {
            return;
        }
        driver.stopAutoAdvance();
        hitBreakpointStageIndex = null;
        bypassedBreakpointStageIndex = null;
        checkpointBannerDismissed = true;
        driver.jumpToStep(cp.stepIndex());
        snapshot = driver.snapshot();
        rebuildStageModel();
        auditLog.record(
            ExecutionAuditLog.Severity.info,
            ExecutionAuditLog.DiagnosticKind.info,
            cp.stageIndex(),
            CirrusLangs.Execution.Cockpit.auditCheckpointResumed.get(cp.stageIndex() + 1),
            null
        );
    }

    public void dismissCheckpointBanner() {
        checkpointBannerDismissed = true;
        ExecutionCheckpointStore.clearCheckpoint(ExecutionTargetRecipeState.current());
    }

    public List<OverlayMaterialRequirement> sortedMaterials() {
        if (snapshot == null || snapshot.materials() == null || snapshot.materials().isEmpty()) {
            return List.of();
        }
        List<OverlayMaterialRequirement> list = new ArrayList<>(snapshot.materials().castToList());
        list.sort(
            Comparator.comparingLong(OverlayMaterialRequirement::missing).reversed()
                    .thenComparing(Comparator.comparingLong(OverlayMaterialRequirement::required).reversed())
        );
        return list;
    }

    public int missingMaterialCount() {
        if (snapshot == null || snapshot.materials() == null) {
            return 0;
        }
        int missing = 0;
        for (OverlayMaterialRequirement req : snapshot.materials()) {
            if (req.missing() > 0L) {
                missing++;
            }
        }
        return missing;
    }
    //endregion

    //region driver & async session build

    private ExecutionDriver ensureDriver() {
        if (driver == null) {
            driver = driverHandle.create();
            driver.setRatePerSecond(speedRate);
        }
        return driver;
    }

    private ExecutionDriver recreateDriver() {
        double previousRate = snapshot == null ? speedRate : snapshot.ratePerSecond();
        driver = driverHandle.create();
        driver.setRatePerSecond(previousRate);
        snapshot = driver.snapshot();
        return driver;
    }

    private void syncDriverSelection() {
        pollPendingSessionBuild();
        ExecutionDriver currentDriver = ensureDriver();
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            cancelPendingSessionBuild();
            if (hasIncompleteSession(currentDriver)) {
                return;
            }
            if (appliedSelectionVersion != Long.MIN_VALUE) {
                appliedSelectionVersion = Long.MIN_VALUE;
                failedSelectionVersion = Long.MIN_VALUE;
                hitBreakpointStageIndex = null;
                bypassedBreakpointStageIndex = null;
                inspectedStageIndex = -1;
                recreateDriver();
                rebuildStageModel();
            }
            return;
        }
        ExecutionQueueStore.syncFromSelection(selection);
        if (pendingSessionBuild != null || targetTimesDebouncer.pending()) {
            return;
        }
        if (selection.version() == appliedSelectionVersion || selection.version() == failedSelectionVersion) {
            return;
        }
        startSessionBuild(selection, currentDriver, null);
    }

    private void consumeReTranslationHint() {
        ExecutionDriver currentDriver = driver;
        if (currentDriver == null || snapshot == null) {
            return;
        }
        ReTranslationHint hint = snapshot.reTranslationHint();
        if (hint == null || pendingSessionBuild != null) {
            return;
        }
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            return;
        }
        var previousSession = snapshot.session();
        ExecutionDriver recreatedDriver = recreateDriver();
        startSessionBuild(selection, recreatedDriver, previousSession);
    }

    private void startSessionBuild(
        ExecutionTargetRecipeState.Selection selection,
        ExecutionDriver targetDriver,
        @Nullable ExecutionSession previousSession
    ) {
        targetDriver.stopAutoAdvance();
        ExecutionInteraction interaction = targetDriver.interaction();
        ExecutionPlanningSnapshot planningSnapshot = interaction.planningSnapshot();
        ExecutionInteraction frozenInteraction = ExecutionTargetRecipeState
                .withPlanningSnapshot(interaction, planningSnapshot);
        long requestId = ++sessionBuildRequestId;
        long selectionVersion = selection.version();
        failedSelectionVersion = Long.MIN_VALUE;
        pendingSessionBuild = new PendingSessionBuild(
            requestId,
            selectionVersion,
            targetDriver,
            CirrusUiAsync
                    .recipeTask(
                        () -> previousSession == null
                                ? ExecutionTargetRecipeState.buildSession(selection, frozenInteraction)
                                : ExecutionTargetRecipeState
                                        .rebuildSession(selection, frozenInteraction, previousSession)
                    )
        );
    }

    private void pollPendingSessionBuild() {
        PendingSessionBuild pending = pendingSessionBuild;
        if (pending == null || !pending.future().isDone()) {
            return;
        }
        pendingSessionBuild = null;
        ExecutionTargetRecipeState.Selection currentSelection = ExecutionTargetRecipeState.current();
        if (currentSelection == null || currentSelection.version() != pending.selectionVersion()) {
            return;
        }
        try {
            ExecutionSession session = pending.future().join();
            applyBuiltSession(pending, session);
        } catch (CompletionException exception) {
            failedSelectionVersion = pending.selectionVersion();
            auditLog.setActiveDiagnostic(
                ExecutionAuditLog.Severity.error,
                ExecutionAuditLog.DiagnosticKind.planUnavailable,
                -1,
                CirrusLangs.Execution.Cockpit.diagPlanTitle.get(),
                CirrusLangs.Execution.Cockpit.diagPlanDetail.get()
            );
        }
    }

    private void applyBuiltSession(PendingSessionBuild pending, ExecutionSession session) {
        if (pending.driver() == driver && pending.requestId() == sessionBuildRequestId) {
            pending.driver().stopAutoAdvance();
            pending.driver().initialize(session);
            appliedSelectionVersion = pending.selectionVersion();
            hitBreakpointStageIndex = null;
            bypassedBreakpointStageIndex = null;
            inspectedStageIndex = -1;
            checkpointBannerDismissed = false;
            lastObservedRawStep = 0;
            lastObservedCompletedStages = 0;
            auditLog.startSession();
            snapshot = pending.driver().snapshot();
            rebuildStageModel();
            syncQueueStatus();
            invalidateActiveRecipeContext();
        }
    }

    private void cancelPendingSessionBuild() {
        if (pendingSessionBuild != null) {
            pendingSessionBuild = null;
            sessionBuildRequestId++;
        }
    }

    private boolean hasIncompleteSession(ExecutionDriver currentDriver) {
        try {
            ExecutionSession currentSession = currentDriver.session();
            return currentSession != null && !currentSession.completed();
        } catch (IllegalStateException ignored) {
            ExecutionSession currentSession = snapshot == null ? null : snapshot.session();
            return currentSession != null && !currentSession.completed();
        }
    }

    private void syncActiveRecipeContext() {
        pollPendingActiveRecipeContext();
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        if (session == null) {
            if (calculating()) {
                invalidateActiveRecipeContext();
                return;
            }
            clearActiveRecipeContext();
            return;
        }
        if (calculating()) {
            invalidateActiveRecipeContext();
            return;
        }
        if (pendingActiveContextBuild != null) {
            if (pendingActiveContextBuild.session() == session) {
                return;
            }
            pendingActiveContextBuild = null;
            activeContextRequestId++;
        }
        if (session == activeContextSession) {
            return;
        }
        activeContextSession = session;
        long requestId = ++activeContextRequestId;
        pendingActiveContextBuild = new PendingActiveContextBuild(
            requestId,
            session,
            CirrusUiAsync.recipeTask(() -> RecipeActiveContext.fromSession(session))
        );
    }

    private void pollPendingActiveRecipeContext() {
        PendingActiveContextBuild pending = pendingActiveContextBuild;
        if (pending == null || !pending.future().isDone()) {
            return;
        }
        pendingActiveContextBuild = null;
        try {
            RecipeActiveContext context = pending.future().join();
            ExecutionSession session = snapshot == null ? null : snapshot.session();
            if (session == pending.session()
                    && activeContextSession == pending.session()
                    && pending.requestId() == activeContextRequestId) {
                activeRecipeContext = context;
            }
        } catch (CompletionException exception) {
            activeContextSession = null;
            activeRecipeContext = null;
        }
    }

    private void clearActiveRecipeContext() {
        activeContextRequestId++;
        activeContextSession = null;
        activeRecipeContext = null;
        pendingActiveContextBuild = null;
    }

    private void invalidateActiveRecipeContext() {
        activeContextRequestId++;
        activeContextSession = null;
        pendingActiveContextBuild = null;
    }

    private void tickPendingTargetTimes() {
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            targetTimesDebouncer.clear();
            return;
        }
        TargetTimesDebouncer.Commit<RecipeEntry<?>> commit = targetTimesDebouncer
                .tick(selection.recipe(), selection.times());
        if (commit != null) {
            stopCurrentExecutionForRecalculation();
            ExecutionTargetRecipeState.Selection updated = ExecutionTargetRecipeState.updateTimes(commit.value());
            if (updated != null) {
                ExecutionQueueStore.syncActiveTimesAndPlan(updated.recipe(), updated.times(), updated.pinnedPlan());
            }
        }
    }

    private boolean flushPendingTargetTimes() {
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            targetTimesDebouncer.clear();
            return false;
        }
        TargetTimesDebouncer.Commit<RecipeEntry<?>> commit = targetTimesDebouncer
                .flush(selection.recipe(), selection.times());
        if (commit == null) {
            return false;
        }
        stopCurrentExecutionForRecalculation();
        ExecutionTargetRecipeState.Selection updated = ExecutionTargetRecipeState.updateTimes(commit.value());
        if (updated != null) {
            ExecutionQueueStore.syncActiveTimesAndPlan(updated.recipe(), updated.times(), updated.pinnedPlan());
        }
        return true;
    }

    private void stopCurrentExecutionForRecalculation() {
        cancelPendingSessionBuild();
        if (driver != null) {
            driver.stopAutoAdvance();
            snapshot = driver.snapshot();
        }
    }
    //endregion

    //region view state

    @Override
    public @Nullable ExecutionDriver driver() {
        return driver;
    }

    @Override
    public @Nullable ExecutionDriverSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public @Nullable ExecutionTelemetrySnapshot telemetry() {
        return snapshot == null ? null : snapshot.telemetry();
    }

    @Override
    public @Nullable ExecutionOverlayTraceSnapshot trace() {
        return snapshot == null ? null : snapshot.trace();
    }

    @Override
    public @Nullable OverlayTargetProgress targetProgress() {
        return snapshot == null ? null : snapshot.targetProgress();
    }

    @Override
    public boolean calculating() {
        return pendingSessionBuild != null;
    }

    @Override
    public @Nullable RecipeActiveContext activeRecipeContext() {
        return activeRecipeContext;
    }

    @Override
    public @Nullable String targetLabel() {
        OverlayTargetProgress progress = targetProgress();
        if (progress != null
                && progress.content().notEmpty()
                && ExecutionOverlayContentHelper.hasRenderableVisual(progress.content())) {
            String label = ExecutionOverlayContentHelper.label(progress.content());
            if (label != null && !label.isBlank() && !"-".equals(label)) {
                return label;
            }
        }
        if (activeRecipeContext != null
                && activeRecipeContext.label() != null
                && !activeRecipeContext.label().isBlank()) {
            return activeRecipeContext.label();
        }
        RecipeContent targetContent = targetContent();
        if (targetContent.notEmpty()) {
            return ExecutionOverlayContentHelper.label(targetContent);
        }
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection != null) {
            return ExecutionTargetRecipeState.displayName(selection.recipe()).getString();
        }
        return null;
    }

    @Override
    public int targetTimes() {
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        return selection == null ? 1 : targetTimesDebouncer.visibleValue(selection.recipe(), selection.times());
    }

    @Override
    public void setTargetTimes(int times) {
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection == null) {
            return;
        }
        int normalized = Math.max(1, times);
        if (selection.times() == normalized) {
            targetTimesDebouncer.clear();
            return;
        }
        stopCurrentExecutionForRecalculation();
        targetTimesDebouncer.request(selection.recipe(), normalized, selection.times());
    }

    @Override
    public void flushTargetTimes() {
        if (flushPendingTargetTimes()) {
            syncDriverSelection();
        }
    }

    @Override
    public RecipeContent targetContent() {
        OverlayTargetProgress progress = targetProgress();
        if (progress != null
                && progress.content().notEmpty()
                && ExecutionOverlayContentHelper.hasRenderableVisual(progress.content())) {
            return progress.content();
        }
        if (activeRecipeContext != null && activeRecipeContext.content().notEmpty()) {
            return activeRecipeContext.content();
        }
        if (progress != null && progress.content().notEmpty()) {
            return progress.content();
        }
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
        if (selection != null) {
            return ExecutionTargetRecipeState.previewContent(selection.recipe());
        }
        return RecipeContent.empty();
    }

    @Override
    public boolean traceEnabled() {
        return traceEnabled;
    }

    @Override
    public void setTraceEnabled(boolean enabled) {
        traceEnabled = enabled;
        try {
            ExecutionOverlayClientConfig.setOverlayTraceEnabled(enabled);
        } catch (RuntimeException | LinkageError ignored) {}
    }
    //endregion

    //region debug hooks & config helpers

    boolean debugSessionBuildPending() {
        return pendingSessionBuild != null;
    }

    boolean debugActiveContextBuildPending() {
        return pendingActiveContextBuild != null;
    }

    @Nullable
    RecipeActiveContext debugActiveRecipeContext() {
        return activeRecipeContext;
    }

    void debugCompletePendingActiveContextBuild() {
        PendingActiveContextBuild pending = pendingActiveContextBuild;
        if (pending == null) {
            return;
        }
        pendingActiveContextBuild = null;
        RecipeActiveContext context = pending.future().join();
        ExecutionSession session = snapshot == null ? null : snapshot.session();
        if (session == pending.session()
                && activeContextSession == pending.session()
                && pending.requestId() == activeContextRequestId) {
            activeRecipeContext = context;
        }
    }

    void debugCompletePendingSessionBuild() {
        PendingSessionBuild pending = pendingSessionBuild;
        if (pending == null) {
            return;
        }
        pendingSessionBuild = null;
        ExecutionSession session = pending.future().join();
        applyBuiltSession(pending, session);
    }

    private static boolean readTraceConfig() {
        try {
            return ExecutionOverlayClientConfig.overlayTraceEnabled();
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    private static double readRateConfig() {
        try {
            return ExecutionOverlayClientConfig.overlayRate();
        } catch (RuntimeException | LinkageError ignored) {
            return 2.0d;
        }
    }

    private record PendingSessionBuild(
        long requestId,
        long selectionVersion,
        ExecutionDriver driver,
        CompletableFuture<ExecutionSession> future
    ) {}

    private record PendingActiveContextBuild(
        long requestId,
        ExecutionSession session,
        CompletableFuture<RecipeActiveContext> future
    ) {}
    //endregion
}

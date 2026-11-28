package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionAuditLog;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionCheckpointStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionHistoryStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionQueueStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotExecutionDriver;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionCockpitTheme;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionControlDeckWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionMaterialsPanelWidget;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionStageModel;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayRoot;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionCockpitModelAndControlTest {

    private static final Namespace interactionId = Namespace.of("test", "cockpit");

    @AfterEach
    void cleanup() {
        ExecutionTargetRecipeState.clear();
        ExecutionCheckpointStore.clearAll();
        ExecutionQueueStore.clearAll();
        ExecutionOverlayOpenState.setOpen(false);
    }

    @Test
    void mergeStackedContentsCombinesDuplicateIngredientsIntoCounts() {
        ContentApiImpl previousContent = ContentApiImpl.instance();
        try {
            attachContentApi();
            RecipeContentRendererTestBootstrap.ensureInitialized();
            List<RecipeContent> blastFurnaceGrid = List.of(
                InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.FURNACE, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.SMOOTH_STONE, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.SMOOTH_STONE, 1), 1),
                InputContent.fromItemStack(new ItemStack(Items.SMOOTH_STONE, 1), 1)
            );
            List<RecipeContent> merged = ExecutionOverlayContentHelper.mergeStackedContents(blastFurnaceGrid);
            assertEquals(3, merged.size(), "9-slot blast furnace grid must stack into 3 distinct ingredients");
            assertEquals(5L, merged.get(0).amount());
            assertEquals(1L, merged.get(1).amount());
            assertEquals(3L, merged.get(2).amount());
        } finally {
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void stageModelCollapsesTechnicalOpsAndSupportsSkipJumpBreakpointAndCheckpoint() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(6);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            RecipeContentRendererTestBootstrap.ensureInitialized();
            ExecutionTargetRecipeState.set(scenario.target(), 1);
            MainPanel panel = new MainPanel(handle());
            panel.tick();
            panel.debugCompletePendingSessionBuild();

            ExecutionStageModel model = panel.stageModel();
            assertFalse(model.isEmpty());
            assertTrue(
                model.totalStageCount() < model.totalRawSteps(),
                "stage model must collapse low-level ops into concise recipe stages"
            );
            assertEquals(0, model.activeStageIndex());

            // Skip current stage jumps cleanly to the start of the next stage and marks prior ops completed
            int expectedNextStep = model.nextSkipTargetStepIndex();
            panel.skipCurrentStage();
            var snapAfterSkip = panel.snapshot();
            assertNotNull(snapAfterSkip);
            ExecutionSession afterSkip = snapAfterSkip.session();
            assertNotNull(afterSkip);
            assertEquals(expectedNextStep, afterSkip.nextStepIndex());
            assertEquals(expectedNextStep, afterSkip.completedSteps().size());
            assertFalse(afterSkip.paused());

            // Spotlight stage inspect override and sync-back
            panel.inspectStage(2);
            assertTrue(panel.isInspectingDifferentStage());
            var inspected = panel.spotlightStage();
            assertNotNull(inspected);
            assertEquals(2, inspected.stageIndex());
            panel.clearInspectedStage();
            assertFalse(panel.isInspectingDifferentStage());

            // Save checkpoint and verify resume after reset
            panel.saveCheckpointNow();
            assertTrue(panel.hasResumableCheckpoint());
            ExecutionCheckpointStore.Checkpoint saved = panel.activeCheckpoint();
            assertNotNull(saved);
            assertEquals(expectedNextStep, saved.stepIndex());

            // Re-initialize a fresh panel (simulating closing & reopening Crafting Table) and resume from checkpoint
            MainPanel reopened = new MainPanel(handle());
            reopened.tick();
            reopened.debugCompletePendingSessionBuild();
            assertTrue(reopened.isCheckpointBannerVisible(), "reopened panel must offer checkpoint resume banner");
            reopened.resumeFromCheckpoint();
            var snapResumed = reopened.snapshot();
            assertNotNull(snapResumed);
            var sessionResumed = snapResumed.session();
            assertNotNull(sessionResumed);
            assertEquals(expectedNextStep, sessionResumed.nextStepIndex());
            assertFalse(reopened.isCheckpointBannerVisible());

            // Toggle breakpoint and verify auto-advance pauses when hitting it
            int bpStage = Math.min(1, reopened.stageModel().totalStageCount() - 1);
            assertTrue(reopened.toggleStageBreakpoint(bpStage));
            assertTrue(reopened.stageModel().stages().get(bpStage).breakpoint());
            reopened.jumpToStage(bpStage);
            var snapJumped = reopened.snapshot();
            assertNotNull(snapJumped);
            var sessionJumped = snapJumped.session();
            assertNotNull(sessionJumped);
            assertEquals(reopened.stageModel().stages().get(bpStage).startStepIndex(), sessionJumped.nextStepIndex());

            // Speed control updates driver rate
            reopened.setSpeedRate(ExecutionControlDeckWidget.speedRates[3]);
            var snapSpeed = reopened.snapshot();
            assertNotNull(snapSpeed);
            assertEquals(ExecutionControlDeckWidget.speedRates[3], snapSpeed.ratePerSecond(), 0.001);
            assertEquals(3, ExecutionControlDeckWidget.activeSpeedIndex(snapSpeed.ratePerSecond()));

            // Audit log captured skip, jump, checkpoint, and reset events
            reopened.resetExecution();
            assertFalse(reopened.auditLog().events(null).isEmpty());
            var snapReset = reopened.snapshot();
            assertNotNull(snapReset);
            var sessionReset = snapReset.session();
            assertNotNull(sessionReset);
            assertEquals(0, sessionReset.nextStepIndex());
            assertFalse(reopened.hasResumableCheckpoint());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    @Test
    void stageModelPreservesConcreteNineSlotGridAndDerivesUpstreamDependencies() {
        ContentApiImpl previousContent = ContentApiImpl.instance();
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(4);
        RecipeApiImpl previousApi = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        try {
            attachContentApi();
            RecipeContentRendererTestBootstrap.ensureInitialized();
            RecipeEntry<?> target = scenario.target();
            MutableList<InteractionStep> steps = Lists.mutable.empty();
            appendStage(
                steps,
                target,
                1,
                Lists.immutable.of(in(Items.COBBLESTONE, 8)),
                Lists.immutable.of(out(Items.FURNACE, 1))
            );
            appendStage(
                steps,
                target,
                1,
                Lists.immutable.of(
                    in(Items.IRON_INGOT, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.FURNACE, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.SMOOTH_STONE, 1),
                    in(Items.SMOOTH_STONE, 1),
                    in(Items.SMOOTH_STONE, 1)
                ),
                Lists.immutable.of(out(Items.BLAST_FURNACE, 1))
            );
            InteractionExecutionPlan plan = new InteractionExecutionPlan(target, 1, steps.toImmutable());
            ExecutionFlowNode root = ExecutionFlowNode.pending(
                interactionId.toString(),
                Component.literal("Blast"),
                plan.steps(),
                null,
                Lists.immutable.empty()
            );
            ExecutionStageModel model = ExecutionStageModel.derive(ExecutionSession.start(plan, root), Set.of(), null);
            assertEquals(2, model.totalStageCount());
            ExecutionStageModel.Stage furnaceStage = model.stages().get(0);
            ExecutionStageModel.Stage blastStage = model.stages().get(1);
            assertEquals(9, blastStage.rawGridInputs().size(), "concrete 3x3 grid slots must not be abbreviated");
            assertEquals(3, blastStage.inputs().size(), "merged summary inputs must stack duplicates");
            assertEquals(List.of(0), blastStage.upstreamStages(), "stage 2 must depend on stage 1 furnace output");
            assertEquals(List.of(1), model.downstreamStagesOf(0));
            assertEquals(0, furnaceStage.stageIndex());
        } finally {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void materialsFilterAndSortSupportsFiveModesAscDescAndSearch() {
        ContentApiImpl previousContent = ContentApiImpl.instance();
        try {
            attachContentApi();
            RecipeContentRendererTestBootstrap.ensureInitialized();
            OverlayMaterialRequirement iron = new OverlayMaterialRequirement(
                new ItemStack(Items.IRON_INGOT, 1),
                10,
                4,
                6
            );
            OverlayMaterialRequirement stone = new OverlayMaterialRequirement(
                new ItemStack(Items.SMOOTH_STONE, 1),
                6,
                2,
                4
            );
            OverlayMaterialRequirement cobble = new OverlayMaterialRequirement(
                new ItemStack(Items.COBBLESTONE, 1),
                16,
                16,
                0
            );
            List<OverlayMaterialRequirement> mats = List.of(iron, stone, cobble);

            // Shortfall descending
            var byShortfall = ExecutionMaterialsPanelWidget.filterAndSort(
                mats,
                ExecutionMaterialsPanelWidget.FilterMode.all,
                ExecutionMaterialsPanelWidget.SortMode.shortfall,
                false,
                "",
                ExecutionStageModel.empty
            );
            assertEquals(6L, byShortfall.get(0).missing());
            assertEquals(0L, byShortfall.get(2).missing());

            // Missing-only filter
            var missingOnly = ExecutionMaterialsPanelWidget.filterAndSort(
                mats,
                ExecutionMaterialsPanelWidget.FilterMode.missing,
                ExecutionMaterialsPanelWidget.SortMode.shortfall,
                false,
                "",
                ExecutionStageModel.empty
            );
            assertEquals(2, missingOnly.size());

            // Ready-only filter
            var readyOnly = ExecutionMaterialsPanelWidget.filterAndSort(
                mats,
                ExecutionMaterialsPanelWidget.FilterMode.ready,
                ExecutionMaterialsPanelWidget.SortMode.required,
                false,
                "",
                ExecutionStageModel.empty
            );
            assertEquals(1, readyOnly.size());
            assertEquals(16L, readyOnly.get(0).required());

            // Required descending puts cobblestone (16) first
            var byRequired = ExecutionMaterialsPanelWidget.filterAndSort(
                mats,
                ExecutionMaterialsPanelWidget.FilterMode.all,
                ExecutionMaterialsPanelWidget.SortMode.required,
                false,
                "",
                ExecutionStageModel.empty
            );
            assertEquals(16L, byRequired.get(0).required());

            // Readiness ascending puts smooth stone (33%) before iron (40%) before cobble (100%)
            var byProgressAsc = ExecutionMaterialsPanelWidget.filterAndSort(
                mats,
                ExecutionMaterialsPanelWidget.FilterMode.all,
                ExecutionMaterialsPanelWidget.SortMode.progress,
                true,
                "",
                ExecutionStageModel.empty
            );
            assertEquals(4L, byProgressAsc.get(0).missing());
            assertEquals(6L, byProgressAsc.get(1).missing());
            assertEquals(0L, byProgressAsc.get(2).missing());

            // Substring search query
            var searchIron = ExecutionMaterialsPanelWidget.filterAndSort(
                mats,
                ExecutionMaterialsPanelWidget.FilterMode.all,
                ExecutionMaterialsPanelWidget.SortMode.name,
                true,
                "iron",
                ExecutionStageModel.empty
            );
            assertEquals(1, searchIron.size());
            assertEquals(10L, searchIron.get(0).required());
        } finally {
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }

    @Test
    void queueStoreSupportsMultiplePlansSwitchingAndRemoval() {
        CalculatorSyntheticScenario.Scenario s1 = CalculatorSyntheticScenario.createWorkbenchLike(4);
        CalculatorSyntheticScenario.Scenario s2 = CalculatorSyntheticScenario.createWorkbenchLike(5);

        ExecutionQueueStore.Entry first = ExecutionQueueStore.enqueue(s1.target(), 1, null, 3, true);
        ExecutionQueueStore.Entry second = ExecutionQueueStore.enqueue(s2.target(), 2, null, 4, false);
        assertEquals(2, ExecutionQueueStore.size());
        assertEquals(0, ExecutionQueueStore.activeIndex());
        ExecutionQueueStore.Entry activeAfterEnqueue = ExecutionQueueStore.activeEntry();
        assertNotNull(activeAfterEnqueue);
        assertEquals(first.id(), activeAfterEnqueue.id());

        ExecutionQueueStore.Entry switched = ExecutionQueueStore.selectNext();
        assertNotNull(switched);
        assertEquals(second.id(), switched.id());
        assertEquals(1, ExecutionQueueStore.activeIndex());

        ExecutionQueueStore.updateActiveStatus(ExecutionQueueStore.Status.paused, 5);
        ExecutionQueueStore.Entry activeAfterPause = ExecutionQueueStore.activeEntry();
        assertNotNull(activeAfterPause);
        assertEquals(ExecutionQueueStore.Status.paused, activeAfterPause.status());

        assertTrue(ExecutionQueueStore.remove(second.id()));
        assertEquals(1, ExecutionQueueStore.size());
        ExecutionQueueStore.Entry activeAfterRemove = ExecutionQueueStore.activeEntry();
        assertNotNull(activeAfterRemove);
        assertEquals(first.id(), activeAfterRemove.id());
    }

    @Test
    void executionUpdateTimesPreservesMultiStagePinnedPlanAndUpdatesQueueInPlace() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(2);
        RecipeEntry<?> targetRecipe = scenario.target();
        CompiledRecipeKey step1Key = new CompiledRecipeKey("minecraft:crafting", Optional.of("test:step_1"), 0L);
        CompiledRecipeKey step2Key = new CompiledRecipeKey("minecraft:crafting", Optional.of("test:final_item"), 0L);
        ProductionPlan multiStage = new ProductionPlan(
            "plan-multi",
            "balanced",
            List.of(
                new AcquireStep(
                    "a1",
                    List.of(),
                    ResourceKey.item("minecraft:oak_log"),
                    2L,
                    AcquisitionKind.manualGather,
                    CostVector.zero
                ),
                new CraftStep(
                    "c1",
                    List.of("a1"),
                    step1Key,
                    2L,
                    Optional.empty(),
                    false,
                    Map.of(),
                    Map.of(),
                    CostVector.zero
                ),
                new CraftStep(
                    "c2",
                    List.of("c1"),
                    step2Key,
                    1L,
                    Optional.empty(),
                    false,
                    Map.of(),
                    Map.of(),
                    CostVector.zero
                )
            ),
            PreparationSummary.empty,
            List.of(),
            CostVector.zero,
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );

        ExecutionQueueStore.enqueue(targetRecipe, 1, multiStage, 2, true);
        assertEquals(1, ExecutionQueueStore.size());

        ExecutionTargetRecipeState.Selection updated = ExecutionTargetRecipeState.updateTimes(4);
        assertNotNull(updated);
        assertEquals(4, updated.times());
        assertNotNull(updated.pinnedPlan());
        assertEquals(3, updated.pinnedPlan().steps().size());
        AcquireStep scaledAcquire = (AcquireStep) updated.pinnedPlan().steps().get(0);
        CraftStep scaledStep1 = (CraftStep) updated.pinnedPlan().steps().get(1);
        CraftStep scaledStep2 = (CraftStep) updated.pinnedPlan().steps().get(2);
        assertEquals(8L, scaledAcquire.amount());
        assertEquals(8L, scaledStep1.batches());
        assertEquals(4L, scaledStep2.batches());

        ExecutionQueueStore.syncActiveTimesAndPlan(updated.recipe(), updated.times(), updated.pinnedPlan());
        assertEquals(1, ExecutionQueueStore.size());
        ExecutionQueueStore.Entry active = ExecutionQueueStore.activeEntry();
        assertNotNull(active);
        assertEquals(4, active.times());
        assertEquals(updated.pinnedPlan(), active.pinnedPlan());
    }

    @Test
    void completedExecutionClearsCheckpointAndCloseArchivesToHistoryWithoutAutoReload() {
        ExecutionHistoryStore.clearAll();
        ExecutionCheckpointStore.clearAll();
        ExecutionQueueStore.clearAll();
        ExecutionTargetRecipeState.clear();
        try {
            CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(2);
            RecipeEntry<?> targetRecipe = scenario.target();
            ProductionPlan multiStage = new ProductionPlan(
                "plan-hist",
                "balanced",
                List.of(
                    new CraftStep(
                        "c1",
                        List.of(),
                        new CompiledRecipeKey("minecraft:crafting", Optional.of("test:step_1"), 0L),
                        2L,
                        Optional.empty(),
                        false,
                        Map.of(),
                        Map.of(),
                        CostVector.zero
                    ),
                    new CraftStep(
                        "c2",
                        List.of("c1"),
                        new CompiledRecipeKey("minecraft:crafting", Optional.of("test:final_item"), 0L),
                        1L,
                        Optional.empty(),
                        false,
                        Map.of(),
                        Map.of(),
                        CostVector.zero
                    )
                ),
                PreparationSummary.empty,
                List.of(),
                CostVector.zero,
                new VerificationCertificate(true, true, true, true, true, true, List.of())
            );

            ExecutionQueueStore.enqueue(targetRecipe, 1, multiStage, 2, true);
            ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.current();
            assertNotNull(selection);

            // Simulate mid-execution checkpoint, then completion: checkpoint must be cleared and never re-saved.
            ExecutionCheckpointStore.saveCheckpoint(selection, 3, 6, 1, 2);
            assertNotNull(ExecutionCheckpointStore.checkpointFor(selection));
            ExecutionCheckpointStore.markCompleted(selection);
            ExecutionCheckpointStore.saveCheckpoint(selection, 3, 6, 1, 2);
            assertNull(ExecutionCheckpointStore.checkpointFor(selection));

            // Archiving on close stores full path in history and clears active target so reopen does not auto-load.
            MainPanel panel = new MainPanel(handle());
            panel.archiveAndClearOnClose();
            assertNull(ExecutionTargetRecipeState.current());
            assertEquals(0, ExecutionQueueStore.size());
            assertEquals(1, ExecutionHistoryStore.size());
            ExecutionHistoryStore.Entry archived = ExecutionHistoryStore.entries().get(0);
            assertTrue(archived.completed());
            assertFalse(archived.resumable());
            assertEquals(multiStage, archived.pinnedPlan());

            // Restoring from history reloads full multi-stage plan without stale resume checkpoint.
            assertTrue(panel.restoreFromHistory(archived.id()));
            ExecutionTargetRecipeState.Selection restored = ExecutionTargetRecipeState.current();
            assertNotNull(restored);
            assertEquals(multiStage, restored.pinnedPlan());
            assertNull(ExecutionCheckpointStore.checkpointFor(restored));
        } finally {
            ExecutionHistoryStore.clearAll();
            ExecutionCheckpointStore.clearAll();
            ExecutionQueueStore.clearAll();
            ExecutionTargetRecipeState.clear();
        }
    }

    @Test
    void auditLogTracksDiagnosticsTelemetryAndSeverityFilters() {
        ExecutionAuditLog log = new ExecutionAuditLog();
        log.startSession();
        log.recordStepAdvance(4);
        log.recordStageCompleted(0, Component.literal("Stage 1 done"));
        log.setActiveDiagnostic(
            ExecutionAuditLog.Severity.error,
            ExecutionAuditLog.DiagnosticKind.inventoryFull,
            1,
            Component.literal("Inventory Full"),
            Component.literal("Free one slot")
        );

        assertNotNull(log.activeDiagnostic());
        assertEquals(1, log.errorAndWarnCount());
        assertEquals(1, log.events(ExecutionAuditLog.Severity.error).size());
        assertEquals(1, log.events(ExecutionAuditLog.Severity.info).size());
        assertEquals(2, log.events(null).size());
        assertTrue(log.estimateRemainingSeconds(10, 2.0d) > 0L);

        log.clearActiveDiagnostic();
        assertNull(log.activeDiagnostic());
        assertEquals("1m 5s", ExecutionAuditLog.formatDuration(65L));
    }

    @Test
    void overlayRootReleasesExclusionsImmediatelyAndTracksOpenAnimation() {
        ExecutionOverlayOpenState.setOpen(true);
        ExecutionOverlayRoot root = new ExecutionOverlayRoot(handle(), new Rect2i(6, 6, 260, 600), 1280, 720);
        assertEquals(1f, root.openAnimationProgress(), 0.01f);
        assertFalse(root.exclusionBounds().isEmpty());

        ExecutionOverlayOpenState.setOpen(false);
        assertTrue(root.exclusionBounds().isEmpty(), "closing cockpit must release JEI exclusion bounds immediately");
    }

    @Test
    void checkpointStoreTracksPerSelectionCheckpointsAndBreakpoints() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(4);
        ExecutionTargetRecipeState.Selection sel = ExecutionTargetRecipeState.set(scenario.target(), 2);

        ExecutionCheckpointStore.saveCheckpoint(sel, 5, 12, 1, 3);
        ExecutionCheckpointStore.Checkpoint cp = ExecutionCheckpointStore.checkpointFor(sel);
        assertNotNull(cp);
        assertTrue(cp.resumable());
        assertEquals(5, cp.stepIndex());

        assertTrue(ExecutionCheckpointStore.toggleBreakpoint(sel, 2));
        assertEquals(Set.of(2), ExecutionCheckpointStore.breakpointsFor(sel));
        assertFalse(ExecutionCheckpointStore.toggleBreakpoint(sel, 2));
        assertTrue(ExecutionCheckpointStore.breakpointsFor(sel).isEmpty());

        // Completing all steps auto-clears checkpoint
        ExecutionCheckpointStore.saveCheckpoint(sel, 12, 12, 2, 3);
        assertNull(ExecutionCheckpointStore.checkpointFor(sel));
    }

    @Test
    void cockpitDefaultsToDarkThemeEvenUnderCloudLibDefaultOverlayScene() {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        Widget child = root.addWidget(new Widget());
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 640;
            }

            @Override
            public int height() {
                return 360;
            }
        }));
        try {
            assertEquals(
                WorkbenchThemeMode.obsidianBlueprint,
                ExecutionCockpitTheme.mode(child),
                "Execution Cockpit on container overlay scenes must default to obsidianBlueprint (dark), not light fallback"
            );
        } finally {
            scene.destroy();
        }
    }

    @Test
    void diagnosticsDrawerExpandsUpwardWithoutOverflowingCompactMainPanel() {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(UIStyles.sizeOf(640, 360));
        MainPanel panel = new MainPanel(handle());
        panel.useStyle(
            UIStyles.positionAbsolute(),
            UIStyles.insetLeft(10),
            UIStyles.insetTop(10),
            UIStyles.sizeOf(220, 280)
        );
        root.addWidget(panel);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 640;
            }

            @Override
            public int height() {
                return 360;
            }
        }));
        try {
            scene.setLayoutArea(640, 360);
            scene.stabilize();

            var drawer = panel.diagnosticsDrawer();
            int collapsedY = drawer.posY();
            int collapsedBottom = drawer.posY() + drawer.height();
            assertEquals(dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionDiagnosticsDrawerWidget.collapsedHeight, drawer.height());
            assertTrue(collapsedBottom <= panel.height(), "collapsed drawer must stay inside MainPanel bottom");

            drawer.setExpanded(true);
            drawer.snapHeightToTarget();
            scene.stabilize();

            int expandedBottom = drawer.posY() + drawer.height();
            assertTrue(drawer.height() > dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionDiagnosticsDrawerWidget.collapsedHeight);
            assertTrue(
                drawer.posY() < collapsedY,
                "expanded drawer must rise upward (topY " + drawer.posY() + " < collapsedY " + collapsedY + ")"
            );
            assertTrue(
                expandedBottom <= panel.height(),
                "expanded drawer must never overflow below MainPanel bottom (" + expandedBottom + " <= " + panel.height() + ")"
            );
            assertEquals(
                collapsedBottom,
                expandedBottom,
                "drawer bottom edge must stay anchored flush with MainPanel bottom"
            );
            assertTrue(
                drawer.posY() >= panel.topFixedHeight(),
                "expanded drawer must stay below Header and Control Deck"
            );
        } finally {
            scene.destroy();
        }
    }

    @Test
    void mainPanelConsumesClicksInsideBoundsAndPassesOutsideClicks() {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(UIStyles.sizeOf(640, 360));
        MainPanel panel = new MainPanel(handle());
        panel.useStyle(
            UIStyles.positionAbsolute(),
            UIStyles.insetLeft(10),
            UIStyles.insetTop(10),
            UIStyles.sizeOf(220, 320)
        );
        root.addWidget(panel);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 640;
            }

            @Override
            public int height() {
                return 360;
            }
        }));
        try {
            scene.setLayoutArea(640, 360);
            scene.stabilize();
            scene.layout();
            root.applyLayout();
            assertEquals(220, panel.width());
            assertEquals(320, panel.height());
            assertTrue(
                scene.mouseClicked(100, 160, 0),
                "clicks inside Execution Cockpit must be consumed so CraftingScreen does not receive bleed-through clicks"
            );
            assertFalse(
                scene.mouseClicked(420, 160, 0),
                "clicks outside Execution Cockpit must pass through to CraftingScreen"
            );
        } finally {
            scene.destroy();
        }
    }

    @Test
    void noAccessibleProcessorDistinguishesMissingMaterialsAndOutputSyncFromWrongStation() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.createWorkbenchLike(2);
        MutableList<InteractionStep> steps = Lists.mutable.empty();
        appendStage(steps, scenario.target(), 1, Lists.immutable.empty(), Lists.immutable.empty());
        InteractionExecutionPlan plan = new InteractionExecutionPlan(scenario.target(), 1, steps.toImmutable());
        ExecutionFlowNode root = ExecutionFlowNode.pending(
            interactionId.toString(),
            Component.literal("PauseCheck"),
            plan.steps(),
            null,
            Lists.immutable.empty()
        );
        ExecutionSession base = ExecutionSession.start(plan, root);
        InteractionStep activeStep = plan.steps().get(1);

        PauseReason missingPlacements = new PauseReason(
            PauseReasons.noAccessibleProcessor,
            ExecutionGuidance
                    .simple(Component.translatable("cirrus.ui.execution.slot.pause.prepare.no_placements.summary"))
        );
        ExecutionSession missingSession = new ExecutionSession(
            base.plan(),
            base.flowRoot(),
            1,
            Sets.immutable.of(plan.steps().get(0)),
            Sets.immutable.of(activeStep),
            activeStep,
            missingPlacements,
            null,
            base.flowViewModel(),
            base.baselinePlanningSnapshot(),
            base.observedStockPolicy()
        );
        ExecutionStageModel missingModel = ExecutionStageModel.derive(missingSession, Set.of(), null);
        ExecutionStageModel.Stage missingStage = missingModel.activeStage();
        assertNotNull(missingStage);
        assertEquals(
            CirrusLangs.Execution.Cockpit.phasePausedMissing.get(),
            missingStage.phaseHint(),
            "no_placements must not be mislabeled as wrong station"
        );

        PauseReason waitingOutput = new PauseReason(
            PauseReasons.noAccessibleProcessor,
            ExecutionGuidance
                    .simple(Component.translatable("cirrus.ui.execution.slot.pause.execute.missing_output.summary"))
        );
        ExecutionSession outputSession = new ExecutionSession(
            base.plan(),
            base.flowRoot(),
            2,
            Sets.immutable.of(plan.steps().get(0), plan.steps().get(1)),
            Sets.immutable.of(plan.steps().get(2)),
            plan.steps().get(2),
            waitingOutput,
            null,
            base.flowViewModel(),
            base.baselinePlanningSnapshot(),
            base.observedStockPolicy()
        );
        ExecutionStageModel outputModel = ExecutionStageModel.derive(outputSession, Set.of(), null);
        ExecutionStageModel.Stage outputStage = outputModel.activeStage();
        assertNotNull(outputStage);
        assertEquals(
            CirrusLangs.Execution.Cockpit.flowWaitingOutput.get(),
            outputStage.phaseHint(),
            "missing_output must report output sync instead of wrong station"
        );
    }

    private static ExecutionDriverHandle handle() {
        return new ExecutionDriverHandle(lookup -> new MultiStageTestDriver(interactionId), DriverLookup.of());
    }

    private static void attachContentApi() {
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(builder);
        internal.registerContentVariant(builder);
        internal.registerContentResolver(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
    }

    private static void appendStage(
        MutableList<InteractionStep> steps,
        RecipeEntry<?> recipe,
        int times,
        ImmutableList<InputBinding> inputs,
        ImmutableList<OutputBinding> outputs
    ) {
        steps.add(
            new InteractionStep.OpenInteractionStep(
                ExecutionSideRequirement.clientSide,
                ExecutionCapability.interactionAccess,
                recipe,
                times,
                interactionId
            )
        );
        steps.add(
            new InteractionStep.PrepareInputsStep(
                ExecutionSideRequirement.clientSide,
                ExecutionCapability.inventoryTransfer,
                recipe,
                inputs
            )
        );
        steps.add(
            new InteractionStep.ExecuteRecipeStep(
                ExecutionSideRequirement.clientSide,
                ExecutionCapability.interactionPerform,
                recipe,
                times,
                inputs,
                outputs
            )
        );
        steps.add(
            new InteractionStep.CollectOutputsStep(
                ExecutionSideRequirement.clientSide,
                ExecutionCapability.inventoryTransfer,
                recipe,
                outputs
            )
        );
    }

    private static InputBinding in(Item item, long amount) {
        return new InputBinding(InputContent.fromItemStack(new ItemStack(item, 1), amount), amount);
    }

    private static OutputBinding out(Item item, long amount) {
        return new OutputBinding(OutputContent.fromItemStack(new ItemStack(item, 1), amount).asResource(), amount);
    }

    private static final class MultiStageTestDriver extends SlotExecutionDriver {
        MultiStageTestDriver(Namespace id) {
            super(id, SlotExecutionDriver.automaticInteraction(id), (ExecutionRuntime) null);
        }

        @Override
        public void initialize(ExecutionSession session) {
            RecipeEntry<?> target = session.plan().targetRecipe();
            MutableList<InteractionStep> steps = Lists.mutable.empty();
            for (int stage = 0; stage < 3; stage++) {
                appendStage(steps, target, 1, Lists.immutable.empty(), Lists.immutable.empty());
            }
            InteractionExecutionPlan multiPlan = new InteractionExecutionPlan(target, 1, steps.toImmutable());
            ExecutionFlowNode root = ExecutionFlowNode.pending(
                interactionId.toString(),
                Component.literal("MultiStage"),
                multiPlan.steps(),
                null,
                Lists.immutable.empty()
            );
            super.initialize(ExecutionSession.start(multiPlan, root));
        }
    }
}

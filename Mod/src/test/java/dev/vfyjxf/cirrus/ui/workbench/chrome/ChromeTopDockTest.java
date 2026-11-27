package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.coal;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.demand;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.ironIngot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromeTopDockTest {

    private static MachineRule machineRule(String ruleId) {
        return new MachineRule(ruleId, GuidanceScope.session, RuleStrength.softPrefer, "minecraft:furnace", true);
    }

    @Test
    void presetSelectionWritesAnObjectivePresetProfile() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        TopCommandDockWidget dock = new TopCommandDockWidget(ctx);
        ctx.publishGraph();
        PlanningProfile base = ctx.controller().state().profile();

        assertTrue(dock.selectPreset(ObjectivePreset.quickHandcraft));

        PlanningProfile profile = ctx.controller().state().profile();
        assertEquals(ObjectivePreset.quickHandcraft, profile.preset());
        assertEquals("preset_quickhandcraft", profile.profileId());
        assertEquals(CostWeights.forPreset(ObjectivePreset.quickHandcraft), profile.weights());
        assertEquals(base.diversityThreshold(), profile.diversityThreshold());
        assertEquals(base.maxMachineRecursionDepth(), profile.maxMachineRecursionDepth());
        assertEquals(ObjectivePreset.quickHandcraft, dock.currentPreset());
        assertEquals(
            ObjectivePreset.quickHandcraft,
            ctx.backend.requests.get(ctx.backend.requests.size() - 1).profile().preset()
        );
        assertFalse(dock.selectPreset(null));
        assertEquals(ObjectivePreset.quickHandcraft, ctx.controller().state().profile().preset());

        ChromeTestContext bare = new ChromeTestContext(1280f, 720f, List.of());
        bare.tabManager().closeAll();
        TopCommandDockWidget noController = new TopCommandDockWidget(bare);
        assertFalse(noController.selectPreset(ObjectivePreset.autoSmart));
    }

    @Test
    void undoAndRedoDisabledStateFollowsTheSnapshotHistory() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        TopCommandDockWidget dock = new TopCommandDockWidget(ctx);
        ctx.publishGraph();

        assertFalse(dock.canUndo());
        assertFalse(dock.canRedo());
        assertFalse(dock.undo());
        assertFalse(dock.redo());
        assertEquals(0, ctx.backend.undoCallCount());
        assertEquals(0, ctx.backend.redoCallCount());

        ctx.controller().refine(new ApplyRulesFeedback(List.of(machineRule("rule-undo")), false), null);
        assertTrue(dock.canUndo());
        assertFalse(dock.canRedo());
        assertEquals(1, ctx.backend.refineCalls());

        assertTrue(dock.undo());
        assertEquals(1, ctx.backend.undoCallCount());
        assertFalse(dock.canUndo());
        assertTrue(dock.canRedo());

        assertTrue(dock.redo());
        assertEquals(1, ctx.backend.redoCallCount());
        assertTrue(dock.canUndo());
        assertFalse(dock.canRedo());
    }

    @Test
    void sendButtonNeverDispatchesWithoutAFeasiblePlan() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        TopCommandDockWidget dock = new TopCommandDockWidget(ctx);

        assertFalse(dock.sendToExecution());
        assertEquals(0, ctx.sendExecutionCalls);
        assertEquals(CirrusLangs.Workbench.Flow.bannerUnreachable.key(), lastToast(ctx));

        ctx.publishGraph(request -> ChromeTestContext.result(PlanningStatus.feasible, List.of()));
        assertFalse(dock.sendToExecution());
        assertEquals(0, ctx.sendExecutionCalls);

        ctx.publishGraph();
        assertTrue(dock.sendToExecution());
        assertEquals(1, ctx.sendExecutionCalls);
        assertEquals(7L, dock.elapsedMillis());
    }

    @Test
    void basketToggleIsReachedThroughTheSceneLookup() {
        ChromeTestContext ctx = new ChromeTestContext();
        TopCommandDockWidget dock = new TopCommandDockWidget(ctx);
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);

        assertFalse(dock.openBasket(), "an unmounted dock cannot resolve its basket neighbour");

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(dock, basket)) {
            assertTrue(dock.openBasket());
            assertTrue(basket.isOpen());
            assertTrue(dock.openBasket());
            assertFalse(basket.isOpen());
        }
    }

    @Test
    void targetAmountsAndBasketEditsGoThroughSetTargets() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 5L)));
        TopCommandDockWidget dock = new TopCommandDockWidget(ctx);

        assertEquals(5L, dock.primaryAmount());
        assertTrue(dock.applyTargetAmount(7L));
        assertEquals(List.of(demand(ironIngot, 7L)), lastTargets(ctx));

        assertTrue(dock.applyTarget(coal, false));
        assertEquals(List.of(demand(coal, 1L)), lastTargets(ctx));

        assertTrue(dock.applyTarget(coal, true));
        assertEquals(List.of(demand(coal, 2L)), lastTargets(ctx));
        assertEquals(2L, dock.primaryAmount());

        assertFalse(dock.applyTarget(null, false));

        assertTrue(dock.liveStockToggle().toggled());
        assertTrue(ctx.controller().state().useLiveStock());
        assertTrue(dock.toggleLiveStock());
        assertFalse(dock.liveStockToggle().toggled());
        assertFalse(ctx.controller().state().useLiveStock());
        assertTrue(dock.toggleLiveStock());
        assertTrue(dock.liveStockToggle().toggled());
        assertTrue(ctx.controller().state().useLiveStock());
    }

    @Test
    void searchPreviewSurfacesPlanStepsAndFallsBackToThePalette() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        ctx.graph.add(ironIngot, coal);
        ctx.publishGraph();
        TopCommandDockWidget dock = new TopCommandDockWidget(ctx);

        List<PaletteItem> preview = dock.setSearchQuery("iron");
        assertFalse(preview.isEmpty());
        assertTrue(preview.size() <= TopCommandDockWidget.previewLimit);
        assertEquals("step:s001", preview.get(0).id());
        assertTrue(preview.get(0).primaryAction() instanceof PaletteAction.FocusStep);

        assertFalse(dock.activatePreview(-1));
        assertEquals(1, ctx.openPaletteCalls);

        assertTrue(dock.activatePreview(0));
        assertEquals(2, ctx.openPaletteCalls);

        assertTrue(dock.more());
        assertEquals(3, ctx.openPaletteCalls);
    }

    private static List<TargetDemand> lastTargets(ChromeTestContext ctx) {
        return ctx.targetCalls.get(ctx.targetCalls.size() - 1).targets();
    }

    private static String lastToast(ChromeTestContext ctx) {
        return ctx.toasts.get(ctx.toasts.size() - 1);
    }
}

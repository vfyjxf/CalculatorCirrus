package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchIntegrationProvider;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchSurfaces;
import dev.vfyjxf.cirrus.ui.workbench.WorkspaceProvider;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.coal;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.demand;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.goldIngot;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.ironIngot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Row editing, search append, the 180ms debounce and the five-axis weight apply for the two Z5 popovers.
 */
class ChromePopoverTest {

    private static ChromeTestContext basketContext() {
        return new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 5L), demand(coal, 2L)));
    }

    @Test
    void basketToggleShowsTheDraftRowsWithoutSubmitting() {
        ChromeTestContext ctx = basketContext();
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);

        assertEquals(2, basket.rows().size());
        assertEquals(5L, basket.amountAt(0));
        assertEquals(2L, basket.amountAt(1));
        assertEquals(0, ctx.targetCalls.size());
        assertFalse(basket.isOpen());
        assertFalse(basket.visible());

        basket.toggle();
        assertTrue(basket.isOpen());
        assertTrue(basket.visible());
        basket.toggle();
        assertFalse(basket.isOpen());
        assertFalse(basket.visible());

        basket.show();
        assertTrue(basket.isOpen());
        basket.hide();
        assertFalse(basket.isOpen());

        assertEquals(300, (int) TargetBasketPopoverWidget.width);
        assertEquals(280, (int) TargetBasketPopoverWidget.height);
        assertEquals(8, TargetBasketPopoverWidget.maxRows);
        assertEquals(180L, TargetBasketPopoverWidget.debounceMillis);
    }

    @Test
    void basketAmountEditsAreDebouncedForOneHundredEightyMilliseconds() {
        ChromeTestContext ctx = basketContext();
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        basket.show();

        assertTrue(basket.setAmount(1, 7L));
        assertEquals(7L, basket.amountAt(1));
        assertTrue(basket.pendingReplan());
        long remaining = basket.debounceRemainingMillis();
        assertTrue(remaining > 0L && remaining <= TargetBasketPopoverWidget.debounceMillis, "remaining=" + remaining);
        assertEquals(0, ctx.targetCalls.size(), "the debounce window must delay setTargets");

        assertFalse(basket.advance(0L));
        assertEquals(0, ctx.targetCalls.size());
        assertTrue(basket.advance(TargetBasketPopoverWidget.debounceMillis + 10L));
        assertEquals(1, ctx.targetCalls.size());
        assertEquals(List.of(demand(ironIngot, 5L), demand(coal, 7L)), lastTargets(ctx));
        assertFalse(basket.pendingReplan());
        assertEquals(0L, basket.debounceRemainingMillis());
        assertTrue(ctx.targetCalls.get(0).targets().equals(ctx.activeSnapshot().targets()));
    }

    @Test
    void basketMicroButtonsMultiplyAndDeleteRows() {
        ChromeTestContext ctx = basketContext();
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        basket.show();

        assertTrue(basket.setAmount(0, 3L));
        assertEquals(3L, basket.amountAt(0));
        assertTrue(basket.bumpSixteen(0));
        assertEquals(48L, basket.amountAt(0));
        assertTrue(basket.bumpSixtyFour(0));
        assertEquals(3072L, basket.amountAt(0));
        assertFalse(basket.multiplyAmount(0, 0L));
        assertFalse(basket.multiplyAmount(9, 16L));
        assertTrue(basket.setAmount(0, Long.MAX_VALUE));
        assertTrue(basket.bumpSixteen(0));
        assertEquals(Long.MAX_VALUE, basket.amountAt(0), "overflow must saturate instead of wrapping");

        assertTrue(basket.removeAt(1));
        assertEquals(1, basket.rows().size());
        assertFalse(basket.removeAt(5));
        assertTrue(basket.removeAt(0));
        assertTrue(basket.rows().isEmpty());

        assertFalse(basket.setAmount(0, 1L));
    }

    @Test
    void basketSearchAppendsAResourceTargetAndClearsTheField() {
        ChromeTestContext ctx = basketContext();
        ctx.graph.add(ironIngot, coal, goldIngot);
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        basket.show();

        List<PaletteItem> results = basket.setSearchQuery("gold");
        assertFalse(results.isEmpty());
        assertEquals(CommandPaletteEngine.resourceItemPrefix + goldIngot.canonicalKey(), results.get(0).id());
        assertTrue(results.get(0).primaryAction() instanceof PaletteAction.SetTarget);
        assertTrue(basket.searchResults().size() == results.size());

        assertTrue(basket.addSearchResult(0));
        assertEquals(3, basket.rows().size());
        assertEquals(goldIngot, basket.rows().get(2).resource());
        assertEquals(1L, basket.amountAt(2));
        assertEquals("", basket.searchText());
        assertTrue(basket.searchResults().isEmpty());
        assertFalse(basket.addSearchResult(7));
        assertFalse(basket.addSearchResult(-1));
        assertEquals(List.of(), basket.setSearchQuery("   "));
    }

    @Test
    void basketPlanNowCommitsImmediatelyAndRequestReplanWhenUnchanged() {
        ChromeTestContext ctx = basketContext();
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        basket.show();

        assertTrue(basket.setAmount(0, 9L));
        assertTrue(basket.pendingReplan());
        assertTrue(basket.planNow());
        assertEquals(1, ctx.targetCalls.size());
        assertEquals(List.of(demand(ironIngot, 9L), demand(coal, 2L)), lastTargets(ctx));
        assertFalse(basket.pendingReplan());

        assertEquals(0, ctx.requestReplanCalls);
        assertTrue(basket.planNow());
        assertEquals(1, ctx.requestReplanCalls, "an unchanged draft must ask the shell to replan");
        assertEquals(1, ctx.targetCalls.size());
    }

    @Test
    void basketGeometryKeepsEveryAffordanceInsideThreeHundredByTwoEighty() {
        ChromeTestContext ctx = basketContext();
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        basket.show();

        assertEquals(0, basket.rowAt(10.0, TargetBasketPopoverWidget.rowTop(0) + 2.0));
        assertEquals(1, basket.rowAt(10.0, TargetBasketPopoverWidget.rowTop(1) + 2.0));
        assertEquals(-1, basket.rowAt(400.0, 20.0));
        assertEquals(-1, basket.rowAt(10.0, 400.0));

        Rect number = basket.numberRect(0);
        Rect sixteen = basket.microSixteenRect(0);
        Rect sixtyFour = basket.microSixtyFourRect(0);
        Rect delete = basket.deleteRect(0);
        assertTrue(number.right() <= sixteen.x());
        assertTrue(sixteen.right() <= sixtyFour.x());
        assertTrue(sixtyFour.right() <= delete.x());
        assertTrue(delete.right() <= (int) TargetBasketPopoverWidget.width);
        assertTrue(basket.searchRect().y() > TargetBasketPopoverWidget.rowTop(TargetBasketPopoverWidget.maxRows - 1));
        assertTrue(basket.resultRect(0).y() > basket.searchRect().bottom());
        assertTrue(basket.hintRect().y() > basket.resultRect(TargetBasketPopoverWidget.resultLimit - 1).bottom());
        assertTrue(basket.planNowRect().y() > basket.hintRect().bottom());
        assertTrue(basket.planNowRect().bottom() <= (int) TargetBasketPopoverWidget.height);
        assertTrue(basket.closeRect().right() <= (int) TargetBasketPopoverWidget.width);
    }

    @Test
    void weightsPopoverLoadsFiveAxesAndAppliesAProfile() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        ctx.publishGraph();
        ProfileWeightsPopoverWidget weights = new ProfileWeightsPopoverWidget(ctx);

        assertEquals(5, ProfileWeightsPopoverWidget.axes);
        assertEquals(5, weights.sliders().size());
        assertEquals(
            List.of("H", "P", "K", "S", "C"),
            IntStream.range(0, ProfileWeightsPopoverWidget.axes).mapToObj(ProfileWeightsPopoverWidget::axisLabel)
                    .toList()
        );
        assertEquals(260, (int) ProfileWeightsPopoverWidget.width);
        assertEquals(220, (int) ProfileWeightsPopoverWidget.height);

        PlanningProfile base = ctx.controller().state().profile();
        for (int axis = 0; axis < ProfileWeightsPopoverWidget.axes; axis++) {
            assertFalse(weights.axisTouched(axis));
            assertEquals(ChromeProfiles.axisWeight(base.weights(), axis), weights.axisValue(axis), 1e-9);
        }

        assertFalse(weights.isOpen());
        weights.show();
        assertTrue(weights.isOpen());
        assertTrue(weights.visible());
        weights.toggle();
        assertFalse(weights.isOpen());
        weights.toggle();
        assertTrue(weights.isOpen());

        weights.refresh(new CostWeights(1.0, 0.15, 12.0, 1.0, 3.0));
        assertEquals(12.0, weights.axisValue(2), 1e-9);
        assertTrue(weights.axisOverflowsSlider(2));
        assertEquals(ProfileWeightsPopoverWidget.sliderMax, weights.axisSliderValue(2), 1e-9);
        assertFalse(weights.axisOverflowsSlider(0));

        assertTrue(weights.setAxisValue(0, 1.25));
        assertTrue(weights.axisTouched(0));
        assertEquals(1.25, weights.axisValue(0), 1e-9);
        assertEquals("1.25", weights.formattedValue(0));
        assertTrue(weights.setAxisValue(0, 9.0));
        assertEquals(ProfileWeightsPopoverWidget.sliderMax, weights.axisValue(0), 1e-9);
        assertFalse(weights.setAxisValue(0, Double.NaN));
        assertTrue(weights.dirty());

        CostWeights expected = new CostWeights(2.0, 0.15, 12.0, 1.0, 3.0);
        assertEquals(expected, weights.currentWeights());
        PlanningProfile built = weights.buildProfile();
        assertEquals(base.profileId(), built.profileId());
        assertEquals(base.preset(), built.preset());
        assertEquals(expected, built.weights());
        assertEquals(base.diversityThreshold(), built.diversityThreshold(), 1e-9);
        assertEquals(base.maxMachineRecursionDepth(), built.maxMachineRecursionDepth());

        assertTrue(weights.apply());
        assertFalse(weights.isOpen());
        assertFalse(weights.dirty());
        assertEquals(expected, ctx.controller().state().profile().weights());
        assertEquals(expected, ctx.backend.requests.get(ctx.backend.requests.size() - 1).profile().weights());

        weights.reset();
        assertFalse(weights.axisTouched(0));
        assertFalse(weights.dirty());
        assertEquals(2.0, weights.axisValue(0), 1e-9);
    }

    @Test
    void weightsApplyFailsWithoutAControllerAndGeometryStaysOrdered() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        ProfileWeightsPopoverWidget weights = new ProfileWeightsPopoverWidget(ctx);
        weights.show();

        Rect reset = weights.resetRect();
        Rect apply = weights.applyRect();
        assertTrue(reset.right() <= apply.x());
        assertTrue(apply.right() <= (int) ProfileWeightsPopoverWidget.width);
        for (int axis = 0; axis < ProfileWeightsPopoverWidget.axes; axis++) {
            Rect slider = weights.sliderRect(axis);
            assertTrue(slider.y() >= ProfileWeightsPopoverWidget.rowTop(axis));
            assertTrue(
                slider.bottom() <= ProfileWeightsPopoverWidget.rowTop(axis) + ProfileWeightsPopoverWidget.rowHeight
            );
            assertTrue(weights.valueRect(axis).x() > slider.right());
        }
        weights.reset();

        ctx.tabManager().closeAll();
        assertFalse(weights.apply(), "no active controller means no profile write");
    }

    @Test
    void popoversMountAndLayoutUnderANullFontHost() {
        ChromeTestContext ctx = basketContext();
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        ProfileWeightsPopoverWidget weights = new ProfileWeightsPopoverWidget(ctx);

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(basket, weights).layout()) {
            basket.show();
            weights.show();
            mounted.scene.layout();
            mounted.scene.stabilize();

            assertEquals(300, basket.width());
            assertEquals(280, basket.height());
            assertEquals(260, weights.width());
            assertEquals(220, weights.height());
            assertEquals(2, basket.visibleRowCount());
            assertTrue(basket.isOpen());
            assertTrue(weights.isOpen());
        }
    }

    @Test
    void basketMatchesSpacedResourceNamesAndCommitsDirtyDraftOnHide() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of());
        ctx.graph.add(ironIngot, coal, goldIngot);
        TargetBasketPopoverWidget basket = new TargetBasketPopoverWidget(ctx);
        basket.show();

        List<PaletteItem> results = basket.setSearchQuery("iron ingot");
        assertFalse(results.isEmpty());
        assertEquals(CommandPaletteEngine.resourceItemPrefix + ironIngot.canonicalKey(), results.get(0).id());
        assertTrue(basket.addSearchResult(0));
        assertTrue(basket.pendingReplan());

        basket.hide();
        assertFalse(basket.isOpen());
        assertEquals(1, ctx.targetCalls.size(), "hiding a dirty basket must commit pending target additions");
        assertEquals(List.of(demand(ironIngot, 1L)), lastTargets(ctx));
    }

    @Test
    void basketPopoverOverLeftDockReceivesClicksAtHigherZIndex() {
        ChromeTestContext.RecordingBackend backend = new ChromeTestContext.RecordingBackend(
            ChromeTestContext::defaultResult
        );
        SessionTabManager tabs = new SessionTabManager(
            state -> new PlannerWorkbenchController(state, backend, ChromeTestContext.requestSource(), Runnable::run)
        );
        WorkspaceProvider emptyWorkspaces = new WorkspaceProvider() {
            @Override
            public Widget plannerWorkspace(WorkbenchShellContext ctx) {
                return new WidgetGroup<>();
            }

            @Override
            public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
                return new WidgetGroup<>();
            }

            @Override
            public Widget settingsWorkspace(WorkbenchShellContext ctx) {
                return new WidgetGroup<>();
            }

            @Override
            public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
                return new WidgetGroup<>();
            }
        };
        WorkbenchShellWidget shell = new WorkbenchShellWidget(emptyWorkspaces, tabs, WorkbenchMode.planner);
        WorkbenchSurfaces surfaces = WorkbenchSurfaces.install(shell, new WorkbenchIntegrationProvider());
        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(shell)) {
            shell.applyViewport(1280f, 720f);
            mounted.scene.stabilize();
            mounted.scene.layout();
            mounted.root.applyLayout();

            TargetBasketPopoverWidget basket = shell.children().stream()
                    .filter(TargetBasketPopoverWidget.class::isInstance).map(TargetBasketPopoverWidget.class::cast)
                    .findFirst().orElse(null);
            assertNotNull(basket);
            basket.show();
            mounted.scene.stabilize();
            mounted.scene.layout();
            mounted.root.applyLayout();

            Rect basketBounds = basket.absoluteBounds();
            Rect leftDockBounds = surfaces.leftDock().absoluteBounds();
            assertTrue(basketBounds.x() < leftDockBounds.right(), "basket should overlap leftDock");

            assertSame(basket, mounted.scene.hitTest(basketBounds.x() + 24.0, basketBounds.y() + 60.0));

            Rect browse = basket.browseCatalogRect();
            double clickX = basketBounds.x() + browse.x() + browse.width() / 2.0;
            double clickY = basketBounds.y() + browse.y() + browse.height() / 2.0;
            assertTrue(clickX < leftDockBounds.right(), "browse button should sit over leftDock");
            assertTrue(mounted.scene.mouseClicked(clickX, clickY, 0));
            assertTrue(mounted.scene.mouseReleased(clickX, clickY, 0));
            assertFalse(basket.isOpen(), "clicking Catalog & Recipes should close target basket");
            assertTrue(shell.recipeAssistant().isOpen(), "clicking Catalog & Recipes should open recipe assistant");
        } finally {
            shell.shutdown();
        }
    }

    private static List<TargetDemand> lastTargets(ChromeTestContext ctx) {
        return ctx.targetCalls.get(ctx.targetCalls.size() - 1).targets();
    }
}

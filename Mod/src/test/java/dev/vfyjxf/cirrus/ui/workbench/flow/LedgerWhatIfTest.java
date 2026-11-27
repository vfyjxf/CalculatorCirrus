package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.AcquireRow;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.LedgerSortKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerWhatIfTest {

    private static final long debounce = PreparationLedgerWidget.whatIfDebounceMs;

    private static PreparationLedgerWidget widget(FlowTestFixtures.FakeHost host, InventorySnapshot base) {
        PreparationLedgerWidget widget = new PreparationLedgerWidget(host);
        widget.setInventorySource(() -> base);
        widget.refresh();
        return widget;
    }

    private static AcquireRow row(PreparationLedgerModel model, String resourceId) {
        for (AcquireRow candidate : model.acquireRows()) {
            if (candidate.resource().resourceId().equals(resourceId)) {
                return candidate;
            }
        }
        throw new AssertionError("missing acquire row for " + resourceId);
    }

    @Test
    void baseLedgerShortfallAndCoverageFollowTheInventory() {
        ProductionPlan plan = FlowTestFixtures.ledgerPlan();
        PreparationLedgerWidget widget = widget(
            new FlowTestFixtures.FakeHost(plan),
            FlowTestFixtures.ledgerInventory()
        );
        PreparationLedgerModel model = Objects.requireNonNull(widget.model());
        assertNotNull(model);

        AcquireRow iron = row(model, "minecraft:iron_ore");
        assertEquals(64L, iron.neededAmount());
        assertEquals(16L, iron.availableStock());
        assertEquals(4L, iron.reservedStock());
        assertEquals(12L, iron.usableStock());
        assertEquals(52L, iron.shortfall());
        assertEquals(0.125, model.stockCoverageRatio(), 1e-9);
        assertEquals(1, model.machinesToProvision().size());
        assertEquals(List.of("s003"), model.machinesToProvision().get(0).stepIds());
        assertEquals(1, model.surplusByproducts().size());
    }

    @Test
    void whatIfSliderDispatchesOverrideStockAfterDebounce() {
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan());
        PreparationLedgerWidget widget = widget(host, FlowTestFixtures.ledgerInventory());
        long[] now = {1_000L};
        widget.setClock(() -> now[0]);

        widget.simulateStock(FlowTestFixtures.ironOre, 64L);
        assertEquals(0, widget.flushDueOverrides(now[0]), "the 180ms debounce window is still open");
        now[0] += debounce - 1L;
        assertEquals(0, widget.flushDueOverrides(now[0]));
        now[0] += 1L;
        assertEquals(1, widget.flushDueOverrides(now[0]));

        assertEquals(1, host.dispatched().size());
        assertTrue(host.dispatched().get(0) instanceof OverrideStockFeedback);
        OverrideStockFeedback feedback = (OverrideStockFeedback) host.dispatched().get(0);
        assertEquals(FlowTestFixtures.ironOre, feedback.resource());
        assertEquals(64L, feedback.newAvailableAmount());
        assertEquals(1, widget.dispatchCount());
        assertEquals(0, widget.pendingCount());
    }

    @Test
    void repeatedDragsCoalesceIntoOneOverride() {
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan());
        PreparationLedgerWidget widget = widget(host, FlowTestFixtures.ledgerInventory());
        long[] now = {500L};
        widget.setClock(() -> now[0]);

        widget.simulateStock(FlowTestFixtures.ironOre, 32L);
        now[0] += 60L;
        widget.simulateStock(FlowTestFixtures.ironOre, 48L);
        now[0] += 60L;
        widget.simulateStock(FlowTestFixtures.ironOre, 64L);

        assertEquals(0, widget.flushDueOverrides(now[0] - 1L));
        now[0] += debounce;
        assertEquals(1, widget.flushDueOverrides(now[0]));

        assertEquals(1, host.dispatched().size());
        assertTrue(host.dispatched().get(0) instanceof OverrideStockFeedback);
        OverrideStockFeedback feedback = (OverrideStockFeedback) host.dispatched().get(0);
        assertEquals(64L, feedback.newAvailableAmount(), "the last drag value wins");
    }

    @Test
    void simulatedStockShrinksTheWorldAcquireRow() {
        ProductionPlan plan = FlowTestFixtures.ledgerPlan();
        PreparationLedgerWidget widget = widget(
            new FlowTestFixtures.FakeHost(plan),
            FlowTestFixtures.ledgerInventory()
        );
        AcquireRow baseRow = row(Objects.requireNonNull(widget.model()), "minecraft:iron_ore");
        assertEquals(52L, baseRow.shortfall());

        widget.simulateStock(FlowTestFixtures.ironOre, 64L);

        InventorySnapshot simulated = widget.simulatedInventory();
        assertEquals(64L, simulated.available().amountOf(FlowTestFixtures.ironOre));
        assertEquals(60L, simulated.usableAmount(FlowTestFixtures.ironOre), "4 reserved units stay reserved");
        assertEquals(4L, LedgerSorting.effectiveShortfall(baseRow, simulated));

        PreparationLedgerModel overlaid = PreparationLedgerModel
                .build(plan, simulated, LedgerSortKey.missingFirst, true, null);
        AcquireRow iron = row(overlaid, "minecraft:iron_ore");
        assertEquals(64L, iron.availableStock());
        assertEquals(4L, iron.shortfall(), "the world acquire row shrinks from 52 to 4");
        assertTrue(overlaid.stockCoverageRatio() > 0.6, "coverage rises with simulated stock");
    }

    @Test
    void simulationNeverTouchesThePlayerInventory() {
        InventorySnapshot base = FlowTestFixtures.ledgerInventory();
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan());
        PreparationLedgerWidget widget = widget(host, base);

        widget.simulateStock(FlowTestFixtures.ironOre, 200L);
        widget.flushDueOverrides(Long.MAX_VALUE);

        assertEquals(16L, base.available().amountOf(FlowTestFixtures.ironOre), "base snapshot is immutable");
        assertEquals(4L, base.reserved().amountOf(FlowTestFixtures.ironOre));
        assertEquals(8L, base.available().amountOf(FlowTestFixtures.ironIngot));
        assertNotSame(base, widget.simulatedInventory());
        assertEquals(200L, widget.simulatedInventory().available().amountOf(FlowTestFixtures.ironOre));

        for (HumanFeedback feedback : host.dispatched()) {
            assertTrue(feedback instanceof OverrideStockFeedback, "what-if only ever dispatches a stock override");
        }
        assertEquals(FlowTestFixtures.ironOre, widget.simulatedStock().keySet().iterator().next());
    }

    @Test
    void simulatedAmountClampsToTheSupportedWindow() {
        PreparationLedgerWidget widget = widget(
            new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan()),
            FlowTestFixtures.ledgerInventory()
        );
        widget.simulateStock(FlowTestFixtures.coal, -5L);
        assertEquals(0L, widget.simulatedInventory().usableAmount(FlowTestFixtures.coal));
        widget.simulateStock(FlowTestFixtures.coal, Long.MAX_VALUE);
        assertEquals(
            LedgerSorting.maxSimulatedAmount,
            widget.simulatedInventory().available().amountOf(FlowTestFixtures.coal)
        );
    }

    @Test
    void sortKeysCycleAndQueryFiltersThroughTheModel() {
        PreparationLedgerWidget widget = widget(
            new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan()),
            FlowTestFixtures.ledgerInventory()
        );

        assertEquals(LedgerSortKey.missingFirst, widget.sortKey());
        LedgerSortKey current = widget.sortKey();
        for (int step = 0; step < LedgerSorting.sortKeyCount; step++) {
            current = LedgerSorting.nextKey(current);
        }
        assertEquals(LedgerSortKey.missingFirst, current, "5 keys cycle back to the first one");

        widget.selectSortKey(LedgerSortKey.amount);
        assertEquals(LedgerSortKey.amount, widget.sortKey());
        assertTrue(widget.ascending());
        widget.selectSortKey(LedgerSortKey.amount);
        assertFalse(widget.ascending(), "clicking the active key flips the direction");

        widget.setQuery("coal");
        assertEquals(1, Objects.requireNonNull(widget.model()).acquireRows().size());
        assertEquals(
            "minecraft:coal",
            Objects.requireNonNull(widget.model()).acquireRows().get(0).resource().resourceId()
        );
        widget.setQuery("");
        assertEquals(2, Objects.requireNonNull(widget.model()).acquireRows().size());
    }

    @Test
    void rowsStayInModelOrder() {
        PreparationLedgerWidget widget = widget(
            new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan()),
            FlowTestFixtures.ledgerInventory()
        );
        List<String> modelOrder = Objects.requireNonNull(widget.model()).acquireRows().stream()
                .map(row -> row.resource().resourceId()).toList();
        assertEquals(
            List.of("minecraft:iron_ore", "minecraft:coal"),
            modelOrder,
            "missing-first keeps the largest shortfall on top"
        );

        PreparationLedgerModel rebuilt = PreparationLedgerModel.build(
            FlowTestFixtures.ledgerPlan(),
            widget.simulatedInventory(),
            LedgerSortKey.missingFirst,
            true,
            null
        );
        assertEquals(modelOrder.get(0), rebuilt.acquireRows().get(0).resource().resourceId());
    }

    @Test
    void sectionLayoutSwitchesBetweenGridAndTabs() {
        assertEquals(2, LedgerSorting.sectionColumns(WorkbenchBreakpoint.wide));
        assertEquals(1, LedgerSorting.sectionColumns(WorkbenchBreakpoint.standard));
        assertEquals(1, LedgerSorting.sectionColumns(WorkbenchBreakpoint.compact));
        assertFalse(LedgerSorting.sectionsAsTabs(WorkbenchBreakpoint.wide));
        assertTrue(LedgerSorting.sectionsAsTabs(WorkbenchBreakpoint.standard));
        assertTrue(LedgerSorting.sectionsAsTabs(WorkbenchBreakpoint.compact));
    }

    @Test
    void machineAndResourceActionsDispatchTheDocumentedFeedbacks() {
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan());
        PreparationLedgerWidget widget = widget(host, FlowTestFixtures.ledgerInventory());

        widget.declareOwned(FlowTestFixtures.furnace);
        widget.rejectMachine(FlowTestFixtures.furnace);
        widget.declareFreeSource(FlowTestFixtures.ironOre);
        widget.focusStep("s001");

        assertEquals(3, host.dispatched().size());
        assertTrue(host.dispatched().get(0) instanceof DeclareOwnedMachineFeedback);
        DeclareOwnedMachineFeedback owned = (DeclareOwnedMachineFeedback) host.dispatched().get(0);
        assertEquals(FlowTestFixtures.furnace, owned.machineId());
        assertEquals(1, owned.ownedCount());
        assertFalse(owned.persistToWorld());

        assertTrue(host.dispatched().get(1) instanceof ApplyRulesFeedback);
        ApplyRulesFeedback banned = (ApplyRulesFeedback) host.dispatched().get(1);
        assertTrue(banned.rules().get(0) instanceof MachineRule);
        MachineRule machineRule = (MachineRule) banned.rules().get(0);
        assertEquals(RuleStrength.hardBan, machineRule.strength());
        assertEquals(FlowTestFixtures.furnace, machineRule.machineId());
        assertFalse(machineRule.willingToBuildNew());

        assertTrue(host.dispatched().get(2) instanceof ApplyRulesFeedback);
        ApplyRulesFeedback free = (ApplyRulesFeedback) host.dispatched().get(2);
        assertTrue(free.rules().get(0) instanceof ResourceRule);
        ResourceRule resourceRule = (ResourceRule) free.rules().get(0);
        assertEquals(RuleStrength.hardPin, resourceRule.strength());
        assertTrue(resourceRule.treatAsFreeBaseSource());
        assertEquals(List.of("s001"), host.focusedSteps());
    }

    @Test
    void stackBreakdownAndSliderRangeUsePlainNumbers() {
        assertEquals(2L, LedgerSorting.groupCount(140L));
        assertEquals(12L, LedgerSorting.groupRemainder(140L));
        AcquireRow row = row(
            Objects.requireNonNull(
                widget(new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan()), FlowTestFixtures.ledgerInventory())
                        .model()
            ),
            "minecraft:iron_ore"
        );
        assertEquals(128L, LedgerSorting.sliderMax(row));
        assertEquals(0.0, LedgerSorting.costShare(null));
        assertTrue(LedgerSorting.costShare(row) >= 0.0);
        assertEquals(1L, LedgerSorting.sliderMax(null));
    }

    @Test
    void sectionsSwitchAndScrollClampOnTheWidget() {
        PreparationLedgerWidget widget = widget(
            new FlowTestFixtures.FakeHost(FlowTestFixtures.ledgerPlan()),
            FlowTestFixtures.ledgerInventory()
        );
        assertEquals(0, widget.activeSection());
        widget.selectSection(2);
        assertEquals(2, widget.activeSection());
        widget.selectSection(9);
        assertEquals(2, widget.activeSection(), "out-of-range sections are ignored");
        assertTrue(widget.gridMode());
        widget.setGridMode(false);
        assertFalse(widget.gridMode());
    }

    @Test
    void ledgerAutoPopulatesWhenPlanArrivesAfterInitialEmptyMount() {
        FlowTestFixtures.FakeHost host = new FlowTestFixtures.FakeHost(
            FlowTestFixtures.emptySnapshot(),
            CirrusPalette.obsidianBlueprint
        );
        PreparationLedgerWidget widget = widget(host, FlowTestFixtures.ledgerInventory());
        assertTrue(Objects.requireNonNull(widget.model()).acquireRows().isEmpty());

        host.setSnapshot(FlowTestFixtures.snapshot(FlowTestFixtures.ledgerPlan()));
        widget.tick();

        PreparationLedgerModel populated = Objects.requireNonNull(widget.model());
        assertEquals(2, populated.acquireRows().size());
        assertEquals(1, populated.machinesToProvision().size());
    }
}

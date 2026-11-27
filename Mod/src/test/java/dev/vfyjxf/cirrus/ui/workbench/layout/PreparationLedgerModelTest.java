package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.AcquireRow;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.LedgerSortKey;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.MachinePrepRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparationLedgerModelTest {

    private static final double epsilon = 1e-9;

    private static final ProductionPlan plan = WorkbenchLayoutFixtures.sevenTypePlan();
    private static final InventorySnapshot inventory = WorkbenchLayoutFixtures.inventory();

    private static PreparationLedgerModel build(LedgerSortKey key, boolean ascending, String query) {
        return PreparationLedgerModel.build(plan, inventory, key, ascending, query);
    }

    @Test
    void acquirePartitionIsExact() {
        PreparationLedgerModel model = build(LedgerSortKey.missingFirst, true, "");
        List<AcquireRow> rows = model.acquireRows();
        assertEquals(2, rows.size(), "iron ore and coal need world gathering");

        AcquireRow ore = rows.get(0);
        assertEquals(WorkbenchLayoutFixtures.ironOre, ore.resource());
        assertEquals(64L, ore.neededAmount());
        assertEquals(16L, ore.availableStock());
        assertEquals(4L, ore.reservedStock());
        assertEquals(12L, ore.usableStock());
        assertEquals(52L, ore.shortfall());
        assertEquals(4.0 / 6.7, ore.costShare(), 1e-9);
        assertEquals(List.of("s001"), ore.stepIds());

        AcquireRow coal = rows.get(1);
        assertEquals(WorkbenchLayoutFixtures.coal, coal.resource());
        assertEquals(32L, coal.neededAmount());
        assertEquals(0L, coal.availableStock());
        assertEquals(0L, coal.usableStock());
        assertEquals(2.0 / 6.7, coal.costShare(), 1e-9);
        assertEquals(List.of("s002"), coal.stepIds());
    }

    @Test
    void stockMachineLineAndByproductPartitionsAreExact() {
        PreparationLedgerModel model = build(LedgerSortKey.missingFirst, true, "");

        assertEquals(1, model.stockConsumedRows().size());
        AcquireRow consumed = model.stockConsumedRows().get(0);
        assertEquals(WorkbenchLayoutFixtures.ironIngot, consumed.resource());
        assertEquals(8L, consumed.neededAmount(), "consumedFromInventory amount");
        assertEquals(40L, consumed.availableStock());
        assertEquals(0L, consumed.costShare(), epsilon, "ingots are not acquired directly");
        assertTrue(consumed.stepIds().isEmpty());

        assertEquals(1, model.machinesToProvision().size());
        MachinePrepRow machine = model.machinesToProvision().get(0);
        assertEquals("minecraft:furnace", machine.machineId());
        assertEquals(2, machine.countToBuild());
        assertEquals(List.of("s003"), machine.stepIds());

        assertEquals(Set.of("line:pressing"), model.automatedLinesUsed());
        assertEquals(Set.of(WorkbenchLayoutFixtures.diamond), model.catalystsRequired());

        List<ByproductEntry> surplus = model.surplusByproducts();
        assertEquals(2, surplus.size());
        assertEquals(WorkbenchLayoutFixtures.cogwheel, surplus.get(0).resource());
        assertEquals(WorkbenchLayoutFixtures.shaft, surplus.get(1).resource());
        assertEquals("s009", surplus.get(0).producedByStepId());
    }

    @Test
    void stockCoverageRatioIsBoundedAndFinite() {
        PreparationLedgerModel model = build(LedgerSortKey.missingFirst, true, "");
        assertEquals(12.0 / 96.0, model.stockCoverageRatio(), 1e-9);
        assertTrue(model.stockCoverageRatio() >= 0.0 && model.stockCoverageRatio() <= 1.0);
        assertTrue(Double.isFinite(model.stockCoverageRatio()));

        PreparationLedgerModel fullyStocked = PreparationLedgerModel.build(
            plan,
            new InventorySnapshot(
                new StockLedger(Map.of(WorkbenchLayoutFixtures.ironOre, 64L, WorkbenchLayoutFixtures.coal, 32L)),
                StockLedger.empty
            ),
            LedgerSortKey.missingFirst,
            true,
            ""
        );
        assertEquals(1.0, fullyStocked.stockCoverageRatio(), epsilon);

        ProductionPlan noAcquire = WorkbenchLayoutFixtures.plan(
            "plan-no-acquire",
            List.of(
                WorkbenchLayoutFixtures
                        .simpleCraft("x1", List.of(), WorkbenchLayoutFixtures.cost(1.0, 0.0), Optional.empty())
            ),
            PreparationSummary.empty,
            List.of()
        );
        assertEquals(
            0.0,
            PreparationLedgerModel.build(noAcquire, InventorySnapshot.empty, LedgerSortKey.missingFirst, true, "")
                    .stockCoverageRatio(),
            epsilon
        );
        assertTrue(
            Double.isFinite(
                PreparationLedgerModel.build(noAcquire, InventorySnapshot.empty, LedgerSortKey.missingFirst, true, "")
                        .stockCoverageRatio()
            )
        );
    }

    @Test
    void sixSortOrdersAreDeterministic() {
        assertFirst(LedgerSortKey.missingFirst, true, WorkbenchLayoutFixtures.ironOre);
        assertFirst(LedgerSortKey.missingFirst, false, WorkbenchLayoutFixtures.coal);
        assertFirst(LedgerSortKey.availableFirst, true, WorkbenchLayoutFixtures.ironOre);
        assertFirst(LedgerSortKey.availableFirst, false, WorkbenchLayoutFixtures.coal);
        assertFirst(LedgerSortKey.amount, true, WorkbenchLayoutFixtures.coal);
        assertFirst(LedgerSortKey.amount, false, WorkbenchLayoutFixtures.ironOre);
        assertFirst(LedgerSortKey.name, true, WorkbenchLayoutFixtures.coal);
        assertFirst(LedgerSortKey.name, false, WorkbenchLayoutFixtures.ironOre);
        assertFirst(LedgerSortKey.id, true, WorkbenchLayoutFixtures.coal);
        assertFirst(LedgerSortKey.id, false, WorkbenchLayoutFixtures.ironOre);
    }

    @Test
    void sortingIsStableAcrossRepeatedBuilds() {
        for (LedgerSortKey key : LedgerSortKey.values()) {
            for (boolean ascending : new boolean[]{true, false}) {
                PreparationLedgerModel first = build(key, ascending, "");
                PreparationLedgerModel second = build(key, ascending, "");
                assertEquals(first.acquireRows(), second.acquireRows(), key + " ascending=" + ascending);
                assertEquals(first.stockConsumedRows(), second.stockConsumedRows(), key + " ascending=" + ascending);
                assertEquals(first.stockCoverageRatio(), second.stockCoverageRatio(), 0.0);
            }
        }
    }

    @Test
    void queryFiltersByResourceNameCaseInsensitively() {
        PreparationLedgerModel iron = build(LedgerSortKey.name, true, "IRON");
        assertEquals(1, iron.acquireRows().size());
        assertEquals(WorkbenchLayoutFixtures.ironOre, iron.acquireRows().get(0).resource());

        PreparationLedgerModel blank = build(LedgerSortKey.name, true, "   ");
        assertEquals(2, blank.acquireRows().size(), "blank query keeps every row");

        PreparationLedgerModel none = build(LedgerSortKey.name, true, "diamond");
        assertTrue(none.acquireRows().isEmpty(), "catalysts live in their own partition");
        assertEquals(12.0 / 96.0, none.stockCoverageRatio(), 1e-9, "coverage stays a plan-level metric");
    }

    @Test
    void emptyPlanYieldsEmptyModelWithoutNaN() {
        PreparationLedgerModel model = PreparationLedgerModel.build(
            WorkbenchLayoutFixtures.plan("plan-empty", List.of(), PreparationSummary.empty, List.of()),
            inventory,
            LedgerSortKey.missingFirst,
            true,
            ""
        );
        assertTrue(model.acquireRows().isEmpty());
        assertTrue(model.stockConsumedRows().isEmpty());
        assertTrue(model.machinesToProvision().isEmpty());
        assertTrue(model.automatedLinesUsed().isEmpty());
        assertTrue(model.catalystsRequired().isEmpty());
        assertTrue(model.surplusByproducts().isEmpty());
        assertEquals(0.0, model.stockCoverageRatio(), 0.0);
        assertTrue(Double.isFinite(model.stockCoverageRatio()));
    }

    @Test
    void coverageRatioIsClampedToUnitInterval() {
        PreparationLedgerModel model = PreparationLedgerModel.build(
            plan,
            new InventorySnapshot(
                new StockLedger(Map.of(WorkbenchLayoutFixtures.ironOre, 512L, WorkbenchLayoutFixtures.coal, 512L)),
                StockLedger.empty
            ),
            LedgerSortKey.missingFirst,
            true,
            ""
        );
        assertTrue(model.stockCoverageRatio() <= 1.0);
    }

    private static void assertFirst(LedgerSortKey key, boolean ascending, ResourceKey expected) {
        PreparationLedgerModel model = build(key, ascending, "");
        AcquireRow first = model.acquireRows().get(0);
        assertNotNull(first);
        assertEquals(expected, first.resource(), key + " ascending=" + ascending);
        assertEquals(
            model.acquireRows(),
            build(key, ascending, "").acquireRows(),
            key + " ascending=" + ascending + " must be deterministic"
        );
    }
}

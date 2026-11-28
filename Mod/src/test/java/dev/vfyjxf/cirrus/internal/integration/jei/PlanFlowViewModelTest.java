package dev.vfyjxf.cirrus.internal.integration.jei;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanFlowViewModelTest {

    @BeforeEach
    void resetStore() {
        CirrusPlanBookmarkStore.resetForTests();
    }

    private static CirrusPlanBookmarkStore.PlanBookmarkEntry richEntry() {
        ComponentStackKey ore = OfflineContentTypes.item("minecraft:iron_ore");
        ComponentStackKey coal = OfflineContentTypes.item("minecraft:coal");
        ComponentStackKey ingot = OfflineContentTypes.item("minecraft:iron_ingot");
        ComponentStackKey plate = OfflineContentTypes.item("minecraft:iron_block");
        ComponentStackKey mold = OfflineContentTypes.item("minecraft:bucket");
        ComponentStackKey slag = OfflineContentTypes.item("minecraft:cobblestone");

        CirrusPlanBookmarkStore.StepLine s1 = new CirrusPlanBookmarkStore.StepLine(
            "s1",
            "AcquireStep",
            ore,
            16L,
            16L,
            null,
            false,
            2.0,
            0.0,
            List.of(),
            List.of(),
            List.of()
        );
        CirrusPlanBookmarkStore.StepLine s2 = new CirrusPlanBookmarkStore.StepLine(
            "s2",
            "AcquireStep",
            coal,
            4L,
            4L,
            null,
            false,
            1.0,
            0.0,
            List.of(),
            List.of(),
            List.of()
        );
        CirrusPlanBookmarkStore.StepLine s3 = new CirrusPlanBookmarkStore.StepLine(
            "s3",
            "CraftStep",
            ingot,
            16L,
            16L,
            OfflineContentTypes.machine("minecraft:blast_furnace"),
            false,
            1.5,
            16.0,
            List.of("s1", "s2"),
            List.of(ore, coal),
            List.of(
                new CirrusPlanBookmarkStore.MaterialLine(ore, 16L),
                new CirrusPlanBookmarkStore.MaterialLine(coal, 4L)
            )
        );
        CirrusPlanBookmarkStore.StepLine s4 = new CirrusPlanBookmarkStore.StepLine(
            "s4",
            "CraftStep",
            plate,
            2L,
            2L,
            null,
            false,
            2.0,
            0.0,
            List.of("s3"),
            List.of(ingot),
            List.of(new CirrusPlanBookmarkStore.MaterialLine(ingot, 18L))
        );

        return new CirrusPlanBookmarkStore.PlanBookmarkEntry(
            "rich-1",
            plate,
            2L,
            "Iron Block",
            4,
            2,
            6.5,
            16.0,
            List.of(
                new CirrusPlanBookmarkStore.MaterialLine(ore, 16L),
                new CirrusPlanBookmarkStore.MaterialLine(coal, 4L)
            ),
            List.of(s1, s2, s3, s4),
            Set.of("s2"),
            List.of(new CirrusPlanBookmarkStore.MachineLine(
                OfflineContentTypes.machine("minecraft:blast_furnace"),
                1
            )),
            List.of(mold),
            List.of(new CirrusPlanBookmarkStore.MaterialLine(slag, 3L)),
            List.of("line_ore_wash"),
            null
        );
    }

    @Test
    void dagLayoutComputesNonOverlappingHorizontalAndVerticalLayouts() {
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = richEntry();
        PlanFlowViewModel.DagLayout lr = PlanFlowViewModel.computeDagLayout(entry.steps(), false);
        PlanFlowViewModel.DagLayout tb = PlanFlowViewModel.computeDagLayout(entry.steps(), true);

        assertEquals(4, lr.nodes().size());
        assertEquals(3, lr.stageCount());
        assertEquals(3, lr.edges().size());
        assertFalse(lr.vertical());
        assertTrue(tb.vertical());
        assertNoNodeOverlap(lr.nodes());
        assertNoNodeOverlap(tb.nodes());
    }

    @Test
    void materialDockMergesFilteringSortingScalingAndStepProgress() {
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = richEntry();
        InventorySnapshot inv = new InventorySnapshot(
            new StockLedger(Map.of(
                OfflineContentTypes.item("minecraft:coal"),
                10L,
                OfflineContentTypes.item("minecraft:iron_ore"),
                4L
            )),
            StockLedger.empty
        );

        PlanFlowViewModel.MaterialDockModel scaled = PlanFlowViewModel.buildMaterials(
            entry,
            inv,
            2,
            PlanFlowViewModel.MaterialFilter.all,
            PlanFlowViewModel.MaterialSort.missingFirst
        );
        assertEquals(2, scaled.totalMaterials());
        assertEquals(1, scaled.readyMaterials());
        assertEquals(1, scaled.missingMaterials());
        assertEquals(OfflineContentTypes.item("minecraft:iron_ore"), scaled.visibleMaterials().get(0).resource());
        assertEquals(32L, scaled.visibleMaterials().get(0).needed());
        assertEquals(28L, scaled.visibleMaterials().get(0).missing());
        assertEquals(1, scaled.checkedSteps());
        assertEquals(4, scaled.totalSteps());

        PlanFlowViewModel.MaterialDockModel missingOnly = PlanFlowViewModel.buildMaterials(
            entry,
            inv,
            2,
            PlanFlowViewModel.MaterialFilter.missing,
            PlanFlowViewModel.MaterialSort.missingFirst
        );
        assertEquals(1, missingOnly.visibleMaterials().size());
        assertEquals(OfflineContentTypes.item("minecraft:iron_ore"), missingOnly.visibleMaterials().get(0).resource());
    }

    @Test
    void stationsPrepAggregatesMachinesToBuildCatalystsAndByproducts() {
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = richEntry();
        PlanFlowViewModel.StationsPrepSummary prep = PlanFlowViewModel.buildStationsPrep(entry, 2);

        assertEquals(2, prep.stations().size());
        PlanFlowViewModel.StationGroup first = prep.stations().get(0);
        assertEquals(OfflineContentTypes.machine("minecraft:blast_furnace"), first.machine());
        assertEquals(1, first.toBuildCount());
        assertEquals(32L, first.totalBatches());
        assertEquals(32.0, first.totalWaitSeconds(), 1e-6);

        PlanFlowViewModel.StationGroup second = prep.stations().get(1);
        assertTrue(second.handCraft());
        assertEquals(4L, second.totalBatches());
        assertEquals(1, prep.catalysts().size());
        assertEquals(1, prep.surplusByproducts().size());
        assertEquals(6L, prep.surplusByproducts().get(0).needed());
    }

    @Test
    void dagLayoutUsesAdaptiveNodeWidthsAndDistributesFanInPorts() {
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = richEntry();
        PlanFlowViewModel.DagLayout lr = PlanFlowViewModel.computeDagLayout(entry.steps(), false);
        PlanFlowViewModel.DagLayout tb = PlanFlowViewModel.computeDagLayout(entry.steps(), true);

        PlanFlowViewModel.DagNodeLayout s1 = lr.nodes().stream()
                .filter(n -> "s1".equals(n.step().stepId())).findFirst().orElseThrow();
        PlanFlowViewModel.DagNodeLayout s3 = lr.nodes().stream()
                .filter(n -> "s3".equals(n.step().stepId())).findFirst().orElseThrow();
        PlanFlowViewModel.DagNodeLayout s4 = lr.nodes().stream()
                .filter(n -> "s4".equals(n.step().stepId())).findFirst().orElseThrow();

        assertEquals(36, s1.w());
        assertEquals(87, s3.w());
        assertEquals(70, s4.w());
        assertTrue(s4.finalTarget());

        List<PlanFlowViewModel.DagEdgeLayout> lrIntoS3 = lr.edges().stream()
                .filter(e -> "s3".equals(e.to().step().stepId())).toList();
        assertEquals(2, lrIntoS3.size());
        assertTrue(
            Math.abs(lrIntoS3.get(0).y1() - lrIntoS3.get(1).y1()) >= 4.0f,
            "Fan-in edges should dock at distinct target port heights"
        );
        assertTrue(
            Math.abs(lrIntoS3.get(0).channelRatio() - lrIntoS3.get(1).channelRatio()) >= 0.05f,
            "Shared corridor edges should split into distinct routing lanes"
        );
        assertEquals(PlanFlowViewModel.EdgeKind.material, lrIntoS3.get(0).kind());

        List<PlanFlowViewModel.DagEdgeLayout> tbIntoS3 = tb.edges().stream()
                .filter(e -> "s3".equals(e.to().step().stepId())).toList();
        assertEquals(2, tbIntoS3.size());
        assertTrue(
            Math.abs(tbIntoS3.get(0).x1() - tbIntoS3.get(1).x1()) >= 4.0f,
            "Vertical fan-in edges should dock at distinct horizontal ports"
        );

        CirrusPlanBookmarkStore.StepLine machineStep = new CirrusPlanBookmarkStore.StepLine(
            "m1",
            "ProvisionMachineStep",
            OfflineContentTypes.item("minecraft:blast_furnace"),
            1L,
            1L,
            OfflineContentTypes.machine("minecraft:blast_furnace"),
            false,
            0.0,
            0.0,
            List.of(),
            List.of(),
            List.of()
        );
        CirrusPlanBookmarkStore.StepLine fourInStep = new CirrusPlanBookmarkStore.StepLine(
            "c4",
            "CraftStep",
            OfflineContentTypes.item("minecraft:iron_block"),
            1L,
            1L,
            OfflineContentTypes.machine("minecraft:blast_furnace"),
            true,
            0.0,
            0.0,
            List.of("m1"),
            List.of(
                OfflineContentTypes.item("minecraft:iron_ore"),
                OfflineContentTypes.item("minecraft:coal"),
                OfflineContentTypes.item("minecraft:iron_ingot"),
                OfflineContentTypes.item("minecraft:cobblestone")
            ),
            List.of()
        );
        PlanFlowViewModel.DagLayout typed = PlanFlowViewModel.computeDagLayout(List.of(machineStep, fourInStep), false);
        assertEquals(38, typed.nodes().stream().filter(n -> "m1".equals(n.step().stepId())).findFirst().orElseThrow().w());
        assertEquals(104, typed.nodes().stream().filter(n -> "c4".equals(n.step().stepId())).findFirst().orElseThrow().w());
        assertEquals(PlanFlowViewModel.EdgeKind.machine, typed.edges().get(0).kind());
    }

    @Test
    void enrichedBookmarkRoundTripsThroughJsonAndCodec() {
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry = richEntry();
        CirrusPlanBookmarkStore.put(entry);

        String json = CirrusPlanBookmarkStore.serializeToJson();
        CirrusPlanBookmarkStore.resetForTests();
        CirrusPlanBookmarkStore.loadFromJson(json);

        CirrusPlanBookmarkStore.PlanBookmarkEntry restored = CirrusPlanBookmarkStore.find(entry.id());
        assertNotNull(restored);
        assertEquals(1, restored.machinesToProvision().size());
        assertNotNull(restored.machinesToProvision().get(0).machine());
        assertEquals(
            OfflineContentTypes.machine("minecraft:blast_furnace").canonicalKey(),
            restored.machinesToProvision().get(0).machine().canonicalKey()
        );
        assertEquals(1, restored.catalysts().size());
        assertEquals(1, restored.surplusByproducts().size());
        assertNotNull(restored.steps().get(2).machine());
        assertEquals(
            OfflineContentTypes.machine("minecraft:blast_furnace").canonicalKey(),
            restored.steps().get(2).machine().canonicalKey()
        );
        assertEquals(16.0, restored.steps().get(2).waitSeconds(), 1e-6);

        JsonElement encoded = PlanFlowRecipe.codec.encodeStart(JsonOps.INSTANCE, PlanFlowRecipe.of(restored)).getOrThrow();
        CirrusPlanBookmarkStore.resetForTests();
        PlanFlowRecipe decoded = PlanFlowRecipe.codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.latestEntry().machinesToProvision().size());
        assertEquals(1, decoded.latestEntry().catalysts().size());
        assertEquals(1, decoded.latestEntry().surplusByproducts().size());
        assertNotNull(decoded.latestEntry().steps().get(2).machine());
        assertEquals(
            OfflineContentTypes.machine("minecraft:blast_furnace").canonicalKey(),
            decoded.latestEntry().steps().get(2).machine().canonicalKey()
        );
        assertEquals(2, decoded.latestEntry().steps().get(2).inputAmounts().size());
    }

    @Test
    void stackFormattingRespectsRealMaxStackSizeFluidsAndToggleMode() {
        PlanFlowViewModel.ResourceUnitSpec stack64 = PlanFlowViewModel.ResourceUnitSpec.defaultItem;
        PlanFlowViewModel.ResourceUnitSpec stack16 = new PlanFlowViewModel.ResourceUnitSpec(
            PlanFlowViewModel.QuantityUnitKind.itemStack,
            16L
        );
        PlanFlowViewModel.ResourceUnitSpec unstackable = new PlanFlowViewModel.ResourceUnitSpec(
            PlanFlowViewModel.QuantityUnitKind.itemStack,
            1L
        );
        PlanFlowViewModel.ResourceUnitSpec fluid = PlanFlowViewModel.ResourceUnitSpec.fluid;

        assertEquals("", PlanFlowViewModel.formatStackBreakdown(63L, stack64, ""));
        assertEquals("1\u00d764", PlanFlowViewModel.formatStackBreakdown(64L, stack64, ""));
        assertEquals("2\u00d764+16", PlanFlowViewModel.formatStackBreakdown(144L, stack64, ""));
        assertEquals("2\u7ec4+4", PlanFlowViewModel.formatStackBreakdown(36L, stack16, "\u7ec4"));
        assertEquals("2\u00d716+4", PlanFlowViewModel.formatStackBreakdown(36L, stack16, ""));
        assertEquals("", PlanFlowViewModel.formatStackBreakdown(3L, unstackable, "\u7ec4"));

        assertEquals("36 (2\u7ec4+4 \u00b7 \u00d716)", PlanFlowViewModel.formatTooltipQuantity(36L, stack16, false, "\u7ec4"));
        assertEquals("2\u7ec4+4 \u00b7 \u00d716 (36)", PlanFlowViewModel.formatTooltipQuantity(36L, stack16, true, "\u7ec4"));
        assertEquals("3", PlanFlowViewModel.formatTooltipQuantity(3L, unstackable, true, "\u7ec4"));

        assertEquals("2.5B", PlanFlowViewModel.formatSlotQuantity(2500L, fluid, true, ""));
        assertEquals("2500mB", PlanFlowViewModel.formatSlotQuantity(2500L, fluid, false, ""));
        assertEquals("2500 mB (2.5B)", PlanFlowViewModel.formatTooltipQuantity(2500L, fluid, false, ""));
        assertEquals("2.5B (2500 mB)", PlanFlowViewModel.formatTooltipQuantity(2500L, fluid, true, ""));
        assertEquals("500 mB", PlanFlowViewModel.formatTooltipQuantity(500L, fluid, true, ""));
    }

    private static void assertNoNodeOverlap(List<PlanFlowViewModel.DagNodeLayout> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            PlanFlowViewModel.DagNodeLayout a = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                PlanFlowViewModel.DagNodeLayout b = nodes.get(j);
                boolean overlap = a.worldX() < b.worldX() + b.w()
                        && a.worldX() + a.w() > b.worldX()
                        && a.worldY() < b.worldY() + b.h()
                        && a.worldY() + a.h() > b.worldY();
                assertFalse(overlap, "DAG nodes must not overlap: " + a.step().stepId() + " vs " + b.step().stepId());
            }
        }
    }
}

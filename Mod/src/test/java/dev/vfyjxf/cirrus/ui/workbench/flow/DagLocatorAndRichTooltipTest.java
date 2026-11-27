package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagEdgeRoute;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayout;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltipRenderer;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DagLocatorAndRichTooltipTest {

    private static ProductionPlan multiInputPlan() {
        List<PlanStep> steps = List.of(
            new AcquireStep(
                "a1",
                List.of(),
                SpatialDagFixtures.ore,
                32L,
                AcquisitionKind.manualGather,
                SpatialDagFixtures.cost(4.0, 0.0, 0.0, 1.0, 0.2)
            ),
            new AcquireStep(
                "a2",
                List.of(),
                ResourceKey.item("minecraft:coal"),
                16L,
                AcquisitionKind.manualGather,
                SpatialDagFixtures.cost(2.0, 0.0, 0.0, 0.5, 0.1)
            ),
            new AcquireStep(
                "a3",
                List.of(),
                ResourceKey.item("minecraft:redstone"),
                8L,
                AcquisitionKind.manualGather,
                SpatialDagFixtures.cost(3.0, 0.0, 0.0, 1.5, 0.2)
            ),
            SpatialDagFixtures.craft(
                "c1",
                List.of("a1", "a2", "a3"),
                SpatialDagFixtures.r1,
                SpatialDagFixtures.cost(5.0, 18.0, 2.0, 0.5, 0.4)
            ),
            SpatialDagFixtures
                    .craft("c2", List.of("c1"), SpatialDagFixtures.r2, SpatialDagFixtures.cost(3.0, 4.0, 0.0, 0.2, 0.2))
        );
        return SpatialDagFixtures.plan("plan-multi", steps, List.of());
    }

    @Test
    void incomingPortsDistributeAcrossNodeFaceWithoutColliding() {
        PlanDagLayoutResult layout = PlanDagLayout.compute(
            multiInputPlan(),
            null,
            SpatialDagFixtures.graph(),
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            Set.of()
        );
        List<DagEdgeRoute> intoC1 = layout.edges().stream().filter(e -> "c1".equals(e.toStepId())).toList();
        assertEquals(3, intoC1.size(), "c1 should have 3 incoming edges");
        Set<Float> targetYs = new HashSet<>();
        for (DagEdgeRoute edge : intoC1) {
            targetYs.add(edge.y1());
        }
        assertEquals(3, targetYs.size(), "incoming edges must land on distinct distributed Y ports");
    }

    @Test
    void orthogonalChannelRatiosSeparateParallelEdgesAndChamferCorners() {
        PlanDagLayoutResult layout = PlanDagLayout.compute(
            multiInputPlan(),
            null,
            SpatialDagFixtures.graph(),
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            Set.of()
        );
        Map<DagEdgeRoute, Float> ratios = DagHitTester
                .orthogonalChannelRatios(layout.edges(), DagLayoutMode.horizontalLr);
        assertEquals(layout.edges().size(), ratios.size());
        Set<Float> c1Ratios = new HashSet<>();
        for (DagEdgeRoute edge : layout.edges()) {
            if ("c1".equals(edge.toStepId())) {
                c1Ratios.add(ratios.get(edge));
            }
        }
        assertEquals(3, c1Ratios.size(), "parallel edges into c1 should occupy distinct bus lanes");
        float[] chamfered = DagHitTester.chamferPolyline(new float[]{0f, 0f, 40f, 0f, 40f, 40f, 80f, 40f}, 7.5f);
        assertEquals(12, chamfered.length, "two 90-degree bends should expand into 6 points (12 floats)");
    }

    @Test
    void resourceConduitPaletteDistinguishesKeyMaterialsInBothThemes() {
        ResourceKey coal = ResourceKey.item("minecraft:coal");
        ResourceKey iron = ResourceKey.item("minecraft:iron_ore");
        ResourceKey redstone = ResourceKey.item("minecraft:redstone");
        ResourceKey planks = ResourceKey.item("minecraft:oak_planks");
        for (CirrusPalette palette : List.of(CirrusPalette.obsidianBlueprint, CirrusPalette.atelierStone)) {
            int cCoal = DagEdgeRenderer.resourceConduitColor(coal, palette);
            int cIron = DagEdgeRenderer.resourceConduitColor(iron, palette);
            int cRedstone = DagEdgeRenderer.resourceConduitColor(redstone, palette);
            int cPlanks = DagEdgeRenderer.resourceConduitColor(planks, palette);
            assertNotEquals(cCoal, cIron);
            assertNotEquals(cIron, cRedstone);
            assertNotEquals(cRedstone, cPlanks);
        }
    }

    @Test
    void richTooltipMeasurementAndEdgeClampingStayInsideViewport() {
        OreUiRichTooltip tooltip = OreUiRichTooltip.builder("Blast Furnace Smelt").subtitle("Stage 02 · Blast Furnace")
                .badge("CRAFT", OreUiRichTooltip.Tone.accent)
                .itemFlow(
                    Component.literal("Flow"),
                    List.of(OreUiRichTooltip.ItemBadge.of(ResourceKey.item("minecraft:iron_ore"), "×32")),
                    List.of(OreUiRichTooltip.ItemBadge.of(ResourceKey.item("minecraft:iron_ingot"), "×32"))
                ).meter("Stage Time", 0.65, "26s (65%)", OreUiRichTooltip.Tone.costH)
                .pills(
                    OreUiRichTooltip.Pill.of("Work 4s", OreUiRichTooltip.Tone.costH),
                    OreUiRichTooltip.Pill.of("Wait 22s", OreUiRichTooltip.Tone.costP)
                ).footer("Click to inspect").build();

        OreUiRichTooltipRenderer.Measured measured = OreUiRichTooltipRenderer.measure(tooltip, null);
        assertTrue(measured.width() >= OreUiRichTooltipRenderer.minWidth);
        assertTrue(measured.width() <= OreUiRichTooltipRenderer.maxWidth);
        assertTrue(measured.height() > 80);

        OreUiRichTooltipRenderer.Placement nearBottomRight = OreUiRichTooltipRenderer
                .position(1270, 710, measured.width(), measured.height(), 1280, 720);
        assertTrue(nearBottomRight.x() >= OreUiRichTooltipRenderer.viewportMargin);
        assertTrue(nearBottomRight.y() >= OreUiRichTooltipRenderer.viewportMargin);
        assertTrue(nearBottomRight.x() + nearBottomRight.width() <= 1280 - OreUiRichTooltipRenderer.viewportMargin);
        assertTrue(nearBottomRight.y() + nearBottomRight.height() <= 720 - OreUiRichTooltipRenderer.viewportMargin);
    }

    @Test
    void locatorRulerBuildsAlignedStagesAndProvidesRichTooltips() {
        PlanDagLayoutResult layout = PlanDagLayout.compute(
            multiInputPlan(),
            null,
            SpatialDagFixtures.graph(),
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            Set.of("a1", "c1")
        );
        DagViewport viewport = new DagViewport();
        DagLocatorRulerWidget ruler = DagLocatorRulerWidget.of(viewport);
        ruler.syncState(
            layout,
            DagLayoutMode.horizontalLr,
            Map.of(),
            null,
            Set.of("c1"),
            Set.of("a1", "c1"),
            Map.of(),
            780,
            600,
            150
        );
        List<DagLocatorRulerWidget.StageCell> cells = ruler.stageCells();
        assertEquals(3, cells.size(), "3-layer plan must produce 3 aligned stage cells");
        DagLocatorRulerWidget.StageCell first = cells.get(0);
        assertEquals(3, first.iconHits().size(), "stage 01 with 3 inputs must expose 3 clickable icon hits");

        OreUiRichTooltip stageTip = ruler.richTooltipAt(first.x() + 6, first.y() + 6);
        assertNotNull(stageTip, "hovering a stage cell must produce a rich tooltip");
        assertTrue(!stageTip.blocks().isEmpty(), "stage tooltip must contain rich blocks");

        DagLocatorRulerWidget.NodeIconHit iconHit = first.iconHits().get(0);
        OreUiRichTooltip iconTip = ruler.richTooltipAt(iconHit.x() + 2, iconHit.y() + 2);
        assertNotNull(iconTip, "hovering a mini item icon must produce a node-specific rich tooltip");
        assertTrue(!iconTip.blocks().isEmpty(), "node tooltip must contain rich blocks");

        assertEquals("-1", DagLocatorRulerWidget.relativeOffsetLabel(0, 1));
        assertEquals("@", DagLocatorRulerWidget.relativeOffsetLabel(1, 1));
        assertEquals("+2", DagLocatorRulerWidget.relativeOffsetLabel(3, 1));
    }

    @Test
    void oreUiSlotAmountFormatsCompactCountsAndParsesLabels() {
        assertEquals("", OreUiSlotAmount.format(0L));
        assertEquals("", OreUiSlotAmount.format(1L));
        assertEquals("2", OreUiSlotAmount.format(2L));
        assertEquals("64", OreUiSlotAmount.format(64L));
        assertEquals("999", OreUiSlotAmount.format(999L));
        assertEquals("1K", OreUiSlotAmount.format(1_000L));
        assertEquals("1.3K", OreUiSlotAmount.format(1_250L));
        assertEquals("12K", OreUiSlotAmount.format(12_400L));
        assertEquals("1.5M", OreUiSlotAmount.format(1_500_000L));
        assertEquals("12G", OreUiSlotAmount.format(12_000_000_000L));

        assertEquals(32L, OreUiSlotAmount.parseCount("\u00d732"));
        assertEquals(1_500L, OreUiSlotAmount.parseCount("1.5K"));
        assertEquals(2_000_000L, OreUiSlotAmount.parseCount("2M"));
        assertEquals(0L, OreUiSlotAmount.parseCount("invalid"));
    }

    @Test
    void oreUiRecipeGridAdaptsFromSmallToHugeModdedRecipes() {
        OreUiRecipeGrid.Layout small = OreUiRecipeGrid.measure(2, 1, OreUiRecipeGrid.defaultSlotSize, 220, 2);
        assertEquals(2, small.inCols());
        assertEquals(1, small.inRows());
        assertEquals(0, small.inOverflow());
        assertEquals(1, small.outCols());

        OreUiRecipeGrid.Layout crafting = OreUiRecipeGrid.measure(9, 1, OreUiRecipeGrid.compactSlotSize, 160, 3);
        assertEquals(3, crafting.inCols());
        assertEquals(3, crafting.inRows());
        assertEquals(0, crafting.inOverflow());

        OreUiRecipeGrid.Layout huge = OreUiRecipeGrid.measure(25, 4, OreUiRecipeGrid.compactSlotSize, 140, 2);
        assertTrue(huge.width() <= 140, "huge recipe grid must respect maxWidth");
        assertTrue(huge.inRows() <= 2, "huge recipe grid must respect maxRows");
        assertTrue(huge.inOverflow() > 0, "huge recipe exceeding cell budget must reserve +N overflow slot");
    }

    @Test
    void richTooltipWrapsLongTextAcrossMultipleLinesWithoutTruncation() {
        OreUiRichTooltip shortTip = OreUiRichTooltip.builder("Preset 1: Balanced")
                .text("Short summary.", OreUiRichTooltip.Tone.secondary).footer("Click to apply").build();
        OreUiRichTooltip longTip = OreUiRichTooltip.builder(
            "Preset 1: Balanced Optimization Profile With Extended Descriptive Title"
        ).subtitle("Balances Work, Wait, Equipment, Stock, and Step count simultaneously across all branches").text(
            "Minimizes manual crafting work while keeping machine wait time, setup overhead, raw material consumption, and recipe complexity evenly balanced so no single bottleneck dominates the plan.",
            OreUiRichTooltip.Tone.secondary
        ).footer("Click to apply this weighting preset immediately, or open Weights to tune individual cost axes")
                .build();

        OreUiRichTooltipRenderer.Measured shortM = OreUiRichTooltipRenderer.measure(shortTip, null);
        OreUiRichTooltipRenderer.Measured longM = OreUiRichTooltipRenderer.measure(longTip, null);
        assertTrue(longM.width() <= OreUiRichTooltipRenderer.maxWidth, "wrapped tooltip must stay within maxWidth");
        assertTrue(
            longM.height() > shortM.height() + 40,
            "long tooltip content must wrap vertically instead of single-line truncation"
        );
        assertTrue(longM.footerHeight() > 14, "long footer hint must wrap onto multiple lines");
    }
}

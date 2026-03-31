package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.process.ProcessGraph;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialType;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixMilestonesContractTest {

    @Test
    void matrixViewExposesStableRowCount() {
        ProcessGraph graph = DebugRecipeProvider.createDebugProcessGraph();
        MatrixViewWidget widget = new MatrixViewWidget(graph);

        assertEquals(graph.nodeCount(), widget.visibleRowCount());
    }

    @Test
    void milestonesViewExposesStablePhaseColumnCount() {
        ProcessGraph graph = DebugRecipeProvider.createDebugProcessGraph();
        MilestonesViewWidget widget = new MilestonesViewWidget(graph);

        assertEquals(graph.groupCount(), widget.phaseColumnCount());
    }

    @Test
    void milestonesUseIndependentVerticalScrollPerPhase() {
        MilestonesViewWidget widget = new MilestonesViewWidget(DebugRecipeProvider.createDebugProcessGraph());

        widget.debugSetPhaseScroll(0, 48);
        widget.debugSetPhaseScroll(1, 12);

        assertEquals(48f, widget.debugPhaseScroll(0));
        assertEquals(12f, widget.debugPhaseScroll(1));
        assertEquals(0f, widget.debugPhaseScroll(2));
    }

    @Test
    void matrixViewExposesIndependentTableScrollState() {
        MatrixViewWidget widget = new MatrixViewWidget(DebugRecipeProvider.createDebugProcessGraph());

        widget.debugSetTableScroll(54);

        assertEquals(54f, widget.debugTableScroll());
    }

    @Test
    void matrixRowsAutoHideScrollbarWhenRowsFitViewport() {
        MatrixViewWidget widget = new MatrixViewWidget(DebugRecipeProvider.createDebugProcessGraph());

        assertFalse(widget.debugTableNeedsScrollbar(10_000));
        assertTrue(widget.debugTableNeedsScrollbar(30));
    }

    @Test
    void matrixRowsUseDenseFixedHeight() {
        assertEquals(30, MatrixViewWidget.debugRowHeight());
    }

    @Test
    void matrixUsesLightChromeAndGraphicalRows() {
        MatrixViewWidget widget = new MatrixViewWidget();

        assertFalse(widget.debugHeaderUsesFrameTexture());
        assertTrue(widget.debugHeaderUsesFlatAccentChrome());
        assertTrue(widget.debugColumnHeaderUsesIcons());
        assertFalse(widget.debugColumnHeaderUsesText());
        assertFalse(widget.debugRowsRenderOutputNameText());
        assertFalse(widget.debugRowsRenderOutputSummaryText());
        assertTrue(widget.debugRowsRenderMachineIcons());
        assertFalse(widget.debugRowsUsePerRowBorderChrome());
        assertFalse(widget.debugRowsUseRichTooltips());
        assertTrue(widget.debugRowsUseVisibleWindowRendering());
        assertTrue(widget.debugRowsUseSharedMaterialSlotRenderer());
        assertFalse(widget.debugRowsUseGregTechTierAccent());
        assertFalse(widget.debugHeaderShowsTitleText());
        assertFalse(widget.debugUsesStandaloneSummaryHeader());
    }

    @Test
    void milestoneCardsHideLegacyStageTextAndAmountBadge() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertFalse(widget.debugStepCardsShowStageText());
        assertFalse(widget.debugStepCardsShowAmountBadge());
        assertTrue(widget.debugStepCardsShowActionText());
        assertFalse(widget.debugPhaseHeadersUseExternalProgressBar());
    }

    @Test
    void milestoneCardsDescribeCollectAndMakeQuantities() {
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "source/cobblestone",
                "Cobblestone",
                "Material",
                List.of(),
                List.of(MaterialSlot.item(Items.COBBLESTONE.getDefaultInstance(), 128)),
                0,
                0,
                0,
                List.of(),
                null
        ));
        graph.addNode(new ProcessNode(
                "craft/smoker",
                "Smoker",
                "Crafting Table",
                List.of(new MaterialSlot("Cobblestone", 8, MaterialType.ITEM)),
                List.of(new MaterialSlot("Smoker", 16, MaterialType.ITEM)),
                0,
                0,
                0,
                List.of(),
                null
        ));

        MilestonesViewWidget widget = new MilestonesViewWidget(graph);

        assertEquals("collect", widget.debugStepCardActionKind(0, 0));
        assertEquals("Cobblestone", widget.debugStepCardMaterialName(0, 0));
        assertEquals(128, widget.debugStepCardQuantity(0, 0));
        assertEquals("make", widget.debugStepCardActionKind(0, 1));
        assertEquals("Smoker", widget.debugStepCardMaterialName(0, 1));
        assertEquals(16, widget.debugStepCardQuantity(0, 1));
    }

    @Test
    void milestoneProgressBarsUsePerContainerInsets() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertEquals(0, widget.debugSummaryProgressInset());
        assertEquals(4, widget.debugPhaseProgressInset());
        assertEquals(0, widget.debugStepCardProgressInset());
        assertTrue(widget.debugSummaryProgressTouchesHorizontalEdges());
        assertTrue(widget.debugSummaryProgressTouchesBottomEdge());
        assertTrue(widget.debugSummaryProgressMergedTopBorder());
    }

    @Test
    void milestoneSummaryHeaderRemovesLegacyScrollableDescription() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertFalse(widget.debugSummaryHeaderShowsSubtitle());
        assertFalse(widget.debugPhaseHeadersShowSubtitle());
    }

    @Test
    void milestoneCardsSupportManualChecklistState() {
        MilestonesViewWidget widget = new MilestonesViewWidget(DebugRecipeProvider.createDebugProcessGraph());

        assertTrue(widget.debugSupportsManualStepToggle());
        assertFalse(widget.debugManualStepChecked(0, 0));
        String before = widget.debugSummaryHeaderProgressText();

        widget.debugToggleManualStep(0, 0);

        assertTrue(widget.debugManualStepChecked(0, 0));
        assertNotEquals(before, widget.debugSummaryHeaderProgressText());
    }

    @Test
    void milestoneCardsMatchCurrentStorageForCompletionAndProgress() {
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "source/cobblestone",
                "Cobblestone",
                "Material",
                List.of(),
                List.of(new MaterialSlot("Cobblestone", 128, MaterialType.ITEM)),
                0,
                0,
                0,
                List.of(),
                null
        ));
        MilestonesViewWidget partial = new MilestonesViewWidget(graph, new HashSet<>(), request -> 64L);
        MilestonesViewWidget complete = new MilestonesViewWidget(graph, new HashSet<>(), request -> 128L);

        assertFalse(partial.debugStepCompleted(0, 0));
        assertEquals(64L, partial.debugStepAvailableStock(0, 0));
        assertEquals(0.5f, partial.debugStepProgressRatio(0, 0), 0.0001f);
        assertEquals("0 / 1", partial.debugSummaryHeaderProgressText());

        assertTrue(complete.debugStepCompleted(0, 0));
        assertEquals("1 / 1", complete.debugSummaryHeaderProgressText());
    }

    @Test
    void milestoneManualChecklistStateIsRecordedByStableStepKey() {
        ProcessGraph graph = DebugRecipeProvider.createDebugProcessGraph();
        Set<String> completed = new HashSet<>();
        MilestonesViewWidget first = new MilestonesViewWidget(graph, completed, request -> 0L);

        first.debugToggleManualStep(0, 0);

        MilestonesViewWidget rebuilt = new MilestonesViewWidget(graph, completed, request -> 0L);
        assertTrue(rebuilt.debugManualStepChecked(0, 0));
        assertTrue(rebuilt.debugStepCompleted(0, 0));
    }

    @Test
    void milestoneProgressUsesUnifiedColorAndCheckboxToggleChrome() throws Exception {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugProgressUsesUnifiedFillColor());
        assertFalse(widget.debugManualToggleUsesSwitchTexture());
        assertTrue(widget.debugManualToggleUsesCheckboxTexture());
        assertTrue(widget.debugManualToggleIsProgressMarker());
        assertFalse(widget.debugManualToggleUsesStatusMarker());
        assertTrue(widget.debugManualTogglePlaysClickSound());
        assertTrue(widget.debugManualToggleIsRightAligned());
        assertEquals(14, milestoneBoardConstant("manualToggleWidth"));
        assertEquals(14, milestoneBoardConstant("manualToggleHeight"));
    }

    @Test
    void milestoneCardsUseSharedMaterialRendererWithoutBarrierFallback() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugStepCardsUseSharedMaterialSlotRenderer());
        assertFalse(widget.debugStepCardsUseFallbackBarrierIcon());
        assertFalse(OmniCalcSlotRenderer.rendersMachinePlaceholder());
    }

    @Test
    void milestoneSummaryHeaderUsesLighterSinglePanelChrome() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugSummaryHeaderUsesMinimalChrome());
        assertEquals("cirrus.ui.calc.milestones.title", widget.debugSummaryHeaderTitle());
        assertEquals("0 / 0", widget.debugSummaryHeaderProgressText());
        assertFalse(widget.debugUsesPlaceholderHeaderTexture());
    }

    @Test
    void milestonePhaseColumnsAutoHideVerticalScrollbar() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugPhaseColumnsAutoHideScrollbar());
        assertEquals(10, widget.debugPhaseScrollbarWidth());
    }

    @Test
    void milestoneScrollbarsOnlyAppearWhenTheirContentOverflows() {
        ProcessGraph graph = new ProcessGraph();
        graph.addNode(new ProcessNode(
                "single",
                "Single",
                "Crafting Table",
                List.of(new MaterialSlot("Cobblestone", 1, MaterialType.ITEM)),
                List.of(new MaterialSlot("Stone", 1, MaterialType.ITEM)),
                0,
                0,
                0,
                List.of(),
                null
        ));
        MilestonesViewWidget widget = new MilestonesViewWidget(graph);

        assertFalse(widget.debugBoardNeedsHorizontalScrollbar(10_000));
        assertTrue(widget.debugBoardNeedsHorizontalScrollbar(10));
        assertFalse(widget.debugPhaseNeedsScrollbar(0, 10_000));
        assertTrue(widget.debugPhaseNeedsScrollbar(0, 40));
    }

    @Test
    void milestoneScrollRoutingPrefersPhaseVerticalScrollInsidePhaseBody() {
        assertFalse(MilestonesViewWidget.shouldRouteToHorizontalScroll(
                false, 0f, false, true
        ));
        assertTrue(MilestonesViewWidget.shouldRouteToHorizontalScroll(
                false, 0f, true, false
        ));
        assertTrue(MilestonesViewWidget.shouldRouteToHorizontalScroll(
                true, 0f, false, true
        ));
    }

    @Test
    void calculatorLightPanelTypographyUsesClassicMinecraftContrast() {
        assertEquals(0xFF3F3F3F, OmniCalcColors.textPrimary);
        assertEquals(0xFF555555, OmniCalcColors.textSecondary);
        assertEquals(0xFFFFFFFF, OmniCalcColors.textWhite);
    }

    private static int milestoneBoardConstant(String name) throws Exception {
        Class<?> boardClass = Class.forName(
                "dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.MilestonesViewWidget$MilestoneBoardWidget");
        Field field = boardClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}

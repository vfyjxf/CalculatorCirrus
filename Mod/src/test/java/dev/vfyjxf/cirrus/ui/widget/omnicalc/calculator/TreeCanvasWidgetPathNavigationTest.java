package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cirrus.ui.process.ProcessNode;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeCanvasWidgetPathNavigationTest {

    @Test
    void pathNavigationReturnsToAncestorSubgraph() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        String layerOnePath = widget.getLayerNodePathIds(1).getFirst();
        String layerTwoPath = widget.getLayerNodePathIds(2).getFirst();

        widget.focusSubgraph(layerTwoPath);
        assertEquals(layerTwoPath, widget.getCurrentRootPathId());

        widget.navigatePathContextTo(layerOnePath);

        assertEquals(layerOnePath, widget.getCurrentRootPathId());
    }

    @Test
    void pathNavigationCanReturnToFullTree() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(DebugRecipeProvider.createDebugProcessGraph());

        String layerOnePath = widget.getLayerNodePathIds(1).getFirst();
        widget.focusSubgraph(layerOnePath);
        assertNotEquals("root", widget.getCurrentRootPathId());

        widget.navigatePathContextTo("root");

        assertEquals("root", widget.getCurrentRootPathId());
    }

    @Test
    void focusButtonBoundsExposeLargerHeaderHitArea() throws Exception {
        Method method = TreeCanvasWidget.class.getDeclaredMethod("focusButtonBounds", int.class, int.class);
        method.setAccessible(true);

        int[] bounds = (int[]) method.invoke(null, 100, 50);

        assertEquals(4, bounds.length);
        assertTrue(bounds[2] >= 18, "focus button width should remain comfortably clickable");
        assertTrue(bounds[3] >= 18, "focus button height should remain comfortably clickable");
        assertTrue(bounds[0] > 100 + 160, "focus button should stay near the right edge of the card header");
        assertTrue(bounds[1] <= 60, "focus button should stay in the header band");
    }

    @Test
    void stepMetaLinesKeepGenericRecipeDataOutOfGregTechTierDetails() throws Exception {
        Method method = TreeCanvasWidget.class.getDeclaredMethod("buildStepMetaLines", ProcessNode.class);
        method.setAccessible(true);

        ProcessNode node = new ProcessNode(
                "n1",
                "Arc Furnace Smelting",
                "Arc Furnace",
                List.of(),
                List.of(new ProcessNode.MaterialSlot("Steel Ingot", 4, ProcessNode.MaterialType.ITEM)),
                120,
                480,
                3,
                List.of(
                        new ProcessNode.MachineInfo("Arc Furnace", ItemStack.EMPTY),
                        new ProcessNode.MachineInfo("Large Arc Furnace", ItemStack.EMPTY)
                ),
                null
        );

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) method.invoke(null, node);

        assertFalse(lines.stream().anyMatch(line -> line.contains("HV")), "generic cards should not render GregTech tier data by default");
        assertTrue(lines.stream().anyMatch(line -> line.contains(CirrusLangs.Calc.Tree.machineCount.string(2))),
                "meta lines should include localized alternate machine data when available");
        assertFalse(lines.stream().anyMatch(line -> line.contains("EU/t")), "meta lines should not repeat energy data on compact cards");
    }

    @Test
    void stepCardsUseSeparatedHeaderChromeAndSlotBackedIcons() {
        TreeCanvasWidget.StepCardChrome chrome = TreeCanvasWidget.debugStepCardChrome();

        assertEquals(0xFF656E77, chrome.machineTitleColor());
        assertFalse(chrome.machineTitleShadow());
        assertTrue(chrome.usesSlotBackedMachineIcon());
        assertTrue(chrome.usesSlotBackedOutputIcon());
        assertFalse(chrome.usesDrawnSeparator());
        assertTrue(chrome.focusButtonUsesTreeGlyph());
        assertFalse(chrome.leafRendersMachineSlot());
        assertFalse(chrome.rendersMachinePlaceholder());
        assertTrue(chrome.machineOptionIndicatorAvoidsText());
        assertTrue(chrome.extensionUsesIconMetrics());
        assertTrue(chrome.hasProjectionAction());
        assertTrue(chrome.collapseChildCountRenderedInTooltip());
        assertFalse(TreeCanvasWidget.debugStepCardsRenderTierByDefault());
    }

    @Test
    void actionBoundsKeepProjectionAndFocusSeparated() throws Exception {
        Method focusMethod = TreeCanvasWidget.class.getDeclaredMethod("focusButtonBounds", int.class, int.class);
        Method projectionMethod = TreeCanvasWidget.class.getDeclaredMethod("projectionButtonBounds", int.class, int.class);
        focusMethod.setAccessible(true);
        projectionMethod.setAccessible(true);

        int[] focus = (int[]) focusMethod.invoke(null, 100, 50);
        int[] projection = (int[]) projectionMethod.invoke(null, 100, 50);

        assertTrue(projection[0] < focus[0]);
        assertTrue(projection[0] + projection[2] <= focus[0] - 2);
        assertEquals(focus[2], projection[2]);
        assertEquals(focus[3], projection[3]);
        assertEquals(focus[1], projection[1]);
    }

    @Test
    void pathBreadcrumbCanBuildRecipeTooltipForHoveredNode() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setGraph(DebugRecipeProvider.createDebugProcessGraph());
        String layerOnePath = widget.getLayerNodePathIds(1).getFirst();

        assertNotNull(widget.buildNodeTooltipForPath(layerOnePath));
        assertNull(widget.buildNodeTooltipForPath("missing-path"));
    }
}

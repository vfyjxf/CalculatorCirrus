package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(bounds[0] > 100 + 180, "focus button should stay near the right edge of the card header");
        assertTrue(bounds[1] <= 60, "focus button should stay in the header band");
    }

    @Test
    void stepMetaLinesIncludeTierAndOperationalData() throws Exception {
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
                )
        );

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) method.invoke(null, node);

        assertTrue(lines.stream().anyMatch(line -> line.contains("HV")), "meta lines should include the tier");
        assertTrue(lines.stream().anyMatch(line -> line.contains("EU/t")), "meta lines should include energy data when available");
    }

    @Test
    void stepCardsUseSeparatedHeaderChromeAndSlotBackedIcons() {
        TreeCanvasWidget.StepCardChrome chrome = TreeCanvasWidget.debugStepCardChrome();

        assertEquals(0xFFFFFFFF, chrome.machineTitleColor());
        assertTrue(chrome.machineTitleShadow());
        assertTrue(chrome.usesSlotBackedMachineIcon());
        assertTrue(chrome.usesSlotBackedOutputIcon());
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

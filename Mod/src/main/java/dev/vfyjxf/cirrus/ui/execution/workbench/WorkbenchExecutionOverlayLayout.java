package dev.vfyjxf.cirrus.ui.execution.workbench;

import dev.vfyjxf.cirrus.ui.widget.execution.ExecutionOverlayWidget;
import net.minecraft.client.renderer.Rect2i;

/**
 * Shared geometry for the workbench execution overlay and JEI exclusion area.
 */
public final class WorkbenchExecutionOverlayLayout {

    //region layout constants

    public static final int panelMarginLeft = 8;
    public static final int panelMarginTop = 12;
    public static final int panelMarginBottom = 36;
    public static final int controlsHeight = 24;
    public static final int controlsGap = 4;
    private static final int vanillaWorkbenchWidth = 176;
    private static final int panelWorkbenchGap = 10;
    private static final int minVisibleOverlayWidth = 96;

    //endregion

    private WorkbenchExecutionOverlayLayout() {
    }

    //region geometry

    public static Rect2i overlayBounds(int screenWidth, int screenHeight) {
        int preferredWidth = ExecutionOverlayWidget.preferredPanelWidth(screenWidth);
        int workbenchLeft = Math.max(0, (screenWidth - vanillaWorkbenchWidth) / 2);
        int maxWidthBeforeWorkbench = Math.max(0, workbenchLeft - panelWorkbenchGap - panelMarginLeft);
        int overlayWidth = Math.min(preferredWidth, maxWidthBeforeWorkbench);
        if (overlayWidth < minVisibleOverlayWidth) {
            overlayWidth = maxWidthBeforeWorkbench;
        }
        return new Rect2i(
            panelMarginLeft,
            panelMarginTop,
            overlayWidth,
            Math.max(72, screenHeight - panelMarginTop - panelMarginBottom)
        );
    }

    public static int contentHeight(int screenHeight) {
        return Math.max(40, overlayBounds(0, screenHeight).getHeight() - controlsHeight - controlsGap);
    }

    //endregion
}

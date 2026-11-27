package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-17: exact clamping, centre canvas protection and double-click reset across real drag sequences.
 */
class ChromeDockResizeTest {

    private static ChromeTestContext wideContext() {
        return new ChromeTestContext(1280f, 720f, List.of());
    }

    @Test
    void leftHandleClampsToTheDocumentedBounds() {
        ChromeTestContext ctx = wideContext();
        DockResizeHandleWidget handle = new DockResizeHandleWidget(ctx, true);
        assertEquals(176f, WorkbenchBreakpoint.leftDockMin, 0.001f);
        assertEquals(320f, WorkbenchBreakpoint.leftDockMax, 0.001f);

        assertFalse(handle.dragTo(500.0), "a drag must start before it can move");
        assertTrue(handle.beginDrag(500.0));
        assertTrue(handle.dragging());
        assertEquals(228f, handle.dragStartWidth(), 0.001f);

        assertTrue(handle.dragTo(900.0));
        assertEquals(WorkbenchBreakpoint.leftDockMax, ctx.leftDockWidth(), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMax, lastLeft(ctx), 0.001f);

        assertTrue(handle.dragTo(-900.0));
        assertEquals(WorkbenchBreakpoint.leftDockMin, ctx.leftDockWidth(), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMin, lastLeft(ctx), 0.001f);

        handle.release();
        assertFalse(handle.dragging());
        int calls = ctx.leftWidthCalls.size();
        assertFalse(handle.dragTo(700.0));
        assertEquals(calls, ctx.leftWidthCalls.size(), "a released handle must ignore drag deltas");
    }

    @Test
    void rightHandleMirrorsTheDragAndClampsToItsOwnBounds() {
        ChromeTestContext ctx = wideContext();
        DockResizeHandleWidget handle = new DockResizeHandleWidget(ctx, false);
        assertFalse(handle.leftSide());
        assertTrue(handle.beginDrag(1000.0));

        assertTrue(handle.dragTo(500.0));
        assertEquals(WorkbenchBreakpoint.rightDockMax, ctx.rightDockWidth(), 0.001f);
        assertEquals(WorkbenchBreakpoint.rightDockMax, lastRight(ctx), 0.001f);

        assertTrue(handle.dragTo(1500.0));
        assertEquals(WorkbenchBreakpoint.rightDockMin, ctx.rightDockWidth(), 0.001f);
        assertEquals(WorkbenchBreakpoint.rightDockMin, lastRight(ctx), 0.001f);
    }

    @Test
    void centerCanvasNeverDropsBelowTwoHundredTwentyPixels() {
        ChromeTestContext ctx = new ChromeTestContext(720f, 540f, List.of());
        DockResizeHandleWidget left = new DockResizeHandleWidget(ctx, true);
        DockResizeHandleWidget right = new DockResizeHandleWidget(ctx, false);

        ctx.setLeftDockWidth(WorkbenchBreakpoint.leftDockMin);
        ctx.setRightDockWidth(WorkbenchBreakpoint.rightDockMax);

        float rightClamped = right.clampWidth(WorkbenchBreakpoint.rightDockMax);
        assertEquals(284f, rightClamped, 0.001f);
        ctx.setRightDockWidth(rightClamped);
        assertEquals(
            720f - ctx.leftRailWidth() - ctx.leftDockWidth() - rightClamped,
            WorkbenchBreakpoint.minCenterCanvasWidth,
            0.001f
        );

        float leftClamped = left.clampWidth(WorkbenchBreakpoint.leftDockMax);
        assertEquals(176f, leftClamped, 0.001f);
        assertEquals(
            WorkbenchBreakpoint.minCenterCanvasWidth,
            720f - ctx.leftRailWidth() - leftClamped - rightClamped,
            0.001f
        );

        assertTrue(left.beginDrag(0.0));
        assertTrue(left.dragTo(9999.0));
        assertTrue(left.dragTo(-9999.0));
        assertEquals(WorkbenchBreakpoint.leftDockMin, ctx.leftDockWidth(), 0.001f);
    }

    @Test
    void doubleClickResetRestoresTheBreakpointDefaults() {
        ChromeTestContext ctx = wideContext();
        DockResizeHandleWidget left = new DockResizeHandleWidget(ctx, true);
        DockResizeHandleWidget right = new DockResizeHandleWidget(ctx, false);

        ctx.setLeftDockWidth(300f);
        assertTrue(left.resetToDefault());
        assertEquals(WorkbenchShellLayout.defaultLeftDockWidth(1280f), lastLeft(ctx), 0.001f);
        assertEquals(228f, lastLeft(ctx), 0.001f);

        ctx.setRightDockWidth(380f);
        assertTrue(right.resetToDefault());
        assertEquals(WorkbenchShellLayout.defaultRightDockWidth(1280f), lastRight(ctx), 0.001f);
        assertEquals(310f, lastRight(ctx), 0.001f);

        ChromeTestContext compact = new ChromeTestContext(427f, 240f, List.of());
        DockResizeHandleWidget compactLeft = new DockResizeHandleWidget(compact, true);
        assertEquals(0f, compact.leftDockWidth(), 0.001f);
        assertFalse(compactLeft.beginDrag(10.0));
        assertFalse(compactLeft.resetToDefault());
    }

    @Test
    void handlesSitOnTheirDockSideWithFivePixelWidth() {
        ChromeTestContext ctx = wideContext();
        DockResizeHandleWidget left = new DockResizeHandleWidget(ctx, true);
        DockResizeHandleWidget right = new DockResizeHandleWidget(ctx, false);

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(left, right).layout()) {
            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(
                ctx.shellWidth(),
                ctx.shellHeight(),
                ctx.breakpoint(),
                false,
                ctx.leftDockWidth(),
                ctx.rightDockWidth()
            );

            assertEquals((int) DockResizeHandleWidget.handleWidth, left.width());
            assertEquals((int) DockResizeHandleWidget.handleWidth, right.width());
            assertEquals((int) layout.leftDock().right(), left.posX());
            assertEquals((int) layout.rightDock().x() - (int) DockResizeHandleWidget.handleWidth, right.posX());
            assertEquals((int) layout.leftDock().y(), left.posY());
            assertEquals((int) layout.rightDock().y(), right.posY());
            assertTrue(left.visible());
            assertTrue(right.visible());
            assertTrue(mounted.scene.root().children().size() == 2);
        }
    }

    @Test
    void sceneClickDragAndReleaseDriveTheHandle() {
        ChromeTestContext ctx = wideContext();
        DockResizeHandleWidget handle = new DockResizeHandleWidget(ctx, true);

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(handle).layout()) {
            double startX = handle.posX() + 2.0;
            double startY = handle.posY() + 20.0;

            mounted.scene.mouseClicked(startX, startY, 0);
            assertTrue(
                mounted.scene.mouseReleased(startX, startY, 0),
                "the click pair must reach the handle and open the drag session"
            );
            assertTrue(handle.dragging(), "a real click must start the drag session");
            assertEquals(228f, handle.dragStartWidth(), 0.001f);

            assertTrue(mounted.scene.mouseDragged(startX + 1.0, startY, 0, 1.0, 0.0));
            assertEquals(229f, ctx.leftDockWidth(), 0.001f);

            assertTrue(handle.dragTo(startX + 5000.0));
            assertEquals(WorkbenchBreakpoint.leftDockMax, ctx.leftDockWidth(), 0.001f);

            assertTrue(mounted.scene.mouseReleased(startX, startY, 0));
            assertFalse(handle.dragging());
        }
    }

    @Test
    void sceneDoubleClickResetsTheDefaultWidth() {
        ChromeTestContext ctx = wideContext();
        DockResizeHandleWidget handle = new DockResizeHandleWidget(ctx, true);

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(handle).layout()) {
            double x = handle.posX() + 2.0;
            double y = handle.posY() + 20.0;
            ctx.setLeftDockWidth(300f);

            mounted.scene.mouseClicked(x, y, 0);
            mounted.scene.mouseReleased(x, y, 0);
            mounted.scene.mouseClicked(x, y, 0);
            mounted.scene.mouseReleased(x, y, 0);

            assertEquals(WorkbenchShellLayout.defaultLeftDockWidth(1280f), lastLeft(ctx), 0.001f);
            assertFalse(handle.dragging());
        }
    }

    private static float lastLeft(ChromeTestContext ctx) {
        return ctx.leftWidthCalls.get(ctx.leftWidthCalls.size() - 1);
    }

    private static float lastRight(ChromeTestContext ctx) {
        return ctx.rightWidthCalls.get(ctx.rightWidthCalls.size() - 1);
    }
}

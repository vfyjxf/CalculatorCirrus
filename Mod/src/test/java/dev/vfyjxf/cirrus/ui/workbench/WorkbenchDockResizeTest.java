package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.ui.workbench.chrome.DockResizeHandleWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchDockResizeTest {

    @Test
    void dockWidthsClampExactlyToTheirDocumentedBounds() {
        assertEquals(WorkbenchBreakpoint.leftDockMin, WorkbenchBreakpoint.clampLeftDockWidth(-500f), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMin, WorkbenchBreakpoint.clampLeftDockWidth(0f), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMin, WorkbenchBreakpoint.clampLeftDockWidth(175.9f), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMin, WorkbenchBreakpoint.clampLeftDockWidth(176f), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMax, WorkbenchBreakpoint.clampLeftDockWidth(320.1f), 0.001f);
        assertEquals(WorkbenchBreakpoint.leftDockMax, WorkbenchBreakpoint.clampLeftDockWidth(9999f), 0.001f);

        assertEquals(WorkbenchBreakpoint.rightDockMin, WorkbenchBreakpoint.clampRightDockWidth(-1f), 0.001f);
        assertEquals(WorkbenchBreakpoint.rightDockMin, WorkbenchBreakpoint.clampRightDockWidth(209.9f), 0.001f);
        assertEquals(WorkbenchBreakpoint.rightDockMax, WorkbenchBreakpoint.clampRightDockWidth(380.1f), 0.001f);
        assertEquals(WorkbenchBreakpoint.rightDockMax, WorkbenchBreakpoint.clampRightDockWidth(9999f), 0.001f);
    }

    @Test
    void canvasClampCollapsesTheRightDockBeforeBreakingTheMinimumCanvas() {
        assertEquals(310f, WorkbenchBreakpoint.clampDockWidthForCanvas(228f, 310f, 1280f, 40f), 0.001f);
        assertEquals(
            WorkbenchBreakpoint.rightDockMin,
            WorkbenchBreakpoint.clampDockWidthForCanvas(320f, 380f, 720f, 40f),
            0.001f
        );
        assertEquals(300f, WorkbenchBreakpoint.clampDockWidthForCanvas(200f, 300f, 800f, 40f), 0.001f);
    }

    @Test
    void layoutKeepsTheCenterCanvasAtLeastTwoHundredTwentyPixels() {
        WorkbenchShellLayout layout = WorkbenchShellLayout
                .compute(720f, 540f, WorkbenchBreakpoint.wide, false, 320f, 380f);
        assertEquals(320f, layout.leftDockWidth(), 0.001f);
        assertEquals(140f, layout.rightDockWidth(), 0.001f);
        assertTrue(layout.stage().width() >= WorkbenchBreakpoint.minCenterCanvasWidth);
        assertEquals(720f - 40f - layout.leftDockWidth() - layout.rightDockWidth(), layout.stage().width(), 0.001f);

        WorkbenchShellLayout wide = WorkbenchShellLayout
                .compute(1280f, 720f, WorkbenchBreakpoint.wide, false, 9999f, 9999f);
        assertEquals(WorkbenchBreakpoint.leftDockMax, wide.leftDockWidth(), 0.001f);
        assertEquals(WorkbenchBreakpoint.rightDockMax, wide.rightDockWidth(), 0.001f);
        assertTrue(wide.stage().width() >= WorkbenchBreakpoint.minCenterCanvasWidth);
    }

    @Test
    void shellSettersClampDragRequestsAndDoubleClickRestoresDefaults() {
        DockFixture fixture = DockFixture.mount(1280, 720);
        try {
            assertEquals(228f, fixture.shell.leftDockWidth(), 0.001f);
            assertEquals(310f, fixture.shell.rightDockWidth(), 0.001f);

            fixture.shell.setLeftDockWidth(9999f);
            assertEquals(WorkbenchBreakpoint.leftDockMax, fixture.shell.leftDockWidth(), 0.001f);

            fixture.shell.setLeftDockWidth(-40f);
            assertEquals(WorkbenchBreakpoint.leftDockMin, fixture.shell.leftDockWidth(), 0.001f);

            fixture.shell.setRightDockWidth(9999f);
            assertEquals(WorkbenchBreakpoint.rightDockMax, fixture.shell.rightDockWidth(), 0.001f);

            fixture.shell.setRightDockWidth(-1f);
            assertEquals(WorkbenchBreakpoint.rightDockMin, fixture.shell.rightDockWidth(), 0.001f);

            fixture.shell.setLeftDockWidth(0f);
            assertEquals(0f, fixture.shell.leftDockWidth(), 0.001f);

            fixture.shell.setLeftDockWidth(WorkbenchShellLayout.defaultLeftDockWidth(1280f));
            assertEquals(228f, fixture.shell.leftDockWidth(), 0.001f);
        } finally {
            fixture.close();
        }
    }

    @Test
    void shellKeepsTheCanvasBelowTheMinimumForExtremeDraggedWidths() {
        DockFixture fixture = DockFixture.mount(720, 540);
        try {
            fixture.shell.setLeftDockWidth(WorkbenchBreakpoint.leftDockMax);
            fixture.shell.setRightDockWidth(WorkbenchBreakpoint.rightDockMax);

            assertTrue(
                fixture.shell.leftDockWidth() + fixture.shell.rightDockWidth()
                        <= 720f - 40f - WorkbenchBreakpoint.minCenterCanvasWidth + 0.001f
            );
            assertTrue(
                fixture.stageWidth() >= WorkbenchBreakpoint.minCenterCanvasWidth,
                "stage width=" + fixture.stageWidth()
            );
        } finally {
            fixture.close();
        }
    }

    @Test
    void resizeHandlesSitOnTheirOwnDockSideWithPixelWidth() {
        DockFixture fixture = DockFixture.mount(1280, 720);
        try {
            DockResizeHandleWidget left = fixture.handle(true);
            DockResizeHandleWidget right = fixture.handle(false);
            assertEquals(DockResizeHandleWidget.handleWidth, left.width());
            assertEquals(DockResizeHandleWidget.handleWidth, right.width());
            assertTrue(left.leftSide());
            assertTrue(!right.leftSide());

            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(1280f, 720f);
            assertEquals((int) layout.leftDock().right(), left.posX());
            assertEquals((int) layout.leftDock().y(), left.posY());
            assertEquals((int) (layout.rightDock().x() - DockResizeHandleWidget.handleWidth), right.posX());
        } finally {
            fixture.close();
        }
    }

    private static final class DockFixture {
        private final WorkbenchShellWidget shell;
        private final Scene scene;
        private final Widget pane;

        private DockFixture(WorkbenchShellWidget shell, Scene scene, Widget pane) {
            this.shell = shell;
            this.scene = scene;
            this.pane = pane;
        }

        private static DockFixture mount(int width, int height) {
            PaneProvider provider = new PaneProvider();
            WorkbenchShellWidget shell = new WorkbenchShellWidget(provider, headlessTabs(), WorkbenchMode.planner);
            WidgetGroup<Widget> root = new WidgetGroup<>();
            root.useStyle(sizeFull());
            root.addWidget(shell);
            Scene scene = new Scene(root);
            scene.init();
            scene.mount(SceneContext.create(host(width, height)));
            scene.setLayoutArea(width, height);
            shell.applyViewport((float) width, (float) height);
            relayout(scene);
            return new DockFixture(shell, scene, provider.planner);
        }

        private int stageWidth() {
            relayout(scene);
            return pane.absoluteBounds().width();
        }

        private DockResizeHandleWidget handle(boolean leftSide) {
            for (Widget child : shell.children()) {
                if (child instanceof DockResizeHandleWidget handle && handle.leftSide() == leftSide) {
                    return handle;
                }
            }
            throw new AssertionError("Missing dock handle leftSide=" + leftSide);
        }

        private void close() {
            shell.shutdown();
            scene.destroy();
        }

        private static void relayout(Scene scene) {
            scene.layout();
            scene.stabilize();
            scene.layout();
            scene.stabilize();
        }
    }

    private static final class PaneProvider implements WorkspaceProvider {
        private Widget planner = pane();

        @Override
        public Widget plannerWorkspace(WorkbenchShellContext ctx) {
            planner = pane();
            return planner;
        }

        @Override
        public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
            return pane();
        }

        @Override
        public Widget settingsWorkspace(WorkbenchShellContext ctx) {
            return pane();
        }

        @Override
        public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
            return pane();
        }

        private static Widget pane() {
            WidgetGroup<Widget> group = new WidgetGroup<>();
            group.useStyle(sizeFull());
            return group;
        }
    }

    private static SessionTabManager headlessTabs() {
        return new SessionTabManager(state -> {
            throw new IllegalStateException("headless tab factory must not be used");
        });
    }

    private static SceneHost host(int width, int height) {
        return new SceneHost() {
            @Override
            // Headless host: no Minecraft Font exists in unit tests, and the paths under test never read it.
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }
}

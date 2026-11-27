package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.ui.workbench.chrome.BottomTimelineStripWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.DockResizeHandleWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.LeftModeRailWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.SessionTabBarWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TopCommandDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchZenModeTest {

    private static final int[][] viewports = {{1280, 720}, {960, 540}, {640, 360}};

    @Test
    void zenModeRestoresBoundsExactlyOnToggleBack() {
        for (int[] viewport : viewports) {
            assertZenToggle(viewport[0], viewport[1]);
        }
    }

    @Test
    void zenModeHidesTabBarDockChromeAndBottomStrip() {
        ZenFixture fixture = ZenFixture.mount(1280, 720);
        try {
            fixture.shell.setZenMode(true);
            fixture.relayout();

            assertTrue(fixture.shell.zenMode());
            assertEquals(0f, fixture.shell.leftDockWidth(), 0.001f);
            assertEquals(0f, fixture.shell.rightDockWidth(), 0.001f);
            assertEquals(24f, fixture.shell.leftRailWidth(), 0.001f);

            assertEquals(0, chrome(fixture.shell, SessionTabBarWidget.class).height());
            assertEquals(0, chrome(fixture.shell, TopCommandDockWidget.class).height());
            assertEquals(0, chrome(fixture.shell, BottomTimelineStripWidget.class).height());
            assertFalse(chrome(fixture.shell, SessionTabBarWidget.class).visible());
            assertFalse(chrome(fixture.shell, TopCommandDockWidget.class).visible());
            assertFalse(chrome(fixture.shell, BottomTimelineStripWidget.class).visible());
            assertFalse(chrome(fixture.shell, DockResizeHandleWidget.class, true).visible());
            assertFalse(chrome(fixture.shell, DockResizeHandleWidget.class, false).visible());
            assertTrue(chrome(fixture.shell, LeftModeRailWidget.class).visible());

            Rect stage = fixture.stageBounds();
            assertEquals(new Rect(0, 0, 1280, 720), stage);
            assertTrue(stageRatio(stage, 1280, 720) >= WorkbenchShellLayout.zenMinStageAreaRatio);
            assertTrue(stageRatio(stage, 1280, 720) >= 0.96d);
        } finally {
            fixture.close();
        }
    }

    private static void assertZenToggle(int width, int height) {
        ZenFixture fixture = ZenFixture.mount(width, height);
        try {
            Map<String, Rect> before = fixture.snapshotBounds();
            float leftBefore = fixture.shell.leftDockWidth();
            float rightBefore = fixture.shell.rightDockWidth();

            fixture.shell.setZenMode(true);
            fixture.relayout();
            assertTrue(fixture.shell.zenMode());

            fixture.shell.setZenMode(false);
            fixture.relayout();
            assertFalse(fixture.shell.zenMode());
            assertEquals(leftBefore, fixture.shell.leftDockWidth(), 0.001f);
            assertEquals(rightBefore, fixture.shell.rightDockWidth(), 0.001f);
            assertEquals(before, fixture.snapshotBounds());
        } finally {
            fixture.close();
        }
    }

    private static double stageRatio(Rect stage, int width, int height) {
        return (double) stage.width() * stage.height() / ((double) width * height);
    }

    private static <T extends Widget> T chrome(WorkbenchShellWidget shell, Class<T> type) {
        for (Widget child : shell.children()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        throw new AssertionError("Missing chrome widget " + type.getSimpleName());
    }

    private static DockResizeHandleWidget chrome(
        WorkbenchShellWidget shell,
        Class<DockResizeHandleWidget> type,
        boolean leftSide
    ) {
        for (Widget child : shell.children()) {
            if (type.isInstance(child) && ((DockResizeHandleWidget) child).leftSide() == leftSide) {
                return type.cast(child);
            }
        }
        throw new AssertionError("Missing dock handle leftSide=" + leftSide);
    }

    private static final class ZenFixture {
        private final WorkbenchShellWidget shell;
        private final Scene scene;
        private final Widget pane;

        private ZenFixture(WorkbenchShellWidget shell, Scene scene, Widget pane) {
            this.shell = shell;
            this.scene = scene;
            this.pane = pane;
        }

        private static ZenFixture mount(int width, int height) {
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
            return new ZenFixture(shell, scene, provider.planner);
        }

        private void relayout() {
            relayout(scene);
        }

        private static void relayout(Scene scene) {
            scene.layout();
            scene.stabilize();
            scene.layout();
            scene.stabilize();
        }

        private Rect stageBounds() {
            return pane.absoluteBounds();
        }

        private Map<String, Rect> snapshotBounds() {
            Map<String, Rect> bounds = new LinkedHashMap<>();
            bounds.put("rail", chrome(shell, LeftModeRailWidget.class).bounds());
            bounds.put("tabBar", chrome(shell, SessionTabBarWidget.class).bounds());
            bounds.put("topDock", chrome(shell, TopCommandDockWidget.class).bounds());
            bounds.put("bottomStrip", chrome(shell, BottomTimelineStripWidget.class).bounds());
            bounds.put("leftHandle", chrome(shell, DockResizeHandleWidget.class, true).bounds());
            bounds.put("rightHandle", chrome(shell, DockResizeHandleWidget.class, false).bounds());
            bounds.put("stage", stageBounds());
            return bounds;
        }

        private void close() {
            shell.shutdown();
            scene.destroy();
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

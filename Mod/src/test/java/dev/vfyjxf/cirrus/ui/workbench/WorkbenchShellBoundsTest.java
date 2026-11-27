package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.ui.workbench.chrome.BottomTimelineStripWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.CommandPaletteOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.DockResizeHandleWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.LeftModeRailWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.SessionTabBarWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ToastStackWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TopCommandDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchShellBoundsTest {

    private static final int[][] viewports = {{1280, 720}, {960, 540}, {640, 360}, {427, 240}};

    @Test
    void shellMountsAndStaysInsideEveryViewport() {
        for (int[] viewport : viewports) {
            int width = viewport[0];
            int height = viewport[1];
            assertDoesNotThrow(() -> mountAndAssert(width, height), width + "x" + height + " threw");
        }
    }

    @Test
    void openOverlaysAlsoStayInsideTheViewport() {
        for (int[] viewport : viewports) {
            int width = viewport[0];
            int height = viewport[1];
            assertDoesNotThrow(() -> mountWithOverlays(width, height), width + "x" + height + " threw");
        }
    }

    private static void mountAndAssert(int width, int height) {
        PaneProvider provider = new PaneProvider();
        WorkbenchShellWidget shell = new WorkbenchShellWidget(provider, headlessTabs(), WorkbenchMode.planner);
        Scene scene = mount(shell, width, height);
        try {
            assertVisibleWidgetsInside(shell, width, height);
            assertChromeBoundToLayout(shell, provider, width, height);
            assertFalse(chrome(shell, CommandPaletteOverlayWidget.class).visible());
            assertFalse(chrome(shell, ToastStackWidget.class).visible());
        } finally {
            shell.shutdown();
            scene.destroy();
        }
    }

    private static void mountWithOverlays(int width, int height) {
        WorkbenchShellWidget shell = new WorkbenchShellWidget(
            new PaneProvider(),
            headlessTabs(),
            WorkbenchMode.planner
        );
        Scene scene = mount(shell, width, height);
        try {
            shell.openCommandPalette();
            shell.toast(Component.literal("cirrus test toast"));
            scene.layout();
            scene.stabilize();
            scene.layout();
            scene.stabilize();
            assertTrue(shell.commandPaletteOpen());
            assertTrue(chrome(shell, CommandPaletteOverlayWidget.class).visible());
            assertTrue(chrome(shell, ToastStackWidget.class).visible());
            assertVisibleWidgetsInside(shell, width, height);
        } finally {
            shell.shutdown();
            scene.destroy();
        }
    }

    private static Scene mount(WorkbenchShellWidget shell, int width, int height) {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull());
        root.addWidget(shell);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(host(width, height)));
        scene.setLayoutArea(width, height);
        shell.applyViewport((float) width, (float) height);
        scene.layout();
        scene.stabilize();
        scene.layout();
        scene.stabilize();
        return scene;
    }

    private static void assertChromeBoundToLayout(
        WorkbenchShellWidget shell,
        PaneProvider provider,
        int width,
        int height
    ) {
        WorkbenchShellLayout layout = WorkbenchShellLayout.compute(
            shell.shellWidth(),
            shell.shellHeight(),
            shell.breakpoint(),
            shell.zenMode(),
            shell.leftDockWidth(),
            shell.rightDockWidth()
        );
        assertBounds(chrome(shell, LeftModeRailWidget.class), layout.leftRail());
        assertBounds(chrome(shell, SessionTabBarWidget.class), layout.sessionTabBar());
        assertBounds(chrome(shell, TopCommandDockWidget.class), layout.topDock());
        assertBounds(chrome(shell, BottomTimelineStripWidget.class), layout.bottomStrip());
        assertBounds(chrome(shell, CommandPaletteOverlayWidget.class), layout.commandPalette());
        assertBounds(chrome(shell, ToastStackWidget.class), layout.toastStack());

        DockResizeHandleWidget leftHandle = chrome(shell, DockResizeHandleWidget.class, true);
        DockResizeHandleWidget rightHandle = chrome(shell, DockResizeHandleWidget.class, false);
        assertBounds(
            leftHandle,
            new WorkbenchShellLayout.Rect(
                layout.leftDock().right(),
                layout.leftDock().y(),
                DockResizeHandleWidget.handleWidth,
                layout.leftDock().height()
            )
        );
        assertBounds(
            rightHandle,
            new WorkbenchShellLayout.Rect(
                layout.rightDock().x() - DockResizeHandleWidget.handleWidth,
                layout.rightDock().y(),
                DockResizeHandleWidget.handleWidth,
                layout.rightDock().height()
            )
        );

        assertAbsoluteBounds(provider.planner, layout.stage());
    }

    private static void assertBounds(Widget widget, WorkbenchShellLayout.Rect expected) {
        assertPixelBounds(widget, expected, widget.posX(), widget.posY(), widget.width(), widget.height());
    }

    private static void assertAbsoluteBounds(Widget widget, WorkbenchShellLayout.Rect expected) {
        var bounds = widget.absoluteBounds();
        assertPixelBounds(widget, expected, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    private static void assertPixelBounds(
        Widget widget,
        WorkbenchShellLayout.Rect expected,
        int x,
        int y,
        int width,
        int height
    ) {
        String label = widget.getClass().getSimpleName();
        assertEquals(expected.x(), x, 1.0d, label + ".x");
        assertEquals(expected.y(), y, 1.0d, label + ".y");
        assertEquals(expected.width(), width, 1.0d, label + ".width");
        assertEquals(expected.height(), height, 1.0d, label + ".height");
    }

    private static void assertVisibleWidgetsInside(Widget widget, int width, int height) {
        if (!widget.visible()) {
            return;
        }
        var bounds = widget.absoluteBounds();
        assertTrue(
            bounds.x() >= 0 && bounds.y() >= 0 && bounds.right() <= width && bounds.bottom() <= height,
            widget.getClass().getSimpleName() + " escaped viewport: " + bounds + " in " + width + "x" + height
        );
        for (Widget child : childrenOf(widget)) {
            assertVisibleWidgetsInside(child, width, height);
        }
    }

    private static Iterable<? extends Widget> childrenOf(Widget widget) {
        return widget instanceof CompositeWidget<?> composite ? composite.children() : List.of();
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
            WidgetGroup<Widget> pane = new WidgetGroup<>();
            pane.useStyle(sizeFull());
            return pane;
        }
    }
}

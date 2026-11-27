package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchShellLayoutTest {

    private record Viewport(
        String name,
        float width,
        float height,
        WorkbenchBreakpoint breakpoint,
        float minStageWidth
    ) {}

    private static final List<Viewport> viewports = List.of(
        new Viewport("1280x720", 1280f, 720f, WorkbenchBreakpoint.wide, 230f),
        new Viewport("960x540", 960f, 540f, WorkbenchBreakpoint.wide, 230f),
        new Viewport("640x360", 640f, 360f, WorkbenchBreakpoint.standard, 260f),
        new Viewport("427x240", 427f, 240f, WorkbenchBreakpoint.compact, 400f)
    );

    @Test
    void everyRegionStaysInsideTheViewportAtAllFourBreakpoints() {
        for (Viewport viewport : viewports) {
            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(viewport.width(), viewport.height());
            for (Rect rect : regions(layout)) {
                assertTrue(
                    rect.within(layout.width(), layout.height()),
                    viewport.name() + " region escaped the viewport: " + rect
                );
            }
            WorkbenchShellLayout zen = WorkbenchShellLayout
                    .compute(viewport.width(), viewport.height(), viewport.breakpoint(), true, -1f, -1f);
            for (Rect rect : regions(zen)) {
                assertTrue(
                    rect.within(zen.width(), zen.height()),
                    viewport.name() + " zen region escaped the viewport: " + rect
                );
            }
        }
    }

    @Test
    void tilingRegionsNeverOverlap() {
        for (Viewport viewport : viewports) {
            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(viewport.width(), viewport.height());
            for (int first = 0; first < tilingRegions(layout).size(); first++) {
                for (int second = first + 1; second < tilingRegions(layout).size(); second++) {
                    Rect left = tilingRegions(layout).get(first);
                    Rect right = tilingRegions(layout).get(second);
                    if (left.empty() || right.empty()) {
                        continue;
                    }
                    assertFalse(
                        left.intersects(right),
                        viewport.name() + " tiling regions overlap: " + left + " vs " + right
                    );
                }
            }
        }
    }

    @Test
    void stageKeepsTheMinimumCanvasWidthFromTheLayoutTable() {
        for (Viewport viewport : viewports) {
            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(viewport.width(), viewport.height());
            assertEquals(viewport.breakpoint(), layout.breakpoint(), viewport.name());
            assertTrue(
                layout.stage().width() >= viewport.minStageWidth(),
                viewport.name() + " stage width=" + layout.stage().width()
            );
            assertEquals(
                viewport.width() - layout.leftRail().width() - layout.leftDock().width() - layout.rightDock().width(),
                layout.stage().width(),
                0.001f,
                viewport.name()
            );
        }
    }

    @Test
    void breakpointChromeSizesFollowTheLayoutTable() {
        WorkbenchShellLayout wide = WorkbenchShellLayout.compute(1280f, 720f);
        assertEquals(40f, wide.leftRail().width(), 0.001f);
        assertEquals(24f, wide.sessionTabBar().height(), 0.001f);
        assertEquals(34f, wide.topDock().height(), 0.001f);
        assertEquals(28f, wide.bottomStrip().height(), 0.001f);
        assertEquals(228f, wide.leftDockWidth(), 0.001f);
        assertEquals(310f, wide.rightDockWidth(), 0.001f);
        assertEquals(140f, wide.minimap().width(), 0.001f);
        assertEquals(80f, wide.minimap().height(), 0.001f);

        WorkbenchShellLayout standard = WorkbenchShellLayout.compute(640f, 360f);
        assertEquals(32f, standard.leftRail().width(), 0.001f);
        assertEquals(22f, standard.sessionTabBar().height(), 0.001f);
        assertEquals(30f, standard.topDock().height(), 0.001f);
        assertEquals(24f, standard.bottomStrip().height(), 0.001f);
        assertEquals(0f, standard.leftDockWidth(), 0.001f);
        assertEquals(214f, standard.rightDockWidth(), 0.001f);
        assertEquals(104f, standard.minimap().width(), 0.001f);

        WorkbenchShellLayout compact = WorkbenchShellLayout.compute(427f, 240f);
        assertEquals(26f, compact.leftRail().width(), 0.001f);
        assertEquals(0f, compact.sessionTabBar().height(), 0.001f);
        assertEquals(28f, compact.topDock().height(), 0.001f);
        assertEquals(22f, compact.bottomStrip().height(), 0.001f);
        assertEquals(0f, compact.leftDockWidth(), 0.001f);
        assertEquals(0f, compact.rightDockWidth(), 0.001f);
        assertTrue(compact.minimap().empty());
    }

    @Test
    void zenModeReleasesAtLeastNinetyTwoPercentOfTheViewport() {
        for (Viewport viewport : viewports) {
            WorkbenchShellLayout zen = WorkbenchShellLayout
                    .compute(viewport.width(), viewport.height(), viewport.breakpoint(), true, -1f, -1f);
            assertTrue(zen.zen());
            assertTrue(
                zen.stageAreaRatio() >= WorkbenchShellLayout.zenMinStageAreaRatio,
                viewport.name() + " zen stage ratio=" + zen.stageAreaRatio()
            );
            assertTrue(zen.stageAreaRatio() >= 0.96f, viewport.name() + " zen stage ratio=" + zen.stageAreaRatio());
            assertEquals(0f, zen.leftDockWidth(), 0.001f);
            assertEquals(0f, zen.rightDockWidth(), 0.001f);
            assertTrue(zen.sessionTabBar().empty());
            assertTrue(zen.topDock().empty());
            assertTrue(zen.leftDock().empty());
            assertTrue(zen.rightDock().empty());
            assertTrue(zen.bottomStrip().empty());
            assertEquals(24f, zen.leftRail().width(), 0.001f);
            assertEquals(24f, zen.leftRail().height(), 0.001f);
            assertFalse(zen.minimap().empty());
            assertTrue(zen.stage().contains(zen.minimap()));
        }
    }

    @Test
    void commandPaletteIsCenteredAndInsideTheViewport() {
        for (Viewport viewport : viewports) {
            WorkbenchShellLayout layout = WorkbenchShellLayout.compute(viewport.width(), viewport.height());
            Rect palette = layout.commandPalette();
            assertEquals(
                Math.min(WorkbenchShellLayout.paletteMaxWidth, viewport.width() - 40f),
                palette.width(),
                0.001f,
                viewport.name()
            );
            assertEquals(Math.max(36f, 0.12f * viewport.height()), palette.y(), 0.001f, viewport.name());
            assertEquals((viewport.width() - palette.width()) / 2f, palette.x(), 0.001f, viewport.name());
            assertTrue(palette.within(viewport.width(), viewport.height()), viewport.name());
            assertTrue(layout.toastStack().within(viewport.width(), viewport.height()), viewport.name());
            assertTrue(layout.minimap().within(viewport.width(), viewport.height()), viewport.name());
        }
    }

    @Test
    void workspaceOnlyModeGivesTheWholeAreaBelowTheTabBar() {
        for (Viewport viewport : viewports) {
            WorkbenchShellLayout planner = WorkbenchShellLayout.compute(viewport.width(), viewport.height());
            WorkbenchShellLayout workspaceOnly = planner.workspaceOnly();
            Rect expected = new Rect(
                planner.leftRail().width(),
                planner.sessionTabBar().height(),
                viewport.width() - planner.leftRail().width(),
                viewport.height() - planner.sessionTabBar().height()
            );
            assertEquals(expected, workspaceOnly.stage(), viewport.name());
            assertTrue(workspaceOnly.leftDock().empty(), viewport.name());
            assertTrue(workspaceOnly.rightDock().empty(), viewport.name());
            assertTrue(workspaceOnly.topDock().empty(), viewport.name());
            assertTrue(workspaceOnly.bottomStrip().empty(), viewport.name());
            assertEquals(planner.leftRail(), workspaceOnly.leftRail(), viewport.name());
            assertEquals(planner.sessionTabBar(), workspaceOnly.sessionTabBar(), viewport.name());
        }
    }

    @Test
    void defaultDockWidthsFollowTheBreakpointTable() {
        assertEquals(228f, WorkbenchShellLayout.compute(1280f, 720f).leftDockWidth(), 0.001f);
        assertEquals(0f, WorkbenchShellLayout.compute(640f, 360f).leftDockWidth(), 0.001f);
        assertEquals(0f, WorkbenchShellLayout.compute(427f, 240f).leftDockWidth(), 0.001f);
        assertEquals(310f, WorkbenchShellLayout.defaultRightDockWidth(1280f), 0.001f);
        assertEquals(249.6f, WorkbenchShellLayout.defaultRightDockWidth(960f), 0.01f);
        assertEquals(214f, WorkbenchShellLayout.defaultRightDockWidth(640f), 0.001f);
        assertEquals(0f, WorkbenchShellLayout.defaultRightDockWidth(427f), 0.001f);
        assertEquals(228f, WorkbenchShellLayout.defaultLeftDockWidth(1280f), 0.001f);
    }

    @Test
    void degenerateViewportsStayContained() {
        WorkbenchShellLayout zero = WorkbenchShellLayout.compute(0f, 0f);
        for (Rect rect : regions(zero)) {
            assertTrue(rect.empty());
        }
        WorkbenchShellLayout tiny = WorkbenchShellLayout.compute(120f, 80f);
    }

    private static List<Rect> regions(WorkbenchShellLayout layout) {
        return List.of(
            layout.leftRail(),
            layout.sessionTabBar(),
            layout.topDock(),
            layout.leftDock(),
            layout.stage(),
            layout.rightDock(),
            layout.bottomStrip(),
            layout.minimap(),
            layout.commandPalette(),
            layout.toastStack()
        );
    }

    private static List<Rect> tilingRegions(WorkbenchShellLayout layout) {
        return List.of(
            layout.leftRail(),
            layout.sessionTabBar(),
            layout.topDock(),
            layout.leftDock(),
            layout.stage(),
            layout.rightDock(),
            layout.bottomStrip()
        );
    }
}

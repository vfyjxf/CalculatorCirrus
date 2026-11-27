package dev.vfyjxf.cirrus.ui.workbench;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchBreakpointContractTest {

    @Test
    void freshClassificationMatchesSpecThresholds() {
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(1280f));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(960f));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(720f));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(719f));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(640f));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(520f));
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(519f));
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(427f));
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(0f));
    }

    @Test
    void freshClassificationIgnoresPreviousWhenWidthIsFarFromBoundaries() {
        float[] widths = {0f, 100f, 400f, 515f, 525f, 600f, 715f, 725f, 960f, 1920f};
        for (float width : widths) {
            WorkbenchBreakpoint exact = WorkbenchBreakpoint.classify(width);
            for (WorkbenchBreakpoint previous : WorkbenchBreakpoint.values()) {
                assertEquals(
                    exact,
                    WorkbenchBreakpoint.classify(width, previous),
                    "previous=" + previous + " width=" + width
                );
            }
        }
    }

    @Test
    void wideToStandardHysteresis() {
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(720f, WorkbenchBreakpoint.wide));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(719f, WorkbenchBreakpoint.wide));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(717f, WorkbenchBreakpoint.wide));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(716f, WorkbenchBreakpoint.wide));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(715f, WorkbenchBreakpoint.wide));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(600f, WorkbenchBreakpoint.wide));
    }

    @Test
    void standardToWideAndCompactHysteresis() {
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(720f, WorkbenchBreakpoint.standard));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(723f, WorkbenchBreakpoint.standard));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(724f, WorkbenchBreakpoint.standard));

        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(520f, WorkbenchBreakpoint.standard));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(519f, WorkbenchBreakpoint.standard));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(517f, WorkbenchBreakpoint.standard));
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(516f, WorkbenchBreakpoint.standard));
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(400f, WorkbenchBreakpoint.standard));
    }

    @Test
    void compactWideningRequiresFourPixelsPastBoundary() {
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(519f, WorkbenchBreakpoint.compact));
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(523f, WorkbenchBreakpoint.compact));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(524f, WorkbenchBreakpoint.compact));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(719f, WorkbenchBreakpoint.compact));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(723f, WorkbenchBreakpoint.compact));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(724f, WorkbenchBreakpoint.compact));
        assertEquals(WorkbenchBreakpoint.wide, WorkbenchBreakpoint.classify(1280f, WorkbenchBreakpoint.compact));
    }

    @Test
    void previousTierIsKeptOnlyInsideDeadBand() {
        assertEquals(WorkbenchBreakpoint.compact, WorkbenchBreakpoint.classify(400f, WorkbenchBreakpoint.wide));
        assertEquals(WorkbenchBreakpoint.standard, WorkbenchBreakpoint.classify(517f, WorkbenchBreakpoint.wide));
    }

    @Test
    void railAndDockDimensionsMatchSpecTable() {
        assertEquals(40f, WorkbenchBreakpoint.wide.leftRailWidth());
        assertEquals(32f, WorkbenchBreakpoint.standard.leftRailWidth());
        assertEquals(26f, WorkbenchBreakpoint.compact.leftRailWidth());

        assertEquals(28f, WorkbenchBreakpoint.wide.modeRailIconSize());
        assertEquals(24f, WorkbenchBreakpoint.standard.modeRailIconSize());
        assertEquals(20f, WorkbenchBreakpoint.compact.modeRailIconSize());

        assertEquals(24f, WorkbenchBreakpoint.wide.sessionTabBarHeight());
        assertEquals(22f, WorkbenchBreakpoint.standard.sessionTabBarHeight());
        assertEquals(0f, WorkbenchBreakpoint.compact.sessionTabBarHeight());

        assertEquals(34f, WorkbenchBreakpoint.wide.topDockHeight());
        assertEquals(30f, WorkbenchBreakpoint.standard.topDockHeight());
        assertEquals(28f, WorkbenchBreakpoint.compact.topDockHeight());

        assertEquals(28f, WorkbenchBreakpoint.wide.bottomStripHeight());
        assertEquals(24f, WorkbenchBreakpoint.standard.bottomStripHeight());
        assertEquals(22f, WorkbenchBreakpoint.compact.bottomStripHeight());
    }

    @Test
    void defaultDockWidthsMatchSpecTable() {
        assertEquals(228f, WorkbenchBreakpoint.wide.leftDockDefaultWidth(1280f));
        assertEquals(0f, WorkbenchBreakpoint.standard.leftDockDefaultWidth(640f));
        assertEquals(0f, WorkbenchBreakpoint.compact.leftDockDefaultWidth(427f));

        assertEquals(310f, WorkbenchBreakpoint.wide.rightDockDefaultWidth(1280f));
        assertEquals(249.6f, WorkbenchBreakpoint.wide.rightDockDefaultWidth(960f), 0.0001f);
        assertEquals(248f, WorkbenchBreakpoint.wide.rightDockDefaultWidth(720f), 0.0001f);
        assertEquals(214f, WorkbenchBreakpoint.standard.rightDockDefaultWidth(640f));
        assertEquals(0f, WorkbenchBreakpoint.compact.rightDockDefaultWidth(427f));
    }

    @Test
    void minimapAndVisibilityFlagsMatchSpecTable() {
        assertEquals(140f, WorkbenchBreakpoint.wide.minimapWidth());
        assertEquals(80f, WorkbenchBreakpoint.wide.minimapHeight());
        assertEquals(104f, WorkbenchBreakpoint.standard.minimapWidth());
        assertEquals(60f, WorkbenchBreakpoint.standard.minimapHeight());
        assertEquals(0f, WorkbenchBreakpoint.compact.minimapWidth());
        assertEquals(0f, WorkbenchBreakpoint.compact.minimapHeight());

        assertTrue(WorkbenchBreakpoint.wide.showsLeftDock());
        assertFalse(WorkbenchBreakpoint.standard.showsLeftDock());
        assertFalse(WorkbenchBreakpoint.compact.showsLeftDock());

        assertTrue(WorkbenchBreakpoint.wide.showsRightDock());
        assertTrue(WorkbenchBreakpoint.standard.showsRightDock());
        assertFalse(WorkbenchBreakpoint.compact.showsRightDock());

        assertTrue(WorkbenchBreakpoint.wide.showsMinimap());
        assertTrue(WorkbenchBreakpoint.standard.showsMinimap());
        assertFalse(WorkbenchBreakpoint.compact.showsMinimap());
    }

    @Test
    void dockWidthClampsUseSpecBounds() {
        assertEquals(176f, WorkbenchBreakpoint.clampLeftDockWidth(100f));
        assertEquals(320f, WorkbenchBreakpoint.clampLeftDockWidth(999f));
        assertEquals(228f, WorkbenchBreakpoint.clampLeftDockWidth(228f));

        assertEquals(210f, WorkbenchBreakpoint.clampRightDockWidth(100f));
        assertEquals(380f, WorkbenchBreakpoint.clampRightDockWidth(999f));
        assertEquals(272f, WorkbenchBreakpoint.clampRightDockWidth(272f));
    }

    @Test
    void dockClampForCanvasKeepsCenterAtLeast220Pixels() {
        float rail = WorkbenchBreakpoint.wide.leftRailWidth();
        assertEquals(300f, WorkbenchBreakpoint.clampDockWidthForCanvas(228f, 300f, 1280f, rail));
        assertEquals(214f, WorkbenchBreakpoint.clampDockWidthForCanvas(0f, 214f, 640f, rail));

        float shrunk = WorkbenchBreakpoint.clampDockWidthForCanvas(0f, 380f, 560f, rail);
        assertEquals(300f, shrunk);
        assertTrue(560f - rail - 0f - shrunk >= WorkbenchBreakpoint.minCenterCanvasWidth);

        assertEquals(
            WorkbenchBreakpoint.rightDockMin,
            WorkbenchBreakpoint.clampDockWidthForCanvas(0f, 380f, 400f, rail)
        );
        assertEquals(
            WorkbenchBreakpoint.rightDockMin,
            WorkbenchBreakpoint.clampDockWidthForCanvas(228f, 380f, 500f, rail)
        );
    }

    @Test
    void modeLabelsUseWorkbenchModeKeyFamily() {
        assertEquals("cirrus.ui.workbench.mode.planner", WorkbenchMode.planner.labelKey().key());
        assertEquals("cirrus.ui.workbench.mode.facilities", WorkbenchMode.facilities.labelKey().key());
        assertEquals("cirrus.ui.workbench.mode.database", WorkbenchMode.database.labelKey().key());
        assertEquals("cirrus.ui.workbench.mode.settings", WorkbenchMode.settings.labelKey().key());
    }
}

package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchReadoutContractTest {

    @Test
    void countBadgeAbbreviatesAtLadderBoundaries() {
        assertEquals("0", WorkbenchCountBadge.abbreviate(0L));
        assertEquals("999", WorkbenchCountBadge.abbreviate(999L));
        assertEquals("1k", WorkbenchCountBadge.abbreviate(1000L));
        assertEquals("1.2k", WorkbenchCountBadge.abbreviate(1200L));
        assertEquals("1.2k", WorkbenchCountBadge.abbreviate(1234L));
        assertEquals("1.1k", WorkbenchCountBadge.abbreviate(1050L));
        assertEquals("999.9k", WorkbenchCountBadge.abbreviate(999_900L));
        assertEquals("1M", WorkbenchCountBadge.abbreviate(999_999L));
        assertEquals("1M", WorkbenchCountBadge.abbreviate(1_000_000L));
        assertEquals("1.2M", WorkbenchCountBadge.abbreviate(1_200_000L));
        assertEquals("-5", WorkbenchCountBadge.abbreviate(-5L));
    }

    @Test
    void cellRowQuantisesLitCellsWithRound() {
        assertEquals(0, WorkbenchCellRow.of(10, () -> 0.0).litCount());
        assertEquals(5, WorkbenchCellRow.of(10, () -> 0.49).litCount());
        assertEquals(6, WorkbenchCellRow.of(10, () -> 0.55).litCount());
        assertEquals(10, WorkbenchCellRow.of(10, () -> 1.0).litCount());
        assertEquals(10, WorkbenchCellRow.of(10, () -> 2.0).litCount());
        assertEquals(0, WorkbenchCellRow.of(10, () -> -0.5).litCount());
        assertEquals(0, WorkbenchCellRow.of(10, () -> Double.NaN).litCount());
    }

    @Test
    void cellRowResolvesZonesByTrailingFraction() {
        WorkbenchCellRow row = WorkbenchCellRow.of(10, () -> 0.8).setZones(
            List.of(
                new WorkbenchCellRow.Zone(0.55, "info"),
                new WorkbenchCellRow.Zone(0.80, "warn"),
                new WorkbenchCellRow.Zone(1.0, "danger")
            )
        );

        assertEquals("info", row.zoneStyle(4));
        assertEquals("warn", row.zoneStyle(5));
        assertEquals("warn", row.zoneStyle(7));
        assertEquals("danger", row.zoneStyle(8));
        assertEquals("danger", row.zoneStyle(9));
        assertEquals(8, row.litCount());
        assertEquals(3, row.zones().size());
        assertEquals("", WorkbenchCellRow.of(4, () -> 0.0).zoneStyle(0));
    }

    @Test
    void cellRowClampsGapAndLaysCellsOutWithoutOverlap() {
        WorkbenchCellRow row = WorkbenchCellRow.of(10, () -> 0.0);
        assertEquals(2, row.gap());
        assertEquals(0, row.setGap(-3).gap());
        assertEquals(4, row.setGap(9).gap());
        assertEquals(3, row.setGap(3).gap());
        assertEquals(4, row.setGap(3).setGap(9).gap());

        assertEquals(8, WorkbenchCellRow.cellWidth(100, 10, 2));
        assertEquals(0, WorkbenchCellRow.cellX(0, 100, 10, 2, 0));
        assertEquals(92, WorkbenchCellRow.cellX(0, 100, 10, 2, 9));
        assertEquals(100, WorkbenchCellRow.cellX(0, 100, 10, 2, 9) + WorkbenchCellRow.cellWidth(100, 10, 2));
    }

    @Test
    void ringGaugeExposesSegmentGeometry() {
        WorkbenchRingGauge gauge = WorkbenchRingGauge.of(() -> 0.5);
        assertEquals(96, WorkbenchRingGauge.segments);
        assertEquals(-88.125, gauge.segmentAngle(0), 1e-9);
        assertEquals(268.125, gauge.segmentAngle(95), 1e-9);
        assertTrue(gauge.segmentLit(0));
        assertTrue(gauge.segmentLit(47));
        assertFalse(gauge.segmentLit(48));
        assertFalse(gauge.segmentLit(95));

        assertEquals(2, gauge.setThickness(0).thickness());
        assertEquals(6, gauge.setThickness(99).thickness());
        assertEquals(360, gauge.setSweep(0, 999).extentDegrees());
        assertEquals(180, gauge.setSweep(90, 180).extentDegrees());
        assertEquals(0.0, WorkbenchRingGauge.of(() -> Double.NaN).fraction(), 1e-9);
        assertTrue(gauge.setShowPercent(true).showPercent());
    }

    @Test
    void meterBarClampsSegmentsAndMapsStyleClasses() {
        WorkbenchMeterBar bar = WorkbenchMeterBar.of(() -> 0.42);
        assertEquals(0.42, bar.fillFraction(), 1e-9);
        assertEquals(0.0, WorkbenchMeterBar.of(() -> Double.NaN).fillFraction(), 1e-9);
        assertFalse(bar.vertical());
        assertTrue(bar.setVertical(true).vertical());

        WorkbenchMeterBar.Segment clamped = new WorkbenchMeterBar.Segment(2.0, null);
        assertEquals(1.0, clamped.fraction(), 1e-9);
        assertEquals("", clamped.styleClass());

        WorkbenchMeterBar segmented = WorkbenchMeterBar
                .segmented(new WorkbenchMeterBar.Segment(0.5, "ok"), new WorkbenchMeterBar.Segment(0.5, "danger"));
        assertEquals(2, segmented.segments().size());

        CirrusPalette palette = CirrusPalette.obsidianBlueprint;
        assertEquals(palette.costS(), WorkbenchMeterBar.styleColor(palette, "ok"));
        assertEquals(palette.costH(), WorkbenchMeterBar.styleColor(palette, "warn"));
        assertEquals(palette.ruleHardBan(), WorkbenchMeterBar.styleColor(palette, "danger"));
        assertEquals(palette.focusRing(), WorkbenchMeterBar.styleColor(palette, "unknown"));
    }

    @Test
    void iconButtonTintsByState() {
        WorkbenchIconButton button = WorkbenchIconButton.of(CirrusTextures.Icon.search, () -> {});
        assertSame(CirrusTextures.Icon.search, button.icon());
        assertTrue(button.enabled());
        assertEquals(CirrusPalette.obsidianBlueprint.textPrimary(), button.iconTint());

        button.enabled(false);
        assertFalse(button.enabled());
        assertEquals(CirrusPalette.obsidianBlueprint.textDisabled(), button.iconTint());

        button.enabled(true).palette(() -> CirrusPalette.atelierStone);
        assertEquals(CirrusPalette.atelierStone.textPrimary(), button.iconTint());
    }
}

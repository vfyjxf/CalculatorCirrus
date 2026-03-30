package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixMilestonesContractTest {

    @Test
    void matrixViewExposesStableRowCount() {
        MatrixViewWidget widget = new MatrixViewWidget();

        assertEquals(6, widget.visibleRowCount());
    }

    @Test
    void milestonesViewExposesStablePhaseColumnCount() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertEquals(4, widget.phaseColumnCount());
    }

    @Test
    void milestonesUseIndependentVerticalScrollPerPhase() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        widget.debugSetPhaseScroll(0, 48);
        widget.debugSetPhaseScroll(1, 12);

        assertEquals(48f, widget.debugPhaseScroll(0));
        assertEquals(12f, widget.debugPhaseScroll(1));
        assertEquals(0f, widget.debugPhaseScroll(2));
    }

    @Test
    void matrixViewExposesIndependentTableScrollState() {
        MatrixViewWidget widget = new MatrixViewWidget();

        widget.debugSetTableScroll(54);

        assertEquals(54f, widget.debugTableScroll());
    }

    @Test
    void matrixLedgerChipTextIsTrimmedBeforeRendering() {
        assertEquals("+24 Stabiliz..", MatrixViewWidget.trimLedgerChipLabel("+24 Stabilizer Mesh"));
        assertEquals("+6 Wire", MatrixViewWidget.trimLedgerChipLabel("+6 Wire"));
    }

    @Test
    void matrixRowsUseDenseFixedHeight() {
        assertEquals(30, MatrixViewWidget.debugRowHeight());
    }

    @Test
    void milestoneCardsHideLegacyStageTextAndAmountBadge() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertFalse(widget.debugStepCardsShowStageText());
        assertFalse(widget.debugStepCardsShowAmountBadge());
        assertFalse(widget.debugPhaseHeadersUseExternalProgressBar());
    }

    @Test
    void amountTextUsesWhiteWithHardShadow() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugAmountTextUsesShadow());
        assertEquals(OmniCalcColors.textWhite, widget.debugAmountTextColor());
    }

    @Test
    void milestoneProgressBarsUsePerContainerInsets() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertEquals(0, widget.debugSummaryProgressInset());
        assertEquals(4, widget.debugPhaseProgressInset());
        assertEquals(0, widget.debugStepCardProgressInset());
        assertTrue(widget.debugSummaryProgressTouchesHorizontalEdges());
        assertTrue(widget.debugSummaryProgressTouchesBottomEdge());
        assertTrue(widget.debugSummaryProgressMergedTopBorder());
    }

    @Test
    void milestoneSummaryHeaderRemovesLegacyScrollableDescription() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertFalse(widget.debugSummaryHeaderShowsSubtitle());
        assertFalse(widget.debugPhaseHeadersShowSubtitle());
    }

    @Test
    void milestoneCardsSupportManualChecklistState() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugSupportsManualStepToggle());
        assertFalse(widget.debugManualStepChecked(0, 0));

        widget.debugToggleManualStep(0, 0);

        assertTrue(widget.debugManualStepChecked(0, 0));
    }

    @Test
    void milestoneProgressUsesUnifiedColorAndCheckboxToggleChrome() throws Exception {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugProgressUsesUnifiedFillColor());
        assertFalse(widget.debugManualToggleUsesSwitchTexture());
        assertEquals(10, milestoneBoardConstant("manualToggleWidth"));
        assertEquals(10, milestoneBoardConstant("manualToggleHeight"));
    }

    @Test
    void milestoneSummaryHeaderUsesLighterSinglePanelChrome() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugSummaryHeaderUsesMinimalChrome());
        assertEquals("Milestone Progress", widget.debugSummaryHeaderTitle());
        assertEquals("10 / 36", widget.debugSummaryHeaderProgressText());
        assertFalse(widget.debugUsesPlaceholderHeaderTexture());
    }

    @Test
    void milestonePhaseColumnsAlwaysReserveVisibleVerticalScrollbar() {
        MilestonesViewWidget widget = new MilestonesViewWidget();

        assertTrue(widget.debugPhaseColumnsReserveScrollbarSpace());
        assertEquals(10, widget.debugPhaseScrollbarWidth());
    }

    @Test
    void milestoneScrollRoutingPrefersPhaseVerticalScrollInsidePhaseBody() {
        assertFalse(MilestonesViewWidget.shouldRouteToHorizontalScroll(
                false, 0f, false, true
        ));
        assertTrue(MilestonesViewWidget.shouldRouteToHorizontalScroll(
                false, 0f, true, false
        ));
        assertTrue(MilestonesViewWidget.shouldRouteToHorizontalScroll(
                true, 0f, false, true
        ));
    }

    @Test
    void calculatorLightPanelTypographyUsesClassicMinecraftContrast() {
        assertEquals(0xFF3F3F3F, OmniCalcColors.textPrimary);
        assertEquals(0xFF555555, OmniCalcColors.textSecondary);
        assertEquals(0xFFFFFFFF, OmniCalcColors.textWhite);
    }

    private static int milestoneBoardConstant(String name) throws Exception {
        Class<?> boardClass = Class.forName(
                "dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.MilestonesViewWidget$MilestoneBoardWidget");
        Field field = boardClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }
}

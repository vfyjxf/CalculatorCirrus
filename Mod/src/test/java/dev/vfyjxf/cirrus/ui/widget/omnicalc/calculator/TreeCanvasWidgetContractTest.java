package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeCanvasWidgetContractTest {

    @Test
    void togglingMaterialPanelDoesNotResetViewport() {
        TreeCanvasWidget widget = new TreeCanvasWidget();
        widget.setViewport(120, 240, 0.75f);

        widget.toggleMaterialPanel();

        assertEquals(120, widget.getPanX());
        assertEquals(240, widget.getPanY());
        assertEquals(0.75f, widget.getZoom());
    }

    @Test
    void materialPanelUsesCirrusPixelPalette() {
        TreeCanvasWidget.MaterialPanelPalette palette = TreeCanvasWidget.debugMaterialPanelPalette();

        assertEquals(0xFFCFCFCF, palette.baseFill());
        assertEquals(0xFF555555, palette.shadow());
        assertEquals(0xFFFFFFFF, palette.highlight());
        assertEquals(0xFFE6E6E6, palette.frame());
    }

    @Test
    void minimapChromeRemovesTitleAndTrackedGlow() {
        assertEquals(false, TreeCanvasWidget.debugMinimapShowsTitle());
        assertEquals(false, TreeCanvasWidget.debugTrackedLinksUseGlow());
    }

    @Test
    void stepCardsUseCustomSeparatorAndForkActionButton() {
        TreeCanvasWidget.StepCardChrome chrome = TreeCanvasWidget.debugStepCardChrome();

        assertEquals(true, chrome.usesDrawnSeparator());
        assertEquals(true, chrome.focusButtonUsesForkGlyph());
        assertEquals(8, chrome.sectionGap());
    }

    @Test
    void minimapViewportIndicatorAlwaysClampedInsideContentArea() {
        TreeCanvasWidget.MinimapViewportRect rect = TreeCanvasWidget.debugClampMinimapViewportRect(
                10, 20, 48, 32,
                8, 16, 64, 42
        );

        assertEquals(true, rect.x() >= 10);
        assertEquals(true, rect.y() >= 20);
        assertEquals(true, rect.x() + rect.w() <= 58);
        assertEquals(true, rect.y() + rect.h() <= 52);
    }
}

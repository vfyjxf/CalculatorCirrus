package dev.vfyjxf.cirrus.ui.widget.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusSlotGroupRendererTest {

    @Test
    void defaultsToWhiteBorderAndBuildsTightRowMajorCells() {
        assertEquals(0xFFFFFFFF, CirrusSlotGroupRenderer.defaultBorderColor);
        assertEquals(1, CirrusSlotGroupRenderer.borderWidth);
        assertEquals(
            List.of(
                new CirrusSlotGroupRenderer.Cell(0, 0),
                new CirrusSlotGroupRenderer.Cell(1, 0),
                new CirrusSlotGroupRenderer.Cell(2, 0),
                new CirrusSlotGroupRenderer.Cell(0, 1)
            ),
            CirrusSlotGroupRenderer.rowMajorCells(4, 3)
        );
        assertEquals(
            List.of(
                new CirrusSlotGroupRenderer.Cell(2, 3),
                new CirrusSlotGroupRenderer.Cell(3, 3),
                new CirrusSlotGroupRenderer.Cell(2, 4),
                new CirrusSlotGroupRenderer.Cell(3, 4)
            ),
            CirrusSlotGroupRenderer.rectCells(2, 3, 2, 2)
        );
    }

    @Test
    void irregularBorderOnlyDrawsOuterEdges() {
        List<CirrusSlotGroupRenderer.BorderSegment> segments = CirrusSlotGroupRenderer.borderSegments(
            List.of(
                new CirrusSlotGroupRenderer.Cell(0, 0),
                new CirrusSlotGroupRenderer.Cell(1, 0),
                new CirrusSlotGroupRenderer.Cell(0, 1)
            ),
            18
        );

        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(0, -1, 36, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(36, 0, 1, 18)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(0, 36, 18, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(-1, 0, 1, 36)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(-1, -1, 1, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(36, -1, 1, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(-1, 36, 1, 1)));
        assertFalse(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(18, 0, 1, 18)));
        assertFalse(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(0, 18, 18, 1)));
    }

    @Test
    void rectangularBorderMergesToOnlyFourOuterEdges() {
        List<CirrusSlotGroupRenderer.BorderSegment> segments = CirrusSlotGroupRenderer
                .borderSegments(CirrusSlotGroupRenderer.rectCells(2, 2), 18);

        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(0, -1, 36, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(0, 36, 36, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(-1, 0, 1, 36)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(36, 0, 1, 36)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(-1, -1, 1, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(36, -1, 1, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(-1, 36, 1, 1)));
        assertTrue(segments.contains(new CirrusSlotGroupRenderer.BorderSegment(36, 36, 1, 1)));
    }

    @Test
    void matrixCellsUseOnlyOccupiedCells() {
        assertEquals(
            List.of(
                new CirrusSlotGroupRenderer.Cell(0, 0),
                new CirrusSlotGroupRenderer.Cell(2, 0),
                new CirrusSlotGroupRenderer.Cell(1, 1)
            ),
            CirrusSlotGroupRenderer.matrixCells(new boolean[][]{{true, false, true}, {false, true, false}})
        );
    }

    @Test
    void reportsBoundsForIrregularShapes() {
        CirrusSlotGroupRenderer.Bounds bounds = CirrusSlotGroupRenderer.bounds(
            List.of(
                new CirrusSlotGroupRenderer.Cell(2, 1),
                new CirrusSlotGroupRenderer.Cell(4, 3),
                new CirrusSlotGroupRenderer.Cell(3, 2)
            )
        );

        assertEquals(2, bounds.minColumn());
        assertEquals(1, bounds.minRow());
        assertEquals(4, bounds.maxColumn());
        assertEquals(3, bounds.maxRow());
        assertEquals(54, bounds.width(18));
        assertEquals(54, bounds.height(18));
    }
}

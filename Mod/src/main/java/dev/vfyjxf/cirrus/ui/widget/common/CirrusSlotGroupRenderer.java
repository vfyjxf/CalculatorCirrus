package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CirrusSlotGroupRenderer {

    public static final int defaultBorderColor = 0xFFFFFFFF;
    public static final int borderWidth = 1;

    private CirrusSlotGroupRenderer() {
    }

    public record Cell(int column, int row) {
    }

    public record BorderSegment(int x, int y, int width, int height) {
    }

    public record Bounds(int minColumn, int minRow, int maxColumn, int maxRow) {
        public int width(int slotSize) {
            return (maxColumn - minColumn + 1) * slotSize;
        }

        public int height(int slotSize) {
            return (maxRow - minRow + 1) * slotSize;
        }

        public boolean empty() {
            return maxColumn < minColumn || maxRow < minRow;
        }
    }

    public static void renderSingle(SceneCanvas canvas, int x, int y, int slotSize) {
        renderRect(canvas, x, y, 1, 1, slotSize, defaultBorderColor);
    }

    public static void renderSingle(SceneCanvas canvas, int x, int y, int slotSize, int borderColor) {
        renderRect(canvas, x, y, 1, 1, slotSize, borderColor);
    }

    public static void renderRect(SceneCanvas canvas, int x, int y, int columns, int rows, int slotSize) {
        renderRect(canvas, x, y, columns, rows, slotSize, defaultBorderColor);
    }

    public static void renderRect(
        SceneCanvas canvas,
        int x,
        int y,
        int columns,
        int rows,
        int slotSize,
        int borderColor
    ) {
        if (columns <= 0 || rows <= 0 || slotSize <= 0) {
            return;
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                canvas.texture(
                    CirrusTextures.Background.Slot.base,
                    x + column * slotSize,
                    y + row * slotSize,
                    slotSize,
                    slotSize
                );
            }
        }
        int width = columns * slotSize;
        int height = rows * slotSize;
        renderOuterRectBorder(canvas, x, y, width, height, borderColor);
    }

    public static void renderCells(SceneCanvas canvas, int x, int y, int slotSize, Collection<Cell> cells) {
        renderCells(canvas, x, y, slotSize, cells, defaultBorderColor);
    }

    public static void renderRowMajorCells(SceneCanvas canvas, int x, int y, int slotSize, int count, int columns) {
        renderRowMajorCells(canvas, x, y, slotSize, count, columns, defaultBorderColor);
    }

    public static void renderRowMajorCells(
        SceneCanvas canvas,
        int x,
        int y,
        int slotSize,
        int count,
        int columns,
        int borderColor
    ) {
        if (slotSize <= 0 || count <= 0 || columns <= 0) {
            return;
        }
        int cappedColumns = Math.min(count, columns);
        int fullRows = count / columns;
        int remainder = count % columns;
        if (remainder == 0) {
            renderRect(canvas, x, y, cappedColumns, fullRows, slotSize, borderColor);
            return;
        }
        if (fullRows == 0) {
            renderRect(canvas, x, y, remainder, 1, slotSize, borderColor);
            return;
        }

        for (int row = 0; row < fullRows; row++) {
            for (int column = 0; column < columns; column++) {
                canvas.texture(
                    CirrusTextures.Background.Slot.base,
                    x + column * slotSize,
                    y + row * slotSize,
                    slotSize,
                    slotSize
                );
            }
        }
        int lastRowY = y + fullRows * slotSize;
        for (int column = 0; column < remainder; column++) {
            canvas.texture(
                CirrusTextures.Background.Slot.base,
                x + column * slotSize,
                lastRowY,
                slotSize,
                slotSize
            );
        }

        int fullWidth = columns * slotSize;
        int partialWidth = remainder * slotSize;
        int fullHeight = fullRows * slotSize;
        int totalHeight = fullHeight + slotSize;
        canvas.fill(x - borderWidth, y - borderWidth, fullWidth + borderWidth * 2, borderWidth, borderColor);
        canvas.fill(x - borderWidth, y, borderWidth, totalHeight, borderColor);
        canvas.fill(x + fullWidth, y, borderWidth, fullHeight, borderColor);
        canvas.fill(x + partialWidth, lastRowY, borderWidth, slotSize, borderColor);
        canvas.fill(x + partialWidth, lastRowY, fullWidth - partialWidth + borderWidth, borderWidth, borderColor);
        canvas.fill(x - borderWidth, y + totalHeight, partialWidth + borderWidth * 2, borderWidth, borderColor);
    }

    public static void renderCells(
        SceneCanvas canvas,
        int x,
        int y,
        int slotSize,
        Collection<Cell> cells,
        int borderColor
    ) {
        if (slotSize <= 0 || cells.isEmpty()) {
            return;
        }
        Set<Cell> uniqueCells = new LinkedHashSet<>(cells);
        for (Cell cell : uniqueCells) {
            canvas.texture(
                CirrusTextures.Background.Slot.base,
                x + cell.column() * slotSize,
                y + cell.row() * slotSize,
                slotSize,
                slotSize
            );
        }
        for (BorderSegment segment : outerBorderSegments(uniqueCells, slotSize)) {
            canvas.fill(x + segment.x(), y + segment.y(), segment.width(), segment.height(), borderColor);
        }
    }

    public static List<Cell> rowMajorCells(int count, int columns) {
        if (count <= 0 || columns <= 0) {
            return List.of();
        }
        ArrayList<Cell> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new Cell(i % columns, i / columns));
        }
        return result;
    }

    public static List<Cell> matrixCells(boolean[][] occupied) {
        if (occupied == null || occupied.length == 0) {
            return List.of();
        }
        ArrayList<Cell> result = new ArrayList<>();
        for (int row = 0; row < occupied.length; row++) {
            boolean[] columns = occupied[row];
            if (columns == null) {
                continue;
            }
            for (int column = 0; column < columns.length; column++) {
                if (columns[column]) {
                    result.add(new Cell(column, row));
                }
            }
        }
        return List.copyOf(result);
    }

    public static List<Cell> rectCells(int columns, int rows) {
        return rectCells(0, 0, columns, rows);
    }

    public static List<Cell> rectCells(int startColumn, int startRow, int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return List.of();
        }
        ArrayList<Cell> result = new ArrayList<>(columns * rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                result.add(new Cell(startColumn + column, startRow + row));
            }
        }
        return result;
    }

    public static List<BorderSegment> borderSegments(Collection<Cell> cells, int slotSize) {
        return outerBorderSegments(cells, slotSize);
    }

    public static List<BorderSegment> outerBorderSegments(Collection<Cell> cells, int slotSize) {
        if (slotSize <= 0 || cells.isEmpty()) {
            return List.of();
        }
        Set<Cell> uniqueCells = new LinkedHashSet<>(cells);
        ArrayList<BorderSegment> segments = new ArrayList<>(uniqueCells.size() * 4);
        for (Cell cell : uniqueCells) {
            int column = cell.column();
            int row = cell.row();
            int x = column * slotSize;
            int y = row * slotSize;
            if (!uniqueCells.contains(new Cell(column, row - 1))) {
                segments.add(new BorderSegment(x, y - borderWidth, slotSize, borderWidth));
            }
            if (!uniqueCells.contains(new Cell(column + 1, row))) {
                segments.add(new BorderSegment(x + slotSize, y, borderWidth, slotSize));
            }
            if (!uniqueCells.contains(new Cell(column, row + 1))) {
                segments.add(new BorderSegment(x, y + slotSize, slotSize, borderWidth));
            }
            if (!uniqueCells.contains(new Cell(column - 1, row))) {
                segments.add(new BorderSegment(x - borderWidth, y, borderWidth, slotSize));
            }
        }
        addCornerSegments(segments, uniqueCells, slotSize);
        return mergeBorderSegments(segments);
    }

    public static Bounds bounds(Collection<Cell> cells) {
        if (cells.isEmpty()) {
            return new Bounds(0, 0, -1, -1);
        }
        int minColumn = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxColumn = Integer.MIN_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (Cell cell : cells) {
            minColumn = Math.min(minColumn, cell.column());
            minRow = Math.min(minRow, cell.row());
            maxColumn = Math.max(maxColumn, cell.column());
            maxRow = Math.max(maxRow, cell.row());
        }
        return new Bounds(minColumn, minRow, maxColumn, maxRow);
    }

    private static void renderOuterRectBorder(SceneCanvas canvas, int x, int y, int width, int height, int borderColor) {
        canvas.fill(x - borderWidth, y - borderWidth, width + borderWidth * 2, borderWidth, borderColor);
        canvas.fill(x - borderWidth, y + height, width + borderWidth * 2, borderWidth, borderColor);
        canvas.fill(x - borderWidth, y, borderWidth, height, borderColor);
        canvas.fill(x + width, y, borderWidth, height, borderColor);
    }

    private static void addCornerSegments(List<BorderSegment> segments, Set<Cell> uniqueCells, int slotSize) {
        for (Cell cell : uniqueCells) {
            int column = cell.column();
            int row = cell.row();
            int x = column * slotSize;
            int y = row * slotSize;
            boolean north = !uniqueCells.contains(new Cell(column, row - 1));
            boolean east = !uniqueCells.contains(new Cell(column + 1, row));
            boolean south = !uniqueCells.contains(new Cell(column, row + 1));
            boolean west = !uniqueCells.contains(new Cell(column - 1, row));
            if (north && west) {
                segments.add(new BorderSegment(x - borderWidth, y - borderWidth, borderWidth, borderWidth));
            }
            if (north && east) {
                segments.add(new BorderSegment(x + slotSize, y - borderWidth, borderWidth, borderWidth));
            }
            if (south && west) {
                segments.add(new BorderSegment(x - borderWidth, y + slotSize, borderWidth, borderWidth));
            }
            if (south && east) {
                segments.add(new BorderSegment(x + slotSize, y + slotSize, borderWidth, borderWidth));
            }
        }
    }

    private static List<BorderSegment> mergeBorderSegments(List<BorderSegment> segments) {
        if (segments.size() <= 1) {
            return List.copyOf(segments);
        }
        ArrayList<BorderSegment> horizontal = new ArrayList<>();
        ArrayList<BorderSegment> vertical = new ArrayList<>();
        ArrayList<BorderSegment> other = new ArrayList<>();
        for (BorderSegment segment : segments) {
            if (segment.width() == borderWidth && segment.height() == borderWidth) {
                other.add(segment);
            } else if (segment.height() == borderWidth) {
                horizontal.add(segment);
            } else if (segment.width() == borderWidth) {
                vertical.add(segment);
            } else {
                other.add(segment);
            }
        }
        horizontal.sort(Comparator
            .comparingInt(BorderSegment::y)
            .thenComparingInt(BorderSegment::x)
            .thenComparingInt(BorderSegment::width));
        vertical.sort(Comparator
            .comparingInt(BorderSegment::x)
            .thenComparingInt(BorderSegment::y)
            .thenComparingInt(BorderSegment::height));

        ArrayList<BorderSegment> result = new ArrayList<>(segments.size());
        appendMergedHorizontal(result, horizontal);
        appendMergedVertical(result, vertical);
        result.addAll(other);
        return List.copyOf(result);
    }

    private static void appendMergedHorizontal(List<BorderSegment> result, List<BorderSegment> segments) {
        BorderSegment current = null;
        for (BorderSegment segment : segments) {
            if (current == null) {
                current = segment;
                continue;
            }
            if (current.y() == segment.y()
                && current.height() == segment.height()
                && current.x() + current.width() == segment.x()) {
                current = new BorderSegment(current.x(), current.y(), current.width() + segment.width(), current.height());
            } else {
                result.add(current);
                current = segment;
            }
        }
        if (current != null) {
            result.add(current);
        }
    }

    private static void appendMergedVertical(List<BorderSegment> result, List<BorderSegment> segments) {
        BorderSegment current = null;
        for (BorderSegment segment : segments) {
            if (current == null) {
                current = segment;
                continue;
            }
            if (current.x() == segment.x()
                && current.width() == segment.width()
                && current.y() + current.height() == segment.y()) {
                current = new BorderSegment(current.x(), current.y(), current.width(), current.height() + segment.height());
            } else {
                result.add(current);
                current = segment;
            }
        }
        if (current != null) {
            result.add(current);
        }
    }
}

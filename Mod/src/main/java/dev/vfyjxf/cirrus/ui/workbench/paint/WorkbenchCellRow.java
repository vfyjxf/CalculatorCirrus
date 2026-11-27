package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class WorkbenchCellRow extends Widget {
    private static final int defaultGap = 2;
    private static final int minGap = 0;
    private static final int maxGap = 4;

    public record Zone(double upTo, String styleClass) {
        public Zone {
            upTo = clamp01(upTo);
            styleClass = styleClass == null ? "" : styleClass;
        }
    }

    private final int cells;
    private final DoubleSupplier fill;
    private final List<Zone> zones = new ArrayList<>();
    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private int gap = defaultGap;

    public WorkbenchCellRow(int cells, DoubleSupplier fill) {
        if (cells <= 0) {
            throw new IllegalArgumentException("cells must be positive, got " + cells);
        }
        this.cells = cells;
        this.fill = fill == null ? () -> 0.0 : fill;
        useStyle(sizeOf(cells * 8, 6));
    }

    public static WorkbenchCellRow of(int cells, DoubleSupplier fill) {
        return new WorkbenchCellRow(cells, fill);
    }

    public WorkbenchCellRow palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public WorkbenchCellRow setGap(int gap) {
        this.gap = Math.max(minGap, Math.min(maxGap, gap));
        return this;
    }

    public int gap() {
        return gap;
    }

    public int cells() {
        return cells;
    }

    public WorkbenchCellRow setZones(List<Zone> zones) {
        this.zones.clear();
        if (zones != null) {
            this.zones.addAll(zones);
        }
        return this;
    }

    public List<Zone> zones() {
        return List.copyOf(zones);
    }

    public int litCount() {
        double clamped = clamp01(fill.getAsDouble());
        long lit = Math.round(clamped * cells);
        return (int) Math.max(0, Math.min(cells, lit));
    }

    public String zoneStyle(int cellIndex) {
        if (cellIndex < 0 || cellIndex >= cells) {
            throw new IllegalArgumentException("cellIndex out of range: " + cellIndex);
        }
        double fraction = (cellIndex + 1) / (double) cells;
        for (int index = 0; index < zones.size(); index++) {
            Zone zone = zones.get(index);
            if (fraction <= zone.upTo()) {
                return zone.styleClass();
            }
        }
        return "";
    }

    public static int cellWidth(int width, int cells, int gap) {
        int available = Math.max(0, width - gap * (cells - 1));
        return cells <= 0 ? 0 : available / cells;
    }

    public static int cellX(int x, int width, int cells, int gap, int index) {
        int cellWidth = cellWidth(width, cells, gap);
        int available = Math.max(0, width - gap * (cells - 1));
        int remainder = available - cellWidth * cells;
        return x + index * (cellWidth + gap) + Math.min(index, remainder);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette theme = palette.get();
        int cellWidth = cellWidth(width(), cells, gap);
        int lit = litCount();
        for (int index = 0; index < cells; index++) {
            int x = cellX(0, width(), cells, gap, index);
            if (index < lit) {
                canvas.fill(x, 0, cellWidth, height(), styleColor(theme, zoneStyle(index)));
            } else {
                canvas.fill(x, 0, cellWidth, height(), theme.textDisabled());
            }
        }
    }

    private static int styleColor(CirrusPalette palette, String styleClass) {
        return WorkbenchMeterBar.styleColor(palette, styleClass);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}

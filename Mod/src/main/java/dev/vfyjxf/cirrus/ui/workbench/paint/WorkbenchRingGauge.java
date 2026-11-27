package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class WorkbenchRingGauge extends Widget {
    public static final int segments = 96;
    public static final int minThickness = 2;
    public static final int maxThickness = 6;

    private final DoubleSupplier fraction;
    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private int thickness = 4;
    private int startDegrees = -90;
    private int extentDegrees = 360;
    private boolean showPercent;

    public WorkbenchRingGauge(DoubleSupplier fraction) {
        this.fraction = fraction == null ? () -> 0.0 : fraction;
        useStyle(sizeOf(24, 24));
    }

    public static WorkbenchRingGauge of(DoubleSupplier fraction) {
        return new WorkbenchRingGauge(fraction);
    }

    public WorkbenchRingGauge palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public WorkbenchRingGauge setThickness(int thickness) {
        this.thickness = Math.max(minThickness, Math.min(maxThickness, thickness));
        return this;
    }

    public int thickness() {
        return thickness;
    }

    public WorkbenchRingGauge setSweep(int startDegrees, int extentDegrees) {
        this.startDegrees = startDegrees;
        this.extentDegrees = Math.max(0, Math.min(360, extentDegrees));
        return this;
    }

    public int startDegrees() {
        return startDegrees;
    }

    public int extentDegrees() {
        return extentDegrees;
    }

    public WorkbenchRingGauge setShowPercent(boolean showPercent) {
        this.showPercent = showPercent;
        return this;
    }

    public boolean showPercent() {
        return showPercent;
    }

    public double fraction() {
        double value = fraction.getAsDouble();
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public double segmentAngle(int index) {
        return segmentAngle(startDegrees, extentDegrees, index);
    }

    public static double segmentAngle(int startDegrees, int extentDegrees, int index) {
        return startDegrees + extentDegrees * ((index + 0.5) / segments);
    }

    public boolean segmentLit(int index) {
        return (index + 0.5) / segments <= fraction();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        CirrusPalette theme = palette.get();
        float centerX = width / 2f;
        float centerY = height / 2f;
        float outerRadius = Math.min(width, height) / 2f;
        float innerRadius = Math.max(0f, outerRadius - thickness);
        if (outerRadius <= 0f) {
            return;
        }
        for (int index = 0; index < segments; index++) {
            double angle = Math.toRadians(segmentAngle(index));
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            int color = segmentLit(index) ? theme.focusRing() : theme.hairlineBorder();
            canvas.line(
                centerX + cos * innerRadius,
                centerY + sin * innerRadius,
                centerX + cos * outerRadius,
                centerY + sin * outerRadius,
                1f,
                color
            );
        }
        if (showPercent) {
            String label = Math.round(fraction() * 100.0) + "%";
            canvas.drawCenteredString(label, (int) centerX, (int) centerY - 4, theme.textPrimary());
        }
    }
}

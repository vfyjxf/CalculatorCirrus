package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeStyle;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.StyleVar;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class WorkbenchMeterBar extends Widget {
    public static final int chamfer = 3;

    public record Segment(double fraction, @Nullable String styleClass) {
        public Segment {
            fraction = clamp01(fraction);
            styleClass = styleClass == null ? "" : styleClass;
        }
    }

    private final DoubleSupplier fill;
    private final List<Segment> segments;
    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private boolean vertical;

    public WorkbenchMeterBar(@Nullable DoubleSupplier fill, List<Segment> segments) {
        this.fill = fill == null ? () -> 0.0 : fill;
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        useStyle(sizeOf(64, 6));
    }

    public static WorkbenchMeterBar of(DoubleSupplier fill) {
        return new WorkbenchMeterBar(fill, List.of());
    }

    public static WorkbenchMeterBar segmented(List<Segment> segments) {
        return new WorkbenchMeterBar(null, segments == null ? List.of() : segments);
    }

    public static WorkbenchMeterBar segmented(Segment... segments) {
        return segmented(segments == null ? List.of() : List.of(segments));
    }

    public WorkbenchMeterBar palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public WorkbenchMeterBar setVertical(boolean vertical) {
        this.vertical = vertical;
        return this;
    }

    public boolean vertical() {
        return vertical;
    }

    public List<Segment> segments() {
        return segments;
    }

    public double fillFraction() {
        return clamp01(fill.getAsDouble());
    }

    public static int styleColor(CirrusPalette palette, @Nullable String styleClass) {
        if (styleClass == null || styleClass.isBlank()) {
            return ink("--meter-focus", palette.focusRing());
        }
        return switch (styleClass) {
            case "ok", "good", "success", "prefer", "s", "scarce" -> ink("--meter-ok", palette.costS());
            case "warn", "warning", "caution", "h", "human", "active" -> ink("--meter-warn", palette.costH());
            case "danger", "error", "ban" -> ink("--meter-danger", palette.ruleHardBan());
            case "p", "passive", "wait" -> ink("--meter-p", palette.costP());
            case "k", "capital" -> ink("--meter-k", palette.costK());
            case "c", "cognitive" -> ink("--meter-c", palette.costC());
            default -> ink("--meter-focus", palette.focusRing());
        };
    }

    private static int ink(String name, int fallback) {
        return CirrusThemeStyle.color(StyleVar.color(name), fallback);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette theme = palette.get();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.fill(0, 0, width, height, theme.recessedSurface());
        if (segments.isEmpty()) {
            renderFill(canvas, width, height, 0.0, fillFraction(), theme.focusRing());
            return;
        }
        double cursor = 0.0;
        for (int index = 0; index < segments.size() && cursor < 1.0; index++) {
            Segment segment = segments.get(index);
            double end = Math.min(1.0, cursor + segment.fraction());
            if (end > cursor) {
                renderFill(canvas, width, height, cursor, end, styleColor(theme, segment.styleClass()));
            }
            cursor = end;
        }
    }

    private void renderFill(SceneCanvas canvas, int width, int height, double from, double to, int color) {
        if (to <= from) {
            return;
        }
        if (vertical) {
            int y = (int) Math.round(height * (1.0 - to));
            int yEnd = (int) Math.round(height * (1.0 - from));
            fillChamfered(canvas, 0, y, width, yEnd - y, color, true);
        } else {
            int x = (int) Math.round(width * from);
            int xEnd = (int) Math.round(width * to);
            fillChamfered(canvas, x, 0, xEnd - x, height, color, false);
        }
    }

    private static void fillChamfered(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        int color,
        boolean vertical
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int effectiveChamfer = Math.min(chamfer, vertical ? width / 2 : height / 2);
        if (effectiveChamfer <= 0) {
            canvas.fill(x, y, width, height, color);
            return;
        }
        if (vertical) {
            canvas.fill(x, y + effectiveChamfer, 1, height - 2 * effectiveChamfer, color);
            if (width > 2) {
                canvas.fill(x + 1, y, width - 2, height, color);
            }
            canvas.fill(x + width - 1, y + effectiveChamfer, 1, height - 2 * effectiveChamfer, color);
        } else {
            canvas.fill(x + effectiveChamfer, y, width - 2 * effectiveChamfer, 1, color);
            if (height > 2) {
                canvas.fill(x, y + 1, width, height - 2, color);
            }
            canvas.fill(x + effectiveChamfer, y + height - 1, width - 2 * effectiveChamfer, 1, color);
        }
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}

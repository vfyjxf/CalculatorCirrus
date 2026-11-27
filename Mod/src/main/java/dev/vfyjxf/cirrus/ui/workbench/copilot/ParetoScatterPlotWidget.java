package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingEffect;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * 2D Pareto frontier scatter plot: a square plot board, faint tick grid, frontier polyline, glowing points and axis dropdowns.
 */
public class ParetoScatterPlotWidget extends Widget {

    public static final int dotDiameter = 8;
    public static final int selectedRing = 12;
    public static final int compareRing = 10;
    public static final int gridDivisions = 4;
    public static final int headerHeight = 12;
    public static final int footerHeight = 14;
    public static final double dragThresholdPx = 3.0;

    private final CoPilotHost host;
    private ParetoGeometry.Axis xAxis = ParetoGeometry.Axis.capital;
    private ParetoGeometry.Axis yAxis = ParetoGeometry.Axis.human;
    private @Nullable AxisPopup popup;
    private @Nullable String pressedPlanId;
    private @Nullable String draggingPlanId;
    private double pressX;
    private double pressY;
    private @Nullable String hoveredPlanId;

    public ParetoScatterPlotWidget(CoPilotHost host) {
        this.host = Objects.requireNonNull(host, "host");
        useStyle(flexGrow(1), flexShrink(1), minWidth(0), minHeight(0));
        onMouseClicked((input, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (axisButtonRect(true).contains(local.x(), local.y())) {
                togglePopup(true);
                return EventDispatch.consumed;
            }
            if (axisButtonRect(false).contains(local.x(), local.y())) {
                togglePopup(false);
                return EventDispatch.consumed;
            }
            pressedPlanId = pointAt(local.x(), local.y());
            pressX = local.x();
            pressY = local.y();
            draggingPlanId = null;
            return pressedPlanId != null ? EventDispatch.consumed : EventDispatch.pass;
        });
        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (pressedPlanId == null) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (draggingPlanId == null
                    && Math.abs(local.x() - pressX) <= dragThresholdPx
                    && Math.abs(local.y() - pressY) <= dragThresholdPx) {
                return EventDispatch.pass;
            }
            draggingPlanId = pressedPlanId;
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            String dragged = draggingPlanId;
            String pressed = pressedPlanId;
            draggingPlanId = null;
            pressedPlanId = null;
            if (dragged != null) {
                String target = compareTargetAt(local.x(), local.y(), dragged);
                if (target != null) {
                    host.setComparePlan(target);
                    CirrusUiSound.playClickSound();
                }
                return EventDispatch.consumed;
            }
            String clicked = pressed == null ? null : pointAt(local.x(), local.y());
            if (clicked != null) {
                host.selectPlan(clicked);
                CirrusUiSound.playClickSound();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseLeave((mouseX, mouseY, context) -> hoveredPlanId = null);
    }

    public CoPilotHost host() {
        return host;
    }

    public ParetoGeometry.Axis xAxis() {
        return xAxis;
    }

    public ParetoGeometry.Axis yAxis() {
        return yAxis;
    }

    public ParetoScatterPlotWidget setXAxis(ParetoGeometry.Axis axis) {
        this.xAxis = Objects.requireNonNull(axis, "axis");
        return this;
    }

    public ParetoScatterPlotWidget setYAxis(ParetoGeometry.Axis axis) {
        this.yAxis = Objects.requireNonNull(axis, "axis");
        return this;
    }

    public ParetoScatterPlotWidget swapAxes() {
        ParetoGeometry.Axis previousX = xAxis;
        xAxis = yAxis;
        yAxis = previousX;
        return this;
    }

    public List<ProductionPlan> plans() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot == null || snapshot.result() == null) {
            return List.of();
        }
        return snapshot.result().paretoPlans();
    }

    public List<ParetoGeometry.Point> points() {
        List<ProductionPlan> plans = plans();
        if (plans.isEmpty()) {
            return List.of();
        }
        ParetoGeometry.Extent extent = ParetoGeometry.Extent.of(plans, xAxis, yAxis).padded();
        return ParetoGeometry.projectAll(plotFrame(), extent, plans, xAxis, yAxis);
    }

    public ParetoGeometry.Extent extent() {
        List<ProductionPlan> plans = plans();
        if (plans.isEmpty()) {
            return new ParetoGeometry.Extent(0.0, 1.0, 0.0, 1.0);
        }
        return ParetoGeometry.Extent.of(plans, xAxis, yAxis).padded();
    }

    public Rect plotFrame() {
        int available = Math.min(Math.max(1, width() - 8), Math.max(1, height() - headerHeight - footerHeight - 8));
        int x = Math.max(0, (width() - available) / 2);
        int y = headerHeight + Math.max(0, (height() - headerHeight - footerHeight - available) / 2);
        return new Rect(x, y, available, available);
    }

    public Rect axisButtonRect(boolean forX) {
        int btnWidth = Math.min(62, Math.max(40, (width() - 16) / 3));
        int y = Math.max(0, height() - footerHeight + 1);
        return new Rect(forX ? 4 : btnWidth + 8, y, btnWidth, footerHeight - 2);
    }

    public @Nullable String pointAt(double localX, double localY) {
        ParetoGeometry.Point point = ParetoGeometry.nearest(points(), localX, localY);
        return point == null ? null : point.planId();
    }

    public @Nullable String compareTargetAt(double localX, double localY, @Nullable String draggedPlanId) {
        if (draggedPlanId == null) {
            return null;
        }
        String target = pointAt(localX, localY);
        if (target == null || target.equals(draggedPlanId)) {
            return null;
        }
        return target;
    }

    public boolean popupOpen() {
        return popup != null;
    }

    public void togglePopup(boolean forX) {
        if (popup != null) {
            closePopup();
            return;
        }
        if (!lifecycle().mounted()) {
            return;
        }
        AxisPopup next = new AxisPopup(host::palette, forX ? xAxis : yAxis, axis -> {
            if (forX) {
                setXAxis(axis);
            } else {
                setYAxis(axis);
            }
        });
        next.setClickGroup(this);
        next.onClickOutside(context -> scheduleClosePopup());
        Rect anchor = axisButtonRect(forX);
        next.useEffect(FloatingEffect.create(this, FloatingPlacement.topStart, offset(4, anchor.x()), shift(4)));
        addRootWidget(scene().root(), next);
        popup = next;
    }

    public void closePopup() {
        AxisPopup active = popup;
        popup = null;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    private void scheduleClosePopup() {
        if (popup == null || !lifecycle().mounted()) {
            return;
        }
        scene().nextTick(this::closePopup);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette palette = host.palette();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        Rect frame = plotFrame();
        canvas.fill(0, 0, width, height, palette.recessedSurface());
        canvas.strokeRect(0, 0, width, height, palette.hairlineBorder(), 1);
        renderGrid(canvas, palette, frame);
        hoveredPlanId = pointAt(mouseX, mouseY);
        renderPoints(canvas, palette, frame, mouseX, mouseY);
        renderHeader(canvas, palette, width);
        renderFooter(canvas, palette, mouseX, mouseY);
        renderHoverCard(canvas, palette, width);
    }

    private void renderGrid(SceneCanvas canvas, CirrusPalette palette, Rect frame) {
        for (int step = 0; step <= gridDivisions; step++) {
            int x = frame.x() + frame.width() * step / gridDivisions;
            int y = frame.y() + frame.height() * step / gridDivisions;
            canvas.fill(x, frame.y(), 1, frame.height(), palette.canvasGridMinor());
            canvas.fill(frame.x(), y, frame.width(), 1, palette.canvasGridMinor());
        }
        canvas.strokeRect(frame.x(), frame.y(), frame.width(), frame.height(), palette.canvasGridMajor(), 1);
    }

    private void renderPoints(SceneCanvas canvas, CirrusPalette palette, Rect frame, int mouseX, int mouseY) {
        List<ParetoGeometry.Point> points = points();
        if (points.isEmpty()) {
            Component emptyText = ChromeText
                    .fit(canvas, CirrusLangs.Workbench.Flow.bannerNoTarget.get(), Math.max(12, frame.width() - 12));
            int tw = ChromeText.width(canvas, emptyText);
            CoPilotPaint.text(
                canvas,
                emptyText,
                frame.x() + Math.max(4, (frame.width() - tw) / 2),
                ChromeText.centeredY(frame.y(), frame.height(), canvas),
                palette.textMuted()
            );
            return;
        }
        List<ParetoGeometry.Point> ordered = ParetoGeometry.frontierOrder(points);
        for (int index = 0; index + 1 < ordered.size(); index++) {
            ParetoGeometry.Point from = ordered.get(index);
            ParetoGeometry.Point to = ordered.get(index + 1);
            canvas.line(from.px(), from.py(), to.px(), to.py(), 1.5f, (palette.focusRing() & 0x00FFFFFF) | 0x66000000);
        }
        String selectedPlanId = host.snapshot().selectedPlanId();
        String comparePlanId = host.snapshot().comparePlanId();
        for (ParetoGeometry.Point point : points) {
            boolean selected = point.planId().equals(selectedPlanId);
            boolean compared = point.planId().equals(comparePlanId);
            int ink = selected ? palette.focusRing() : palette.costS();
            canvas.circle(point.px(), point.py(), dotDiameter / 2f + 2.5f, (ink & 0x00FFFFFF) | 0x33000000);
            canvas.circle(point.px(), point.py(), dotDiameter / 2f, ink);
            if (selected) {
                canvas.circle(point.px(), point.py(), selectedRing / 2f, 0, 1.5f, palette.focusRing());
            }
            if (compared) {
                canvas.circle(point.px(), point.py(), compareRing / 2f, 0, 1.5f, palette.costH());
            }
            if (point.planId().equals(hoveredPlanId) || point.planId().equals(draggingPlanId)) {
                canvas.circle(point.px(), point.py(), selectedRing / 2f + 2f, 0, 1f, palette.textPrimary());
            }
        }
        if (points.size() == 1) {
            ProductionPlan only = plans().get(0);
            Component label = ChromeText
                    .fit(canvas, Component.translatable(only.strategyLabelKey()), Math.max(12, frame.width() - 12));
            int lw = ChromeText.width(canvas, label);
            int lx = Math.max(frame.x() + 4, Math.min(frame.right() - lw - 4, Math.round(points.get(0).px()) - lw / 2));
            int ly = Math.min(frame.bottom() - 12, Math.round(points.get(0).py()) + 10);
            CoPilotPaint.text(canvas, label, lx, ly, palette.textSecondary());
        }
    }

    private void renderHeader(SceneCanvas canvas, CirrusPalette palette, int width) {
        Component legend = CoPilotPaint.withSubject(CirrusLangs.Workbench.Pareto.yAxis, yAxis.letter());
        CoPilotPaint.text(canvas, CoPilotPaint.fit(canvas, legend, width - 10), 4, 2, palette.textSecondary());
        Component xLegend = CoPilotPaint.withSubject(CirrusLangs.Workbench.Pareto.xAxis, xAxis.shortLabel());
        CoPilotPaint.textRight(canvas, xLegend, width - 4, 2, palette.textSecondary());
    }

    private void renderFooter(SceneCanvas canvas, CirrusPalette palette, int mouseX, int mouseY) {
        Rect xButton = axisButtonRect(true);
        Rect yButton = axisButtonRect(false);
        renderAxisButton(canvas, palette, xButton, "X", xAxis, xButton.contains(mouseX, mouseY));
        renderAxisButton(canvas, palette, yButton, "Y", yAxis, yButton.contains(mouseX, mouseY));
        CoPilotPaint.textRight(
            canvas,
            CirrusLangs.Workbench.Pareto.betterDir.get(),
            width() - 4,
            Math.max(0, height() - footerHeight + 3),
            palette.textMuted()
        );
    }

    private void renderAxisButton(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        String prefix,
        ParetoGeometry.Axis axis,
        boolean hover
    ) {
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            false,
            hover,
            false,
            false
        );
        CoPilotPaint.text(
            canvas,
            CoPilotPaint.fit(
                canvas,
                Component.literal(prefix + ": " + axis.shortLabel().getString()),
                Math.max(8, rect.width() - 6)
            ),
            rect.x() + 3,
            ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
            palette.textPrimary()
        );
    }

    private void renderHoverCard(SceneCanvas canvas, CirrusPalette palette, int width) {
        String planId = hoveredPlanId;
        if (planId == null) {
            return;
        }
        ProductionPlan plan = null;
        for (ProductionPlan candidate : plans()) {
            if (candidate.planId().equals(planId)) {
                plan = candidate;
                break;
            }
        }
        if (plan == null) {
            return;
        }
        CostVector cost = plan.totalCost();
        int cardWidth = Math.min(width - 8, 116);
        int cardHeight = 34;
        int x = 4;
        int y = headerHeight + 2;
        canvas.fill(x, y, cardWidth, cardHeight, palette.popoverSurface());
        canvas.strokeRect(x, y, cardWidth, cardHeight, palette.focusRing(), 1);
        Component strategy = Component.translatable(plan.strategyLabelKey());
        CoPilotPaint
                .text(canvas, CoPilotPaint.fit(canvas, strategy, cardWidth - 6), x + 3, y + 2, palette.textPrimary());
        CoPilotPaint.text(
            canvas,
            Component.literal(
                costLine(ParetoGeometry.Axis.human, cost.humanActiveSeconds()) + "  "
                        + costLine(ParetoGeometry.Axis.passive, cost.passiveWaitSeconds())
            ),
            x + 3,
            y + 12,
            palette.textSecondary()
        );
        CoPilotPaint.text(
            canvas,
            Component.literal(
                costLine(ParetoGeometry.Axis.capital, cost.machineSetupCapital()) + "  "
                        + costLine(ParetoGeometry.Axis.scarcity, cost.materialScarcity())
            ),
            x + 3,
            y + 21,
            palette.textSecondary()
        );
    }

    private static String costLine(ParetoGeometry.Axis axis, double value) {
        return axis.shortLabel().getString() + " " + CoPilotPaint.decimal(value, 1);
    }

    /** The five-row popup of an axis dropdown. */
    static final class AxisPopup extends WidgetGroup<Widget> {

        private static final int rowHeight = 14;
        private static final int width = 66;
        private final @Nullable Supplier<CirrusPalette> paletteSupplier;
        private final ParetoGeometry.Axis current;
        private final Consumer<ParetoGeometry.Axis> onPick;

        AxisPopup(ParetoGeometry.Axis current, Consumer<ParetoGeometry.Axis> onPick) {
            this(null, current, onPick);
        }

        AxisPopup(
            @Nullable Supplier<CirrusPalette> paletteSupplier,
            ParetoGeometry.Axis current,
            Consumer<ParetoGeometry.Axis> onPick
        ) {
            this.paletteSupplier = paletteSupplier;
            this.current = current;
            this.onPick = onPick;
            useStyle(sizeOf(width, rowHeight * ParetoGeometry.Axis.values().length + 2));
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                int index = (int) ((local.y() - 1) / rowHeight);
                ParetoGeometry.Axis[] all = ParetoGeometry.Axis.values();
                if (index >= 0 && index < all.length) {
                    onPick.accept(all[index]);
                    context.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = paletteSupplier != null ? paletteSupplier.get() : CirrusUiTheme.palette(this);
            int width = width();
            int height = height();
            OreUiPaint.popover(canvas, 0, 0, width, height, palette);
            ParetoGeometry.Axis[] all = ParetoGeometry.Axis.values();
            int hoveredRow = (int) ((mouseY - 1) / rowHeight);
            for (int index = 0; index < all.length; index++) {
                int rowY = 1 + index * rowHeight;
                boolean hover = index == hoveredRow;
                if (hover) {
                    canvas.fill(1, rowY, width - 2, rowHeight, palette.cardSurfaceHover());
                }
                boolean selected = all[index] == current;
                canvas.drawString(
                    all[index].shortLabel(),
                    6,
                    ChromeText.centeredY(rowY, rowHeight, canvas),
                    selected ? palette.focusRing() : palette.textPrimary(),
                    false
                );
            }
        }
    }
}

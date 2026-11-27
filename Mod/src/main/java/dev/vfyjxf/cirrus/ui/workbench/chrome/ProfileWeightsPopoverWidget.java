package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.ParetoGeometry;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.ui.widget.SliderWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z5 five-axis cost weight popover: H/P/K/S/C sliders, numeric readouts, reset and apply.
 */
public class ProfileWeightsPopoverWidget extends WidgetGroup<Widget> {
    public static final float width = 260f;
    public static final float height = 220f;
    public static final int axes = ChromeProfiles.weightAxes;
    public static final double sliderMin = 0.0;
    public static final double sliderMax = 2.0;
    public static final double sliderStep = 0.05;
    public static final int pad = 8;
    public static final int headerTop = 5;
    public static final int headerHeight = 10;
    public static final int rowTop = 22;
    public static final int rowHeight = 26;
    public static final int labelWidth = 12;
    public static final int sliderLeft = pad + labelWidth + 4;
    public static final int sliderWidth = 148;
    public static final int sliderHeight = 12;
    public static final int footerTop = 186;
    public static final int footerHeight = 20;
    public static final int buttonWidth = 100;
    public static final long outsideClickGraceMillis = 120L;

    private final WorkbenchShellContext shellContext;
    private final SliderWidget[] sliders = new SliderWidget[axes];
    private final double[] values = new double[axes];
    private final boolean[] touched = new boolean[axes];
    private final AtomicBoolean pendingRefresh = new AtomicBoolean(false);
    private final Runnable contextListener = this::scheduleRefresh;

    private boolean open;
    private boolean dirty;
    private boolean refreshing;
    private long clockOffsetMillis;
    private long shownAtMillis;
    private @Nullable Boolean reducedMotionOverride;

    public ProfileWeightsPopoverWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        addStyleClass("workbench-popover");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.cardSurface(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(width, height));
        setInteractive(true);
        setVisible(false);
        setClickGroup(this);
        onClickOutside(ignored -> dismissOnOutsideClick());

        for (int axis = 0; axis < axes; axis++) {
            int index = axis;
            SliderWidget slider = SliderWidget.create(sliderMin, sliderMax, sliderMin).setStep(sliderStep)
                    .onValueChanged(value -> onSliderChanged(index, value));
            slider.useStyle(
                positionAbsolute(),
                insetLeft(sliderLeft),
                insetTop(rowTop(index) + (rowHeight - sliderHeight) / 2),
                sizeOf(sliderWidth, sliderHeight)
            );
            sliders[index] = slider;
            addWidget(slider);
        }

        onMount((scene, sceneContext, handle) -> {
            pendingRefresh.set(false);
            shellContext.addListener(contextListener);
            refresh();
        });
        onUnmount(() -> {
            pendingRefresh.set(false);
            shellContext.removeListener(contextListener);
        });
        onMouseClicked((input, clickContext) -> {
            if (!open) {
                return EventDispatch.pass;
            }
            clickContext.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, clickContext) -> {
            if (!open || !input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var local = input.mouseRelative(this);
            if (contains(resetRect(), local.x(), local.y())) {
                reset();
                clickContext.consume();
                return EventDispatch.consumed;
            }
            if (contains(applyRect(), local.x(), local.y())) {
                apply();
                clickContext.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        refresh();
    }

    //region state

    public void toggle() {
        if (open) {
            hide();
        } else {
            show();
        }
    }

    public void show() {
        if (open) {
            return;
        }
        if (shellContext instanceof WorkbenchShellWidget shell) {
            shell.closeTransientOverlays(this);
        }
        open = true;
        shownAtMillis = clock();
        setVisible(true);
        setActive(true);
        refresh();
    }

    public void hide() {
        open = false;
        setVisible(false);
        setActive(false);
    }

    public boolean isOpen() {
        return open;
    }

    public ProfileWeightsPopoverWidget reducedMotion(@Nullable Boolean reducedMotion) {
        this.reducedMotionOverride = reducedMotion;
        return this;
    }

    public float appearProgress(long nowMillis) {
        if (!open) {
            return 0f;
        }
        boolean reduced = reducedMotionOverride != null ? reducedMotionOverride : ChromeMotion.reducedMotion();
        return ChromeMotion.cubicOutProgress(nowMillis - shownAtMillis, ChromeMotion.overlayAppearMillis, reduced);
    }

    public void refresh() {
        refresh(shellContext.activeSnapshot().profile().weights());
    }

    /**
     * Reloads the five sliders from the given weights; a raw value beyond a slider's range is kept until the user touches that axis.
     */
    public void refresh(CostWeights weights) {
        CostWeights source = weights == null ? CostWeights.forPreset(activeProfile().preset()) : weights;
        dirty = false;
        refreshing = true;
        try {
            for (int axis = 0; axis < axes; axis++) {
                values[axis] = ChromeProfiles.axisWeight(source, axis);
                touched[axis] = false;
                sliders[axis].setValue(sliderValue(values[axis]));
            }
        } finally {
            refreshing = false;
        }
    }

    public void reset() {
        refresh();
    }

    public boolean apply() {
        PlannerWorkbenchController controller = shellContext.activeController();
        if (controller == null) {
            return false;
        }
        dirty = false;
        controller.setProfile(buildProfile());
        hide();
        return true;
    }

    public PlanningProfile buildProfile() {
        PlanningProfile current = activeProfile();
        return new PlanningProfile(
            current.profileId(),
            current.preset(),
            currentWeights(),
            current.diversityThreshold(),
            current.maxMachineRecursionDepth()
        );
    }

    public CostWeights currentWeights() {
        double[] weights = new double[axes];
        for (int axis = 0; axis < axes; axis++) {
            weights[axis] = values[axis];
        }
        return new CostWeights(weights[0], weights[1], weights[2], weights[3], weights[4]);
    }

    public double axisValue(int axis) {
        checkAxis(axis);
        return values[axis];
    }

    public double axisSliderValue(int axis) {
        checkAxis(axis);
        return sliders[axis].value();
    }

    public boolean axisTouched(int axis) {
        checkAxis(axis);
        return touched[axis];
    }

    public boolean axisOverflowsSlider(int axis) {
        checkAxis(axis);
        return values[axis] > sliderMax;
    }

    public boolean setAxisValue(int axis, double value) {
        checkAxis(axis);
        if (!Double.isFinite(value)) {
            return false;
        }
        double clamped = Math.max(sliderMin, Math.min(sliderMax, value));
        onSliderChanged(axis, clamped);
        refreshing = true;
        try {
            sliders[axis].setValue(clamped);
        } finally {
            refreshing = false;
        }
        return true;
    }

    public static String axisLabel(int axis) {
        return switch (axis) {
            case 0 -> "H";
            case 1 -> "P";
            case 2 -> "K";
            case 3 -> "S";
            case 4 -> "C";
            default -> throw new IllegalArgumentException("axis must be in [0, 4], got " + axis);
        };
    }

    public String formattedValue(int axis) {
        checkAxis(axis);
        return String.format(Locale.ROOT, "%.2f", values[axis]);
    }

    public long clock() {
        return System.currentTimeMillis() + clockOffsetMillis;
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    private PlanningProfile activeProfile() {
        return shellContext.activeSnapshot().profile();
    }

    private void onSliderChanged(int axis, double value) {
        if (refreshing) {
            return;
        }
        values[axis] = ChromeProfiles.clampWeight(value);
        touched[axis] = true;
        dirty = true;
    }

    private void scheduleRefresh() {
        if (lifecycle().destroyed() || !lifecycle().mounted()) {
            return;
        }
        if (pendingRefresh.compareAndSet(false, true)) {
            scene().nextTick(() -> {
                pendingRefresh.set(false);
                if (!lifecycle().destroyed()) {
                    refreshIfClean();
                }
            });
        }
    }

    /**
     * On snapshot publish only the weights not covered by a local edit are re-read.
     */
    private void refreshIfClean() {
        if (!dirty) {
            refresh();
        }
    }

    public boolean dirty() {
        return dirty;
    }

    private static double sliderValue(double weight) {
        return Math.max(sliderMin, Math.min(sliderMax, weight));
    }

    private void dismissOnOutsideClick() {
        if (open && clock() - shownAtMillis >= outsideClickGraceMillis) {
            hide();
        }
    }

    private static void checkAxis(int axis) {
        if (axis < 0 || axis >= axes) {
            throw new IllegalArgumentException("axis must be in [0, 4], got " + axis);
        }
    }

    //endregion

    //region geometry

    public static int rowTop(int axis) {
        return rowTop + axis * rowHeight;
    }

    public Rect headerRect() {
        return new Rect(pad, headerTop, (int) width - pad * 2, headerHeight);
    }

    public Rect rowRect(int axis) {
        return new Rect(pad, rowTop(axis), (int) width - pad * 2, rowHeight);
    }

    public Rect sliderRect(int axis) {
        return new Rect(sliderLeft, rowTop(axis) + (rowHeight - sliderHeight) / 2, sliderWidth, sliderHeight);
    }

    public Rect valueRect(int axis) {
        return new Rect(
            sliderLeft + sliderWidth + 4,
            rowTop(axis) + 8,
            (int) width - sliderLeft - sliderWidth - 12,
            10
        );
    }

    public Rect resetRect() {
        return new Rect(pad, footerTop, buttonWidth, footerHeight);
    }

    public Rect applyRect() {
        return new Rect((int) width - pad - buttonWidth, footerTop, buttonWidth, footerHeight);
    }

    public List<SliderWidget> sliders() {
        return List.of(sliders);
    }

    private static boolean contains(Rect rect, double x, double y) {
        return x >= rect.x() && y >= rect.y() && x < rect.right() && y < rect.bottom();
    }

    //endregion

    //region render

    @Override
    public void render(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!open || width() <= 0 || height() <= 0) {
            return;
        }
        float progress = appearProgress(clock());
        float slideY = (1f - progress) * ChromeMotion.overlaySlidePx;
        canvas.pushTransform();
        canvas.translate(0f, slideY);
        try {
            super.render(canvas, mouseX, mouseY, partialTicks);
        } finally {
            canvas.popTransform();
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!open || width() <= 0 || height() <= 0) {
            return;
        }
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = shellContext.palette();
        OreUiPaint.popover(canvas, 0, 0, width(), height(), palette);
        OreUiPaint.headerBand(canvas, 0, 0, width(), 17, palette, false);
        Rect header = headerRect();
        ChromeText.drawFitted(
            canvas,
            CirrusLangs.Workbench.Topbar.customWeights.get(),
            header.x(),
            ChromeText.centeredY(1, 17, canvas),
            header.width(),
            palette.textPrimary()
        );
        ParetoGeometry.Axis[] axisList = ParetoGeometry.Axis.values();
        for (int axis = 0; axis < axes; axis++) {
            int labelY = ChromeText.centeredY(rowTop(axis), rowHeight, canvas);
            Component label = axis < axisList.length ? axisList[axis].shortLabel() : Component.literal(axisLabel(axis));
            ChromeText.drawFitted(
                canvas,
                label,
                pad,
                labelY,
                Math.max(16, sliderLeft - pad - 4),
                palette.costColor(axis)
            );
            Rect value = valueRect(axis);
            ChromeText.drawRight(
                canvas,
                Component.literal(formattedValue(axis)),
                value.right(),
                labelY,
                axisOverflowsSlider(axis) ? palette.costK() : palette.textSecondary()
            );
        }
        renderButton(
            canvas,
            palette,
            resetRect(),
            CirrusLangs.Workbench.Topbar.resetSession.get(),
            mouseX,
            mouseY,
            false
        );
        renderButton(
            canvas,
            palette,
            applyRect(),
            CirrusLangs.Calculator.Rules.actionApply.get(),
            mouseX,
            mouseY,
            true
        );
    }

    private void renderButton(
        SceneCanvas canvas,
        CirrusPalette palette,
        Rect rect,
        Component label,
        int mouseX,
        int mouseY,
        boolean primary
    ) {
        boolean hovered = ChromeText.hit(mouseX, mouseY, rect.x(), rect.y(), rect.width(), rect.height());
        OreUiPaint.raisedButton(
            canvas,
            rect.x(),
            rect.y(),
            rect.width(),
            rect.height(),
            palette,
            false,
            hovered,
            primary,
            false
        );
        ChromeText.drawFitted(
            canvas,
            label,
            rect.x() + 6,
            ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
            rect.width() - 12,
            primary ? palette.textOnAccent() : palette.textPrimary()
        );
    }

    //endregion
}

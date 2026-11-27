package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z3 dock island width handle: dragging resizes live, double-click restores the default, hovering highlights the line (DND-08 / UI-TC-17).
 */
public class DockResizeHandleWidget extends Widget {
    public static final float handleWidth = 5f;
    private static final int doubleClickCount = 2;

    private final WorkbenchShellContext shellContext;
    private final boolean leftSide;

    private boolean dragging;
    private boolean dragMoved;
    private double dragStartMouseX;
    private float dragStartWidth;

    public DockResizeHandleWidget(WorkbenchShellContext shellContext, boolean leftSide) {
        this.shellContext = Objects.requireNonNull(shellContext, "shellContext");
        this.leftSide = leftSide;
        WorkbenchShellLayout layout = shellLayout();
        WorkbenchShellLayout.Rect dock = leftSide ? layout.leftDock() : layout.rightDock();
        float x = leftSide ? dock.right() : dock.x() - handleWidth;
        useStyle(positionAbsolute(), sizeOf(handleWidth, dock.height()), insetLeft(x), insetTop(dock.y()));
        setInteractive(true);
        setVisible(!dock.empty());
        // the press starts the resize: the click counter only arrives on release, far too late to drag from
        onMouseClicked((input, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            beginDrag(input.mouseX());
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            if (clickCount >= doubleClickCount) {
                resetToDefault();
            }
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (!dragging) {
                return EventDispatch.pass;
            }
            dragTo(input.mouseX());
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            if (!dragging) {
                return EventDispatch.pass;
            }
            // a click arms the session and keeps it; only a release that follows a real resize ends it
            if (dragMoved) {
                release();
            }
            context.consume();
            return EventDispatch.consumed;
        });
    }

    public boolean leftSide() {
        return leftSide;
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    @Override
    public boolean dragging() {
        return dragging;
    }

    public float dragStartWidth() {
        return dragStartWidth;
    }

    public boolean beginDrag(double sceneMouseX) {
        float current = leftSide ? shellContext.leftDockWidth() : shellContext.rightDockWidth();
        if (current <= 0f) {
            return false;
        }
        dragging = true;
        dragMoved = false;
        dragStartWidth = current;
        dragStartMouseX = sceneMouseX;
        return true;
    }

    public boolean dragTo(double sceneMouseX) {
        if (!dragging) {
            return false;
        }
        dragMoved = true;
        float delta = (float) (sceneMouseX - dragStartMouseX);
        float raw = leftSide ? dragStartWidth + delta : dragStartWidth - delta;
        float clamped = clampWidth(raw);
        if (leftSide) {
            shellContext.setLeftDockWidth(clamped);
        } else {
            shellContext.setRightDockWidth(clamped);
        }
        return true;
    }

    /** Ends the session, wherever the pointer is; the scene only ever reports a release to the widget it hit. */
    public void release() {
        dragging = false;
        dragMoved = false;
    }

    public boolean resetToDefault() {
        release();
        WorkbenchBreakpoint breakpoint = shellContext.breakpoint();
        float defaultWidth = leftSide
                ? breakpoint.leftDockDefaultWidth(shellContext.shellWidth())
                : breakpoint.rightDockDefaultWidth(shellContext.shellWidth());
        if (leftSide) {
            shellContext.setLeftDockWidth(defaultWidth);
        } else {
            shellContext.setRightDockWidth(defaultWidth);
        }
        return defaultWidth > 0f;
    }

    public float clampWidth(float raw) {
        float shellWidth = shellContext.shellWidth();
        float railWidth = shellContext.leftRailWidth();
        float otherDock = leftSide ? shellContext.rightDockWidth() : shellContext.leftDockWidth();
        float canvasCap = shellWidth - railWidth - otherDock - WorkbenchBreakpoint.minCenterCanvasWidth;
        if (leftSide) {
            float upper = Math
                    .max(WorkbenchBreakpoint.leftDockMin, Math.min(WorkbenchBreakpoint.leftDockMax, canvasCap));
            return Math.max(WorkbenchBreakpoint.leftDockMin, Math.min(upper, raw));
        }
        float upper = Math.max(WorkbenchBreakpoint.rightDockMin, Math.min(WorkbenchBreakpoint.rightDockMax, canvasCap));
        return Math.max(WorkbenchBreakpoint.rightDockMin, Math.min(upper, raw));
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = shellContext.palette();
        int handleHeight = height();
        int width = Math.max(1, width());
        if (handleHeight <= 0) {
            return;
        }
        int centerX = width / 2;
        if (dragging) {
            canvas.fill(0, 0, width, handleHeight, ChromeMotion.modulateAlpha(palette.focusRing(), 0.36f));
            canvas.fill(Math.max(0, centerX - 1), 0, Math.min(2, width), handleHeight, palette.focusRing());
            drawGrip(canvas, centerX, handleHeight, palette.textOnAccent());
        } else if (hovered()) {
            canvas.fill(0, 0, width, handleHeight, ChromeMotion.modulateAlpha(palette.focusRing(), 0.22f));
            canvas.fill(centerX, 0, 1, handleHeight, palette.focusRing());
            drawGrip(canvas, centerX, handleHeight, palette.hairlineHighlight());
        } else {
            canvas.fill(centerX, 0, 1, handleHeight, palette.hairlineBorder());
        }
    }

    private static void drawGrip(SceneCanvas canvas, int centerX, int handleHeight, int color) {
        if (handleHeight < 20) {
            return;
        }
        int midY = handleHeight / 2;
        for (int offset = -4; offset <= 4; offset += 4) {
            canvas.fill(centerX, midY + offset, 1, 2, color);
        }
    }

    public WorkbenchThemeMode theme() {
        return shellContext.theme();
    }

    private WorkbenchShellLayout shellLayout() {
        return WorkbenchShellLayout.compute(
            shellContext.shellWidth(),
            shellContext.shellHeight(),
            shellContext.breakpoint(),
            shellContext.zenMode(),
            shellContext.leftDockWidth(),
            shellContext.rightDockWidth()
        );
    }
}

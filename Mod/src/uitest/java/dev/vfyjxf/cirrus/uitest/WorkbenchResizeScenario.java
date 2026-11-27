package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.DockResizeHandleWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.Selectors;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The dock separators: a drag resizes the island live, the clamp keeps the centre canvas usable, and a double click restores
 * the breakpoint default.
 *
 * <p>Every assertion reads two independent observables — the shell's dock width and the canvas rectangle — so a width written
 * without a relayout cannot pass: the canvas has to move by the same number of pixels the dock grew.
 */
@ClientScenario(
    name = "cirrus.workbench.resize",
    group = "workbench",
    tags = {"workbench", "resize"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 180_000
)
public final class WorkbenchResizeScenario {

    private static final double dragRight = 60.0;

    private static final double overshoot = 2000.0;

    /** How far past the separator the reproduction release lands: well outside its five-pixel strip. */
    private static final double releaseInsetX = 120.0;

    private static final double releaseInsetY = 120.0;

    private @Nullable WorkbenchShellWidget shell;

    private WorkbenchShellWidget sh() {
        WorkbenchShellWidget held = shell;
        if (held == null) {
            throw new IllegalStateException("the shell was not captured by the open step");
        }
        return held;
    }

    private float leftWidthBefore;

    private float widthBeforeReleaseDrag;

    private Rect canvasBefore = Rect.empty;

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.resize: dragging the dock separators and the canvas minimum")
                .openScreen(CalculatorScreen::new).awaitScreen(CalculatorScreen.class)
                .checkScreen(CalculatorScreen.class).action("hold the shell and its handles", context -> {
                    shell = WorkbenchTestKit.shell(context);
                    leftWidthBefore = sh().leftDockWidth();
                    Rect canvas = canvasRect();
                    if (canvas == null) {
                        throw new IllegalStateException("the planner canvas is not in the scene");
                    }
                    canvasBefore = canvas;
                }).frames(3).screenshot("workbench-resize")

                .check("the shell has both separators", () -> handles().size() == 2)
                .check("the separators sit on opposite sides", () -> {
                    List<DockResizeHandleWidget> handles = handles();
                    return handles.size() == 2 && handles.get(0).leftSide() != handles.get(1).leftSide();
                }).check("the separators are only as wide as their hit area", () -> {
                    for (DockResizeHandleWidget handle : handles()) {
                        if (Math.abs(handle.absoluteBounds().width() - DockResizeHandleWidget.handleWidth) > 1f) {
                            return false;
                        }
                    }
                    return true;
                }).check("the left separator touches the left dock's edge", () -> {
                    DockResizeHandleWidget handle = handle(true);
                    LeftPlanDockWidget dock = WorkbenchTestKit.first(LeftPlanDockWidget.class);
                    return handle != null
                            && dock != null
                            && Math.abs(handle.absoluteBounds().x() - dock.absoluteBounds().right()) <= 1;
                })

                .group(
                    "dragging the left separator widens the left dock",
                    () -> build
                            .action("drag the left separator outwards", context -> dragHandle(context, true, dragRight))
                            .screenshot("workbench-resize-wide")
                            .check(
                                "the left dock grew by the drag distance",
                                () -> shell != null
                                        && Math.abs(sh().leftDockWidth() - (leftWidthBefore + dragRight)) <= 4f,
                                () -> shell == null ? "no shell" : sh().leftDockWidth() + " from " + leftWidthBefore
                            ).check("the canvas starts where the wider dock ends", () -> {
                                Rect canvas = canvasRect();
                                return canvas != null
                                        && canvasBefore != null
                                        && Math.abs(
                                            (canvas.x() - canvasBefore.x()) - (sh().leftDockWidth() - leftWidthBefore)
                                        ) <= 1f;
                            }).check("the canvas gave up exactly the width the dock took", () -> {
                                Rect canvas = canvasRect();
                                return canvas != null
                                        && canvasBefore != null
                                        && Math.abs(
                                            (canvasBefore.width() - canvas.width())
                                                    - (sh().leftDockWidth() - leftWidthBefore)
                                        ) <= 1f;
                            }).check("the canvas is still above the minimum centre width", () -> {
                                Rect canvas = canvasRect();
                                return canvas != null && canvas.width() >= WorkbenchBreakpoint.minCenterCanvasWidth;
                            }).check("the separator remembers the width the drag started from", () -> {
                                DockResizeHandleWidget handle = handle(true);
                                return handle != null && Math.abs(handle.dragStartWidth() - leftWidthBefore) <= 1f;
                            })
                )

                .group(
                    "overshooting keeps the canvas usable",
                    () -> build
                            .check(
                                "the wide clamp is wider than the minimum centre width",
                                () -> shell != null
                                        && WorkbenchBreakpoint.leftDockMax + WorkbenchBreakpoint.minCenterCanvasWidth
                                                < sh().width() - sh().leftRailWidth()
                            )
                            .action(
                                "drag the left separator far past the maximum",
                                context -> dragHandle(context, true, overshoot)
                            )
                            .check(
                                "the left dock stops at the maximum width",
                                () -> shell != null
                                        && Math.abs(sh().leftDockWidth() - WorkbenchBreakpoint.leftDockMax) <= 1f,
                                () -> shell == null ? "no shell" : String.valueOf(sh().leftDockWidth())
                            ).check("the canvas survives the clamp", () -> {
                                Rect canvas = canvasRect();
                                return canvas != null && canvas.width() >= WorkbenchBreakpoint.minCenterCanvasWidth;
                            })
                            .action(
                                "drag the left separator far inside the minimum",
                                context -> dragHandle(context, true, -overshoot)
                            ).check(
                                "the left dock stops at the minimum width",
                                () -> shell != null
                                        && Math.abs(sh().leftDockWidth() - WorkbenchBreakpoint.leftDockMin) <= 1f,
                                () -> shell == null ? "no shell" : String.valueOf(sh().leftDockWidth())
                            )
                )

                .group(
                    "a release away from the separator ends the resize",
                    () -> build.action(
                        "press on the left separator, drag it, and release it over the canvas",
                        context -> dragOffHandle(context, true, dragRight)
                    ).check(
                        "the drag really resized the dock before the release",
                        () -> shell != null
                                && Math.abs(sh().leftDockWidth() - (widthBeforeReleaseDrag + dragRight)) <= 1f,
                        () -> shell == null ? "no shell" : sh().leftDockWidth() + " from " + widthBeforeReleaseDrag
                    ).check("the separator is no longer in a drag session after the release", () -> {
                        DockResizeHandleWidget handle = handle(true);
                        return handle != null && !handle.dragging();
                    }, () -> {
                        DockResizeHandleWidget handle = handle(true);
                        return handle == null ? "no handle" : "dragging=" + handle.dragging();
                    }).check("the separator is not left highlighted either", () -> {
                        DockResizeHandleWidget handle = handle(true);
                        return handle != null && !handle.hovered();
                    }).check(
                        "the width the drag reached survives the release",
                        () -> shell != null
                                && Math.abs(sh().leftDockWidth() - (widthBeforeReleaseDrag + dragRight)) <= 1f
                    )
                )

                .group(
                    "a double click restores the default width",
                    () -> build.doubleClick(Selectors.first(Selectors.byType(DockResizeHandleWidget.class))).check(
                        "the left dock is back at the breakpoint default",
                        () -> shell != null
                                && Math.abs(
                                    sh().leftDockWidth() - WorkbenchBreakpoint.wide.leftDockDefaultWidth(sh().width())
                                ) <= 1f,
                        () -> shell == null ? "no shell" : String.valueOf(sh().leftDockWidth())
                    ).check("the canvas is back at its default rectangle", () -> {
                        Rect canvas = canvasRect();
                        return canvas != null
                                && canvasBefore != null
                                && canvas.width() == canvasBefore.width()
                                && canvas.x() == canvasBefore.x();
                    })
                )

                .group(
                    "the right separator resizes the co-pilot dock",
                    () -> build
                            .action(
                                "drag the right separator left, which widens it",
                                context -> dragHandle(context, false, -overshoot)
                            )
                            .check(
                                "the right dock stops at the maximum width",
                                () -> shell != null
                                        && Math.abs(sh().rightDockWidth() - WorkbenchBreakpoint.rightDockMax) <= 1f,
                                () -> shell == null ? "no shell" : String.valueOf(sh().rightDockWidth())
                            ).check("the canvas keeps the minimum centre width", () -> {
                                Rect canvas = canvasRect();
                                return canvas != null && canvas.width() >= WorkbenchBreakpoint.minCenterCanvasWidth;
                            })
                            .action(
                                "drag the right separator right, which narrows it",
                                context -> dragHandle(context, false, overshoot)
                            )
                            .check(
                                "the right dock stops at the minimum width",
                                () -> shell != null
                                        && Math.abs(sh().rightDockWidth() - WorkbenchBreakpoint.rightDockMin) <= 1f
                            ).check("the canvas grew into the space the right dock gave up", () -> {
                                Rect canvas = canvasRect();
                                return canvas != null && canvasBefore != null && canvas.width() > canvasBefore.width();
                            })
                ).teardown(this::close);
    }

    private void dragHandle(TestContext context, boolean leftSide, double deltaX) {
        DockResizeHandleWidget handle = handle(leftSide);
        if (handle == null) {
            throw new IllegalStateException("the " + (leftSide ? "left" : "right") + " separator is not in the scene");
        }
        Rect bounds = handle.absoluteBounds();
        double fromX = bounds.x() + bounds.width() / 2.0;
        double y = bounds.y() + bounds.height() / 2.0;
        double toX = fromX + deltaX;
        context.driver().placeCursor((float) fromX, (float) y);
        context.driver().moveTo(context.screenOrThrow(), (float) fromX, (float) y);
        context.driver().mouseDown(context.screenOrThrow(), (float) fromX, (float) y, 0);
        for (int step = 1; step <= 6; step++) {
            float x = (float) (fromX + deltaX * step / 6.0);
            context.driver().dragTo(context.screenOrThrow(), x, (float) y, 0, (float) (deltaX / 6.0), 0f);
        }
        context.driver().mouseUp(context.screenOrThrow(), (float) toX, (float) y, 0);
    }

    /** Presses on the separator, drags it, then releases far away from its five-pixel strip. */
    private void dragOffHandle(TestContext context, boolean leftSide, double deltaX) {
        DockResizeHandleWidget handle = handle(leftSide);
        if (handle == null) {
            throw new IllegalStateException("the separator is not in the scene");
        }
        widthBeforeReleaseDrag = leftSide ? sh().leftDockWidth() : sh().rightDockWidth();
        Rect bounds = handle.absoluteBounds();
        double fromX = bounds.x() + bounds.width() / 2.0;
        double y = bounds.y() + bounds.height() / 2.0;
        context.driver().placeCursor((float) fromX, (float) y);
        context.driver().moveTo(context.screenOrThrow(), (float) fromX, (float) y);
        context.driver().mouseDown(context.screenOrThrow(), (float) fromX, (float) y, 0);
        for (int step = 1; step <= 6; step++) {
            float x = (float) (fromX + deltaX * step / 6.0);
            context.driver().dragTo(context.screenOrThrow(), x, (float) y, 0, (float) (deltaX / 6.0), 0f);
        }
        float releaseX = (float) (fromX + deltaX + releaseInsetX);
        float releaseY = (float) (y + releaseInsetY);
        context.driver().placeCursor(releaseX, releaseY);
        context.driver().mouseUp(context.screenOrThrow(), releaseX, releaseY, 0);
    }

    private List<DockResizeHandleWidget> handles() {
        return WorkbenchTestKit.all(DockResizeHandleWidget.class);
    }

    private @Nullable DockResizeHandleWidget handle(boolean leftSide) {
        for (DockResizeHandleWidget handle : handles()) {
            if (handle.leftSide() == leftSide) {
                return handle;
            }
        }
        return null;
    }

    private @Nullable Rect canvasRect() {
        SpatialDagCanvasWidget canvas = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
        return canvas == null ? null : canvas.absoluteBounds();
    }

    private void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CalculatorScreen open) {
            open.onClose();
        }
        minecraft.setScreen(null);
    }
}

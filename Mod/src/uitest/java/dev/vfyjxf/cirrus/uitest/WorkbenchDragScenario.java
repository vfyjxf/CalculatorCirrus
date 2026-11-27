package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagEdgeRoute;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Dragging a step card on the spatial DAG: the card follows the pointer, the move is committed to the plan, and the edges and
 * the layout surface are re-routed around the new position.
 *
 * <p>The plan is a real one: the client compiles the recipe graph from the loaded world and plans four crafting tables, so the
 * cards come from the same pipeline a player's plan does. A drag is only proof if three traces agree — the node box moved, the
 * plan recorded the manual offset, and the edge leaving that node now starts somewhere else — because a painted ghost would move
 * the first without the other two.
 */
@ClientScenario(
    name = "cirrus.workbench.drag",
    group = "workbench",
    tags = {"workbench", "drag"},
    requiresWorld = true,
    hideGui = true,
    timeoutMs = 60_000,
    scenarioTimeoutMs = 300_000
)
public final class WorkbenchDragScenario {

    private static final double dragStepX = 14.0;

    private static final double dragStepY = 7.0;

    private static final int dragSteps = 6;

    private @Nullable WorkbenchShellWidget shell;

    private @Nullable String stepId;

    private @Nullable DagNodeBox nodeBefore;

    private PlanDagLayoutResult layoutBefore = PlanDagLayoutResult.empty;

    private @Nullable DagEdgeRoute edgeBefore;

    private float cameraXBefore;

    private float cameraYBefore;

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.drag: a step card, its edges and the layout surface follow the pointer")
                .openScreen(CalculatorScreen::new).awaitScreen(CalculatorScreen.class)
                .checkScreen(CalculatorScreen.class)
                .action("hold the shell", context -> shell = WorkbenchTestKit.shell(context)).frames(3)
                .screenshot("workbench-drag")

                .group(
                    "build a real plan for the canvas",
                    () -> build.action("publish the recipe graph from the loaded world", context -> {
                        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedOrCreate();
                        if (runtime.currentGraph().isEmpty()) {
                            runtime.reloadFromRuntimeAsync();
                        }
                    })
                            .waitUntil(
                                "the client publishes a recipe graph",
                                () -> CirrusPlanningRuntime.sharedOrCreate().currentGraph().isPresent()
                            )
                            .action(
                                "ask the workbench to plan four crafting tables",
                                context -> sh().setTargets(
                                    List.of(new TargetDemand(ResourceKey.item("minecraft:crafting_table"), 4)),
                                    null
                                )
                            )
                            .waitUntil(
                                "the planner publishes a plan",
                                () -> sh().activeSnapshot().selectedPlan().isPresent()
                            )
                            .waitUntil(
                                "the canvas lays the plan out as cards",
                                () -> canvas() != null && canvas().dagLayout().nodes().size() >= 2
                            )
                            .check(
                                "the plan is a real plan with steps",
                                () -> sh().activeSnapshot().selectedPlan().map(plan -> plan.steps().size() >= 2)
                                        .orElse(false)
                            )
                            .check(
                                "the canvas lays the plan out as cards",
                                () -> canvas() != null && canvas().dagLayout().nodes().size() >= 2,
                                () -> canvas() == null ? "no canvas" : canvas().dagLayout().nodes().size() + " cards"
                            ).check(
                                "the canvas routes an edge between the cards",
                                () -> canvas() != null && !canvas().dagLayout().edges().isEmpty()
                            )
                )

                .group(
                    "drag the first card",
                    () -> build.action("remember the card, its edges and the camera", context -> {
                        SpatialDagCanvasWidget widget = canvas();
                        if (widget == null) {
                            throw new IllegalStateException("the canvas is not in the scene");
                        }
                        layoutBefore = widget.dagLayout();
                        DagNodeBox first = layoutBefore.nodes().get(0);
                        stepId = first.stepId();
                        nodeBefore = first;
                        edgeBefore = edgeLeaving(first.stepId(), layoutBefore);
                        cameraXBefore = widget.camera().camX();
                        cameraYBefore = widget.camera().camY();
                    }).action("press on the card and drag it", context -> dragCard(context))
                            .screenshot("workbench-drag-moved")
                            .check("the plan recorded the manual offset for that step", () -> {
                                FloatPos offset = sh().activeSnapshot().manualNodeOffsets().get(step());
                                return offset != null && offset.x() >= 40f && offset.y() >= 20f;
                            }, () -> sh().activeSnapshot().manualNodeOffsets().toString())
                            .check("the card moved on the canvas", () -> {
                                DagNodeBox moved = nodeOf(step());
                                return moved != null
                                        && Math.abs(moved.x() - node().x()) >= 40f
                                        && Math.abs(moved.y() - node().y()) >= 20f;
                            }, () -> {
                                DagNodeBox after = nodeOf(step());
                                return after == null
                                        ? "the card vanished"
                                        : "before " + node().x() + "," + node().y() + " after " + after.x() + ","
                                                + after.y();
                            })
                            .check(
                                "the press selected the card",
                                () -> canvas() != null && canvas().selectedStepIds().contains(step())
                            ).check(
                                "dragging a card does not pan the camera",
                                () -> canvas() != null
                                        && canvas().camera().camX() == cameraXBefore
                                        && canvas().camera().camY() == cameraYBefore
                            )
                )

                .group(
                    "the edges and the layout surface follow the card",
                    () -> build.check(
                        "the moved card still has an outgoing edge",
                        () -> edgeLeaving(step(), after()) != null
                    ).check("the outgoing edge starts on the moved card", () -> {
                        DagEdgeRoute before = edge();
                        DagEdgeRoute after = edgeLeaving(step(), after());
                        DagNodeBox moved = nodeOf(step());
                        if (before == null || after == null || moved == null) {
                            return false;
                        }
                        return Math.abs(after.x0() - before.x0()) >= 40f && Math.abs(after.x0() - moved.maxX()) <= 1f;
                    }, () -> {
                        DagEdgeRoute before = edge();
                        DagEdgeRoute after = edgeLeaving(step(), after());
                        DagNodeBox moved = nodeOf(step());
                        return (before == null || after == null || moved == null)
                                ? "missing edge"
                                : "before x0=" + before.x0() + " after x0=" + after.x0() + " card maxX=" + moved.maxX();
                    }).check("the layout surface still covers every card where it now is", () -> {
                        DagNodeBox moved = nodeOf(step());
                        return moved != null
                                && after().contentMaxX() >= moved.maxX()
                                && after().contentMaxY() >= moved.maxY()
                                && after().contentMaxX() >= layoutBefore.contentMaxX();
                    }, () -> {
                        DagNodeBox moved = nodeOf(step());
                        return moved == null
                                ? "the card vanished"
                                : "card maxX=" + moved.maxX() + " surface maxX=" + after().contentMaxX() + " (was "
                                        + layoutBefore.contentMaxX() + ")";
                    }).check("the other cards stayed where they were", () -> {
                        for (DagNodeBox before : layoutBefore.nodes()) {
                            if (before.stepId().equals(step())) {
                                continue;
                            }
                            DagNodeBox sibling = nodeOf(before.stepId());
                            if (sibling == null || sibling.x() != before.x() || sibling.y() != before.y()) {
                                return false;
                            }
                        }
                        return true;
                    })
                            .check(
                                "the drag added or dropped no step",
                                () -> after().nodes().size() == layoutBefore.nodes().size()
                            ).check("the committed offset survives a canvas relayout", () -> {
                                SpatialDagCanvasWidget widget = canvas();
                                if (widget == null) {
                                    return false;
                                }
                                widget.invalidate();
                                DagNodeBox after = nodeOf(step());
                                return after != null && Math.abs(after.x() - node().x()) >= 40f;
                            })
                ).teardown(this::close);
    }

    private WorkbenchShellWidget sh() {
        WorkbenchShellWidget held = shell;
        if (held == null) {
            throw new IllegalStateException("the shell was not captured by the open step");
        }
        return held;
    }

    private String step() {
        String held = stepId;
        if (held == null) {
            throw new IllegalStateException("the dragged card was not captured");
        }
        return held;
    }

    private DagNodeBox node() {
        DagNodeBox held = nodeBefore;
        if (held == null) {
            throw new IllegalStateException("the dragged card was not captured");
        }
        return held;
    }

    private @Nullable DagEdgeRoute edge() {
        return edgeBefore;
    }

    private PlanDagLayoutResult after() {
        return canvas() == null ? PlanDagLayoutResult.empty : canvas().dagLayout();
    }

    private @Nullable SpatialDagCanvasWidget canvas() {
        return WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
    }

    private @Nullable DagNodeBox nodeOf(String id) {
        SpatialDagCanvasWidget widget = canvas();
        if (widget == null) {
            return null;
        }
        for (DagNodeBox node : widget.dagLayout().nodes()) {
            if (node.stepId().equals(id)) {
                return node;
            }
        }
        return null;
    }

    private static @Nullable DagEdgeRoute edgeLeaving(String id, PlanDagLayoutResult layout) {
        for (DagEdgeRoute edge : layout.edges()) {
            if (edge.fromStepId().equals(id)) {
                return edge;
            }
        }
        return null;
    }

    private void dragCard(TestContext context) {
        SpatialDagCanvasWidget widget = canvas();
        if (widget == null) {
            throw new IllegalStateException("the canvas is not in the scene");
        }
        DagNodeBox card = node();
        Rect bounds = widget.absoluteBounds();
        double fromX = bounds.x() + widget.camera().worldToScreenX(card.centerX());
        double fromY = bounds.y() + widget.camera().worldToScreenY(card.centerY());
        context.driver().placeCursor((float) fromX, (float) fromY);
        context.driver().moveTo(context.screenOrThrow(), (float) fromX, (float) fromY);
        context.driver().mouseDown(context.screenOrThrow(), (float) fromX, (float) fromY, 0);
        for (int step = 1; step <= dragSteps; step++) {
            float x = (float) (fromX + dragStepX * step);
            float y = (float) (fromY + dragStepY * step);
            context.driver().dragTo(context.screenOrThrow(), x, y, 0, (float) dragStepX, (float) dragStepY);
        }
        context.driver().mouseUp(
            context.screenOrThrow(),
            (float) (fromX + dragStepX * dragSteps),
            (float) (fromY + dragStepY * dragSteps),
            0
        );
    }

    private void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CalculatorScreen open) {
            open.onClose();
        }
        minecraft.setScreen(null);
    }
}

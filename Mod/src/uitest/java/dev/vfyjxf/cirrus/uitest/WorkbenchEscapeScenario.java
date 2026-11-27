package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TargetBasketPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TopCommandDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.Scenes;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Escape peels one layer at a time: the palette, then the popover, then zen mode, and only then the shell itself.
 *
 * <p>The order matters and nothing else proves it. A popover is opened first and the palette on top of it, so the first escape
 * has to leave the popover standing; zen is switched on with the plan's own shortcut, so its escape has to restore the dock
 * geometry rather than merely flip a flag; the last escape has to leave the screen, which is the only layer that ends the
 * workbench.
 */
@ClientScenario(
    name = "cirrus.workbench.escape",
    group = "workbench",
    tags = {"workbench", "escape"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 180_000
)
public final class WorkbenchEscapeScenario {

    private @Nullable WorkbenchShellWidget shell;

    private WorkbenchShellWidget sh() {
        WorkbenchShellWidget held = shell;
        if (held == null) {
            throw new IllegalStateException("the shell was not captured by the open step");
        }
        return held;
    }

    private float leftDockWidth;

    private float rightDockWidth;

    private Rect canvas = Rect.empty;

    private dev.vfyjxf.cloudlib.api.ui.base.@Nullable Scene workbenchScene;

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.escape: palette, popover, zen, shell").openScreen(CalculatorScreen::new)
                .awaitScreen(CalculatorScreen.class).checkScreen(CalculatorScreen.class)
                .action("hold the shell geometry", context -> {
                    workbenchScene = Scenes.sceneOrThrow(context.minecraft().screen);
                    shell = WorkbenchTestKit.shell(context);
                    leftDockWidth = sh().leftDockWidth();
                    rightDockWidth = sh().rightDockWidth();
                    SpatialDagCanvasWidget canvasWidget = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                    if (canvasWidget == null) {
                        throw new IllegalStateException("the planner canvas is not in the scene");
                    }
                    canvas = canvasWidget.absoluteBounds();
                }).frames(3).screenshot("workbench-popover")

                .group(
                    "open a popover, then the palette on top of it",
                    () -> build.action("click the target slot", context -> {
                        Rect bounds = targetSlot();
                        WorkbenchTestKit.click(context, bounds.centerX(), bounds.centerY());
                    }).check("the target basket popped up", () -> basket() != null && basket().isOpen())
                            .check("the popover is visible", () -> basket() != null && basket().visible())
                            .action("open the palette over the popover", context -> press(context, GLFW.GLFW_KEY_K))
                            .check(
                                "the palette is on top of the popover",
                                () -> shell != null
                                        && sh().commandPaletteOpen()
                                        && basket() != null
                                        && basket().isOpen()
                            )
                )

                .group(
                    "the first escape closes only the palette",
                    () -> build.action("press escape once", context -> press(context, GLFW.GLFW_KEY_ESCAPE))
                            .check("the palette is gone", () -> shell != null && !sh().commandPaletteOpen())
                            .check(
                                "the popover survived the palette escape",
                                () -> basket() != null && basket().isOpen()
                            ).check(
                                "the shell is still open",
                                () -> Minecraft.getInstance().screen instanceof CalculatorScreen
                            )
                )

                .group(
                    "the second escape closes the popover",
                    () -> build.action("press escape again", context -> press(context, GLFW.GLFW_KEY_ESCAPE))
                            .check("the popover is gone", () -> basket() == null || !basket().isOpen())
                            .check(
                                "the shell is still open",
                                () -> Minecraft.getInstance().screen instanceof CalculatorScreen
                            ).check(
                                "the dock geometry is untouched",
                                () -> shell != null
                                        && sh().leftDockWidth() == leftDockWidth
                                        && sh().rightDockWidth() == rightDockWidth
                            )
                )

                .group(
                    "zen mode collapses the docks and an escape restores them",
                    () -> build.action("press tab to enter zen mode", context -> press(context, GLFW.GLFW_KEY_TAB))
                            .screenshot("workbench-zen").check("zen mode is on", () -> shell != null && sh().zenMode())
                            .check(
                                "zen hides both dock islands",
                                () -> shell != null
                                        && sh().leftDockWidth() == 0f
                                        && sh().rightDockWidth() == 0f
                                        && !WorkbenchTestKit.shown(LeftPlanDockWidget.class)
                                        && !WorkbenchTestKit.shown(RightCoPilotDockWidget.class)
                            ).check("zen gives the canvas the whole viewport", () -> {
                                SpatialDagCanvasWidget canvasWidget = WorkbenchTestKit
                                        .first(SpatialDagCanvasWidget.class);
                                return canvasWidget != null
                                        && canvas != null
                                        && canvasWidget.absoluteBounds().width() > canvas.width()
                                        && canvasWidget.absoluteBounds().width() >= Math.round(sh().width());
                            }).action("press escape in zen mode", context -> press(context, GLFW.GLFW_KEY_ESCAPE))
                            .check("zen mode is off again", () -> shell != null && !sh().zenMode())
                            .check(
                                "the dock geometry came back exactly",
                                () -> shell != null
                                        && sh().leftDockWidth() == leftDockWidth
                                        && sh().rightDockWidth() == rightDockWidth
                            ).check("the canvas is back where it started", () -> {
                                SpatialDagCanvasWidget canvasWidget = WorkbenchTestKit
                                        .first(SpatialDagCanvasWidget.class);
                                return canvasWidget != null
                                        && canvas != null
                                        && canvasWidget.absoluteBounds().equals(canvas);
                            }).check(
                                "the shell is still open",
                                () -> Minecraft.getInstance().screen instanceof CalculatorScreen
                            )
                )

                .group(
                    "the last escape leaves the workbench",
                    () -> build
                            .action(
                                "press escape with nothing left to close",
                                context -> press(context, GLFW.GLFW_KEY_ESCAPE)
                            )
                            .check(
                                "the workbench screen is gone",
                                () -> !(Minecraft.getInstance().screen instanceof CalculatorScreen)
                            ).check(
                                "the workbench scene was destroyed with the screen",
                                () -> workbenchScene == null || !Scene.liveScenes().contains(workbenchScene)
                            )
                ).teardown(this::close);
    }

    /** The dock's first slot is the target chip; the slot type itself stays package-private. */
    private static Rect targetSlot() {
        TopCommandDockWidget dock = WorkbenchTestKit.first(TopCommandDockWidget.class);
        if (dock == null || dock.children().isEmpty()) {
            throw new IllegalStateException("the top command dock has no slots");
        }
        return dock.children().get(0).absoluteBounds();
    }

    private @Nullable TargetBasketPopoverWidget basket() {
        return WorkbenchTestKit.first(TargetBasketPopoverWidget.class);
    }

    private static void press(TestContext context, int key) {
        var target = context.screen();
        if (target == null) {
            return;
        }
        context.driver().keyDown(target, key, 0, 0);
        var current = context.minecraft().screen;
        if (current != null) {
            context.driver().keyUp(current, key, 0, 0);
        }
    }

    private void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CalculatorScreen open) {
            open.onClose();
        }
        minecraft.setScreen(null);
    }
}

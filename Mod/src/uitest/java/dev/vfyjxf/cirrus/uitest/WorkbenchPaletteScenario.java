package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.CommandPaletteOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The command palette: the hotkey opens it, typing narrows the list, Enter runs the highlighted command, and the run leaves a
 * real trace behind.
 *
 * <p>Nothing here asserts on a rendered label. The list is asserted by command id, the selection by index, and the execution by
 * the two things a dispatch changes outside the palette: the shell's zen flag (toggled twice, so a no-op cannot pass) and the
 * shell's mode (switched to settings). Escape is the counter-case: it must close the palette and change nothing else.
 */
@ClientScenario(
    name = "cirrus.workbench.palette",
    group = "workbench",
    tags = {"workbench", "palette"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 180_000
)
public final class WorkbenchPaletteScenario {

    private @Nullable CalculatorScreen screen;

    private @Nullable WorkbenchShellWidget shell;

    private WorkbenchShellWidget sh() {
        WorkbenchShellWidget held = shell;
        if (held == null) {
            throw new IllegalStateException("the shell was not captured by the open step");
        }
        return held;
    }

    private CalculatorScreen scr() {
        CalculatorScreen held = screen;
        if (held == null) {
            throw new IllegalStateException("the screen was not captured by the open step");
        }
        return held;
    }

    private CommandPaletteOverlayWidget pal() {
        CommandPaletteOverlayWidget found = WorkbenchTestKit.first(CommandPaletteOverlayWidget.class);
        if (found == null) {
            throw new IllegalStateException("the command palette is not in the scene");
        }
        return found;
    }

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.palette: hotkey, filter, execute, escape").openScreen(CalculatorScreen::new)
                .awaitScreen(CalculatorScreen.class).checkScreen(CalculatorScreen.class)
                .action("hold the open shell", context -> {
                    screen = WorkbenchTestKit.screen(context);
                    shell = scr().shell();
                }).frames(3).screenshot("workbench-palette")

                .action("open the palette with the workbench hotkey", context -> {
                    press(context, GLFW.GLFW_KEY_K);
                }).check("the hotkey opened the palette", () -> shell != null && sh().commandPaletteOpen())
                .screenshot("workbench-palette-open")
                .check("the palette widget reports itself open", () -> pal() != null && pal().isOpen())
                .check("the palette is visible and sized inside the viewport", () -> {
                    CommandPaletteOverlayWidget palette = pal();
                    if (palette == null || !palette.visible()) {
                        return false;
                    }
                    Rect bounds = palette.absoluteBounds();
                    Rect viewport = viewport();
                    return bounds.width() > 0 && bounds.height() > 0 && viewport.contains(bounds);
                })
                .check(
                    "an empty query lists the four frequent commands",
                    () -> sorted(ids(pal().results())).equals(
                        sorted(List.of("cmd:view.dag", "cmd:action.deepen", "cmd:theme.toggle", "cmd:zen.toggle"))
                    ),
                    () -> sorted(ids(pal().results())).toString()
                )

                .action("type a filter that matches one command", context -> type(context, "zen"))
                .checkEquals("the query text is what was typed", "zen", () -> pal().queryText())
                .checkEquals(
                    "the filter narrows the list to one command",
                    List.of("cmd:zen.toggle"),
                    () -> ids(pal().results())
                ).checkEquals("the highlight moves to the only result", 0, () -> pal().selectedIndex())

                .action("run the highlighted command", context -> press(context, GLFW.GLFW_KEY_ENTER))
                .check("running a command closes the palette", () -> shell != null && !sh().commandPaletteOpen())
                .checkEquals(
                    "the palette dispatched the zen toggle",
                    "zen.toggle",
                    () -> pal().lastDispatchedCommandId()
                ).check("the dispatch really toggled zen mode", () -> shell != null && sh().zenMode())

                .group(
                    "the same command runs the other way",
                    () -> build.action("open the palette again", context -> press(context, GLFW.GLFW_KEY_K))
                            .check(
                                "the palette reopened with a fresh query",
                                () -> pal() != null && pal().isOpen() && pal().queryText().isEmpty()
                            ).action("filter to the zen command", context -> type(context, "zen"))
                            .action("run it again", context -> press(context, GLFW.GLFW_KEY_ENTER))
                            .check("zen mode is back off", () -> shell != null && !sh().zenMode()).checkEquals(
                                "the second dispatch is recorded too",
                                "zen.toggle",
                                () -> pal().lastDispatchedCommandId()
                            )
                )

                .group(
                    "the command prefix reaches the whole command table",
                    () -> build
                            .action(
                                "open the palette for the command prefix",
                                context -> press(context, GLFW.GLFW_KEY_K)
                            ).action("type the command prefix", context -> type(context, ">"))
                            .checkEquals(
                                "a bare command prefix lists as many commands as the palette shows",
                                CommandPaletteOverlayWidget.queryLimit,
                                () -> pal().results().size()
                            )
                            .action(
                                "narrow the commands to the settings workspace",
                                context -> type(context, "settings")
                            )
                            .checkEquals(
                                "the settings workspace is the only match",
                                List.of("cmd:workspace.settings"),
                                () -> ids(pal().results())
                            ).action("run the workspace switch", context -> press(context, GLFW.GLFW_KEY_ENTER))
                            .checkEquals("the shell switched workspace", WorkbenchMode.settings, () -> sh().mode())
                            .check(
                                "the planner canvas went away with the workspace",
                                () -> WorkbenchTestKit.all(SpatialDagCanvasWidget.class).isEmpty()
                            )
                )

                .group(
                    "escape closes the palette without running anything",
                    () -> build.action("open the palette one last time", context -> press(context, GLFW.GLFW_KEY_K))
                            .action("type a filter", context -> type(context, "zen"))
                            .action("press escape", context -> press(context, GLFW.GLFW_KEY_ESCAPE))
                            .check("escape closed the palette", () -> !sh().commandPaletteOpen())
                            .checkEquals(
                                "escape dispatched nothing",
                                "workspace.settings",
                                () -> pal().lastDispatchedCommandId()
                            )
                            .check(
                                "escape did not run the filtered command",
                                () -> sh().mode() == WorkbenchMode.settings && !sh().zenMode()
                            ).check(
                                "escape left the shell open",
                                () -> Minecraft.getInstance().screen instanceof CalculatorScreen
                            )
                ).teardown(this::close);
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    private static List<String> ids(List<PaletteItem> items) {
        List<String> ids = new ArrayList<>();
        for (PaletteItem item : items) {
            ids.add(item.id());
        }
        return ids;
    }

    private static Rect viewport() {
        var window = Minecraft.getInstance().getWindow();
        return new Rect(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    private static void press(TestContext context, int key) {
        context.driver().keyDown(context.screenOrThrow(), key, 0, 0);
        context.driver().keyUp(context.screenOrThrow(), key, 0, 0);
    }

    private static void type(TestContext context, String text) {
        for (int index = 0; index < text.length(); index++) {
            context.driver().charTyped(context.screenOrThrow(), text.charAt(index), 0);
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

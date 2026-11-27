package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.LeftModeRailWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.SessionTabBarWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The four workspaces, reached the way a player reaches them: one click per icon on the left mode rail.
 *
 * <p>Each mode is checked for the same three facts — the shell's mode enum, the one region that mode owns being mounted with a
 * real rectangle, and that region being the only workspace in the tree — plus the layout switch that makes the non-planning
 * modes full-bleed. The regions are asserted through their own widget classes, never through a translated label.
 */
@ClientScenario(
    name = "cirrus.workbench.modes",
    group = "workbench",
    tags = {"workbench", "modes"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 180_000
)
public final class WorkbenchModesScenario {

    private static final int plannerIndex = 0;

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

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.modes: the four workspaces, one rail click each")
                .openScreen(CalculatorScreen::new).awaitScreen(CalculatorScreen.class)
                .checkScreen(CalculatorScreen.class).action("hold the open shell", context -> {
                    screen = WorkbenchTestKit.screen(context);
                    shell = scr().shell();
                }).frames(3).check("the rail shows one icon per mode", () -> {
                    var rail = rail();
                    return rail != null && LeftModeRailWidget.modeCount == WorkbenchMode.values().length;
                }).check("the rail maps every index onto a distinct mode", () -> {
                    List<WorkbenchMode> modes = List.of(
                        LeftModeRailWidget.modeAt(0),
                        LeftModeRailWidget.modeAt(1),
                        LeftModeRailWidget.modeAt(2),
                        LeftModeRailWidget.modeAt(3)
                    );
                    return modes.containsAll(List.of(WorkbenchMode.values()));
                });

        for (WorkbenchMode mode : List
                .of(WorkbenchMode.facilities, WorkbenchMode.database, WorkbenchMode.settings, WorkbenchMode.planner)) {
            build.group("switch to " + mode, () -> {
                build.action("click the " + mode + " rail icon", context -> clickMode(context, mode));
                build.waitUntil(
                    "the " + mode + " workspace is mounted",
                    () -> sh().mode() == mode && workspaceShown(mode)
                );
                build.frames(3);
                build.screenshot("workbench-mode-" + mode);
                build.checkEquals("the shell mode is " + mode, mode, () -> sh().mode());
                build.check(
                    "the " + mode + " workspace is the only workspace",
                    () -> workspaceCount(mode) == 1 && otherWorkspaces(mode).isEmpty(),
                    () -> "own=" + workspaceCount(mode) + " others=" + otherWorkspaces(mode)
                );
                build.check("the " + mode + " workspace has a usable rectangle", () -> {
                    Rect rect = workspaceRect(mode);
                    return rect != null && rect.width() > 0 && rect.height() > 0;
                });
                build.check("the " + mode + " workspace sits below the tab bar and right of the rail", () -> {
                    Rect rect = workspaceRect(mode);
                    return rect != null && rect.x() >= Math.round(sh().leftRailWidth()) && rect.y() >= tabBarBottom();
                });
                build.check("the " + mode + " workspace never overlaps the rail or the tab bar", () -> {
                    Rect rect = workspaceRect(mode);
                    Rect rail = railBounds();
                    Rect tabBar = tabBarBounds();
                    return rect != null
                            && rail != null
                            && tabBar != null
                            && !rect.intersects(rail)
                            && !rect.intersects(tabBar);
                });
                if (mode == WorkbenchMode.planner) {
                    build.check(
                        "planner brings back the dock islands and the canvas",
                        () -> WorkbenchTestKit.shown(LeftPlanDockWidget.class)
                                && WorkbenchTestKit.shown(RightCoPilotDockWidget.class)
                                && WorkbenchTestKit.shown(SpatialDagCanvasWidget.class)
                    );
                    build.check("planner keeps the centre canvas above its minimum width", () -> {
                        var canvas = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                        return canvas != null
                                && canvas.absoluteBounds().width() >= WorkbenchBreakpoint.minCenterCanvasWidth;
                    });
                } else {
                    build.check(
                        "the planning-only chrome is collapsed in " + mode,
                        () -> sh().leftDockWidth() == 0f
                                && sh().rightDockWidth() == 0f
                                && !WorkbenchTestKit.shown(SpatialDagCanvasWidget.class)
                    );
                    if (mode == WorkbenchMode.facilities) {
                        for (FacilitiesAndRulesWorkspace.Section targetSection : List.of(
                            FacilitiesAndRulesWorkspace.Section.lines,
                            FacilitiesAndRulesWorkspace.Section.recipes,
                            FacilitiesAndRulesWorkspace.Section.optIn,
                            FacilitiesAndRulesWorkspace.Section.rules,
                            FacilitiesAndRulesWorkspace.Section.machines
                        )) {
                            build.action("select facilities section " + targetSection, ctx -> {
                                FacilitiesAndRulesWorkspace ws = WorkbenchTestKit
                                        .first(FacilitiesAndRulesWorkspace.class);
                                if (ws != null) {
                                    ws.selectSection(targetSection);
                                }
                            }).frames(2).check("facilities section " + targetSection + " is active and mounted", () -> {
                                FacilitiesAndRulesWorkspace ws = WorkbenchTestKit
                                        .first(FacilitiesAndRulesWorkspace.class);
                                return ws != null
                                        && ws.section() == targetSection
                                        && ws.activePanel().lifecycle().mounted();
                            });
                        }
                    }
                }
            });
        }

        build.group(
            "the rail stays usable in every mode",
            () -> build.check("the rail keeps one clickable icon per mode", () -> {
                var rail = rail();
                return rail != null
                        && rail.width() > 0
                        && rail.modeIconSize() > 0
                        && rail.absoluteBounds().height() >= sh().height();
            }).check("the mode icons keep their vertical order in the rail", () -> {
                var rail = rail();
                if (rail == null) {
                    return false;
                }
                int previous = Integer.MIN_VALUE;
                for (int index = 0; index < LeftModeRailWidget.modeCount; index++) {
                    int y = rail.absoluteBounds().y() + rail.iconY(index);
                    if (y <= previous) {
                        return false;
                    }
                    previous = y;
                }
                return true;
            })
        ).teardown(this::close);
    }

    private void clickMode(TestContext context, WorkbenchMode mode) {
        var rail = rail();
        if (rail == null) {
            throw new IllegalStateException("the mode rail is not in the scene");
        }
        int index = List.of(WorkbenchMode.values()).indexOf(mode);
        Rect bounds = rail.absoluteBounds();
        int size = rail.modeIconSize();
        WorkbenchTestKit
                .click(context, bounds.x() + rail.iconX() + size / 2.0, bounds.y() + rail.iconY(index) + size / 2.0);
    }

    private @Nullable LeftModeRailWidget rail() {
        return WorkbenchTestKit.first(LeftModeRailWidget.class);
    }

    private boolean workspaceShown(WorkbenchMode mode) {
        Rect rect = workspaceRect(mode);
        return rect != null && rect.width() > 0 && rect.height() > 0;
    }

    private @Nullable Rect workspaceRect(WorkbenchMode mode) {
        for (Widget widget : workspaceWidgets(mode)) {
            if (widget.lifecycle().mounted() && widget.visible()) {
                return widget.absoluteBounds();
            }
        }
        return null;
    }

    private int workspaceCount(WorkbenchMode mode) {
        return workspaceWidgets(mode).size();
    }

    private List<String> otherWorkspaces(WorkbenchMode current) {
        List<String> found = new ArrayList<>();
        for (WorkbenchMode mode : WorkbenchMode.values()) {
            if (mode != current && !workspaceWidgets(mode).isEmpty()) {
                found.add(mode.name());
            }
        }
        return found;
    }

    private List<Widget> workspaceWidgets(WorkbenchMode mode) {
        return switch (mode) {
            case planner -> List.copyOf(WorkbenchTestKit.all(FlowStageWidget.class));
            case facilities -> List.copyOf(WorkbenchTestKit.all(FacilitiesAndRulesWorkspace.class));
            case database -> List.copyOf(WorkbenchTestKit.all(DatabasePage.class));
            case settings -> List.copyOf(WorkbenchTestKit.all(SettingsAndDiagnosticsWorkspace.class));
        };
    }

    private int tabBarBottom() {
        SessionTabBarWidget tabBar = WorkbenchTestKit.first(SessionTabBarWidget.class);
        return tabBar == null ? 0 : tabBar.absoluteBounds().bottom();
    }

    private @Nullable Rect railBounds() {
        LeftModeRailWidget rail = rail();
        return rail == null ? null : rail.absoluteBounds();
    }

    private @Nullable Rect tabBarBounds() {
        SessionTabBarWidget tabBar = WorkbenchTestKit.first(SessionTabBarWidget.class);
        return tabBar == null ? null : tabBar.absoluteBounds();
    }

    private void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CalculatorScreen open) {
            open.onClose();
        }
        minecraft.setScreen(null);
    }
}

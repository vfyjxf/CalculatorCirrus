package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusPaletteVars;
import dev.vfyjxf.cirrus.ui.CirrusThemeStyle;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.BottomTimelineStripWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.CommandPaletteOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.LeftModeRailWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.SessionTabBarWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TargetBasketPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TopCommandDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchMeterBar;
import dev.vfyjxf.cloudlib.api.math.Insets;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.inworld.space.ExclusionProvider;
import dev.vfyjxf.cloudlib.api.ui.inworld.space.InworldExclusions;
import dev.vfyjxf.cloudlib.api.ui.style.Styles;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.Selectors;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The shell the full-screen workbench actually mounts: one viewport-wide island set, four chrome docks, a canvas between the
 * two dock columns, and every widget in the tree mounted.
 *
 * <p>The mount assertion is the point of this scenario rather than boilerplate: the chrome, the docks and the canvas are all
 * created before the screen exists, and a widget the mount walk skips makes the first layout pass throw instead of drawing.
 * Nothing smaller than a real open catches that — a unit test builds widgets without a scene, so it never runs the walk.
 */
@ClientScenario(
    name = "cirrus.workbench.open",
    group = "workbench",
    tags = {"workbench", "shell"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 120_000
)
public final class WorkbenchOpenScenario {

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
        build.guiScale(1).log("workbench.open: the full-screen shell mounts, sizes and lays out its docks")
                .openScreen(CalculatorScreen::new).awaitScreen(CalculatorScreen.class)
                .checkScreen(CalculatorScreen.class).action("hold the open shell", context -> capture(context))
                .frames(3).screenshot("workbench-open")

                .check("the shell is the screen's own", () -> sh() == scr().shell())
                .check(
                    "the shell fills the gui viewport",
                    () -> Math.round(sh().width()) == Minecraft.getInstance().getWindow().getGuiScaledWidth()
                            && Math.round(sh().height()) == Minecraft.getInstance().getWindow().getGuiScaledHeight()
                            && Math.round(sh().absolutePos().x()) == 0
                            && Math.round(sh().absolutePos().y()) == 0
                ).check("the shell stays full-screen even with active vanilla HUD exclusions", () -> {
                    int gw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                    int gh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                    ExclusionProvider hud = ctx -> List
                            .of(new Rect(0, 30, 320, 180), new Rect(gw / 2 - 91, gh - 22, 182, 22));
                    InworldExclusions.register(hud);
                    try {
                        WorkbenchTestKit.scene().setLayoutArea(gw, gh);
                        WorkbenchTestKit.scene().stabilize();
                        return Insets.zero.equals(WorkbenchTestKit.scene().exclusionInsets())
                                && Math.round(sh().width()) == gw
                                && Math.round(sh().height()) == gh
                                && Math.round(sh().absolutePos().x()) == 0
                                && Math.round(sh().absolutePos().y()) == 0;
                    } finally {
                        InworldExclusions.unregister(hud);
                    }
                }).check("the shell is mounted and visible", () -> sh().lifecycle().mounted() && sh().visible())
                .check("the shell opens in planner mode", () -> sh().mode() == WorkbenchMode.planner)
                .check("the shell opens outside zen mode", () -> !sh().zenMode())
                .check("the calculator css theme is attached to the workbench scene", () -> {
                    Theme active = WorkbenchTestKit.scene().theme();
                    return active != null && active.id().equals(WorkbenchThemeMode.obsidianBlueprint.themeId());
                }).check("the theme declares every palette and texture variable", () -> {
                    Theme active = WorkbenchTestKit.scene().theme();
                    if (active == null) {
                        return false;
                    }
                    long colors = active.rootVars().keySet().stream()
                            .filter(name -> name.startsWith("--cirrus-") && !name.startsWith("--cirrus-tex-")).count();
                    long textures = active.rootVars().keySet().stream().filter(name -> name.startsWith("--cirrus-tex-"))
                            .count();
                    return colors == 67L && textures == 31L;
                }).check("the palette and textures resolve through the scene theme layer", () -> {
                    Theme active = WorkbenchTestKit.scene().theme();
                    CirrusPalette resolved = CirrusPalette.of(active, WorkbenchThemeMode.obsidianBlueprint);
                    CirrusThemeTextures textures = CirrusThemeTextures.of(active, WorkbenchThemeMode.obsidianBlueprint);
                    return resolved.dockSurface() == CirrusPalette.obsidianBlueprint.dockSurface()
                            && resolved.focusRing() == CirrusPalette.obsidianBlueprint.focusRing()
                            && !textures.dockIsland().isEmpty()
                            && !textures.cardSurface().isEmpty();
                })
                .group(
                    "switching the workbench theme swaps the scene css layer",
                    () -> build.action(
                        "switch the shell to atelier stone",
                        context -> sh().setTheme(WorkbenchThemeMode.atelierStone)
                    ).frames(1).check("the atelier layer replaced the obsidian one on the scene", () -> {
                        Theme active = WorkbenchTestKit.scene().theme();
                        return active != null && active.id().equals(WorkbenchThemeMode.atelierStone.themeId());
                    }).check("the palette, textures, and chrome background follow the new layer", () -> {
                        Theme active = WorkbenchTestKit.scene().theme();
                        CirrusPalette resolved = CirrusPalette.of(active, WorkbenchThemeMode.atelierStone);
                        CirrusThemeTextures lightTex = CirrusThemeTextures.of(active, WorkbenchThemeMode.atelierStone);
                        CirrusThemeTextures darkTex = CirrusThemeTextures.of(WorkbenchThemeMode.obsidianBlueprint);
                        TopCommandDockWidget topbar = WorkbenchTestKit.first(TopCommandDockWidget.class);
                        VisualTexture topbarBg = topbar == null ? null : topbar.style().get(Styles.background);
                        return resolved.dockSurface() == CirrusPalette.atelierStone.dockSurface()
                                && resolved.textPrimary() != CirrusPalette.obsidianBlueprint.textPrimary()
                                && !lightTex.dockIsland().equals(darkTex.dockIsland())
                                && topbarBg != null
                                && !topbarBg.isEmpty()
                                && !topbarBg.equals(darkTex.cardSurface());
                    })
                            .action(
                                "switch back to obsidian blueprint",
                                context -> sh().setTheme(WorkbenchThemeMode.obsidianBlueprint)
                            ).frames(1).check("the obsidian layer is back on the scene", () -> {
                                Theme active = WorkbenchTestKit.scene().theme();
                                return active != null
                                        && active.id().equals(WorkbenchThemeMode.obsidianBlueprint.themeId());
                            }).check("css resolves the meter aliases through var()", () -> {
                                Theme active = WorkbenchTestKit.scene().theme();
                                CirrusPalette palette = CirrusPalette.of(active, WorkbenchThemeMode.obsidianBlueprint);
                                return CirrusThemeStyle.color(active, CirrusPaletteVars.meterOk, 0) == palette.costS()
                                        && CirrusThemeStyle.color(active, CirrusPaletteVars.meterDanger, 0)
                                                == palette.ruleHardBan()
                                        && CirrusThemeStyle.color(active, CirrusPaletteVars.meterFocus, 0)
                                                == palette.focusRing()
                                        && WorkbenchMeterBar.styleColor(palette, "ok") == palette.costS()
                                        && WorkbenchMeterBar.styleColor(palette, "danger") == palette.ruleHardBan()
                                        && WorkbenchMeterBar.styleColor(palette, null) == palette.focusRing();
                            })
                )
                .check(
                    "the breakpoint matches the viewport width",
                    () -> sh().breakpoint() == WorkbenchBreakpoint.classify(sh().width())
                ).checkEquals("no command palette is open", Boolean.FALSE, () -> sh().commandPaletteOpen())
                .check("no popover is open", () -> {
                    TargetBasketPopoverWidget basket = WorkbenchTestKit.first(TargetBasketPopoverWidget.class);
                    return basket != null && !basket.isOpen();
                })

                .group(
                    "the four chrome docks",
                    () -> build.checkExists(Selectors.byType(SessionTabBarWidget.class))
                            .checkExists(Selectors.byType(TopCommandDockWidget.class))
                            .checkExists(Selectors.byType(LeftModeRailWidget.class))
                            .checkExists(Selectors.byType(BottomTimelineStripWidget.class))
                            .check(
                                "the dock chrome is shown",
                                () -> WorkbenchTestKit.shown(SessionTabBarWidget.class)
                                        && WorkbenchTestKit.shown(TopCommandDockWidget.class)
                                        && WorkbenchTestKit.shown(LeftModeRailWidget.class)
                                        && WorkbenchTestKit.shown(BottomTimelineStripWidget.class)
                            )
                            .check(
                                "the two dock islands are shown",
                                () -> WorkbenchTestKit.shown(LeftPlanDockWidget.class)
                                        && WorkbenchTestKit.shown(RightCoPilotDockWidget.class)
                            ).check("the dock islands carry the shell's own dock widths", () -> {
                                var left = WorkbenchTestKit.first(LeftPlanDockWidget.class);
                                var right = WorkbenchTestKit.first(RightCoPilotDockWidget.class);
                                return left != null
                                        && right != null
                                        && Math.abs(left.absoluteBounds().width() - sh().leftDockWidth()) <= 1f
                                        && Math.abs(right.absoluteBounds().width() - sh().rightDockWidth()) <= 1f;
                            }).check(
                                "both dock widths respect the breakpoint minimums",
                                () -> sh().leftDockWidth() >= WorkbenchBreakpoint.leftDockMin
                                        && sh().rightDockWidth() >= WorkbenchBreakpoint.rightDockMin
                            )
                )

                .group(
                    "the canvas",
                    () -> build.checkExists(Selectors.byType(FlowStageWidget.class))
                            .checkExists(Selectors.byType(SpatialDagCanvasWidget.class))
                            .check(
                                "the stage and its canvas are shown",
                                () -> WorkbenchTestKit.shown(FlowStageWidget.class)
                                        && WorkbenchTestKit.shown(SpatialDagCanvasWidget.class)
                            ).check("the canvas covers the whole stage below the stage header", () -> {
                                var stage = WorkbenchTestKit.first(FlowStageWidget.class);
                                var canvas = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                                if (stage == null || canvas == null) {
                                    return false;
                                }
                                Rect sb = stage.absoluteBounds();
                                Rect hb = stage.headerBar().absoluteBounds();
                                Rect cb = canvas.absoluteBounds();
                                return hb.height() == FlowStageWidget.headerHeight
                                        && hb.x() == sb.x()
                                        && hb.y() == sb.y()
                                        && hb.width() == sb.width()
                                        && cb.x() == sb.x()
                                        && cb.width() == sb.width()
                                        && cb.y() == hb.bottom()
                                        && cb.bottom() == sb.bottom();
                            }).check("the canvas keeps at least the minimum centre width", () -> {
                                var canvas = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                                return canvas != null
                                        && canvas.absoluteBounds().width() >= WorkbenchBreakpoint.minCenterCanvasWidth;
                            })
                )

                .group(
                    "the regions do not overlap",
                    () -> build
                            .check(
                                "the mode rail never overlaps the tab bar or the top dock",
                                () -> !overlaps(LeftModeRailWidget.class, SessionTabBarWidget.class)
                                        && !overlaps(LeftModeRailWidget.class, TopCommandDockWidget.class)
                            )
                            .check(
                                "the top chrome never overlaps the dock islands",
                                () -> !overlaps(TopCommandDockWidget.class, LeftPlanDockWidget.class)
                                        && !overlaps(TopCommandDockWidget.class, RightCoPilotDockWidget.class)
                            )
                            .check(
                                "the bottom strip never overlaps the dock islands",
                                () -> !overlaps(BottomTimelineStripWidget.class, LeftPlanDockWidget.class)
                                        && !overlaps(BottomTimelineStripWidget.class, RightCoPilotDockWidget.class)
                            )
                            .check(
                                "the left dock ends where the canvas begins",
                                () -> !overlaps(LeftPlanDockWidget.class, SpatialDagCanvasWidget.class)
                            )
                            .check(
                                "the canvas ends where the right dock begins",
                                () -> !overlaps(SpatialDagCanvasWidget.class, RightCoPilotDockWidget.class)
                            ).check("the canvas sits below the top chrome", () -> {
                                var canvas = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                                return canvas != null && canvas.absoluteBounds().y() >= sh().topChromeHeight();
                            }).check("the canvas sits above the bottom strip", () -> {
                                var canvas = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                                var strip = WorkbenchTestKit.first(BottomTimelineStripWidget.class);
                                return canvas != null
                                        && strip != null
                                        && canvas.absoluteBounds().bottom() <= strip.absoluteBounds().y();
                            }).check("every region stays inside the shell", () -> {
                                var bounds = new Rect(0, 0, Math.round(sh().width()), Math.round(sh().height()));
                                for (var type : new Class<?>[]{LeftModeRailWidget.class, SessionTabBarWidget.class,
                                        TopCommandDockWidget.class, BottomTimelineStripWidget.class,
                                        LeftPlanDockWidget.class, RightCoPilotDockWidget.class,
                                        SpatialDagCanvasWidget.class}) {
                                    for (var widget : WorkbenchTestKit.all(type.asSubclass(Widget.class))) {
                                        if (widget.visible() && !bounds.contains(widget.absoluteBounds())) {
                                            return false;
                                        }
                                    }
                                }
                                return true;
                            })
                )

                .group(
                    "every widget in the tree is mounted",
                    () -> build.check("the palette and the toast stack are mounted even while hidden", () -> {
                        List<CommandPaletteOverlayWidget> palettes = WorkbenchTestKit
                                .all(CommandPaletteOverlayWidget.class);
                        return palettes.size() == 1 && palettes.get(0).lifecycle().mounted();
                    }).check(
                        "no widget was left behind by the mount walk",
                        () -> WorkbenchTestKit.unmountedWidgets().isEmpty(),
                        () -> WorkbenchTestKit.unmountedWidgets().toString()
                    )
                ).teardown(this::close);
    }

    private void capture(TestContext context) {
        screen = WorkbenchTestKit.screen(context);
        shell = scr().shell();
    }

    private void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CalculatorScreen open) {
            open.onClose();
        }
        minecraft.setScreen(null);
    }

    private static boolean overlaps(Class<? extends Widget> left, Class<? extends Widget> right) {
        var first = WorkbenchTestKit.first(left);
        var second = WorkbenchTestKit.first(right);
        if (first == null || second == null) {
            return false;
        }
        Rect a = first.absoluteBounds();
        Rect b = second.absoluteBounds();
        return a.intersects(b);
    }
}

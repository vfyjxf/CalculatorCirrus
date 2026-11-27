package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.CommandPaletteOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.RecipeAssistantPopupWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TargetBasketPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ToastStackWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.LeftPlanDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.ParetoScatterPlotWidget;
import dev.vfyjxf.cirrus.ui.workbench.copilot.RightCoPilotDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.PreparationLedgerWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.state.DagEdgeStyle;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import dev.vfyjxf.cloudlib.ui.widget.CellRowWidget;
import dev.vfyjxf.cloudlib.ui.widget.InfoBarWidget;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Dual-theme visual verification across Obsidian Blueprint (dark) and Atelier Stone (light) with a populated multi-step DAG.
 */
@ClientScenario(
    name = "cirrus.workbench.theme_visual",
    group = "workbench",
    tags = {"workbench", "theme", "visual"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 240_000
)
public final class WorkbenchThemeVisualScenario {

    private WorkbenchVisualFixture.@Nullable VisualShowcaseScreen screen;
    private @Nullable WorkbenchShellWidget shell;

    private WorkbenchShellWidget sh() {
        WorkbenchShellWidget held = shell;
        if (held == null) {
            throw new IllegalStateException("visual showcase shell was not captured");
        }
        return held;
    }

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("workbench.theme_visual: dual-theme visual verification across all modes and widgets")
                .openScreen(WorkbenchVisualFixture::createScreen)
                .awaitScreen(WorkbenchVisualFixture.VisualShowcaseScreen.class).action("capture visual shell", ctx -> {
                    if (ctx.screen() instanceof WorkbenchVisualFixture.VisualShowcaseScreen showcase) {
                        screen = showcase;
                        shell = showcase.shell();
                    }
                }).frames(4).check("populated plan is active with pareto plans and prompts", () -> {
                    var snap = sh().activeSnapshot();
                    return snap.selectedPlan().isPresent()
                            && snap.result() != null
                            && snap.result().paretoPlans().size() == 3
                            && snap.result().decisionPrompts().size() == 2;
                });

        captureThemePass(build, WorkbenchThemeMode.obsidianBlueprint, "dark");
        captureThemePass(build, WorkbenchThemeMode.atelierStone, "light");

        build.teardown(this::close);
    }

    private void captureThemePass(ScenarioBuilder build, WorkbenchThemeMode mode, String tag) {
        build.group("theme pass " + tag, () -> {
            build.action("activate " + mode + " theme in planner DAG", ctx -> {
                sh().closeTransientOverlays(null);
                sh().setZenMode(false);
                sh().setMode(WorkbenchMode.planner);
                setFlowMode(FlowViewMode.spatialDag);
                setDagLayout(DagLayoutMode.horizontalLr, DagEdgeStyle.orthogonal);
                selectRightTab(RightCoPilotDockWidget.Tab.prompts);
                sh().setTheme(mode);
            }).frames(4).check("scene carries " + mode.themeId(), () -> {
                Theme active = WorkbenchTestKit.scene().theme();
                return active != null && active.id().equals(mode.themeId());
            })
                    .check(
                        "planner mounts CloudLib InfoBarWidget, CellRowWidget, and RingWidget",
                        () -> !WorkbenchTestKit.all(InfoBarWidget.class).isEmpty()
                                && !WorkbenchTestKit.all(CellRowWidget.class).isEmpty()
                                && !WorkbenchTestKit.all(RingWidget.class).isEmpty()
                                && WorkbenchTestKit.shown(SpatialDagCanvasWidget.class)
                    ).screenshot("visual-" + tag + "-01-planner-dag");

            build.action("trigger screen-space bottom-right execution toast card", ctx -> {
                ToastStackWidget toast = WorkbenchTestKit.first(ToastStackWidget.class);
                if (toast != null) {
                    toast.push(
                        ToastStackWidget.Tone.success,
                        CirrusLangs.Workbench.Topbar.sentToastTitle.get(),
                        CirrusLangs.Workbench.Topbar.sentToastBody.get("Blast Furnace", 1),
                        CirrusLangs.Workbench.Topbar.sentToastBadge.get(2, 4),
                        null
                    );
                }
            }).frames(4).check("toast stack renders in bottom-right screen space", () -> {
                ToastStackWidget toast = WorkbenchTestKit.first(ToastStackWidget.class);
                if (toast == null || !toast.visible()) {
                    return false;
                }
                Rect tb = toast.absoluteBounds();
                return tb.right() >= 1200f && tb.bottom() >= 680f;
            }).screenshot("visual-" + tag + "-01e-toast").action("clear toast", ctx -> {
                ToastStackWidget toast = WorkbenchTestKit.first(ToastStackWidget.class);
                if (toast != null) {
                    toast.clear();
                }
            }).frames(2);

            build.action("switch DAG to TB layout and open step inspector recipe picker", ctx -> {
                setDagLayout(DagLayoutMode.verticalTb, DagEdgeStyle.orthogonal);
                PlannerWorkbenchController controller = sh().activeController();
                if (controller != null) {
                    controller.setSelectedSteps(Set.of("s-smelt"));
                }
                selectRightTab(RightCoPilotDockWidget.Tab.inspector);
            }).frames(4).screenshot("visual-" + tag + "-01b-dag-tb-inspector");

            build.action("showcase Quick Drop tab, docked stage expand toggle, and View Settings popover", ctx -> {
                setDagLayout(DagLayoutMode.horizontalLr, DagEdgeStyle.orthogonal);
                sh().setLeftDockCollapsed(true);
                selectRightTab(RightCoPilotDockWidget.Tab.quickDrop);
                SpatialDagCanvasWidget dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                FlowStageWidget stage = WorkbenchTestKit.first(FlowStageWidget.class);
                if (dag != null && stage != null) {
                    Rect hdr = stage.headerBar().absoluteBounds();
                    dag.toggleViewSettingsPopoverAt(
                        (int) Math.round(hdr.right() - 6),
                        (int) Math.round(hdr.bottom() + 4)
                    );
                }
            }).frames(4).check("view settings popover stays open", () -> {
                SpatialDagCanvasWidget dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                return dag != null && dag.isViewSettingsPopoverOpen();
            }).screenshot("visual-" + tag + "-01d-quickdrop-popover").action("restore docks and close popover", ctx -> {
                sh().closeTransientOverlays(null);
                sh().setLeftDockCollapsed(false);
            }).frames(2);

            build.action(
                "showcase LR locator ruler, cursor neighborhood preview, and OreUI rich hover tooltip",
                ctx -> {
                    setDagLayout(DagLayoutMode.horizontalLr, DagEdgeStyle.orthogonal);
                    selectRightTab(RightCoPilotDockWidget.Tab.prompts);
                }
            ).waitUntil("horizontal locator ruler ready", () -> {
                SpatialDagCanvasWidget dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                return dag != null
                        && !dag.locatorRuler().isVertical()
                        && dag.locatorRuler().absoluteBounds().width() > 200
                        && dag.locatorRuler().absoluteBounds().height() <= 52
                        && !dag.locatorRuler().stageCells().isEmpty();
            }).frames(12)
                    .action("hover locator stage 02 to trigger cursor neighborhood preview and rich tooltip", ctx -> {
                        SpatialDagCanvasWidget dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                        if (dag != null) {
                            var cells = dag.locatorRuler().stageCells();
                            if (!cells.isEmpty()) {
                                var target = cells.get(Math.min(1, cells.size() - 1));
                                Rect locBounds = dag.locatorRuler().absoluteBounds();
                                float hx = (float) (locBounds.x() + target.x() + Math.max(12, target.width() * 0.65f));
                                float hy = (float) (locBounds.y() + target.y() + target.height() * 0.5f);
                                ctx.driver().placeCursor(hx, hy);
                                ctx.driver().moveTo(ctx.screenOrThrow(), hx, hy);
                            }
                        }
                    }).frames(4)
                    .check("OreUI rich tooltip rendered", () -> sh().tooltipOverlay().lastRendered() != null)
                    .check("locator cursor neighborhood hover active", () -> {
                        SpatialDagCanvasWidget dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
                        return dag != null && dag.locatorRuler().hoveredStageIndex() != null;
                    }).screenshot("visual-" + tag + "-01c-locator-rich-tooltip");

            build.action("switch left plan dock to scatter plot via tab click", ctx -> {
                LeftPlanDockWidget left = WorkbenchTestKit.first(LeftPlanDockWidget.class);
                if (left != null) {
                    Rect b = left.absoluteBounds();
                    float sx = (float) (b.x() + b.width() * 0.65);
                    float sy = (float) (b.y() + 12);
                    ctx.driver().placeCursor(sx, sy);
                    ctx.driver().moveTo(ctx.screenOrThrow(), sx, sy);
                    ctx.driver().mouseDown(ctx.screenOrThrow(), sx, sy, 0);
                    ctx.driver().mouseUp(ctx.screenOrThrow(), sx, sy, 0);
                }
            }).frames(4).check("scatter plot opens and renders with full bounds", () -> {
                LeftPlanDockWidget left = WorkbenchTestKit.first(LeftPlanDockWidget.class);
                ParetoScatterPlotWidget scatter = WorkbenchTestKit.first(ParetoScatterPlotWidget.class);
                return left != null
                        && left.mode() == LeftPlanDockWidget.Mode.scatter
                        && scatter != null
                        && scatter.visible()
                        && scatter.width() > 100
                        && scatter.height() > 100;
            }).screenshot("visual-" + tag + "-01f-scatter").action("restore left plan dock to cards mode", ctx -> {
                LeftPlanDockWidget left = WorkbenchTestKit.first(LeftPlanDockWidget.class);
                if (left != null) {
                    left.setMode(LeftPlanDockWidget.Mode.cards);
                }
            }).frames(2);

            build.action("clear hover cursor and switch flow to preparation ledger", ctx -> {
                sh().tooltipOverlay().clearPinnedTooltip();
                ctx.driver().placeCursor(4f, 4f);
                ctx.driver().moveTo(ctx.screenOrThrow(), 4f, 4f);
                PlannerWorkbenchController controller = sh().activeController();
                if (controller != null) {
                    controller.setSelectedSteps(Set.of());
                }
                setDagLayout(DagLayoutMode.horizontalLr, DagEdgeStyle.orthogonal);
                selectRightTab(RightCoPilotDockWidget.Tab.prompts);
                FlowStageWidget stage = WorkbenchTestKit.first(FlowStageWidget.class);
                if (stage != null) {
                    stage.requestViewMode(FlowViewMode.preparationLedger);
                } else {
                    setFlowMode(FlowViewMode.preparationLedger);
                }
            }).waitUntil("ledger shown", () -> WorkbenchTestKit.shown(PreparationLedgerWidget.class)).frames(4)
                    .check(
                        "stage header flow view switch persists in controller snapshot and ledger is populated",
                        () -> {
                            PreparationLedgerWidget ledger = WorkbenchTestKit.first(PreparationLedgerWidget.class);
                            return sh().activeSnapshot().flowViewMode() == FlowViewMode.preparationLedger
                                    && ledger != null
                                    && ledger.model() != null
                                    && !ledger.model().acquireRows().isEmpty();
                        }
                    ).screenshot("visual-" + tag + "-02-planner-ledger")
                    .action("click second sub-tab inside preparation ledger", ctx -> {
                        PreparationLedgerWidget ledger = WorkbenchTestKit.first(PreparationLedgerWidget.class);
                        if (ledger != null) {
                            Rect b = ledger.absoluteBounds();
                            int cardW = Math.max(40, ledger.width() - PreparationLedgerWidget.cardGap * 2);
                            int tabW = Math.max(40, cardW / 4);
                            float tx = (float) (b.x() + PreparationLedgerWidget.cardGap + tabW + tabW * 0.5);
                            float ty = (float) (b.y()
                                    + PreparationLedgerWidget.headerHeight
                                    + 4
                                    + PreparationLedgerWidget.tabHeight * 0.5);
                            ctx.driver().placeCursor(tx, ty);
                            ctx.driver().moveTo(ctx.screenOrThrow(), tx, ty);
                            ctx.driver().mouseDown(ctx.screenOrThrow(), tx, ty, 0);
                            ctx.driver().mouseUp(ctx.screenOrThrow(), tx, ty, 0);
                        }
                    }).frames(3).check("ledger sub-tab click switches active section", () -> {
                        PreparationLedgerWidget ledger = WorkbenchTestKit.first(PreparationLedgerWidget.class);
                        return ledger != null && ledger.activeSection() == 1;
                    }).action("restore ledger to section 0", ctx -> {
                        PreparationLedgerWidget ledger = WorkbenchTestKit.first(PreparationLedgerWidget.class);
                        if (ledger != null) {
                            ledger.selectSection(0);
                        }
                    }).frames(2);

            build.action("switch flow to execution checklist", ctx -> setFlowMode(FlowViewMode.executionChecklist))
                    .waitUntil("checklist shown", () -> WorkbenchTestKit.shown(ExecutionChecklistWidget.class))
                    .frames(3).check("checklist is populated from active plan", () -> {
                        ExecutionChecklistWidget checklist = WorkbenchTestKit.first(ExecutionChecklistWidget.class);
                        return checklist != null && checklist.cardCount() > 0;
                    }).screenshot("visual-" + tag + "-03-planner-checklist");

            build.action("enter zen mode on DAG", ctx -> {
                ctx.driver().placeCursor(640f, 360f);
                ctx.driver().moveTo(ctx.screenOrThrow(), 640f, 360f);
                setFlowMode(FlowViewMode.spatialDag);
                sh().setZenMode(true);
            }).frames(3).check("zen mode active", () -> sh().zenMode()).screenshot("visual-" + tag + "-04-zen-dag");

            build.action("exit zen and open command palette", ctx -> {
                sh().setZenMode(false);
                sh().openCommandPalette();
            }).frames(8).check("palette open", () -> WorkbenchTestKit.shown(CommandPaletteOverlayWidget.class))
                    .screenshot("visual-" + tag + "-05-palette");

            build.action("open target basket popover", ctx -> {
                sh().closeCommandPalette();
                TargetBasketPopoverWidget basket = WorkbenchTestKit.first(TargetBasketPopoverWidget.class);
                if (basket != null) {
                    basket.show();
                }
            }).frames(8).check("basket open", () -> WorkbenchTestKit.shown(TargetBasketPopoverWidget.class))
                    .screenshot("visual-" + tag + "-06-popover")
                    .action("click Catalog & Recipes button inside target basket over left dock", ctx -> {
                        TargetBasketPopoverWidget basket = WorkbenchTestKit.first(TargetBasketPopoverWidget.class);
                        if (basket != null) {
                            Rect b = basket.absoluteBounds();
                            Rect browse = basket.browseCatalogRect();
                            float bx = (float) (b.x() + browse.x() + browse.width() * 0.5);
                            float by = (float) (b.y() + browse.y() + browse.height() * 0.5);
                            ctx.driver().placeCursor(bx, by);
                            ctx.driver().moveTo(ctx.screenOrThrow(), bx, by);
                            ctx.driver().mouseDown(ctx.screenOrThrow(), bx, by, 0);
                            ctx.driver().mouseUp(ctx.screenOrThrow(), bx, by, 0);
                        }
                    }).frames(4).check(
                        "clicking target basket over left dock closes basket and opens recipe assistant",
                        () -> !WorkbenchTestKit.shown(TargetBasketPopoverWidget.class)
                                && WorkbenchTestKit.shown(RecipeAssistantPopupWidget.class)
                    );

            build.action("open recipe selection assistant popup for iron ingot", ctx -> {
                sh().closeTransientOverlays(null);
                sh().openRecipeAssistant(ResourceKey.item("minecraft:iron_ingot"));
            }).frames(6).check("recipe assistant open", () -> WorkbenchTestKit.shown(RecipeAssistantPopupWidget.class))
                    .screenshot("visual-" + tag + "-06b-recipe-assistant");

            build.action("open full-catalog target and recipe picker inside planner", ctx -> {
                sh().closeTransientOverlays(null);
                sh().openTargetCatalogPicker();
            }).frames(6).check("catalog picker open", () -> WorkbenchTestKit.shown(RecipeAssistantPopupWidget.class))
                    .screenshot("visual-" + tag + "-06c-catalog-picker");

            build.action("open human-in-the-loop guided decision workbench", ctx -> {
                sh().closeTransientOverlays(null);
                sh().openGuidedDecisionWorkbench();
            }).frames(6).check("guided decisions open", () -> WorkbenchTestKit.shown(RecipeAssistantPopupWidget.class))
                    .screenshot("visual-" + tag + "-06d-guided-decisions");

            build.action("switch to facilities workspace", ctx -> {
                sh().closeTransientOverlays(null);
                sh().setMode(WorkbenchMode.facilities);
            }).waitUntil("facilities shown", () -> WorkbenchTestKit.shown(FacilitiesAndRulesWorkspace.class)).frames(3)
                    .check("owned machine ring gauges mounted", () -> !WorkbenchTestKit.all(RingWidget.class).isEmpty())
                    .screenshot("visual-" + tag + "-07-facilities");

            build.action("switch to database workspace", ctx -> sh().setMode(WorkbenchMode.database))
                    .waitUntil("database shown", () -> WorkbenchTestKit.shown(DatabasePage.class))
                    .waitUntil("database results ready", () -> {
                        DatabasePage db = WorkbenchTestKit.first(DatabasePage.class);
                        if (db != null) {
                            db.debugCompletePendingQuery();
                            return db.debugVisibleResultCount() > 0 && db.debugResultCardCount() > 0;
                        }
                        return false;
                    }).frames(3).screenshot("visual-" + tag + "-08-database");

            build.action("hover first database recipe card for OreUI rich tooltip", ctx -> {
                DatabasePage.VirtualizedResultSectionsWidget list = WorkbenchTestKit
                        .first(DatabasePage.VirtualizedResultSectionsWidget.class);
                if (list != null) {
                    Rect bounds = list.absoluteBounds();
                    float hx = (float) (bounds.x() + Math.min(80, Math.max(16, bounds.width() * 0.25)));
                    float hy = (float) (bounds.y() + 44);
                    ctx.driver().placeCursor(hx, hy);
                    ctx.driver().moveTo(ctx.screenOrThrow(), hx, hy);
                }
            }).frames(4)
                    .check(
                        "database recipe card renders OreUI rich tooltip",
                        () -> sh().tooltipOverlay().lastRendered() != null
                    ).screenshot("visual-" + tag + "-08c-database-tooltip")
                    .action("clear database hover cursor", ctx -> {
                        ctx.driver().placeCursor(4f, 4f);
                        ctx.driver().moveTo(ctx.screenOrThrow(), 4f, 4f);
                    });

            build.action("open recipe projection transform pipeline view", ctx -> {
                DatabasePage db = WorkbenchTestKit.first(DatabasePage.class);
                if (db != null) {
                    db.debugSetProjectionContextOption("calculator_cirrus:debug/projection_scale", 2L);
                    db.debugSetProjectionContextOption("calculator_cirrus:debug/projection_extra_output", true);
                    db.debugSetQuery("projection_probe");
                    db.debugCompletePendingQuery();
                    db.debugOpenFirstResultDetail();
                    if (!db.debugRecipeDetailOpen()) {
                        db.debugSetQuery("");
                        db.debugCompletePendingQuery();
                        db.debugOpenFirstResultDetail();
                    }
                }
            }).frames(4).screenshot("visual-" + tag + "-08b-database-pipeline");

            build.action("close recipe detail before settings", ctx -> {
                DatabasePage db = WorkbenchTestKit.first(DatabasePage.class);
                if (db != null) {
                    if (db.debugRecipeDetailOpen()) {
                        db.debugLeaveRecipeDetail();
                    }
                    db.debugSetQuery("");
                    db.debugCompletePendingQuery();
                }
            }).frames(3);

            build.action("switch to settings workspace", ctx -> sh().setMode(WorkbenchMode.settings))
                    .waitUntil("settings shown", () -> WorkbenchTestKit.shown(SettingsAndDiagnosticsWorkspace.class))
                    .frames(3)
                    .check(
                        "three diagnostics RingWidgets shown",
                        () -> WorkbenchTestKit.all(RingWidget.class).size() >= 3
                    ).screenshot("visual-" + tag + "-09-settings");
        });
    }

    private void setFlowMode(FlowViewMode flowMode) {
        PlannerWorkbenchController controller = sh().activeController();
        if (controller != null) {
            controller.setFlowViewMode(flowMode);
        }
    }

    private void setDagLayout(DagLayoutMode layoutMode, DagEdgeStyle edgeStyle) {
        PlannerWorkbenchController controller = sh().activeController();
        if (controller != null) {
            controller.setDagLayoutMode(layoutMode);
        }
        SpatialDagCanvasWidget dag = WorkbenchTestKit.first(SpatialDagCanvasWidget.class);
        if (dag != null) {
            dag.setEdgeStyle(edgeStyle);
        }
    }

    private void selectRightTab(RightCoPilotDockWidget.Tab tab) {
        RightCoPilotDockWidget right = WorkbenchTestKit.first(RightCoPilotDockWidget.class);
        if (right != null) {
            right.setTab(tab);
        }
    }

    private void close() {
        if (shell != null) {
            setDagLayout(DagLayoutMode.horizontalLr, DagEdgeStyle.orthogonal);
            shell.setTheme(WorkbenchThemeMode.obsidianBlueprint);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (screen != null) {
            screen.onClose();
            screen = null;
        }
        minecraft.setScreen(null);
    }
}

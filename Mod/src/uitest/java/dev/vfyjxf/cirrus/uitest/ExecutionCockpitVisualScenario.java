package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionAuditLog;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionCheckpointStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionQueueStore;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTargetRecipeState;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotExecutionDriver;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotTrace;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeStyle;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionCockpitTheme;
import dev.vfyjxf.cirrus.ui.execution.cockpit.ExecutionDiagnosticsDrawerWidget;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayOpenState;
import dev.vfyjxf.cirrus.ui.execution.overlay.ExecutionOverlayRoot;
import dev.vfyjxf.cirrus.ui.execution.overlay.SlotFlowTraceRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.inset;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/**
 * Dual-theme visual verification of the Pro OreUI Execution Cockpit: Queue switcher, concrete 3x3 Active Recipe Spotlight, searchable/sortable Materials panel, rich dependency Execution Graph cards, Diagnostics & Telemetry drawer, and rich tooltips.
 */
@ClientScenario(
    name = "cirrus.execution.cockpit_visual",
    group = "workbench",
    tags = {"workbench", "execution", "visual"},
    requiresWorld = true,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 120_000
)
public final class ExecutionCockpitVisualScenario {

    private @Nullable CockpitShowcaseScreen screen;
    private @Nullable RecipeApiImpl previousApi;

    public void define(ScenarioBuilder build) {
        build.guiScale(1).log("execution.cockpit_visual: dual-theme visual verification of the Pro Execution Cockpit")
                .action("setup synthetic multi-stage execution plan and 2-plan queue", ctx -> {
                    CalculatorSyntheticScenario.Scenario primary = CalculatorSyntheticScenario.createWorkbenchLike(5);
                    CalculatorSyntheticScenario.Scenario secondary = CalculatorSyntheticScenario.createWorkbenchLike(4);
                    previousApi = CalculatorSyntheticScenario.attachRecipeApi(primary.system());
                    ExecutionOverlayOpenState.setOpen(true);
                    ExecutionCheckpointStore.clearAll();
                    ExecutionQueueStore.clearAll();
                    ExecutionQueueStore.enqueue(primary.target(), 2, null, 4, true);
                    ExecutionQueueStore.enqueue(secondary.target(), 1, null, 3, false);
                }).openScreen(() -> new CockpitShowcaseScreen(createShowcaseHandle()))
                .awaitScreen(CockpitShowcaseScreen.class).action("capture screen and complete async build", ctx -> {
                    if (ctx.screen() instanceof CockpitShowcaseScreen showcase) {
                        screen = showcase;
                    }
                }).waitUntil("execution session built and stages populated", () -> {
                    MainPanel panel = activePanel();
                    return panel != null && !panel.calculating() && !panel.stageModel().isEmpty();
                })
                .action(
                    "configure realistic mid-run state: skip stage 1, inspect 3x3 blast furnace stage 3, set BP, save checkpoint",
                    ctx -> {
                        MainPanel panel = activePanel();
                        if (panel != null) {
                            panel.skipCurrentStage();
                            int bpIdx = Math.min(2, panel.stageModel().totalStageCount() - 1);
                            panel.toggleStageBreakpoint(bpIdx);
                            panel.inspectStage(bpIdx);
                            panel.saveCheckpointNow();
                            panel.setSpeedRate(5.0d);
                            panel.auditLog().recordStepAdvance(4);
                        }
                    }
                ).frames(4)
                .check(
                    "cockpit has queue, 3x3 spotlight, materials, rich dependency graph, and diagnostics drawer",
                    () -> {
                        MainPanel panel = activePanel();
                        return panel != null
                                && ExecutionQueueStore.size() == 2
                                && panel.absoluteBounds().x() == 6
                                && panel.absoluteBounds().y() == 6
                                && panel.width() >= 200
                                && panel.height() >= 400
                                && panel.headerWidget().height() >= 60
                                && panel.controlDeck().height() >= 44
                                && panel.activeRecipeWidget().height() >= 74
                                && panel.materialsPanel().height() >= 60
                                && panel.graphPanel().height() >= 140
                                && panel.diagnosticsDrawer().height() >= 20
                                && panel.stageModel().totalStageCount() >= 4
                                && panel.stageModel().completedStageCount() >= 1
                                && panel.isInspectingDifferentStage()
                                && panel.missingMaterialCount() >= 1
                                && panel.hasResumableCheckpoint();
                    }
                ).action("apply dark theme", ctx -> applyTheme(WorkbenchThemeMode.obsidianBlueprint)).frames(3)
                .screenshot("execution-cockpit-dark")
                .action("hover first missing material row for OreUI rich tooltip", ctx -> {
                    MainPanel panel = activePanel();
                    if (panel != null) {
                        Rect mb = panel.materialsPanel().absoluteBounds();
                        float hx = (float) (mb.x() + Math.min(90, mb.width() * 0.4));
                        float hy = (float) (mb.y() + 50);
                        ctx.driver().placeCursor(hx, hy);
                        ctx.driver().moveTo(ctx.screenOrThrow(), hx, hy);
                    }
                }).frames(3).screenshot("execution-cockpit-dark-tooltip")
                .action("expand materials search and diagnostics drawer with active safety stop", ctx -> {
                    ctx.driver().placeCursor(600f, 360f);
                    ctx.driver().moveTo(ctx.screenOrThrow(), 600f, 360f);
                    MainPanel panel = activePanel();
                    if (panel != null) {
                        panel.materialsPanel().setSearchQuery("iron");
                        panel.auditLog().setActiveDiagnostic(
                            ExecutionAuditLog.Severity.error,
                            ExecutionAuditLog.DiagnosticKind.missingMaterial,
                            2,
                            CirrusLangs.Execution.Cockpit.diagMissingTitle.get(),
                            CirrusLangs.Execution.Cockpit.diagMissingDetail.get(2)
                        );
                        panel.diagnosticsDrawer().setActiveTab(ExecutionDiagnosticsDrawerWidget.Tab.diagnostics);
                        panel.diagnosticsDrawer().setExpanded(true);
                    }
                }).frames(5).screenshot("execution-cockpit-dark-drawer")
                .action("switch to light theme and telemetry audit log tab", ctx -> {
                    MainPanel panel = activePanel();
                    if (panel != null) {
                        panel.materialsPanel().setSearchOpen(false);
                        panel.diagnosticsDrawer().setActiveTab(ExecutionDiagnosticsDrawerWidget.Tab.telemetry);
                        panel.diagnosticsDrawer().setExpanded(true);
                    }
                    applyTheme(WorkbenchThemeMode.atelierStone);
                }).frames(4).screenshot("execution-cockpit-light").teardown(this::cleanup);
    }

    private @Nullable MainPanel activePanel() {
        return screen == null ? null : screen.rootOverlay().panel();
    }

    private void applyTheme(WorkbenchThemeMode mode) {
        ExecutionOverlayClientConfig.setWorkbenchTheme(mode);
        if (screen != null) {
            CirrusThemeStyle.applyToScene(screen.showcaseScene(), mode);
        }
    }

    private void cleanup() {
        ExecutionOverlayClientConfig.setWorkbenchTheme(WorkbenchThemeMode.obsidianBlueprint);
        ExecutionTargetRecipeState.clear();
        ExecutionCheckpointStore.clearAll();
        ExecutionQueueStore.clearAll();
        if (previousApi != null) {
            CalculatorSyntheticScenario.restoreRecipeApi(previousApi);
            previousApi = null;
        }
        if (screen != null) {
            screen.onClose();
            screen = null;
        }
        Minecraft.getInstance().setScreen(null);
    }

    private static ExecutionDriverHandle createShowcaseHandle() {
        Namespace id = Namespace.of("cirrus_test", "cockpit_showcase");
        return new ExecutionDriverHandle(lookup -> new ShowcaseSlotDriver(id), DriverLookup.of());
    }

    private static final class ShowcaseSlotDriver extends SlotExecutionDriver {
        private final Namespace id;

        ShowcaseSlotDriver(Namespace id) {
            super(id, SlotExecutionDriver.automaticInteraction(id), (ExecutionRuntime) null);
            this.id = id;
        }

        @Override
        public void initialize(ExecutionSession session) {
            RecipeEntry<?> target = session.plan().targetRecipe();
            MutableList<InteractionStep> steps = Lists.mutable.empty();
            appendStage(
                steps,
                target,
                2,
                Lists.immutable.of(in(Items.OAK_LOG, 2)),
                Lists.immutable.of(out(Items.OAK_PLANKS, 8))
            );
            appendStage(
                steps,
                target,
                1,
                Lists.immutable.of(in(Items.COBBLESTONE, 8)),
                Lists.immutable.of(out(Items.FURNACE, 1))
            );
            appendStage(
                steps,
                target,
                1,
                Lists.immutable.of(
                    in(Items.IRON_INGOT, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.FURNACE, 1),
                    in(Items.IRON_INGOT, 1),
                    in(Items.SMOOTH_STONE, 1),
                    in(Items.SMOOTH_STONE, 1),
                    in(Items.SMOOTH_STONE, 1)
                ),
                Lists.immutable.of(out(Items.BLAST_FURNACE, 1))
            );
            appendStage(
                steps,
                target,
                2,
                Lists.immutable.of(
                    in(Items.OAK_PLANKS, 6),
                    in(Items.COBBLESTONE, 8),
                    in(Items.IRON_INGOT, 2),
                    in(Items.REDSTONE, 2)
                ),
                Lists.immutable.of(out(Items.PISTON, 2))
            );
            InteractionExecutionPlan plan = new InteractionExecutionPlan(target, 2, steps.toImmutable());
            ExecutionFlowNode root = ExecutionFlowNode.pending(
                id.toString(),
                Component.literal("Blast Furnace"),
                plan.steps(),
                null,
                Lists.immutable.empty()
            );
            super.initialize(ExecutionSession.start(plan, root));
        }

        private void appendStage(
            MutableList<InteractionStep> steps,
            RecipeEntry<?> recipe,
            int times,
            ImmutableList<InputBinding> inputs,
            ImmutableList<OutputBinding> outputs
        ) {
            steps.add(
                new InteractionStep.OpenInteractionStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.interactionAccess,
                    recipe,
                    times,
                    id
                )
            );
            steps.add(
                new InteractionStep.PrepareInputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    recipe,
                    inputs
                )
            );
            steps.add(
                new InteractionStep.ExecuteRecipeStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.interactionPerform,
                    recipe,
                    times,
                    inputs,
                    outputs
                )
            );
            steps.add(
                new InteractionStep.CollectOutputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    recipe,
                    outputs
                )
            );
        }

        private static InputBinding in(Item item, long amount) {
            return new InputBinding(InputContent.fromItemStack(new ItemStack(item, 1), amount), amount);
        }

        private static OutputBinding out(Item item, long amount) {
            return new OutputBinding(OutputContent.fromItemStack(new ItemStack(item, 1), amount).asResource(), amount);
        }

        @Override
        public ExecutionDriverSnapshot snapshot() {
            ExecutionDriverSnapshot base = super.snapshot();
            var mats = Lists.immutable.of(
                new OverlayMaterialRequirement(new ItemStack(Items.IRON_INGOT, 1), 10, 4, 6),
                new OverlayMaterialRequirement(new ItemStack(Items.SMOOTH_STONE, 1), 6, 2, 4),
                new OverlayMaterialRequirement(new ItemStack(Items.COBBLESTONE, 1), 16, 16, 0),
                new OverlayMaterialRequirement(new ItemStack(Items.OAK_PLANKS, 1), 12, 12, 0)
            );
            ExecutionSession session = base.session();
            OverlayTargetProgress progress = new OverlayTargetProgress(
                InputContent.fromItemStack(new ItemStack(Items.BLAST_FURNACE, 1), 1),
                2,
                session != null && session.completed() ? 2 : 0
            );
            return new ExecutionDriverSnapshot(
                base.session(),
                base.viewModel(),
                base.telemetry(),
                mats,
                progress,
                base.trace(),
                base.ratePerSecond(),
                base.autoAdvanceEnabled(),
                base.reTranslationHint()
            );
        }
    }

    private static final class CockpitShowcaseScreen extends BasicScreen {
        private final ExecutionOverlayRoot rootOverlay;

        CockpitShowcaseScreen(ExecutionDriverHandle handle) {
            this.rootOverlay = new ExecutionOverlayRoot(handle, new Rect2i(6, 6, 280, 686), 1280, 720);
            mainGroup.useStyle(sizeFull());
            mainGroup.addWidget(new CraftingFlowShowcaseWidget(rootOverlay.panel()));
            mainGroup.addWidget(rootOverlay);
        }

        @Override
        protected void init() {
            super.init();
            scene().layout();
            mainGroup().applyLayout();
        }

        Scene showcaseScene() {
            return scene();
        }

        ExecutionOverlayRoot rootOverlay() {
            return rootOverlay;
        }
    }

    /** Renders a simulated Crafting Table + animated SlotFlowTraceRenderer beside the Execution Cockpit for visual regression coverage. */
    private static final class CraftingFlowShowcaseWidget extends Widget {
        private final MainPanel panel;
        private final SlotFlowTraceRenderer renderer = new SlotFlowTraceRenderer();

        CraftingFlowShowcaseWidget(MainPanel panel) {
            this.panel = panel;
            setInteractive(false);
            useStyle(UIStyle.of(positionAbsolute(), inset(0), sizeFull()));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = ExecutionCockpitTheme.palette(panel);
            int guiW = 196;
            int guiH = 186;
            int guiLeft = 560;
            int guiTop = 250;
            OreUiPaint.raisedCard(canvas, guiLeft, guiTop, guiW, guiH, palette, false, false);

            List<SlotFlowTraceRenderer.SlotAnchor> anchors = new ArrayList<>();
            // 3x3 crafting grid (slots 1..9)
            int gridX = guiLeft + 26;
            int gridY = guiTop + 20;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int idx = 1 + r * 3 + c;
                    int sx = gridX + c * 18;
                    int sy = gridY + r * 18;
                    OreUiPaint.slot(canvas, sx, sy, 18, palette);
                    ItemStack cur = (idx == 1 || idx == 2 || idx == 5)
                            ? new ItemStack(idx == 5 ? Items.FURNACE : Items.IRON_INGOT, 1)
                            : ItemStack.EMPTY;
                    if (!cur.isEmpty()) {
                        canvas.renderItem(cur, sx + 1, sy + 1);
                    }
                    anchors.add(
                        new SlotFlowTraceRenderer.SlotAnchor(
                            new SlotLayout.Reference.Index(idx),
                            new Rect2i(sx, sy, 18, 18),
                            cur
                        )
                    );
                }
            }
            // Output slot 0
            int outX = guiLeft + 132;
            int outY = guiTop + 38;
            OreUiPaint.slot(canvas, outX, outY, 18, palette);
            anchors.add(
                new SlotFlowTraceRenderer.SlotAnchor(
                    new SlotLayout.Reference.Index(0),
                    new Rect2i(outX, outY, 18, 18),
                    ItemStack.EMPTY
                )
            );
            // 2x9 player inventory strip (slots 10..27)
            int invX = guiLeft + 17;
            int invY = guiTop + 104;
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 9; c++) {
                    int idx = 10 + r * 9 + c;
                    int sx = invX + c * 18;
                    int sy = invY + r * 18;
                    OreUiPaint.slot(canvas, sx, sy, 18, palette);
                    ItemStack cur = ItemStack.EMPTY;
                    if (idx == 11) {
                        cur = new ItemStack(Items.IRON_INGOT, 16);
                    } else if (idx == 14) {
                        cur = new ItemStack(Items.SMOOTH_STONE, 12);
                    }
                    if (!cur.isEmpty()) {
                        canvas.renderItem(cur, sx + 1, sy + 1);
                    }
                    anchors.add(
                        new SlotFlowTraceRenderer.SlotAnchor(
                            new SlotLayout.Reference.Index(idx),
                            new Rect2i(sx, sy, 18, 18),
                            cur
                        )
                    );
                }
            }

            SlotLayout.Reference outRef = new SlotLayout.Reference.Index(0);
            SlotLayout.Reference invIron = new SlotLayout.Reference.Index(11);
            SlotLayout.Reference invStone = new SlotLayout.Reference.Index(14);
            ExecutionOverlayTraceSnapshot trace = new ExecutionOverlayTraceSnapshot(
                true,
                "minecraft:blast_furnace",
                new ItemStack(Items.BLAST_FURNACE, 1),
                Lists.immutable.of(
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(1),
                        SlotTrace.Roles.inputReady,
                        "1/1",
                        new ItemStack(Items.IRON_INGOT),
                        palette.costS()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(2),
                        SlotTrace.Roles.inputReady,
                        "1/1",
                        new ItemStack(Items.IRON_INGOT),
                        palette.costS()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(3),
                        SlotTrace.Roles.inputPreview,
                        "0/1",
                        new ItemStack(Items.IRON_INGOT),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(4),
                        SlotTrace.Roles.inputPreview,
                        "0/1",
                        new ItemStack(Items.IRON_INGOT),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(5),
                        SlotTrace.Roles.inputReady,
                        "1/1",
                        new ItemStack(Items.FURNACE),
                        palette.costS()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(6),
                        SlotTrace.Roles.inputPreview,
                        "0/1",
                        new ItemStack(Items.IRON_INGOT),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(7),
                        SlotTrace.Roles.inputPreview,
                        "0/1",
                        new ItemStack(Items.SMOOTH_STONE),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(8),
                        SlotTrace.Roles.inputPreview,
                        "0/1",
                        new ItemStack(Items.SMOOTH_STONE),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        new SlotLayout.Reference.Index(9),
                        SlotTrace.Roles.inputMissing,
                        "0/1",
                        new ItemStack(Items.SMOOTH_STONE),
                        palette.ruleHardBan()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        invIron,
                        SlotTrace.Roles.inventory,
                        "-3",
                        new ItemStack(Items.IRON_INGOT),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        invStone,
                        SlotTrace.Roles.inventory,
                        "-2",
                        new ItemStack(Items.SMOOTH_STONE),
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotHighlight(
                        outRef,
                        SlotTrace.Roles.outputPreview,
                        "x1",
                        new ItemStack(Items.BLAST_FURNACE),
                        palette.costH()
                    )
                ),
                Lists.immutable.of(
                    new ExecutionOverlayTraceSnapshot.SlotMove(
                        invIron,
                        new SlotLayout.Reference.Index(3),
                        new ItemStack(Items.IRON_INGOT),
                        1,
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotMove(
                        invIron,
                        new SlotLayout.Reference.Index(4),
                        new ItemStack(Items.IRON_INGOT),
                        1,
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotMove(
                        invIron,
                        new SlotLayout.Reference.Index(6),
                        new ItemStack(Items.IRON_INGOT),
                        2,
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotMove(
                        invStone,
                        new SlotLayout.Reference.Index(7),
                        new ItemStack(Items.SMOOTH_STONE),
                        1,
                        palette.focusRing()
                    ),
                    new ExecutionOverlayTraceSnapshot.SlotMove(
                        invStone,
                        new SlotLayout.Reference.Index(8),
                        new ItemStack(Items.SMOOTH_STONE),
                        2,
                        palette.focusRing()
                    )
                )
            );
            renderer.renderTraceForContainer(canvas, guiLeft, guiTop, guiW, anchors, trace, palette, true);
        }
    }
}

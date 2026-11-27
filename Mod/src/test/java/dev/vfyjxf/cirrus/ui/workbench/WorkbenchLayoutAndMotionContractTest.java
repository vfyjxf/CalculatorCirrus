package dev.vfyjxf.cirrus.ui.workbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSegmentedControlWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.CommandPaletteOverlayWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ProfileWeightsPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.RecipeAssistantPopupWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.SessionTabBarWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TargetBasketPopoverWidget;
import dev.vfyjxf.cirrus.ui.workbench.chrome.TopCommandDockWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.DagMinimapWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagCanvasWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.SpatialDagHost;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsHost;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless layout, clipping clearance, responsive chrome, and reducedMotion-respecting animation contracts.
 */
class WorkbenchLayoutAndMotionContractTest {

    private static final ResourceKey ironOre = ResourceKey.item("minecraft:iron_ore");
    private static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    private static final ResourceKey coal = ResourceKey.item("minecraft:coal");

    @Test
    void settingsAndDiagnosticsUsesTwoColumnFullHeightLayoutAndSizedNumberInputs() {
        SettingsAndDiagnosticsWorkspace[] holder = new SettingsAndDiagnosticsWorkspace[1];
        WorkspaceProvider provider = new WorkspaceProvider() {
            @Override
            public Widget plannerWorkspace(WorkbenchShellContext ctx) {
                return pane();
            }

            @Override
            public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
                return pane();
            }

            @Override
            public Widget settingsWorkspace(WorkbenchShellContext ctx) {
                holder[0] = new SettingsAndDiagnosticsWorkspace(new SettingsHost.Detached(), ctx);
                return holder[0];
            }

            @Override
            public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
                return pane();
            }
        };

        WorkbenchShellWidget shell = new WorkbenchShellWidget(provider, headlessTabs(), WorkbenchMode.settings);
        Scene scene = mount(shell, 1280, 720);
        try {
            SettingsAndDiagnosticsWorkspace workspace = Objects.requireNonNull(holder[0]);
            List<Widget> columns = new ArrayList<>(workspace.children());
            assertEquals(2, columns.size());

            Rect leftBounds = columns.get(0).absoluteBounds();
            Rect rightBounds = columns.get(1).absoluteBounds();
            assertTrue(leftBounds.x() < rightBounds.x(), "settings column must sit left of diagnostics column");
            assertEquals(leftBounds.y(), rightBounds.y(), "both columns must share top alignment");
            assertTrue(leftBounds.right() <= rightBounds.x(), "columns must not overlap horizontally");
            assertTrue(leftBounds.height() >= 640, "left column must span full workspace height: " + leftBounds);
            assertTrue(rightBounds.height() >= 640, "right column must span full workspace height: " + rightBounds);

            List<NumberInputWidget> inputs = collectWidgets(workspace, NumberInputWidget.class);
            assertEquals(2, inputs.size());
            for (NumberInputWidget input : inputs) {
                assertEquals(
                    SettingsAndDiagnosticsWorkspace.rowHeight,
                    input.height(),
                    "NumberInputWidget must have explicit rowHeight"
                );
            }
        } finally {
            shell.shutdown();
            scene.destroy();
        }

        WorkbenchShellWidget narrowShell = new WorkbenchShellWidget(provider, headlessTabs(), WorkbenchMode.settings);
        Scene narrowScene = mount(narrowShell, 640, 360);
        try {
            SettingsAndDiagnosticsWorkspace narrow = Objects.requireNonNull(holder[0]);
            List<CirrusSectionWidget> sections = collectWidgets(narrow, CirrusSectionWidget.class);
            assertFalse(sections.isEmpty());
            for (var section : sections) {
                Rect secBounds = section.absoluteBounds();
                for (var choice : collectWidgets(section, CirrusChoiceBoxWidget.class)) {
                    Rect box = choice.absoluteBounds();
                    assertTrue(
                        box.right() <= secBounds.right(),
                        "choice box " + box + " must stay inside section " + secBounds
                    );
                }
                for (var seg : collectWidgets(section, CirrusSegmentedControlWidget.class)) {
                    Rect box = seg.absoluteBounds();
                    assertTrue(
                        box.right() <= secBounds.right(),
                        "segmented control " + box + " must stay inside section " + secBounds
                    );
                }
            }
        } finally {
            narrowShell.shutdown();
            narrowScene.destroy();
        }
    }

    @Test
    void spatialDagMinimapClearsCapsuleEmptyStateClearsZenAndCameraEasesOrSnaps() {
        WorkbenchUiSnapshot snapshot = readySnapshot(sampleChainPlan());
        SpatialDagHost host = new StubDagHost(snapshot);
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create().host(host)
                .graph(minimalGraph(ironOre, ironIngot));
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeOf(800, 480));
        root.addWidget(canvas);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(sceneHost(800, 480)));
        scene.setLayoutArea(800, 480);
        relayout(scene);
        try {
            DagMinimapWidget minimap = canvas.minimap();
            Rect minimapRect = minimap.bounds();
            Rect capsuleRect = canvas.capsuleBounds();
            assertFalse(
                rectsIntersect(minimapRect, capsuleRect),
                "minimap " + minimapRect + " must not overlap top-left toolbar " + capsuleRect
            );
            assertTrue(minimapRect.y() <= 20, "minimap must sit in the top-right corner: " + minimapRect);
            assertTrue(capsuleRect.y() <= 20, "slim flow toolbar must sit at the top-left: " + capsuleRect);
            assertTrue(
                capsuleRect.right() <= minimapRect.x(),
                "top-left toolbar " + capsuleRect + " must clear top-right minimap " + minimapRect
            );
            assertTrue(capsuleRect.x() >= SpatialDagCanvasWidget.capsuleHorizontalMargin);
            assertTrue(capsuleRect.right() <= 800 - SpatialDagCanvasWidget.capsuleHorizontalMargin);

            Rect narrowCapsule = canvas.capsuleBounds(320, 300);
            assertTrue(narrowCapsule.x() >= SpatialDagCanvasWidget.capsuleHorizontalMargin);
            assertTrue(narrowCapsule.right() <= 320 - SpatialDagCanvasWidget.capsuleHorizontalMargin);

            Rect zenExit = new Rect(0, 0, 40, 40);
            Rect emptyCard = canvas.emptyStateBounds(1280, 720);
            assertFalse(rectsIntersect(emptyCard, zenExit), "empty-state card must not collide with Zen exit trigger");

            canvas.reducedMotion(false);
            canvas.camera().setCamera(900f, 600f);
            canvas.camera().setZoom(0.5f);
            canvas.fitToView();
            assertTrue(canvas.cameraAnimating(), "fitToView must ease smoothly when reducedMotion is false");

            float camAt0 = canvas.camera().camX();
            canvas.advanceCameraAnimation(45L);
            float camAt45 = canvas.camera().camX();
            canvas.advanceCameraAnimation(45L);
            float camAt90 = canvas.camera().camX();
            canvas.advanceCameraAnimation(120L);
            float camAtEnd = canvas.camera().camX();
            assertFalse(canvas.cameraAnimating(), "camera tween must finish within 180ms");
            assertTrue(
                monotonicBetween(camAt0, camAt45, camAt90, camAtEnd),
                "camera must ease monotonically: " + camAt0 + " -> " + camAt45 + " -> " + camAt90 + " -> " + camAtEnd
            );

            canvas.camera().setCamera(-500f, -300f);
            canvas.focusHome();
            assertTrue(canvas.cameraAnimating());
            canvas.cancelCameraAnimation();
            assertFalse(canvas.cameraAnimating(), "user pan must cancel active camera interpolation immediately");

            canvas.reducedMotion(true);
            canvas.camera().setCamera(900f, 600f);
            canvas.camera().setZoom(0.5f);
            canvas.fitToView();
            assertFalse(canvas.cameraAnimating(), "reducedMotion must snap camera immediately");
            assertEquals(camAtEnd, canvas.camera().camX(), 0.01f);
        } finally {
            scene.destroy();
        }
    }

    @Test
    void topCommandDockHidesSearchInCompactKeepsNativeLiveStockAndWideSendSlot() {
        WorkbenchShellWidget shell = new WorkbenchShellWidget(paneProvider(), headlessTabs(), WorkbenchMode.planner);
        Scene scene = mount(shell, 1280, 720);
        try {
            TopCommandDockWidget topDock = chrome(shell, TopCommandDockWidget.class);
            assertEquals(20, topDock.liveStockToggle().width());
            assertEquals(13, topDock.liveStockToggle().height());
            assertTrue(topDock.searchField().visible());
            assertTrue(topDock.searchRect().width() >= 100);
            assertTrue(topDock.sendSlotRect().width() >= 108);
            assertTrue(topDock.presetSlotRect().width() >= 68);

            SessionTabBarWidget tabBar = chrome(shell, SessionTabBarWidget.class);
            SessionTabBarWidget.TabView emptyTab = new SessionTabBarWidget.TabView("t1", "", List.of(), true);
            assertTrue(tabBar.tabWidth(emptyTab) >= 86, "New Plan tab must be wide enough to avoid truncation");

            scene.setLayoutArea(427, 240);
            shell.applyViewport(427f, 240f);
            relayout(scene);

            assertEquals(WorkbenchBreakpoint.compact, shell.breakpoint());
            assertFalse(topDock.searchField().visible());
            assertEquals(0, topDock.searchRect().width(), "search must collapse cleanly in compact breakpoint");
            assertEquals(24, topDock.sendSlotRect().width());
        } finally {
            shell.shutdown();
            scene.destroy();
        }
    }

    @Test
    void commandPaletteShrinksDynamicallyAdaptsFooterHintAndEnforcesShellExclusivity() {
        CompiledRecipeGraph graph = minimalGraph(ironIngot, coal);
        WorkbenchUiSnapshot snapshot = readySnapshot(sampleChainPlan());
        WorkbenchShellContext paletteCtx = new StubShellContext(snapshot, graph);
        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(paletteCtx).graphSupplier(() -> graph);
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeOf(1280, 720));
        root.addWidget(palette);
        Scene paletteScene = new Scene(root);
        paletteScene.init();
        paletteScene.mount(SceneContext.create(sceneHost(1280, 720)));
        paletteScene.setLayoutArea(1280, 720);
        relayout(paletteScene);
        try {
            palette.open();
            palette.setQueryText("zzzz-no-match");
            relayout(paletteScene);
            int emptyHeight = palette.height();
            assertTrue(
                emptyHeight < (int) WorkbenchShellLayout.paletteHeight,
                "palette must shrink dynamically when few or zero rows match: " + emptyHeight
            );

            palette.setQueryText(">");
            relayout(paletteScene);
            int commandsHeight = palette.height();
            assertTrue(commandsHeight > emptyHeight, "more matching rows must expand palette height");
            assertEquals(CirrusLangs.Workbench.Palette.hintCommand.get(), palette.footerHint());

            palette.setQueryText("coal");
            PaletteItem highlighted = Objects.requireNonNull(palette.highlightedItem());
            assertEquals(CommandPaletteEngine.categoryResource, highlighted.categoryKey());
            assertEquals(CirrusLangs.Workbench.Palette.hintResource.get(), palette.footerHint());
            assertNotEquals(CirrusLangs.Workbench.Palette.hintCommand.get(), palette.footerHint());
        } finally {
            paletteScene.destroy();
        }

        WorkbenchShellWidget shell = new WorkbenchShellWidget(paneProvider(), headlessTabs(), WorkbenchMode.planner);
        Scene scene = mount(shell, 1280, 720);
        try {
            TargetBasketPopoverWidget basket = chrome(shell, TargetBasketPopoverWidget.class);
            ProfileWeightsPopoverWidget weights = chrome(shell, ProfileWeightsPopoverWidget.class);

            basket.show();
            assertTrue(basket.isOpen());
            assertFalse(weights.isOpen());

            weights.show();
            assertTrue(weights.isOpen());
            assertFalse(basket.isOpen(), "opening profile weights must close target basket");

            shell.openCommandPalette();
            assertTrue(shell.commandPaletteOpen());
            assertTrue(weights.isOpen(), "command palette layers above open Z5 popover for Escape peel order");
            shell.closeCommandPalette();
            assertFalse(shell.commandPaletteOpen());
            assertTrue(weights.isOpen());
        } finally {
            shell.shutdown();
            scene.destroy();
        }
    }

    @Test
    void languageFilesKeepExactKeyParityAndCleanFacilitiesBadgeLabels() throws Exception {
        JsonObject en = JsonParser.parseString(Files.readString(resolveLangFile("en_us"))).getAsJsonObject();
        JsonObject zh = JsonParser.parseString(Files.readString(resolveLangFile("zh_cn"))).getAsJsonObject();
        Set<String> enKeys = new TreeSet<>(en.keySet());
        Set<String> zhKeys = new TreeSet<>(zh.keySet());
        assertEquals(enKeys, zhKeys, "en_us.json and zh_cn.json must maintain exact 1:1 key parity");

        String enBound = en.get("cirrus.ui.workbench.facilities.bound_world").getAsString();
        String zhBound = zh.get("cirrus.ui.workbench.facilities.bound_world").getAsString();
        assertFalse(enBound.contains("WorldFacilityRegistry"));
        assertFalse(enBound.isBlank());
        assertFalse(zhBound.isBlank());
    }

    @Test
    void collapsibleDocksTabCloseTrackpadGesturesAndAssistantModesWork() {
        WorkbenchShellWidget shell = new WorkbenchShellWidget(paneProvider(), headlessTabs(), WorkbenchMode.planner);
        Scene scene = mount(shell, 1280, 720);
        try {
            float leftBefore = shell.leftDockWidth();
            float rightBefore = shell.rightDockWidth();
            assertTrue(leftBefore > 0f);
            assertTrue(rightBefore > 0f);

            shell.setLeftDockCollapsed(true);
            shell.setRightDockCollapsed(true);
            relayout(scene);
            assertTrue(shell.leftDockCollapsed());
            assertTrue(shell.rightDockCollapsed());
            assertEquals(0f, shell.leftDockWidth(), 0.001f);
            assertEquals(0f, shell.rightDockWidth(), 0.001f);

            shell.setLeftDockCollapsed(false);
            shell.setRightDockCollapsed(false);
            relayout(scene);
            assertFalse(shell.leftDockCollapsed());
            assertFalse(shell.rightDockCollapsed());
            assertEquals(leftBefore, shell.leftDockWidth(), 0.001f);
            assertEquals(rightBefore, shell.rightDockWidth(), 0.001f);

            WorkbenchUiSnapshot snap = readySnapshot(sampleChainPlan());
            SpatialDagCanvasWidget dag = SpatialDagCanvasWidget.create().host(new StubDagHost(snap))
                    .graph(minimalGraph(ironOre, ironIngot)).reducedMotion(true);
            SpatialDagCanvasWidget.ScrollNavigationMode prevNav = SpatialDagCanvasWidget.scrollNavigationMode();
            try {
                SpatialDagCanvasWidget.setScrollNavigationMode(SpatialDagCanvasWidget.ScrollNavigationMode.trackpadPan);
                float camX0 = dag.camera().camX();
                float camY0 = dag.camera().camY();
                float zoom0 = dag.camera().zoom();
                assertTrue(dag.handleScroll(200, 150, 1.5, -2.0, false, false));
                assertNotEquals(camX0, dag.camera().camX());
                assertNotEquals(camY0, dag.camera().camY());
                assertEquals(zoom0, dag.camera().zoom(), 0.0001f);

                assertTrue(dag.handleScroll(200, 150, 0.0, 1.0, true, false));
                assertTrue(dag.camera().zoom() > zoom0, "Ctrl/Cmd + scroll must zoom in trackpad mode");
            } finally {
                SpatialDagCanvasWidget.setScrollNavigationMode(prevNav);
            }

            shell.openTargetCatalogPicker();
            assertTrue(shell.recipeAssistant().isOpen());
            assertEquals(RecipeAssistantPopupWidget.Mode.targetCatalogPicker, shell.recipeAssistant().mode());
            shell.openGuidedDecisionWorkbench();
            assertTrue(shell.recipeAssistant().isOpen());
            assertEquals(RecipeAssistantPopupWidget.Mode.guidedDecisions, shell.recipeAssistant().mode());
            shell.closeTransientOverlays(null);
            assertFalse(shell.recipeAssistant().isOpen());
        } finally {
            shell.shutdown();
            scene.destroy();
        }
    }

    private static ProductionPlan sampleChainPlan() {
        CostVector cost = new CostVector(4.0, 2.0, 1.0, 0.5, 0.1);
        CompiledRecipeKey key = new CompiledRecipeKey("minecraft:smelting", Optional.of("smelt_iron"), 0L);
        List<PlanStep> steps = List.of(
            new AcquireStep("s0", List.of(), ironOre, 4L, AcquisitionKind.manualGather, cost),
            new CraftStep(
                "s1",
                List.of("s0"),
                key,
                4L,
                Optional.of("minecraft:furnace"),
                false,
                Map.of(0, new SlotBinding(ironOre, List.of())),
                Map.of(),
                cost
            )
        );
        return new ProductionPlan(
            "plan-contract",
            "cirrus.strategy.balanced",
            steps,
            PreparationSummary.empty,
            List.of(),
            cost,
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    private static WorkbenchUiSnapshot readySnapshot(ProductionPlan plan) {
        PlanningResult result = new PlanningResult(
            PlanningStatus.feasible,
            List.of(plan),
            List.of(),
            new PlanningDiagnostics(1L, plan.steps().size(), 1, 0, 0, false, Optional.empty())
        );
        return new WorkbenchUiSnapshot(
            "tab-1",
            "Plan",
            WorkbenchPhase.readyPareto,
            false,
            1L,
            List.of(new TargetDemand(ironIngot, 4L)),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            false,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            result,
            null,
            plan.planId(),
            null,
            Set.of(),
            null,
            null,
            FlowViewMode.spatialDag,
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    private static CompiledRecipeGraph minimalGraph(ResourceKey... producible) {
        Set<ResourceKey> resources = Set.of(producible);
        return new CompiledRecipeGraph() {
            @Override
            public long catalogVersion() {
                return 1L;
            }

            @Override
            public SystemAdmission admissionOf(String systemId) {
                return SystemAdmission.enabled;
            }

            @Override
            public List<CompiledRecipe> producersOf(ResourceKey output) {
                return List.of();
            }

            @Override
            public List<CompiledRecipe> consumersOf(ResourceKey input) {
                return List.of();
            }

            @Override
            public List<CompiledCycleMacro> cycleMacrosProducing(ResourceKey output) {
                return List.of();
            }

            @Override
            public Optional<CompiledRecipe> recipeByCompactId(int compactId) {
                return Optional.empty();
            }

            @Override
            public Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key) {
                return Optional.empty();
            }

            @Override
            public Set<ResourceKey> allProducibleResources() {
                return resources;
            }

            @Override
            public SccTopology sccTopology() {
                return new SccTopology() {
                    @Override
                    public int sccIdOf(ResourceKey resource) {
                        return 0;
                    }

                    @Override
                    public int sccSize(int sccId) {
                        return 0;
                    }

                    @Override
                    public boolean isInNonTrivialScc(ResourceKey resource) {
                        return false;
                    }

                    @Override
                    public Set<ResourceKey> membersOfScc(int sccId) {
                        return Set.of();
                    }

                    @Override
                    public int largestSccSize() {
                        return 0;
                    }
                };
            }

            @Override
            public GraphCompilationStats stats() {
                return new GraphCompilationStats(0, 0, 0, 0, 0, 0, 0, 0);
            }
        };
    }

    private static boolean monotonicBetween(float v0, float v1, float v2, float v3) {
        if (v3 >= v0) {
            return v0 <= v1 && v1 <= v2 && v2 <= v3 && v3 > v0;
        }
        return v0 >= v1 && v1 >= v2 && v2 >= v3 && v3 < v0;
    }

    private static boolean rectsIntersect(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }

    private static <T extends Widget> List<T> collectWidgets(Widget root, Class<T> type) {
        List<T> found = new ArrayList<>();
        walkWidgets(root, type, found);
        return found;
    }

    private static <T extends Widget> void walkWidgets(Widget widget, Class<T> type, List<T> out) {
        if (type.isInstance(widget)) {
            out.add(type.cast(widget));
        }
        if (widget instanceof CompositeWidget<?> composite) {
            for (Widget child : composite.children()) {
                walkWidgets(child, type, out);
            }
        }
    }

    private static <T extends Widget> T chrome(WorkbenchShellWidget shell, Class<T> type) {
        for (Widget child : shell.children()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        throw new AssertionError("Missing chrome widget " + type.getSimpleName());
    }

    private static Scene mount(WorkbenchShellWidget shell, int width, int height) {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull());
        root.addWidget(shell);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(sceneHost(width, height)));
        scene.setLayoutArea(width, height);
        shell.applyViewport((float) width, (float) height);
        relayout(scene);
        return scene;
    }

    private static void relayout(Scene scene) {
        scene.layout();
        scene.stabilize();
        scene.layout();
        scene.stabilize();
    }

    private static WorkspaceProvider paneProvider() {
        return new WorkspaceProvider() {
            @Override
            public Widget plannerWorkspace(WorkbenchShellContext ctx) {
                return pane();
            }

            @Override
            public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
                return pane();
            }

            @Override
            public Widget settingsWorkspace(WorkbenchShellContext ctx) {
                return pane();
            }

            @Override
            public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
                return pane();
            }
        };
    }

    private static Widget pane() {
        WidgetGroup<Widget> group = new WidgetGroup<>();
        group.useStyle(sizeFull());
        return group;
    }

    private static SessionTabManager headlessTabs() {
        return new SessionTabManager(state -> {
            throw new IllegalStateException("headless tab factory must not be used");
        });
    }

    private static SceneHost sceneHost(int width, int height) {
        return new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }

    private static Path resolveLangFile(String locale) {
        String relative = "src/main/resources/assets/calculator_cirrus/lang/" + locale + ".json";
        String moduleRelative = "Mod/" + relative;
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path local = cursor.resolve(relative);
            if (Files.exists(local)) {
                return local;
            }
            Path module = cursor.resolve(moduleRelative);
            if (Files.exists(module)) {
                return module;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relative);
    }

    private record StubDagHost(WorkbenchUiSnapshot snapshot) implements SpatialDagHost {
        @Override
        public CirrusPalette palette() {
            return CirrusPalette.obsidianBlueprint;
        }

        @Override
        public void onSelectSteps(Set<String> stepIds) {}

        @Override
        public void onNodeMoved(String stepId, FloatPos offset) {}

        @Override
        public void onClearManualOffsets() {}

        @Override
        public void onHoverStep(@Nullable String stepId) {}

        @Override
        public void onHoverEdge(
            @Nullable String fromStepId,
            @Nullable String toStepId,
            @Nullable ResourceKey resource
        ) {}

        @Override
        public void onInlineBeaconClick(String promptId, String stepId) {}

        @Override
        public void onNodeContextMenu(String stepId, double screenX, double screenY, boolean shiftDown) {}

        @Override
        public void onCanvasContextMenu(double screenX, double screenY) {}

        @Override
        public void onDrop(DragPayload payload, @Nullable String targetStepId) {}

        @Override
        public void onSetLayoutMode(DagLayoutMode mode) {}

        @Override
        public boolean isReducedMotion() {
            return false;
        }
    }

    private static final class StubShellContext implements WorkbenchShellContext {
        private final SessionTabManager tabs = headlessTabs();
        private final WorkbenchUiSnapshot snapshot;
        private final CompiledRecipeGraph graph;

        private StubShellContext(WorkbenchUiSnapshot snapshot, CompiledRecipeGraph graph) {
            this.snapshot = snapshot;
            this.graph = graph;
        }

        @Override
        public SessionTabManager tabManager() {
            return tabs;
        }

        @Override
        public @Nullable PlannerWorkbenchController activeController() {
            return null;
        }

        @Override
        public WorkbenchUiSnapshot activeSnapshot() {
            return snapshot;
        }

        @Override
        public @Nullable CompiledRecipeGraph activeGraph() {
            return graph;
        }

        @Override
        public WorkbenchBreakpoint breakpoint() {
            return WorkbenchBreakpoint.wide;
        }

        @Override
        public WorkbenchThemeMode theme() {
            return WorkbenchThemeMode.obsidianBlueprint;
        }

        @Override
        public void setTheme(WorkbenchThemeMode theme) {}

        @Override
        public CirrusPalette palette() {
            return CirrusPalette.obsidianBlueprint;
        }

        @Override
        public WorkbenchMode mode() {
            return WorkbenchMode.planner;
        }

        @Override
        public void setMode(WorkbenchMode mode) {}

        @Override
        public boolean zenMode() {
            return false;
        }

        @Override
        public void setZenMode(boolean zen) {}

        @Override
        public float leftDockWidth() {
            return 228f;
        }

        @Override
        public void setLeftDockWidth(float width) {}

        @Override
        public float rightDockWidth() {
            return 310f;
        }

        @Override
        public void setRightDockWidth(float width) {}

        @Override
        public float shellWidth() {
            return 1280f;
        }

        @Override
        public float shellHeight() {
            return 720f;
        }

        @Override
        public float leftRailWidth() {
            return 40f;
        }

        @Override
        public float topChromeHeight() {
            return 58f;
        }

        @Override
        public void toast(LangEntry message) {}

        @Override
        public void toast(Component message) {}

        @Override
        public void openCommandPalette() {}

        @Override
        public void closeCommandPalette() {}

        @Override
        public boolean commandPaletteOpen() {
            return false;
        }

        @Override
        public void requestReplan() {}

        @Override
        public void setTargets(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {}

        @Override
        public void openTab(List<TargetDemand> targets, @Nullable RecipeEntry<?> primaryRecipe) {}

        @Override
        public void closeActiveTab() {}

        @Override
        public void sendActivePlanToExecution() {}

        @Override
        public void addListener(Runnable listener) {}

        @Override
        public void removeListener(Runnable listener) {}

        @Override
        public void shutdown() {}
    }
}

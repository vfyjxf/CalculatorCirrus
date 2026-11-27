package dev.vfyjxf.cirrus.uitest;

import com.google.gson.JsonElement;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.graph.SccTopology;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.PlanningContextFactory;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.human.LayeredHumanGuidanceStore;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchIntegrationProvider;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchSurfaces;
import dev.vfyjxf.cirrus.ui.workbench.WorkspaceProvider;
import dev.vfyjxf.cirrus.ui.workbench.database.DatabaseExplorerWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesHost;
import dev.vfyjxf.cirrus.ui.workbench.flow.ExecutionChecklistWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.FlowStageWidget;
import dev.vfyjxf.cirrus.ui.workbench.flow.PreparationLedgerWidget;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellFlowViewHost;
import dev.vfyjxf.cirrus.ui.workbench.hosts.ShellSpatialDagHost;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsHost;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

/** Deterministic populated workbench screen for dual-theme visual verification. */
final class WorkbenchVisualFixture {

    static final ResourceKey ironOre = ResourceKey.item("minecraft:iron_ore");
    static final ResourceKey coal = ResourceKey.item("minecraft:coal");
    static final ResourceKey redstone = ResourceKey.item("minecraft:redstone");
    static final ResourceKey oakPlanks = ResourceKey.item("minecraft:oak_planks");
    static final ResourceKey cobblestone = ResourceKey.item("minecraft:cobblestone");
    static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    static final ResourceKey ironBlock = ResourceKey.item("minecraft:iron_block");
    static final ResourceKey piston = ResourceKey.item("minecraft:piston");
    static final ResourceKey compass = ResourceKey.item("minecraft:compass");

    private static final String smelting = "minecraft:smelting";
    private static final String crafting = "minecraft:crafting";
    private static final String blastFurnace = "minecraft:blast_furnace";
    private static final String furnace = "minecraft:furnace";
    private static final String stonecutter = "minecraft:stonecutter";

    private static final String blasting = "minecraft:blasting";
    private static final CompiledRecipeKey rSmeltIron = recipeKey(smelting, "minecraft:iron_ingot_from_smelting");
    private static final CompiledRecipeKey rBlastIron = recipeKey(blasting, "minecraft:iron_ingot_from_blasting");
    private static final CompiledRecipeKey rUnpackIron = recipeKey(crafting, "minecraft:iron_ingot_from_iron_block");
    private static final CompiledRecipeKey rPackBlock = recipeKey(crafting, "minecraft:iron_block");
    private static final CompiledRecipeKey rCraftPiston = recipeKey(crafting, "minecraft:piston");
    private static final CompiledRecipeKey rCraftCompass = recipeKey(crafting, "minecraft:compass");

    private WorkbenchVisualFixture() {}

    static VisualShowcaseScreen createScreen() {
        VisualGraph graph = buildGraph();
        PlanningResult planningResult = buildPlanningResult();
        CirrusPlanningRuntime.PublishedSnapshot snapshot = buildPublishedSnapshot(graph);
        WorldFacilityRegistry registry = buildFacilityRegistry();
        return new VisualShowcaseScreen(graph, planningResult, snapshot, registry);
    }

    private static CompiledRecipeKey recipeKey(String systemId, String id) {
        return new CompiledRecipeKey(systemId, Optional.of(id), 0L);
    }

    private static CostVector cost(double h, double p, double k, double s, double c) {
        return new CostVector(h, p, k, s, c);
    }

    private static VisualGraph buildGraph() {
        VisualGraph graph = new VisualGraph();
        graph.put(recipe(1, rSmeltIron, List.of(ironOre, coal), List.of(ironIngot), List.of(furnace), true));
        graph.put(recipe(5, rBlastIron, List.of(ironOre, coal), List.of(ironIngot), List.of(blastFurnace), true));
        graph.put(recipe(6, rUnpackIron, List.of(ironBlock), List.of(ironIngot), List.of(), false));
        graph.put(recipe(2, rPackBlock, List.of(ironIngot), List.of(ironBlock), List.of(), false));
        graph.put(
            recipe(
                3,
                rCraftPiston,
                List.of(oakPlanks, cobblestone, ironIngot, redstone),
                List.of(piston),
                List.of(),
                false
            )
        );
        graph.put(recipe(4, rCraftCompass, List.of(ironIngot, redstone), List.of(compass), List.of(), false));
        return graph;
    }

    private static CompiledRecipe recipe(
        int compactId,
        CompiledRecipeKey key,
        List<ResourceKey> inputs,
        List<ResourceKey> outputs,
        List<String> machines,
        boolean hasCatalyst
    ) {
        List<InputSlot> inputSlots = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            inputSlots.add(
                new InputSlot(index, 1L, Optional.empty(), List.of(inputs.get(index)), SlotMatchMode.identity)
            );
        }
        List<CatalystSlot> catalysts = hasCatalyst ? List.of(new CatalystSlot(0, List.of(coal), 1, 64)) : List.of();
        List<OutputSlot> outputSlots = new ArrayList<>();
        for (ResourceKey output : outputs) {
            outputSlots.add(new OutputSlot(output, 1L));
        }
        return new CompiledRecipe(
            compactId,
            key,
            RecipeRole.standardProduction,
            inputSlots,
            catalysts,
            outputSlots,
            new MachineRequirement(Optional.empty(), machines, false),
            Map.of()
        );
    }

    private static PlanningResult buildPlanningResult() {
        List<PlanStep> balancedSteps = List.of(
            new AcquireStep(
                "s-ore",
                List.of(),
                ironOre,
                32L,
                AcquisitionKind.manualGather,
                cost(6.0, 0.0, 0.0, 4.0, 0.5)
            ),
            new AcquireStep("s-coal", List.of(), coal, 16L, AcquisitionKind.manualGather, cost(3.0, 0.0, 0.0, 2.0, 0.2)),
            new AcquireStep(
                "s-redstone",
                List.of(),
                redstone,
                8L,
                AcquisitionKind.manualGather,
                cost(2.5, 0.0, 0.0, 3.5, 0.3)
            ),
            new ProvisionMachineStep(
                "s-blast",
                List.of(),
                blastFurnace,
                Optional.of(new MachineCapabilityId("smelting")),
                1,
                false,
                cost(4.0, 0.0, 12.0, 1.0, 1.0)
            ),
            new CraftStep(
                "s-smelt",
                List.of("s-ore", "s-coal", "s-blast"),
                rSmeltIron,
                32L,
                Optional.of(blastFurnace),
                false,
                Map.of(0, new SlotBinding(ironOre, List.of()), 1, new SlotBinding(coal, List.of())),
                Map.of(),
                cost(4.0, 22.0, 0.0, 1.0, 1.2)
            ),
            new SetupLineStep(
                "s-line",
                List.of("s-smelt"),
                smelting,
                blastFurnace,
                ironIngot,
                cost(3.0, 4.0, 6.0, 0.5, 1.4)
            ),
            new ConvertStep(
                "s-block",
                List.of("s-line"),
                rPackBlock,
                2L,
                Map.of(0, new SlotBinding(ironIngot, List.of())),
                cost(1.5, 0.0, 0.0, 0.5, 0.4)
            ),
            new CraftStep(
                "s-piston",
                List.of("s-smelt", "s-redstone", "s-block"),
                rCraftPiston,
                8L,
                Optional.empty(),
                false,
                Map.of(2, new SlotBinding(ironIngot, List.of()), 3, new SlotBinding(redstone, List.of())),
                Map.of(),
                cost(5.0, 2.0, 0.0, 2.0, 1.5)
            )
        );
        PreparationSummary prep = new PreparationSummary(
            new StockLedger(Map.of(ironOre, 20L, coal, 12L, redstone, 8L)),
            new StockLedger(Map.of(ironOre, 12L, coal, 4L, oakPlanks, 24L)),
            Map.of(blastFurnace, 1),
            List.of(),
            List.of()
        );
        VerificationCertificate valid = new VerificationCertificate(true, true, true, true, true, true, List.of());
        ProductionPlan balanced = new ProductionPlan(
            "plan-balanced",
            "cirrus.strategy.balanced",
            balancedSteps,
            prep,
            List.of(new ByproductEntry(ironIngot, 4L, "s-smelt")),
            cost(29.0, 28.0, 18.0, 14.5, 6.5),
            valid
        );
        ProductionPlan fastest = new ProductionPlan(
            "plan-fastest",
            "cirrus.strategy.min_human_time",
            balancedSteps,
            prep,
            List.of(),
            cost(14.0, 42.0, 32.0, 22.0, 9.5),
            valid
        );
        ProductionPlan zeroNew = new ProductionPlan(
            "plan-zero-machines",
            "cirrus.strategy.no_new_machines",
            balancedSteps,
            prep,
            List.of(),
            cost(48.0, 64.0, 4.0, 10.0, 3.0),
            valid
        );

        DecisionPrompt leafPrompt = new DecisionPrompt(
            "prompt-leaf",
            DecisionPromptKind.baseOrSupplyBoundary,
            "cirrus.prompt.expensive_leaf.title",
            "cirrus.prompt.expensive_leaf.explanation",
            0.88,
            List.of(
                new DecisionOption(
                    "opt-treat-free",
                    "cirrus.ui.workbench.action.treat_free",
                    cost(8.5, 0.0, 0.0, 6.0, 1.0),
                    List.of(
                        new ResourceRule("rule-free-ore", GuidanceScope.session, RuleStrength.hardPin, ironOre, true)
                    )
                ),
                new DecisionOption(
                    "opt-keep-smelt",
                    "cirrus.ui.workbench.action.replace_recipe",
                    cost(2.0, 12.0, 4.0, 0.0, 0.5),
                    List.of()
                )
            )
        );
        DecisionPrompt machinePrompt = new DecisionPrompt(
            "prompt-machine",
            DecisionPromptKind.machineAvailability,
            "cirrus.prompt.machine_fork.title",
            "cirrus.prompt.machine_fork.explanation",
            0.62,
            List.of(
                new DecisionOption(
                    "opt-own-blast",
                    "cirrus.ui.workbench.action.declare_owned",
                    cost(4.0, 10.0, 12.0, 0.0, 0.0),
                    List.of(
                        new MachineRule(
                            "rule-own-blast",
                            GuidanceScope.session,
                            RuleStrength.softPrefer,
                            blastFurnace,
                            true
                        )
                    )
                ),
                new DecisionOption(
                    "opt-build-blast",
                    "cirrus.ui.workbench.action.replace_recipe",
                    cost(2.0, 4.0, 12.0, 0.0, 0.5),
                    List.of()
                )
            )
        );

        return new PlanningResult(
            PlanningStatus.feasible,
            List.of(balanced, fastest, zeroNew),
            List.of(leafPrompt, machinePrompt),
            new PlanningDiagnostics(12L, 8, 3, 0, 2, false, Optional.empty())
        );
    }

    private static CirrusPlanningRuntime.PublishedSnapshot buildPublishedSnapshot(VisualGraph graph) {
        LayeredHumanGuidanceStore store = new LayeredHumanGuidanceStore();
        DefaultPlanningService service = new DefaultPlanningService(
            graph,
            DefaultCostMetadataProvider.instance,
            store,
            fb -> {}
        );
        CirrusCatalogAdapter.CaptureStats capture = new CirrusCatalogAdapter.CaptureStats(
            5,
            0,
            428,
            394,
            18,
            4,
            12,
            Map.of()
        );
        return new CirrusPlanningRuntime.PublishedSnapshot(
            7L,
            1L,
            graph,
            service,
            new PlanningContextFactory(graph),
            List.of(),
            capture
        );
    }

    private static WorldFacilityRegistry buildFacilityRegistry() {
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        registry.declareOwnedMachine(furnace, new MachineCapabilityId("smelting"), 4, 1.0, false);
        registry.declareOwnedMachine(blastFurnace, new MachineCapabilityId("smelting"), 2, 2.5, true);
        registry.declareOwnedMachine(stonecutter, new MachineCapabilityId("cutting"), 1, 3.2, false);
        return registry;
    }

    static final class VisualShowcaseScreen extends BasicScreen {

        private final WorkbenchShellWidget shell;
        private final WorkbenchSurfaces surfaces;
        private final SessionTabManager tabManager;

        VisualShowcaseScreen(
            VisualGraph graph,
            PlanningResult planningResult,
            CirrusPlanningRuntime.PublishedSnapshot publishedSnapshot,
            WorldFacilityRegistry registry
        ) {
            tabManager = new SessionTabManager(state -> {
                state.setRuntimeGraphVersion(7L);
                return new PlannerWorkbenchController(
                    state,
                    request -> new StaticSession(planningResult),
                    spec -> new PlanningRequest(
                        spec.targets(),
                        new PlanningContext(
                            InventorySnapshot.empty,
                            FacilitySnapshot.empty,
                            (key, missing, progression) -> Optional.empty(),
                            DefaultHumanGuidanceView.empty(),
                            ProgressionState.overworldDefault
                        ),
                        spec.profile(),
                        spec.budget()
                    ),
                    Runnable::run
                );
            });

            WorkbenchIntegrationProvider baseProvider = new WorkbenchIntegrationProvider();
            WorkspaceProvider provider = new VisualWorkspaceProvider(baseProvider, graph, publishedSnapshot, registry);
            shell = new WorkbenchShellWidget(provider, tabManager, WorkbenchMode.planner) {
                @Override
                public CompiledRecipeGraph activeGraph() {
                    return graph;
                }
            };
            surfaces = WorkbenchSurfaces.install(shell, baseProvider);
            mainGroup.useStyle(sizeFull());
            mainGroup.addWidget(shell);
            shell.openTab(List.of(new TargetDemand(piston, 8L), new TargetDemand(compass, 4L)), null);
        }

        WorkbenchShellWidget shell() {
            return shell;
        }

        WorkbenchSurfaces surfaces() {
            return surfaces;
        }

        @Override
        protected void init() {
            super.init();
            shell.applyViewport(width, height);
            scene().layout();
            mainGroup().applyLayout();
        }

        @Override
        public void resize(Minecraft minecraft, int width, int height) {
            super.resize(minecraft, width, height);
            shell.applyViewport(width, height);
            scene().layout();
            mainGroup().applyLayout();
        }

        @Override
        public void onClose() {
            shell.shutdown();
            super.onClose();
        }
    }

    private static final class StaticSession implements PlanningSession {

        private final PlanningResult result;

        private StaticSession(PlanningResult result) {
            this.result = result;
        }

        @Override
        public PlanningResult currentResult() {
            return result;
        }

        @Override
        public PlanningResult refine(HumanFeedback feedback) {
            return result;
        }

        @Override
        public PlanningResult undo() {
            return result;
        }

        @Override
        public PlanningResult redo() {
            return result;
        }

        @Override
        public void close() {}
    }

    private static final class VisualWorkspaceProvider implements WorkspaceProvider {

        private final WorkbenchIntegrationProvider baseProvider;
        private final VisualGraph graph;
        private final CirrusPlanningRuntime.PublishedSnapshot publishedSnapshot;
        private final WorldFacilityRegistry registry;
        private final List<GuidanceRule> rules = new ArrayList<>();

        private VisualWorkspaceProvider(
            WorkbenchIntegrationProvider baseProvider,
            VisualGraph graph,
            CirrusPlanningRuntime.PublishedSnapshot publishedSnapshot,
            WorldFacilityRegistry registry
        ) {
            this.baseProvider = baseProvider;
            this.graph = graph;
            this.publishedSnapshot = publishedSnapshot;
            this.registry = registry;
            this.rules.add(
                new MachineRule(
                    "visual-prefer-blast",
                    GuidanceScope.worldSave,
                    RuleStrength.softPrefer,
                    blastFurnace,
                    true
                )
            );
            this.rules
                    .add(new ResourceRule("visual-pin-coal", GuidanceScope.session, RuleStrength.hardPin, coal, true));
        }

        @Override
        public Widget plannerWorkspace(WorkbenchShellContext ctx) {
            FlowStageWidget stage = FlowStageWidget.create();
            var copilot = baseProvider.copilotHost(ctx);
            ShellSpatialDagHost dagHost = new ShellSpatialDagHost(
                ctx,
                () -> true,
                (payload, targetStepId) -> WorkbenchSurfaces.routeDrop(ctx, copilot, payload, targetStepId),
                ctx::toast
            );
            ShellFlowViewHost flowHost = new ShellFlowViewHost(ctx, () -> true);
            stage.host(dagHost).resolver(WorkbenchIntegrationProvider.defaultResolver()).graph(graph)
                    .themeMode(ctx.theme())
                    .inlineBeacons(WorkbenchIntegrationProvider.inlineBeaconsOf(ctx.activeSnapshot(), graph));
            PreparationLedgerWidget ledger = new PreparationLedgerWidget(flowHost);
            ledger.setInventorySource(
                () -> new InventorySnapshot(
                    new StockLedger(Map.of(ironOre, 20L, coal, 12L, redstone, 8L)),
                    StockLedger.empty
                )
            );
            stage.setViewWidget(FlowViewMode.preparationLedger, ledger);
            ExecutionChecklistWidget checklist = new ExecutionChecklistWidget(flowHost).setGraph(graph);
            checklist.toggleChecked("s-ore");
            checklist.toggleChecked("s-coal");
            stage.setViewWidget(FlowViewMode.executionChecklist, checklist);
            return stage;
        }

        @Override
        public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
            return new FacilitiesAndRulesWorkspace(new FixtureFacilitiesHost(ctx), ctx);
        }

        @Override
        public Widget settingsWorkspace(WorkbenchShellContext ctx) {
            return new SettingsAndDiagnosticsWorkspace(new FixtureSettingsHost(ctx), ctx);
        }

        @Override
        public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
            DatabasePage page = new DatabasePage();
            if (ctx != null) {
                return new DatabaseExplorerWorkspace(ctx, page);
            }
            WidgetGroup<Widget> container = new WidgetGroup<>();
            container.useStyle(sizeFull(), flexColumn(), minWidth(0), minHeight(0));
            container.addWidget(page);
            return container;
        }

        private final class FixtureFacilitiesHost implements FacilitiesHost {

            private final WorkbenchShellContext ctx;

            private FixtureFacilitiesHost(WorkbenchShellContext ctx) {
                this.ctx = ctx;
            }

            @Override
            public WorldFacilityRegistry registry() {
                return registry;
            }

            @Override
            public boolean worldBound() {
                return true;
            }

            @Override
            public Optional<CompiledRecipeGraph> graph() {
                return Optional.of(graph);
            }

            @Override
            public List<String> systemIds() {
                return List.of(crafting, smelting);
            }

            @Override
            public List<GuidanceRule> rules() {
                return List.copyOf(rules);
            }

            @Override
            public List<CompiledRecipeKey> sessionRecipeKeys() {
                return List.of(rSmeltIron, rPackBlock, rCraftPiston, rCraftCompass);
            }

            @Override
            public void putRule(GuidanceRule rule) {
                rules.removeIf(existing -> existing.ruleId().equals(rule.ruleId()));
                rules.add(rule);
            }

            @Override
            public void removeRule(String ruleId) {
                rules.removeIf(existing -> existing.ruleId().equals(ruleId));
            }

            @Override
            public void toast(Component message) {
                ctx.toast(message);
            }

            @Override
            public Path configDirectory() {
                return Path.of(".");
            }

            @Override
            public Transfer exportConfig() {
                return new Transfer(true, Component.literal("Exported"));
            }

            @Override
            public Transfer importConfig() {
                return new Transfer(true, Component.literal("Imported"));
            }
        }

        private final class FixtureSettingsHost implements SettingsHost {

            private final WorkbenchShellContext ctx;

            private FixtureSettingsHost(WorkbenchShellContext ctx) {
                this.ctx = ctx;
            }

            @Override
            public Optional<CirrusPlanningRuntime.PublishedSnapshot> published() {
                return Optional.of(publishedSnapshot);
            }

            @Override
            public boolean plannerAvailable() {
                return true;
            }

            @Override
            public boolean compiling() {
                return false;
            }

            @Override
            public long lastCompileMillis() {
                return 42L;
            }

            @Override
            public CompletableFuture<Optional<Long>> recompile() {
                return CompletableFuture.completedFuture(Optional.of(42L));
            }

            @Override
            public void toast(Component message) {
                ctx.toast(message);
            }

            @Override
            public Path logDirectory() {
                return Path.of(".");
            }

            @Override
            public Transfer writeDiagnostics(String fileName, JsonElement payload) {
                return new Transfer(true, Component.literal("Written"));
            }
        }
    }

    static final class VisualGraph implements CompiledRecipeGraph {

        private final Map<CompiledRecipeKey, CompiledRecipe> recipes = new TreeMap<>();
        private final Set<ResourceKey> producible = new LinkedHashSet<>();

        void put(CompiledRecipe recipe) {
            recipes.put(recipe.key(), recipe);
            for (OutputSlot output : recipe.outputs()) {
                producible.add(output.resource());
            }
        }

        @Override
        public long catalogVersion() {
            return 7L;
        }

        @Override
        public SystemAdmission admissionOf(String systemId) {
            return SystemAdmission.enabled;
        }

        @Override
        public List<CompiledRecipe> producersOf(ResourceKey output) {
            List<CompiledRecipe> result = new ArrayList<>();
            for (CompiledRecipe recipe : recipes.values()) {
                for (OutputSlot slot : recipe.outputs()) {
                    if (slot.resource().equals(output)) {
                        result.add(recipe);
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public List<CompiledRecipe> consumersOf(ResourceKey input) {
            List<CompiledRecipe> result = new ArrayList<>();
            for (CompiledRecipe recipe : recipes.values()) {
                for (InputSlot slot : recipe.inputs()) {
                    if (slot.candidates().contains(input)) {
                        result.add(recipe);
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        public List<CompiledCycleMacro> cycleMacrosProducing(ResourceKey output) {
            return List.of();
        }

        @Override
        public Optional<CompiledRecipe> recipeByCompactId(int compactId) {
            for (CompiledRecipe recipe : recipes.values()) {
                if (recipe.compactId() == compactId) {
                    return Optional.of(recipe);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<CompiledRecipe> recipeByKey(CompiledRecipeKey key) {
            return Optional.ofNullable(recipes.get(key));
        }

        @Override
        public Set<ResourceKey> allProducibleResources() {
            return Set.copyOf(producible);
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
            return new GraphCompilationStats(428, 368, 22, 18, 12, 8, 4, 2);
        }
    }
}

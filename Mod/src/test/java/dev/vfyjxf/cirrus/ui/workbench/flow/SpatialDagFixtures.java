package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
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
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.layout.CriticalPathCalculator;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayout;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class SpatialDagFixtures {

    static final ComponentStackKey ore = OfflineContentTypes.item("minecraft:iron_ore");
    static final ComponentStackKey ingot = OfflineContentTypes.item("minecraft:iron_ingot");
    static final ComponentStackKey block = OfflineContentTypes.item("minecraft:iron_block");
    static final ComponentStackKey gem = OfflineContentTypes.item("minecraft:diamond");

    static final CompiledRecipeKey r1 = key("minecraft:smelting", "r1");
    static final CompiledRecipeKey r2 = key("minecraft:crafting", "r2");
    static final CompiledRecipeKey r3 = key("minecraft:crafting", "r3");

    private SpatialDagFixtures() {}

    static CompiledRecipeKey key(String systemId, String recipeId) {
        return new CompiledRecipeKey(Namespace.parse(systemId), OfflineContentTypes.tryParseLocation(recipeId), 0L);
    }

    static CostVector cost(double human, double passive, double capital, double scarcity, double cognitive) {
        return new CostVector(human, passive, capital, scarcity, cognitive);
    }

    static CraftStep craft(String stepId, List<String> deps, CompiledRecipeKey key, CostVector cost) {
        return new CraftStep(
            stepId,
            deps,
            key,
            1L,
            Optional.of(OfflineContentTypes.machine("minecraft:furnace")),
            false,
            Map.of(0, new SlotBinding(ore, List.of())),
            Map.of(),
            cost
        );
    }

    static ProductionPlan chainPlan() {
        List<PlanStep> steps = List.of(
            craft("c1", List.of(), r1, cost(10.0, 2.0, 1.0, 0.5, 0.1)),
            craft("c2", List.of("c1"), r2, cost(4.0, 6.0, 2.0, 1.5, 0.2)),
            craft("c3", List.of("c2"), r3, cost(2.0, 0.0, 0.0, 0.5, 0.3))
        );
        return plan("plan-chain", steps, List.of());
    }

    static ProductionPlan tracePlan() {
        List<PlanStep> steps = List.of(
            new AcquireStep("x0", List.of(), ore, 16L, AcquisitionKind.manualGather, cost(3.0, 0.0, 0.0, 0.0, 0.0)),
            craft("c1", List.of("x0"), r1, cost(10.0, 2.0, 1.0, 0.5, 0.1)),
            craft("c2", List.of("c1"), r2, cost(4.0, 6.0, 2.0, 1.5, 0.2)),
            craft("c3", List.of(), r3, cost(2.0, 0.0, 0.0, 0.5, 0.3))
        );
        return plan("plan-trace", steps, List.of());
    }

    static ProductionPlan plan(String planId, List<PlanStep> steps, List<ByproductEntry> byproducts) {
        return new ProductionPlan(
            planId,
            "cirrus.strategy.balanced",
            steps,
            PreparationSummary.empty,
            byproducts,
            cost(10.0, 10.0, 0.0, 0.0, 0.0),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    static CompiledRecipeGraph graph() {
        FakeGraph graph = new FakeGraph();
        graph.put(recipe(0, r1, List.of(ore), List.of(ingot)));
        graph.put(recipe(1, r2, List.of(ingot), List.of(block)));
        graph.put(recipe(2, r3, List.of(block), List.of(gem)));
        return graph;
    }

    static CompiledRecipe recipe(
        int compactId,
        CompiledRecipeKey key,
        List<ComponentStackKey> inputs,
        List<ComponentStackKey> outputs
    ) {
        List<InputSlot> inputSlots = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            inputSlots.add(
                new InputSlot(index, 1L, Optional.empty(), List.of(inputs.get(index)), SlotMatchMode.identity)
            );
        }
        List<OutputSlot> outputSlots = new ArrayList<>();
        for (ComponentStackKey output : outputs) {
            outputSlots.add(new OutputSlot(output, 1L));
        }
        return new CompiledRecipe(
            compactId,
            key,
            RecipeRole.standardProduction,
            inputSlots,
            List.<CatalystSlot>of(),
            outputSlots,
            new MachineRequirement(Optional.empty(), List.of(OfflineContentTypes.machine("minecraft:furnace")), false),
            Map.of()
        );
    }

    static PlanDagLayoutResult layout(ProductionPlan plan, DagLayoutMode mode) {
        return PlanDagLayout.compute(
            plan,
            null,
            graph(),
            mode,
            Set.of(),
            Map.of(),
            CriticalPathCalculator.compute(plan).criticalStepIds()
        );
    }

    static WorkbenchUiSnapshot snapshot(ProductionPlan plan, DagLayoutMode mode, Set<String> selected) {
        PlanningResult result = new PlanningResult(
            PlanningStatus.feasible,
            List.of(plan),
            List.of(),
            new PlanningDiagnostics(1L, plan.steps().size(), 0, 0, 0, false, Optional.empty())
        );
        return new WorkbenchUiSnapshot(
            "tab-1",
            "Tab",
            WorkbenchPhase.readyPareto,
            false,
            1L,
            List.<TargetDemand>of(),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            false,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            result,
            null,
            plan.planId(),
            null,
            selected,
            null,
            null,
            FlowViewMode.spatialDag,
            mode,
            Set.of(),
            Map.<String, FloatPos>of(),
            List.of(),
            List.of(),
            List.<GuidanceRule>of(),
            null
        );
    }

    static final class RecordingHost implements SpatialDagHost {

        private WorkbenchUiSnapshot snapshot;
        final List<Set<String>> selections = new ArrayList<>();
        final Map<String, FloatPos> moved = new TreeMap<>();
        final List<DragPayload> drops = new ArrayList<>();
        final List<DagLayoutMode> layoutModes = new ArrayList<>();
        int clearOffsetsCount;
        int canvasMenus;
        int nodeMenus;
        int beaconClicks;
        @Nullable
        String hoveredStep;
        boolean reducedMotion;

        RecordingHost(WorkbenchUiSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        void snapshot(WorkbenchUiSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public WorkbenchUiSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public CirrusPalette palette() {
            return CirrusPalette.obsidianBlueprint;
        }

        @Override
        public void onSelectSteps(Set<String> stepIds) {
            selections.add(Set.copyOf(stepIds));
        }

        @Override
        public void onNodeMoved(String stepId, FloatPos offset) {
            moved.put(stepId, offset.copy());
        }

        @Override
        public void onClearManualOffsets() {
            clearOffsetsCount++;
        }

        @Override
        public void onHoverStep(@Nullable String stepId) {
            hoveredStep = stepId;
        }

        @Override
        public void onHoverEdge(
            @Nullable String fromStepId,
            @Nullable String toStepId,
            @Nullable ComponentStackKey resource
        ) {}

        @Override
        public void onInlineBeaconClick(String promptId, String stepId) {
            beaconClicks++;
        }

        @Override
        public void onNodeContextMenu(String stepId, double screenX, double screenY, boolean shiftDown) {
            nodeMenus++;
        }

        @Override
        public void onCanvasContextMenu(double screenX, double screenY) {
            canvasMenus++;
        }

        @Override
        public void onDrop(DragPayload payload, @Nullable String targetStepId) {
            drops.add(payload);
        }

        @Override
        public void onSetLayoutMode(DagLayoutMode mode) {
            layoutModes.add(mode);
        }

        @Override
        public boolean isReducedMotion() {
            return reducedMotion;
        }
    }

    static final class RecordingSink implements MultiSelectActionBarWidget.FeedbackSink {

        private final List<HumanFeedback> sent = new ArrayList<>();

        @Override
        public void send(HumanFeedback feedback) {
            sent.add(feedback);
        }

        List<HumanFeedback> sent() {
            return sent;
        }
    }

    private static final class FakeGraph implements CompiledRecipeGraph {

        private final Map<CompiledRecipeKey, CompiledRecipe> recipes = new TreeMap<>();

        void put(CompiledRecipe recipe) {
            recipes.put(recipe.key(), recipe);
        }

        @Override
        public long catalogVersion() {
            return 1L;
        }

        @Override
        public SystemAdmission admissionOf(Namespace systemId) {
            return SystemAdmission.enabled;
        }

        @Override
        public List<CompiledRecipe> producersOf(ComponentStackKey output) {
            List<CompiledRecipe> producers = new ArrayList<>();
            for (CompiledRecipe recipe : recipes.values()) {
                for (OutputSlot slot : recipe.outputs()) {
                    if (slot.resource().equals(output)) {
                        producers.add(recipe);
                        break;
                    }
                }
            }
            return producers;
        }

        @Override
        public List<CompiledRecipe> consumersOf(ComponentStackKey input) {
            List<CompiledRecipe> consumers = new ArrayList<>();
            for (CompiledRecipe recipe : recipes.values()) {
                for (InputSlot slot : recipe.inputs()) {
                    if (slot.candidates().contains(input)) {
                        consumers.add(recipe);
                        break;
                    }
                }
            }
            return consumers;
        }

        @Override
        public List<CompiledCycleMacro> cycleMacrosProducing(ComponentStackKey output) {
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
        public Set<ComponentStackKey> allProducibleResources() {
            return Set.of();
        }

        @Override
        public SccTopology sccTopology() {
            return new SccTopology() {
                @Override
                public int sccIdOf(ComponentStackKey resource) {
                    return 0;
                }

                @Override
                public int sccSize(int sccId) {
                    return 0;
                }

                @Override
                public boolean isInNonTrivialScc(ComponentStackKey resource) {
                    return false;
                }

                @Override
                public Set<ComponentStackKey> membersOfScc(int sccId) {
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
            return new GraphCompilationStats(0, recipes.size(), 0, 0, 0, 0, 0, 0);
        }
    }
}

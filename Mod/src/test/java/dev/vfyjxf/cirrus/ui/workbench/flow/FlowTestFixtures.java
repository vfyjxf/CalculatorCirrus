package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningDiagnostics;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class FlowTestFixtures {

    static final ResourceKey ironOre = ResourceKey.item("minecraft:iron_ore");
    static final ResourceKey coal = ResourceKey.item("minecraft:coal");
    static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    static final ResourceKey cogwheel = ResourceKey.item("create:cogwheel");
    static final ResourceKey diamond = ResourceKey.item("minecraft:diamond");
    static final ResourceKey piston = ResourceKey.item("minecraft:piston");

    static final String smelting = "minecraft:smelting";
    static final String crafting = "minecraft:crafting";
    static final String furnace = "minecraft:furnace";

    static final CompiledRecipeKey smeltIron = key(smelting, "minecraft:iron_ingot_from_smelting");
    static final CompiledRecipeKey craftPiston = key(crafting, "minecraft:piston");

    private FlowTestFixtures() {}

    static CompiledRecipeKey key(String systemId, String recipeId) {
        return new CompiledRecipeKey(systemId, Optional.of(recipeId), 0L);
    }

    static CostVector cost(double human, double passive) {
        return new CostVector(human, passive, 0.0, 0.0, 0.0);
    }

    static SlotBinding binding(ResourceKey resource) {
        return new SlotBinding(resource, List.of());
    }

    static ProductionPlan plan(
        String planId,
        List<PlanStep> steps,
        PreparationSummary preparation,
        List<ByproductEntry> byproducts
    ) {
        return new ProductionPlan(
            planId,
            "cirrus.strategy.balanced",
            steps,
            preparation,
            byproducts,
            cost(10.0, 10.0),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    static CraftStep smeltStep(double human, double passive) {
        return new CraftStep(
            "s003",
            List.of(),
            smeltIron,
            2L,
            Optional.of(furnace),
            false,
            Map.of(0, binding(ironOre)),
            Map.of(0, diamond),
            cost(human, passive)
        );
    }

    static ProductionPlan bottleneckPlan(double passiveSeconds) {
        return plan("plan-bottleneck", List.of(smeltStep(0.5, passiveSeconds)), PreparationSummary.empty, List.of());
    }

    static ProductionPlan bottleneckPlanWithoutMachine(double passiveSeconds) {
        CraftStep step = new CraftStep(
            "s003",
            List.of(),
            smeltIron,
            2L,
            Optional.empty(),
            false,
            Map.of(0, binding(ironOre)),
            Map.of(),
            cost(0.5, passiveSeconds)
        );
        return plan("plan-bottleneck-nomachine", List.of(step), PreparationSummary.empty, List.of());
    }

    static ProductionPlan ledgerPlan() {
        List<PlanStep> steps = List.of(
            new AcquireStep("s001", List.of(), ironOre, 64L, AcquisitionKind.manualGather, cost(2.0, 0.0)),
            new AcquireStep("s002", List.of(), coal, 32L, AcquisitionKind.manualGather, cost(2.0, 0.0)),
            new ProvisionMachineStep("s003", List.of(), furnace, Optional.empty(), 2, false, cost(1.0, 0.0)),
            smeltStep(4.0, 10.0)
        );
        PreparationSummary preparation = new PreparationSummary(
            new StockLedger(Map.of(ironIngot, 8L)),
            new StockLedger(Map.of(ironOre, 64L, coal, 32L)),
            Map.of(furnace, 2),
            List.of("line:pressing"),
            List.of(diamond)
        );
        return plan("plan-ledger", steps, preparation, List.of(new ByproductEntry(cogwheel, 2L, "s003")));
    }

    static InventorySnapshot ledgerInventory() {
        return new InventorySnapshot(
            new StockLedger(Map.of(ironOre, 16L, ironIngot, 8L)),
            new StockLedger(Map.of(ironOre, 4L))
        );
    }

    static ProductionPlan checklistPlan() {
        List<PlanStep> steps = List.of(
            new AcquireStep("p001", List.of(), ironOre, 64L, AcquisitionKind.manualGather, cost(2.0, 0.0)),
            new ProvisionMachineStep("p002", List.of(), furnace, Optional.empty(), 1, false, cost(1.0, 0.0)),
            new CraftStep(
                "c001",
                List.of("p001", "p002"),
                smeltIron,
                4L,
                Optional.of(furnace),
                false,
                Map.of(0, binding(ironOre)),
                Map.of(),
                cost(4.0, 10.0)
            ),
            new ConvertStep("c002", List.of("c001"), craftPiston, 1L, Map.of(0, binding(ironIngot)), cost(0.5, 0.0)),
            new CraftStep(
                "t001",
                List.of("c001", "c002"),
                craftPiston,
                1L,
                Optional.empty(),
                false,
                Map.of(),
                Map.of(),
                cost(1.0, 0.0)
            )
        );
        return plan("plan-checklist", steps, PreparationSummary.empty, List.of());
    }

    static WorkbenchUiSnapshot snapshot(ProductionPlan plan) {
        PlanningResult result = new PlanningResult(
            PlanningStatus.feasible,
            List.of(plan),
            List.of(),
            new PlanningDiagnostics(1L, 1, 1, 0, 0, false, Optional.empty())
        );
        return new WorkbenchUiSnapshot(
            "tab-flow",
            "Flow",
            WorkbenchPhase.readyPareto,
            false,
            1L,
            List.of(),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            true,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            result,
            null,
            plan.planId(),
            null,
            Set.of(),
            null,
            null,
            FlowViewMode.preparationLedger,
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    static WorkbenchUiSnapshot emptySnapshot() {
        return new WorkbenchUiSnapshot(
            "tab-flow-empty",
            "Flow",
            WorkbenchPhase.idleNoTarget,
            false,
            1L,
            List.of(),
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            true,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            null,
            null,
            null,
            null,
            Set.of(),
            null,
            null,
            FlowViewMode.preparationLedger,
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            null
        );
    }

    static final class FakeHost implements FlowViewHost {

        private WorkbenchUiSnapshot snapshot;
        private final CirrusPalette palette;
        private final List<HumanFeedback> dispatched = new ArrayList<>();
        private final List<String> focusedSteps = new ArrayList<>();
        private final List<LangEntry> toasts = new ArrayList<>();
        private boolean reducedMotion;

        FakeHost(ProductionPlan plan) {
            this(FlowTestFixtures.snapshot(plan), CirrusPalette.obsidianBlueprint);
        }

        FakeHost(WorkbenchUiSnapshot snapshot, CirrusPalette palette) {
            this.snapshot = snapshot;
            this.palette = palette;
        }

        void setSnapshot(WorkbenchUiSnapshot next) {
            this.snapshot = next;
        }

        @Override
        public WorkbenchUiSnapshot snapshot() {
            return snapshot;
        }

        @Override
        public CirrusPalette palette() {
            return palette;
        }

        @Override
        public void dispatch(HumanFeedback feedback) {
            dispatched.add(feedback);
        }

        @Override
        public void onFocusStep(String stepId) {
            focusedSteps.add(stepId);
        }

        @Override
        public void toast(LangEntry entry) {
            toasts.add(entry);
        }

        @Override
        public boolean isReducedMotion() {
            return reducedMotion;
        }

        List<HumanFeedback> dispatched() {
            return List.copyOf(dispatched);
        }

        List<String> focusedSteps() {
            return List.copyOf(focusedSteps);
        }

        List<LangEntry> toasts() {
            return List.copyOf(toasts);
        }
    }
}

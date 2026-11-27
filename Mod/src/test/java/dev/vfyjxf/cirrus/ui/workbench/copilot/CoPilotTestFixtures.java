package dev.vfyjxf.cirrus.ui.workbench.copilot;

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
import dev.vfyjxf.cirrus.api.planning.human.ConflictDiagnosis;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
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
import dev.vfyjxf.cirrus.ui.workbench.state.HistoryEntry;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPrompt;
import dev.vfyjxf.cirrus.ui.workbench.state.ResolvedPromptOption;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class CoPilotTestFixtures {

    static final ResourceKey ironOre = ResourceKey.item("minecraft:iron_ore");
    static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    static final ResourceKey coal = ResourceKey.item("minecraft:coal");
    static final ResourceKey certus = ResourceKey.item("ae2:certus_quartz_crystal");
    static final String smelting = "minecraft:smelting";
    static final String charging = "ae2:charger";
    static final CompiledRecipeKey smeltIron = key(smelting, "minecraft:iron_ingot_from_smelting");
    static final CompiledRecipeKey chargeCertus = key(charging, "ae2:certus_quartz_crystal");
    static final String furnace = "minecraft:furnace";

    private CoPilotTestFixtures() {}

    static CompiledRecipeKey key(String systemId, String recipeId) {
        return new CompiledRecipeKey(systemId, Optional.of(recipeId), 0L);
    }

    static CostVector cost(double human, double passive, double capital, double scarcity, double cognitive) {
        return new CostVector(human, passive, capital, scarcity, cognitive);
    }

    static InputSlot input(int slotIndex, long amount, ResourceKey resource) {
        return new InputSlot(slotIndex, amount, Optional.empty(), List.of(resource), SlotMatchMode.identity);
    }

    static SlotBinding binding(ResourceKey resource, ResourceKey... alternatives) {
        return new SlotBinding(resource, List.of(alternatives));
    }

    static AcquireStep acquire(String stepId, ResourceKey resource, long amount) {
        return new AcquireStep(
            stepId,
            List.of(),
            resource,
            amount,
            AcquisitionKind.manualGather,
            cost(4.0, 0.0, 0.0, 0.0, 0.0)
        );
    }

    static CraftStep craft(
        String stepId,
        CompiledRecipeKey recipeKey,
        List<String> dependsOn,
        Map<Integer, SlotBinding> bindings,
        Optional<String> machineId,
        CostVector cost
    ) {
        return new CraftStep(stepId, dependsOn, recipeKey, 1L, machineId, false, bindings, Map.of(), cost);
    }

    static ProvisionMachineStep provision(String stepId, String machineId, int count) {
        return new ProvisionMachineStep(
            stepId,
            List.of(),
            machineId,
            Optional.empty(),
            count,
            false,
            cost(3.0, 0.0, 8.0, 0.0, 0.0)
        );
    }

    static PreparationSummary preparation(String machineId, int count) {
        return new PreparationSummary(
            StockLedger.empty,
            new StockLedger(Map.of(ironOre, 8L)),
            machineId == null ? Map.of() : Map.of(machineId, count),
            List.of(),
            List.of()
        );
    }

    static ProductionPlan plan(String planId, String strategyKey, List<PlanStep> steps, CostVector totalCost) {
        return new ProductionPlan(
            planId,
            strategyKey,
            steps,
            preparation(furnace, 1),
            List.of(new ByproductEntry(coal, 2L, steps.isEmpty() ? "s0" : steps.get(0).stepId())),
            totalCost,
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    static PlanningResult result(
        PlanningStatus status,
        List<ProductionPlan> plans,
        List<DecisionPrompt> prompts,
        Optional<ConflictDiagnosis> diagnosis
    ) {
        return new PlanningResult(status, plans, prompts, new PlanningDiagnostics(3L, 4, 2, 0, 1, false, diagnosis));
    }

    static DecisionOption option(String optionId, CostVector delta, List<GuidanceRule> rules) {
        return new DecisionOption(optionId, "option." + optionId, delta, rules);
    }

    static DecisionOption followUpOption(
        String optionId,
        CostVector delta,
        List<GuidanceRule> rules,
        HumanFeedback followUp
    ) {
        return new DecisionOption(optionId, "option." + optionId, delta, rules, Optional.of(followUp));
    }

    static DecisionPrompt prompt(String promptId, DecisionPromptKind kind, double leverage, DecisionOption... options) {
        return new DecisionPrompt(
            promptId,
            kind,
            "cirrus.prompt.title",
            "cirrus.prompt.explanation",
            leverage,
            List.of(options)
        );
    }

    static ResolvedPrompt resolved(
        DecisionPrompt prompt,
        @Nullable ResourceKey resource,
        @Nullable String machineId,
        @Nullable String tagId,
        @Nullable CompiledRecipeKey recipeKey,
        @Nullable String systemId,
        Set<String> anchors
    ) {
        List<ResolvedPromptOption> options = new ArrayList<>();
        for (DecisionOption option : prompt.options()) {
            ResourceKey variant = tagId == null ? null : resource;
            options.add(
                new ResolvedPromptOption(
                    option,
                    variant,
                    machineId,
                    option.estimatedDelta(),
                    option.followUp().isPresent()
                )
            );
        }
        return new ResolvedPrompt(prompt, resource, machineId, tagId, recipeKey, systemId, anchors, options);
    }

    static MachineRule machineRule(String ruleId, String machineId, RuleStrength strength, boolean willingToBuild) {
        return new MachineRule(ruleId, GuidanceScope.session, strength, machineId, willingToBuild);
    }

    static WorkbenchUiSnapshot snapshot(
        List<TargetDemand> targets,
        @Nullable PlanningResult result,
        @Nullable PlanningResult lastFeasible,
        List<HistoryEntry> undoHistory,
        List<GuidanceRule> sessionRules
    ) {
        return new WorkbenchUiSnapshot(
            "tab-copilot",
            "Tab",
            result != null && result.status() == PlanningStatus.infeasibleByHumanRules
                    ? WorkbenchPhase.conflictInfeasible
                    : result == null ? WorkbenchPhase.idleNoTarget : WorkbenchPhase.readyPareto,
            false,
            1L,
            targets,
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            true,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            result,
            lastFeasible,
            null,
            null,
            Set.of(),
            null,
            null,
            FlowViewMode.spatialDag,
            DagLayoutMode.horizontalLr,
            Set.of(),
            Map.of(),
            undoHistory,
            List.of(),
            sessionRules,
            null
        );
    }

    static WorkbenchUiSnapshot snapshot(@Nullable PlanningResult result) {
        return snapshot(List.of(new TargetDemand(ironIngot, 8L)), result, null, List.of(), List.of());
    }

    static WorkbenchUiSnapshot compareSnapshot(PlanningResult result, String selectedPlanId, String comparePlanId) {
        WorkbenchUiSnapshot base = snapshot(result);
        return new WorkbenchUiSnapshot(
            base.tabId(),
            base.tabTitle(),
            base.phase(),
            base.planningInFlight(),
            base.runtimeGraphVersion(),
            base.targets(),
            base.profile(),
            base.budget(),
            base.useLiveStock(),
            base.reservedLedger(),
            base.progression(),
            base.result(),
            base.lastFeasibleResult(),
            selectedPlanId,
            comparePlanId,
            base.selectedStepIds(),
            base.hoveredStepId(),
            base.hoveredResource(),
            base.flowViewMode(),
            base.dagLayoutMode(),
            base.expandedLoopStepIds(),
            base.manualNodeOffsets(),
            base.undoHistory(),
            base.redoHistory(),
            base.activeSessionRules(),
            base.lastErrorMessage()
        );
    }

    static WorkbenchUiSnapshot stepSnapshot(PlanningResult result, String... stepIds) {
        WorkbenchUiSnapshot base = snapshot(result);
        return new WorkbenchUiSnapshot(
            base.tabId(),
            base.tabTitle(),
            base.phase(),
            base.planningInFlight(),
            base.runtimeGraphVersion(),
            base.targets(),
            base.profile(),
            base.budget(),
            base.useLiveStock(),
            base.reservedLedger(),
            base.progression(),
            base.result(),
            base.lastFeasibleResult(),
            base.selectedPlanId(),
            base.comparePlanId(),
            Set.of(stepIds),
            base.hoveredStepId(),
            base.hoveredResource(),
            base.flowViewMode(),
            base.dagLayoutMode(),
            base.expandedLoopStepIds(),
            base.manualNodeOffsets(),
            base.undoHistory(),
            base.redoHistory(),
            base.activeSessionRules(),
            base.lastErrorMessage()
        );
    }

    static HistoryEntry historyEntry(String entryId, long sequence) {
        return new HistoryEntry(
            entryId,
            "cirrus.ui.workbench.timeline.initial",
            List.of(),
            false,
            HistoryEntry.HistoryKind.initial,
            sequence
        );
    }

    static CompiledRecipeGraph graph() {
        FakeGraph graph = new FakeGraph();
        graph.put(
            new CompiledRecipe(
                0,
                smeltIron,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironOre)),
                List.<CatalystSlot>of(),
                List.of(new OutputSlot(ironIngot, 1)),
                new MachineRequirement(Optional.empty(), List.of(furnace), false),
                Map.of()
            )
        );
        graph.put(
            new CompiledRecipe(
                1,
                chargeCertus,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironIngot)),
                List.<CatalystSlot>of(),
                List.of(new OutputSlot(certus, 1)),
                new MachineRequirement(Optional.empty(), List.of("ae2:charger"), false),
                Map.of()
            )
        );
        graph.producible(ironOre, ironIngot, coal, certus);
        return graph;
    }

    static final class FakeSink implements CoPilotDnd.TargetSink {

        final List<TargetDemand> added = new ArrayList<>();
        final List<TargetDemand> replaced = new ArrayList<>();

        @Override
        public void addTarget(TargetDemand demand) {
            added.add(demand);
        }

        @Override
        public void replaceTarget(TargetDemand demand) {
            replaced.add(demand);
        }

        int calls() {
            return added.size() + replaced.size();
        }
    }

    static final class FakeHost implements CoPilotHost {

        WorkbenchUiSnapshot snapshot;
        CirrusPalette palette = CirrusPalette.obsidianBlueprint;
        final List<HumanFeedback> feedbacks = new ArrayList<>();
        final List<Integer> historyJumps = new ArrayList<>();
        final List<List<String>> relaxedRules = new ArrayList<>();
        final List<String> promotedRules = new ArrayList<>();
        final List<String> selectedPlans = new ArrayList<>();
        final List<String> comparedPlans = new ArrayList<>();
        final List<Set<String>> selectedSteps = new ArrayList<>();
        final List<String> focusedSteps = new ArrayList<>();
        final List<ResourceKey> alternativeRequests = new ArrayList<>();
        final List<LangEntry> toasts = new ArrayList<>();
        boolean reducedMotion;

        FakeHost(WorkbenchUiSnapshot snapshot) {
            this.snapshot = snapshot;
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
        public void refine(HumanFeedback feedback, @Nullable HistoryEntry entry) {
            feedbacks.add(feedback);
        }

        @Override
        public void jumpToHistory(int index) {
            historyJumps.add(index);
        }

        @Override
        public void relaxConflictRules(List<String> ruleIds) {
            relaxedRules.add(List.copyOf(ruleIds));
        }

        @Override
        public void promoteSessionRulesToWorld(@Nullable String singleRuleId) {
            promotedRules.add(singleRuleId == null ? "*" : singleRuleId);
        }

        @Override
        public void selectPlan(@Nullable String planId) {
            selectedPlans.add(planId == null ? "*" : planId);
        }

        @Override
        public void setComparePlan(@Nullable String planId) {
            comparedPlans.add(planId == null ? "*" : planId);
        }

        @Override
        public void setSelectedSteps(Set<String> stepIds) {
            selectedSteps.add(Set.copyOf(stepIds));
        }

        @Override
        public void focusStepInDag(String stepId) {
            focusedSteps.add(stepId);
        }

        @Override
        public void openAlternativesFor(ResourceKey resource) {
            alternativeRequests.add(resource);
        }

        @Override
        public void toast(LangEntry message) {
            toasts.add(message);
        }

        @Override
        public boolean isReducedMotion() {
            return reducedMotion;
        }

        int dispatchCount() {
            return feedbacks.size()
                    + historyJumps.size()
                    + relaxedRules.size()
                    + promotedRules.size()
                    + selectedPlans.size()
                    + comparedPlans.size()
                    + selectedSteps.size()
                    + focusedSteps.size()
                    + alternativeRequests.size()
                    + toasts.size();
        }
    }

    static final class FakeGraph implements CompiledRecipeGraph {

        private final Map<CompiledRecipeKey, CompiledRecipe> recipes = new TreeMap<>();
        private final Set<ResourceKey> producible = new TreeSet<>();

        void put(CompiledRecipe recipe) {
            recipes.put(recipe.key(), recipe);
        }

        void producible(ResourceKey... resources) {
            producible.addAll(List.of(resources));
        }

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
            List<CompiledRecipe> producers = new ArrayList<>();
            for (CompiledRecipe recipe : recipes.values()) {
                for (OutputSlot slot : recipe.outputs()) {
                    if (slot.resource().equals(output)) {
                        producers.add(recipe);
                        break;
                    }
                }
            }
            return List.copyOf(producers);
        }

        @Override
        public List<CompiledRecipe> consumersOf(ResourceKey input) {
            List<CompiledRecipe> consumers = new ArrayList<>();
            for (CompiledRecipe recipe : recipes.values()) {
                for (InputSlot slot : recipe.inputs()) {
                    if (slot.candidates().contains(input)) {
                        consumers.add(recipe);
                        break;
                    }
                }
            }
            return List.copyOf(consumers);
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
            return new GraphCompilationStats(0, recipes.size(), 0, 0, 0, 0, 0, 0);
        }
    }
}

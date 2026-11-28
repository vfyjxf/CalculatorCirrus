package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
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
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
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
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.function.Function;

final class WorkbenchStateFixtures {

    static final ComponentStackKey ironOre = OfflineContentTypes.item("minecraft:iron_ore");
    static final ComponentStackKey coal = OfflineContentTypes.item("minecraft:coal");
    static final ComponentStackKey ironIngot = OfflineContentTypes.item("minecraft:iron_ingot");
    static final ComponentStackKey ironBlock = OfflineContentTypes.item("minecraft:iron_block");
    static final ComponentStackKey ironSheet = OfflineContentTypes.item("create:iron_sheet");
    static final ComponentStackKey diamond = OfflineContentTypes.item("minecraft:diamond");
    static final ComponentStackKey cogwheel = OfflineContentTypes.item("create:cogwheel");
    static final ComponentStackKey shaft = OfflineContentTypes.item("create:shaft");
    static final ComponentStackKey piston = OfflineContentTypes.item("minecraft:piston");
    static final ComponentStackKey cobble = OfflineContentTypes.item("minecraft:cobble");
    static final ComponentStackKey paper = OfflineContentTypes.item("minecraft:paper");
    static final ComponentStackKey chest = OfflineContentTypes.item("minecraft:chest");
    static final ComponentStackKey oakLog = OfflineContentTypes.item("minecraft:oak_log");
    static final ComponentStackKey inscriber = OfflineContentTypes.item("ae2:inscriber");
    static final ComponentStackKey certus = OfflineContentTypes.item("ae2:certus_quartz_crystal");

    static final String smelting = "minecraft:smelting";
    static final String crafting = "minecraft:crafting";
    static final String pressing = "create:pressing";
    static final String ae2Charging = "ae2:charger";

    static final CompiledRecipeKey smeltIron = key(smelting, "minecraft:iron_ingot_from_smelting");
    static final CompiledRecipeKey packIronBlock = key(crafting, "minecraft:iron_block");
    static final CompiledRecipeKey unpackIronBlock = key(crafting, "minecraft:iron_ingot_from_iron_block");
    static final CompiledRecipeKey craftPiston = key(crafting, "minecraft:piston");
    static final CompiledRecipeKey craftChest = key(crafting, "minecraft:chest");
    static final CompiledRecipeKey loopCogwheel = key(crafting, "loop/cogwheel");
    static final CompiledRecipeKey loopShaft = key(crafting, "loop/shaft");
    static final CompiledRecipeKey ae2Charge = key(ae2Charging, "ae2:certus_quartz_crystal");

    private WorkbenchStateFixtures() {}

    static CompiledRecipeKey key(String systemId, String recipeId) {
        return new CompiledRecipeKey(Namespace.parse(systemId), OfflineContentTypes.tryParseLocation(recipeId), 0L);
    }

    static CostVector cost(double human, double passive) {
        return new CostVector(human, passive, 0.0, 0.0, 0.0);
    }

    static InputSlot input(int slotIndex, long amount, ComponentStackKey resource) {
        return new InputSlot(slotIndex, amount, Optional.empty(), List.of(resource), SlotMatchMode.identity);
    }

    static InputSlot taggedInput(int slotIndex, long amount, String tagId, ComponentStackKey candidate) {
        return new InputSlot(
            slotIndex,
            amount,
            Optional.<TagKey<?>>of(OfflineContentTypes.tagFor(OfflineContentTypes.itemContentType, tagId)),
            List.of(candidate),
            SlotMatchMode.identity
        );
    }

    static SlotBinding binding(ComponentStackKey resource) {
        return new SlotBinding(resource, List.of());
    }

    static CompiledRecipe recipe(
        int compactId,
        CompiledRecipeKey key,
        RecipeRole role,
        List<InputSlot> inputs,
        List<OutputSlot> outputs,
        @Nullable MachineEntry machine
    ) {
        MachineRequirement requirement = new MachineRequirement(
            Optional.empty(),
            machine == null ? List.<MachineEntry>of() : List.of(machine),
            machine == null
        );
        return new CompiledRecipe(
            compactId,
            key,
            role,
            inputs,
            List.<CatalystSlot>of(),
            outputs,
            requirement,
            Map.of()
        );
    }

    static CompiledRecipeGraph graph() {
        FakeGraph graph = new FakeGraph();
        graph.put(
            recipe(
                0,
                smeltIron,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironOre)),
                List.of(new OutputSlot(ironIngot, 1)),
                OfflineContentTypes.machine("minecraft:furnace")
            )
        );
        graph.put(
            recipe(
                1,
                packIronBlock,
                RecipeRole.reversiblePacking,
                List.of(input(0, 9, ironIngot)),
                List.of(new OutputSlot(ironBlock, 1)),
                null
            )
        );
        graph.put(
            recipe(
                2,
                unpackIronBlock,
                RecipeRole.reversiblePacking,
                List.of(input(0, 1, ironBlock)),
                List.of(new OutputSlot(ironIngot, 9)),
                null
            )
        );
        graph.put(
            recipe(
                3,
                craftPiston,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironIngot), input(1, 1, ironSheet), input(2, 1, shaft)),
                List.of(new OutputSlot(piston, 1)),
                null
            )
        );
        graph.put(
            recipe(
                4,
                craftChest,
                RecipeRole.standardProduction,
                List.of(taggedInput(0, 8, "minecraft:logs", oakLog)),
                List.of(new OutputSlot(chest, 1)),
                null
            )
        );
        graph.put(
            recipe(
                5,
                loopCogwheel,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironOre)),
                List.of(new OutputSlot(cogwheel, 2)),
                null
            )
        );
        graph.put(
            recipe(
                6,
                loopShaft,
                RecipeRole.standardProduction,
                List.of(input(0, 1, cogwheel)),
                List.of(new OutputSlot(shaft, 2)),
                null
            )
        );
        graph.put(
            recipe(
                7,
                ae2Charge,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironIngot)),
                List.of(new OutputSlot(certus, 1)),
                OfflineContentTypes.machine("ae2:charger")
            )
        );
        graph.addProducible(
            ironOre,
            coal,
            ironIngot,
            ironBlock,
            ironSheet,
            diamond,
            cogwheel,
            shaft,
            piston,
            cobble,
            paper,
            chest,
            oakLog,
            inscriber,
            certus
        );
        return graph;
    }

    static AcquireStep acquire(String stepId, ComponentStackKey resource, long amount) {
        return new AcquireStep(stepId, List.of(), resource, amount, AcquisitionKind.manualGather, cost(4.0, 0.0));
    }

    static CraftStep loopCogwheelCard() {
        return new CraftStep(
            "t1",
            List.of(),
            loopCogwheel,
            2L,
            Optional.empty(),
            false,
            Map.of(0, binding(ironOre)),
            Map.of(),
            cost(1.0, 0.0)
        );
    }

    static CraftStep loopShaftCard() {
        return new CraftStep(
            "t2",
            List.of("t1"),
            loopShaft,
            1L,
            Optional.empty(),
            false,
            Map.of(0, binding(cogwheel)),
            Map.of(),
            cost(1.0, 0.0)
        );
    }

    static List<PlanStep> sevenTypeSteps() {
        List<PlanStep> steps = new ArrayList<>();
        steps.add(acquire("s001", ironOre, 64L));
        steps.add(acquire("s002", coal, 32L));
        steps.add(
            new ProvisionMachineStep(
                "s003",
                List.of(),
                OfflineContentTypes.machine("minecraft:furnace"),
                Optional.empty(),
                2,
                false,
                cost(3.0, 0.0)
            )
        );
        steps.add(
            new CraftStep(
                "s004",
                List.of("s001"),
                smeltIron,
                2L,
                Optional.of(OfflineContentTypes.machine("minecraft:furnace")),
                false,
                Map.of(0, binding(ironOre)),
                Map.of(0, diamond),
                cost(8.0, 60.0)
            )
        );
        steps.add(
            new CraftStep(
                "s005",
                List.of("s004"),
                packIronBlock,
                1L,
                Optional.empty(),
                false,
                Map.of(0, binding(ironIngot)),
                Map.of(),
                cost(1.0, 0.0)
            )
        );
        steps.add(
            new ConvertStep("s006", List.of("s005"), unpackIronBlock, 1L, Map.of(0, binding(ironBlock)), cost(0.5, 0.0))
        );
        steps.add(
            new SetupLineStep(
                "s007",
                List.of("s003"),
                Namespace.parse(pressing),
                OfflineContentTypes.machine("create:mechanical_press"),
                ironSheet,
                cost(6.0, 0.0)
            )
        );
        steps.add(new LinePullStep("s008", List.of("s007"), "line:pressing", ironSheet, 16L, cost(0.5, 0.0)));
        steps.add(
            new CycleLoopStep(
                "s009",
                List.of("s001"),
                new CompiledCycleMacroKey("cirrus:ore_loop"),
                4L,
                Map.of(ironOre, 16L),
                List.of(loopCogwheelCard(), loopShaftCard()),
                Map.of(cogwheel, 2L, shaft, 1L),
                cost(10.0, 40.0)
            )
        );
        steps.add(
            new CraftStep(
                "s010",
                List.of("s004", "s008", "s009", "s006"),
                craftPiston,
                1L,
                Optional.empty(),
                false,
                Map.of(0, binding(ironIngot), 1, binding(ironSheet), 2, binding(shaft)),
                Map.of(),
                cost(2.0, 0.0)
            )
        );
        steps.add(acquire("s011", diamond, 8L));
        return steps;
    }

    static PreparationSummary preparation() {
        return new PreparationSummary(
            new StockLedger(Map.of(ironIngot, 8L)),
            new StockLedger(Map.of(ironOre, 64L, coal, 32L)),
            Map.of(OfflineContentTypes.machine("minecraft:furnace"), 2),
            List.of("line:pressing"),
            List.of(diamond)
        );
    }

    static ProductionPlan plan(String planId, List<PlanStep> steps, List<ByproductEntry> byproducts) {
        return new ProductionPlan(
            planId,
            "cirrus.strategy.balanced",
            steps,
            preparation(),
            byproducts,
            cost(10.0, 10.0),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    static ProductionPlan sevenTypePlan() {
        return plan(
            "plan-seven",
            sevenTypeSteps(),
            List.of(new ByproductEntry(cogwheel, 2L, "s009"), new ByproductEntry(shaft, 3L, "s009"))
        );
    }

    static ProductionPlan singleStepPlan(String planId) {
        return plan(planId, List.of(acquire("p001", ironIngot, 1L)), List.of());
    }

    static ProductionPlan taggedPlan() {
        return plan(
            "plan-tag",
            List.of(
                new CraftStep(
                    "c001",
                    List.of(),
                    craftChest,
                    1L,
                    Optional.empty(),
                    false,
                    Map.of(0, binding(oakLog)),
                    Map.of(),
                    cost(1.0, 0.0)
                )
            ),
            List.of()
        );
    }

    static ProductionPlan cyclePlan() {
        List<PlanStep> steps = List.of(
            new CycleLoopStep(
                "l001",
                List.of(),
                new CompiledCycleMacroKey("cirrus:loop"),
                4L,
                Map.of(ironOre, 16L),
                List.of(loopShaftCard()),
                Map.of(shaft, 1L),
                cost(2.0, 1.0)
            )
        );
        return plan("plan-loop", steps, List.of());
    }

    static PlanningResult result(PlanningStatus status, List<ProductionPlan> plans) {
        return result(status, plans, List.of());
    }

    static PlanningResult result(PlanningStatus status, List<ProductionPlan> plans, List<DecisionPrompt> prompts) {
        return new PlanningResult(
            status,
            plans,
            prompts,
            new PlanningDiagnostics(1L, 0, 0, 0, 0, false, Optional.empty())
        );
    }

    static DecisionOption option(String optionId, CostVector delta, List<GuidanceRule> rules) {
        return new DecisionOption(optionId, "option." + optionId, delta, rules);
    }

    static DecisionOption optionWithFollowUp(
        String optionId,
        CostVector delta,
        List<GuidanceRule> rules,
        HumanFeedback followUp
    ) {
        return new DecisionOption(optionId, "option." + optionId, delta, rules, Optional.of(followUp));
    }

    static DecisionPrompt prompt(
        String promptId,
        DecisionPromptKind kind,
        double leverageScore,
        DecisionOption... options
    ) {
        return new DecisionPrompt(promptId, kind, "prompt.title", "prompt.explain", leverageScore, List.of(options));
    }

    static TargetDemand demand(ComponentStackKey resource, long amount) {
        return new TargetDemand(resource, amount);
    }

    static WorkbenchUiSnapshot snapshot(List<TargetDemand> targets, @Nullable PlanningResult result) {
        return snapshot(targets, result, null);
    }

    static WorkbenchUiSnapshot snapshot(
        List<TargetDemand> targets,
        @Nullable PlanningResult result,
        @Nullable String selectedPlanId
    ) {
        return new WorkbenchUiSnapshot(
            "tab-snap",
            "Tab",
            result == null ? WorkbenchPhase.idleNoTarget : WorkbenchPhase.readyPareto,
            false,
            1L,
            targets,
            PlanningProfile.defaultBalanced(),
            PlanningBudget.defaultInteractive(),
            true,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            result,
            null,
            selectedPlanId,
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

    static SessionTabState state(String tabId, long graphVersion, List<TargetDemand> targets) {
        SessionTabState state = new SessionTabState(tabId);
        state.setRuntimeGraphVersion(graphVersion);
        state.setTargets(targets, null);
        return state;
    }

    static PlanningRequestSource requestSource() {
        AcquisitionOracle oracle = (key, missing, progression) -> Optional.empty();
        PlanningContext context = new PlanningContext(
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            oracle,
            DefaultHumanGuidanceView.empty(),
            ProgressionState.overworldDefault
        );
        return spec -> new PlanningRequest(spec.targets(), context, spec.profile(), spec.budget());
    }

    static Executor sync() {
        return Runnable::run;
    }

    static final class QueuedExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pending() {
            return tasks.size();
        }

        void runAll() {
            List<Runnable> queued = new ArrayList<>(tasks);
            tasks.clear();
            for (Runnable task : queued) {
                task.run();
            }
        }

        void runAllReversed() {
            List<Runnable> queued = new ArrayList<>(tasks);
            tasks.clear();
            for (int i = queued.size() - 1; i >= 0; i--) {
                queued.get(i).run();
            }
        }
    }

    static final class FakeBackend implements PlanningBackend {

        private final Function<PlanningRequest, PlanningResult> planFactory;
        final List<FakeSession> sessions = new ArrayList<>();
        final List<PlanningRequest> requests = new ArrayList<>();
        @Nullable
        PlanningResult refineResult;
        @Nullable
        PlanningResult undoResult;
        @Nullable
        PlanningResult redoResult;

        FakeBackend(Function<PlanningRequest, PlanningResult> planFactory) {
            this.planFactory = planFactory;
        }

        @Override
        public PlanningSession openSession(PlanningRequest request) {
            requests.add(request);
            FakeSession session = new FakeSession(sessions.size(), planFactory.apply(request), this);
            sessions.add(session);
            return session;
        }

        int openCount() {
            return sessions.size();
        }

        FakeSession last() {
            return sessions.get(sessions.size() - 1);
        }

        List<PlanningRequest> requests() {
            return List.copyOf(requests);
        }
    }

    static final class FakeSession implements PlanningSession {

        final int index;
        final PlanningResult initialResult;
        final String planId;
        final FakeBackend backend;
        int closeCount;
        int refineCount;
        int undoCount;
        int redoCount;
        private PlanningResult current;

        FakeSession(int index, PlanningResult initialResult, FakeBackend backend) {
            this.index = index;
            this.initialResult = initialResult;
            this.current = initialResult;
            this.planId = initialResult.paretoPlans().isEmpty() ? "" : initialResult.paretoPlans().get(0).planId();
            this.backend = backend;
        }

        @Override
        public PlanningResult currentResult() {
            return current;
        }

        @Override
        public PlanningResult refine(HumanFeedback feedback) {
            refineCount++;
            if (backend.refineResult != null) {
                current = backend.refineResult;
            }
            return current;
        }

        @Override
        public PlanningResult undo() {
            undoCount++;
            if (backend.undoResult != null) {
                current = backend.undoResult;
            }
            return current;
        }

        @Override
        public PlanningResult redo() {
            redoCount++;
            if (backend.redoResult != null) {
                current = backend.redoResult;
            }
            return current;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    static final class FakeGraph implements CompiledRecipeGraph {

        private final Map<CompiledRecipeKey, CompiledRecipe> recipes = new TreeMap<>();
        private final Set<ComponentStackKey> producible = new TreeSet<>();

        void put(CompiledRecipe recipe) {
            recipes.put(recipe.key(), recipe);
        }

        void addProducible(ComponentStackKey... resources) {
            producible.addAll(List.of(resources));
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
            return List.copyOf(producers);
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
            return List.copyOf(consumers);
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
            return Set.copyOf(producible);
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

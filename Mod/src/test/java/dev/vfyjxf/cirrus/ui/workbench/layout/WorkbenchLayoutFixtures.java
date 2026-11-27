package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
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
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

final class WorkbenchLayoutFixtures {

    static final ResourceKey ironOre = ResourceKey.item("minecraft:iron_ore");
    static final ResourceKey coal = ResourceKey.item("minecraft:coal");
    static final ResourceKey ironIngot = ResourceKey.item("minecraft:iron_ingot");
    static final ResourceKey ironBlock = ResourceKey.item("minecraft:iron_block");
    static final ResourceKey ironSheet = ResourceKey.item("create:iron_sheet");
    static final ResourceKey diamond = ResourceKey.item("minecraft:diamond");
    static final ResourceKey cogwheel = ResourceKey.item("create:cogwheel");
    static final ResourceKey shaft = ResourceKey.item("create:shaft");
    static final ResourceKey piston = ResourceKey.item("minecraft:piston");

    static final String smelting = "minecraft:smelting";
    static final String crafting = "minecraft:crafting";
    static final String pressing = "create:pressing";

    static final CompiledRecipeKey smeltIron = key(smelting, "minecraft:iron_ingot_from_smelting");
    static final CompiledRecipeKey packIronBlock = key(crafting, "minecraft:iron_block");
    static final CompiledRecipeKey unpackIronBlock = key(crafting, "minecraft:iron_ingot_from_iron_block");
    static final CompiledRecipeKey craftPiston = key(crafting, "minecraft:piston");
    static final CompiledRecipeKey loopCogwheel = key(crafting, "loop/cogwheel");
    static final CompiledRecipeKey loopShaft = key(crafting, "loop/shaft");

    private WorkbenchLayoutFixtures() {}

    static CompiledRecipeKey key(String systemId, String recipeId) {
        return new CompiledRecipeKey(systemId, Optional.of(recipeId), 0L);
    }

    static CostVector cost(double human, double passive) {
        return new CostVector(human, passive, 0.0, 0.0, 0.0);
    }

    static CompiledRecipeGraph graph() {
        FakeRecipeGraph graph = new FakeRecipeGraph();
        graph.put(
            recipe(
                0,
                smeltIron,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironOre)),
                List.of(new OutputSlot(ironIngot, 1)),
                "minecraft:furnace"
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
                loopCogwheel,
                RecipeRole.standardProduction,
                List.of(input(0, 1, ironOre)),
                List.of(new OutputSlot(cogwheel, 2)),
                null
            )
        );
        graph.put(
            recipe(
                5,
                loopShaft,
                RecipeRole.standardProduction,
                List.of(input(0, 1, cogwheel)),
                List.of(new OutputSlot(shaft, 2)),
                null
            )
        );
        return graph;
    }

    private static CompiledRecipe recipe(
        int compactId,
        CompiledRecipeKey key,
        RecipeRole role,
        List<InputSlot> inputs,
        List<OutputSlot> outputs,
        @Nullable String machineId
    ) {
        MachineRequirement requirement = new MachineRequirement(
            Optional.empty(),
            machineId == null ? List.of() : List.of(machineId),
            machineId == null
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

    static InputSlot input(int slotIndex, long amountPerBatch, ResourceKey resource) {
        return new InputSlot(slotIndex, amountPerBatch, Optional.empty(), List.of(resource), SlotMatchMode.identity);
    }

    static SlotBinding binding(ResourceKey resource) {
        return new SlotBinding(resource, List.of());
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
        steps.add(new AcquireStep("s001", List.of(), ironOre, 64L, AcquisitionKind.manualGather, cost(4.0, 0.0)));
        steps.add(new AcquireStep("s002", List.of(), coal, 32L, AcquisitionKind.manualGather, cost(2.0, 0.0)));
        steps.add(
            new ProvisionMachineStep("s003", List.of(), "minecraft:furnace", Optional.empty(), 2, false, cost(3.0, 0.0))
        );
        steps.add(
            new CraftStep(
                "s004",
                List.of("s001"),
                smeltIron,
                2L,
                Optional.of("minecraft:furnace"),
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
            new SetupLineStep("s007", List.of("s003"), pressing, "create:mechanical_press", ironSheet, cost(6.0, 0.0))
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
        steps.add(new AcquireStep("s011", List.of(), diamond, 8L, AcquisitionKind.baseSupply, cost(0.2, 0.0)));
        return steps;
    }

    static PreparationSummary preparation() {
        return new PreparationSummary(
            new StockLedger(Map.of(ironIngot, 8L)),
            new StockLedger(Map.of(ironOre, 64L, coal, 32L)),
            Map.of("minecraft:furnace", 2),
            List.of("line:pressing"),
            List.of(diamond)
        );
    }

    static ProductionPlan sevenTypePlan() {
        return plan(
            "plan-seven",
            sevenTypeSteps(),
            preparation(),
            List.of(new ByproductEntry(cogwheel, 2L, "s009"), new ByproductEntry(shaft, 3L, "s009"))
        );
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

    static InventorySnapshot inventory() {
        return new InventorySnapshot(
            new StockLedger(Map.of(ironOre, 16L, ironIngot, 40L)),
            new StockLedger(Map.of(ironOre, 4L))
        );
    }

    static ProductionPlan diamondPlan() {
        List<PlanStep> steps = List.of(
            simpleCraft("d1", List.of(), cost(10.0, 0.0), Optional.of("minecraft:crafting_table")),
            simpleCraft("d2", List.of("d1"), cost(4.0, 6.0), Optional.of("minecraft:furnace")),
            simpleCraft("d3", List.of("d1"), cost(2.0, 0.0), Optional.empty()),
            simpleCraft("d4", List.of("d2", "d3"), cost(1.0, 0.0), Optional.empty()),
            simpleCraft("d5", List.of("d4"), cost(0.0, 0.0), Optional.empty())
        );
        return plan("plan-diamond", steps, PreparationSummary.empty, List.of());
    }

    static ProductionPlan heavyBottleneckPlan() {
        List<PlanStep> steps = List.of(simpleCraft("h1", List.of(), cost(0.5, 25.0), Optional.of("minecraft:furnace")));
        return plan("plan-heavy", steps, PreparationSummary.empty, List.of());
    }

    static CraftStep simpleCraft(String stepId, List<String> deps, CostVector cost, Optional<String> machineId) {
        return new CraftStep(stepId, deps, craftPiston, 1L, machineId, false, Map.of(), Map.of(), cost);
    }

    static final class FakeRecipeGraph implements CompiledRecipeGraph {

        private final Map<CompiledRecipeKey, CompiledRecipe> recipes = new TreeMap<>();

        void put(CompiledRecipe recipe) {
            recipes.put(recipe.key(), recipe);
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
            return Set.of();
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

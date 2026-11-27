package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanDagLayoutStressTest {

    private static final int stepCount = 80;
    private static final long seed = 42L;

    private static final ResourceKey[] pool = {WorkbenchLayoutFixtures.ironOre, WorkbenchLayoutFixtures.coal,
            WorkbenchLayoutFixtures.ironIngot, WorkbenchLayoutFixtures.ironBlock, WorkbenchLayoutFixtures.ironSheet,
            WorkbenchLayoutFixtures.diamond, WorkbenchLayoutFixtures.cogwheel, WorkbenchLayoutFixtures.shaft,
            WorkbenchLayoutFixtures.piston};

    private static final String[] systems = {WorkbenchLayoutFixtures.smelting, WorkbenchLayoutFixtures.crafting,
            WorkbenchLayoutFixtures.pressing, "create:mixing", "minecraft:blasting"};

    @Test
    void largePlanKeepsEveryModeOverlapFreeAndDeterministic() {
        ProductionPlan plan = largePlan();
        CompiledRecipeGraph graph = WorkbenchLayoutFixtures.graph();
        Set<String> expanded = Set.of("s007", "s042", "s077");
        for (DagLayoutMode mode : List
                .of(DagLayoutMode.horizontalLr, DagLayoutMode.verticalTb, DagLayoutMode.systemSwimlane)) {
            PlanDagLayoutResult first = PlanDagLayout.compute(plan, null, graph, mode, expanded, Map.of(), Set.of());
            PlanDagLayoutResult second = PlanDagLayout.compute(plan, null, graph, mode, expanded, Map.of(), Set.of());
            assertEquals(first, second, mode + " must be deterministic");
            assertEquals(stepCount, first.nodes().size(), mode + " must lay out every step");
            assertNoOverlap(first, mode);
            assertLayersClimb(first, mode);
        }
    }

    @Test
    void largePlanExpandedLoopsStayInsideTheirCompoundBox() {
        ProductionPlan plan = largePlan();
        CompiledRecipeGraph graph = WorkbenchLayoutFixtures.graph();
        Set<String> expanded = Set.of("s007", "s042", "s077");
        PlanDagLayoutResult result = PlanDagLayout
                .compute(plan, null, graph, DagLayoutMode.systemSwimlane, expanded, Map.of(), Set.of());
        int expandedLoops = 0;
        for (DagNodeBox node : result.nodes()) {
            if (!node.expandedLoop()) {
                continue;
            }
            expandedLoops++;
            assertTrue(node.innerLoopNodes().size() >= 2, "expanded loop must expose its inner cards");
            for (DagNodeBox inner : node.innerLoopNodes()) {
                assertTrue(inner.x() >= node.x(), "inner card escapes the left edge");
                assertTrue(inner.maxX() <= node.maxX(), "inner card escapes the right edge");
                assertTrue(inner.y() >= node.y(), "inner card escapes the top edge");
                assertTrue(inner.maxY() <= node.maxY(), "inner card escapes the bottom edge");
            }
        }
        assertEquals(3, expandedLoops, "every requested loop must expand");
    }

    private static void assertNoOverlap(PlanDagLayoutResult result, DagLayoutMode mode) {
        List<DagNodeBox> nodes = result.nodes();
        for (int left = 0; left < nodes.size(); left++) {
            for (int right = left + 1; right < nodes.size(); right++) {
                DagNodeBox a = nodes.get(left);
                DagNodeBox b = nodes.get(right);
                double width = Math.min(a.maxX(), b.maxX()) - Math.max(a.x(), b.x());
                double height = Math.min(a.maxY(), b.maxY()) - Math.max(a.y(), b.y());
                double area = width > 0.0 && height > 0.0 ? width * height : 0.0;
                assertEquals(0.0, area, 1e-9, mode + ": " + a.stepId() + " overlaps " + b.stepId() + " by " + area);
            }
        }
    }

    private static void assertLayersClimb(PlanDagLayoutResult result, DagLayoutMode mode) {
        Map<String, DagNodeBox> byId = new HashMap<>();
        for (DagNodeBox node : result.nodes()) {
            byId.put(node.stepId(), node);
        }
        for (DagEdgeRoute edge : result.edges()) {
            if (edge.kind() == DagEdgeRoute.EdgeKind.catalyst || edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
                continue;
            }
            DagNodeBox from = byId.get(edge.fromStepId());
            DagNodeBox to = byId.get(edge.toStepId());
            assertNotNull(from);
            assertNotNull(to);
            assertTrue(
                to.layer() > from.layer(),
                mode + ": " + edge.fromStepId() + " -> " + edge.toStepId() + " does not climb a layer"
            );
        }
    }

    private static ProductionPlan largePlan() {
        Random random = new Random(seed);
        List<PlanStep> steps = new ArrayList<>(stepCount);
        for (int index = 1; index <= stepCount; index++) {
            String stepId = String.format("s%03d", index);
            List<String> deps = new ArrayList<>();
            if (index > 1) {
                int depCount = 1 + random.nextInt(2);
                for (int dep = 0; dep < depCount; dep++) {
                    String candidate = String.format("s%03d", 1 + random.nextInt(index - 1));
                    if (!deps.contains(candidate)) {
                        deps.add(candidate);
                    }
                }
                deps.sort(String::compareTo);
            }
            steps.add(stepFor(index, stepId, List.copyOf(deps), random));
        }
        return WorkbenchLayoutFixtures.plan("plan-large", steps, PreparationSummary.empty, List.of());
    }

    private static PlanStep stepFor(int index, String stepId, List<String> deps, Random random) {
        ResourceKey resource = pool[random.nextInt(pool.length)];
        String system = systems[random.nextInt(systems.length)];
        CostVector cost = WorkbenchLayoutFixtures.cost(random.nextInt(12) * 0.5, random.nextInt(30) * 2.0);
        return switch (index % 7) {
            case 1 ->
                new AcquireStep(stepId, deps, resource, 8L + random.nextInt(64), AcquisitionKind.manualGather, cost);
            case 2 -> new CraftStep(
                stepId,
                deps,
                WorkbenchLayoutFixtures.smeltIron,
                1L + random.nextInt(4),
                Optional.of("minecraft:furnace"),
                false,
                Map.of(0, WorkbenchLayoutFixtures.binding(resource)),
                Map.of(),
                cost
            );
            case 3 -> new ConvertStep(
                stepId,
                deps,
                WorkbenchLayoutFixtures.packIronBlock,
                1L,
                Map.of(0, WorkbenchLayoutFixtures.binding(resource)),
                cost
            );
            case 4 -> new ProvisionMachineStep(
                stepId,
                deps,
                "minecraft:furnace",
                Optional.empty(),
                1 + random.nextInt(3),
                false,
                cost
            );
            case 5 -> new SetupLineStep(stepId, deps, system, "create:mechanical_press", resource, cost);
            case 6 -> new LinePullStep(stepId, deps, "line:" + system, resource, 4L + random.nextInt(16), cost);
            default -> new CycleLoopStep(
                stepId,
                deps,
                new CompiledCycleMacroKey("cirrus:loop"),
                2L + random.nextInt(4),
                Map.of(resource, 8L),
                List.of(loopCard("t1", resource), loopCard("t2", resource)),
                Map.of(resource, 2L),
                cost
            );
        };
    }

    private static CraftStep loopCard(String stepId, ResourceKey resource) {
        CompiledRecipeKey key = "t1".equals(stepId)
                ? WorkbenchLayoutFixtures.loopCogwheel
                : WorkbenchLayoutFixtures.loopShaft;
        return new CraftStep(
            stepId,
            List.of(),
            key,
            1L,
            Optional.empty(),
            false,
            Map.of(0, WorkbenchLayoutFixtures.binding(resource)),
            Map.of(),
            WorkbenchLayoutFixtures.cost(1.0, 0.0)
        );
    }
}

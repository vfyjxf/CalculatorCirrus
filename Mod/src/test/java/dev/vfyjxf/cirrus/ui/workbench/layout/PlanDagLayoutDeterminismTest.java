package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanDagLayoutDeterminismTest {

    private static final int runs = 100;

    private static final ProductionPlan plan = WorkbenchLayoutFixtures.sevenTypePlan();
    private static final CompiledRecipeGraph graph = WorkbenchLayoutFixtures.graph();

    private static PlanDagLayoutResult layout(
        DagLayoutMode mode,
        Set<String> expanded,
        Map<String, FloatPos> offsets,
        Set<String> critical
    ) {
        return PlanDagLayout.compute(plan, null, graph, mode, expanded, offsets, critical);
    }

    @Test
    void horizontalLayoutIsDeterministic() {
        PlanDagLayoutResult reference = layout(DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of());
        for (int run = 0; run < runs; run++) {
            assertEquals(
                reference,
                layout(DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of()),
                "HORIZONTAL_LR diverged on run " + run
            );
        }
    }

    @Test
    void verticalLayoutIsDeterministic() {
        PlanDagLayoutResult reference = layout(DagLayoutMode.verticalTb, Set.of(), Map.of(), Set.of());
        for (int run = 0; run < runs; run++) {
            assertEquals(
                reference,
                layout(DagLayoutMode.verticalTb, Set.of(), Map.of(), Set.of()),
                "VERTICAL_TB diverged on run " + run
            );
        }
    }

    @Test
    void expandedLoopLayoutIsDeterministic() {
        Set<String> expanded = Set.of("s009");
        PlanDagLayoutResult reference = layout(DagLayoutMode.horizontalLr, expanded, Map.of(), Set.of());
        for (int run = 0; run < runs; run++) {
            assertEquals(
                reference,
                layout(DagLayoutMode.horizontalLr, expanded, Map.of(), Set.of()),
                "expanded loop layout diverged on run " + run
            );
        }
    }

    @Test
    void edgeControlPointsAreDeterministicAndRounded() {
        PlanDagLayoutResult reference = layout(DagLayoutMode.horizontalLr, Set.of("s009"), Map.of(), Set.of());
        for (int run = 0; run < runs; run++) {
            PlanDagLayoutResult candidate = layout(DagLayoutMode.horizontalLr, Set.of("s009"), Map.of(), Set.of());
            assertEquals(reference.edges().size(), candidate.edges().size());
            for (int index = 0; index < reference.edges().size(); index++) {
                DagEdgeRoute expected = reference.edges().get(index);
                DagEdgeRoute actual = candidate.edges().get(index);
                assertEquals(expected.fromStepId(), actual.fromStepId());
                assertEquals(expected.toStepId(), actual.toStepId());
                assertEquals(expected.x0(), actual.x0(), 0.0, "x0 drifted on run " + run);
                assertEquals(expected.y0(), actual.y0(), 0.0, "y0 drifted on run " + run);
                assertEquals(expected.cx0(), actual.cx0(), 0.0, "cx0 drifted on run " + run);
                assertEquals(expected.cy0(), actual.cy0(), 0.0, "cy0 drifted on run " + run);
                assertEquals(expected.cx1(), actual.cx1(), 0.0, "cx1 drifted on run " + run);
                assertEquals(expected.cy1(), actual.cy1(), 0.0, "cy1 drifted on run " + run);
                assertEquals(expected.x1(), actual.x1(), 0.0, "x1 drifted on run " + run);
                assertEquals(expected.y1(), actual.y1(), 0.0, "y1 drifted on run " + run);
            }
        }
        for (DagEdgeRoute edge : reference.edges()) {
            for (float value : new float[]{edge.x0(), edge.y0(), edge.cx0(), edge.cy0(), edge.cx1(), edge.cy1(),
                    edge.x1(), edge.y1()}) {
                assertEquals(
                    value,
                    Math.round(value * 100.0) / 100.0,
                    0.0001,
                    "edge coordinate must be rounded to 0.01"
                );
            }
        }
    }

    @Test
    void horizontalNodesNeverOverlap() {
        assertNoOverlap(layout(DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of()));
        assertNoOverlap(layout(DagLayoutMode.horizontalLr, Set.of("s009"), Map.of(), Set.of()));
    }

    @Test
    void allFiveLayoutDirectionsNeverOverlap() {
        for (DagLayoutMode mode : DagLayoutMode.values()) {
            assertNoOverlap(layout(mode, Set.of(), Map.of(), Set.of()));
            assertNoOverlap(layout(mode, Set.of("s009"), Map.of(), Set.of()));
        }
    }

    @Test
    void everyDependencyEdgeClimbsOneOrMoreLayers() {
        for (DagLayoutMode mode : DagLayoutMode.values()) {
            PlanDagLayoutResult result = layout(mode, Set.of(), Map.of(), Set.of());
            Map<String, DagNodeBox> nodes = index(result.nodes());
            int dependencyEdges = 0;
            for (DagEdgeRoute edge : result.edges()) {
                if (edge.kind() == DagEdgeRoute.EdgeKind.catalyst || edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
                    continue;
                }
                DagNodeBox from = nodes.get(edge.fromStepId());
                DagNodeBox to = nodes.get(edge.toStepId());
                assertNotNull(from, mode + " missing source node " + edge.fromStepId());
                assertNotNull(to, mode + " missing target node " + edge.toStepId());
                assertTrue(
                    to.layer() > from.layer(),
                    mode + " layer not monotonic for " + edge.fromStepId() + " -> " + edge.toStepId()
                );
                dependencyEdges++;
            }
        }
    }

    @Test
    void nodeSizesMatchBlueprintSpec() {
        PlanDagLayoutResult result = layout(DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of());
        Map<String, DagNodeBox> nodes = index(result.nodes());
        assertSize(nodes, "s001", 172.0f, 58.0f);
        assertSize(nodes, "s002", 172.0f, 58.0f);
        assertSize(nodes, "s003", 188.0f, 70.0f);
        assertSize(nodes, "s004", 208.0f, 86.0f);
        assertSize(nodes, "s005", 208.0f, 86.0f);
        assertSize(nodes, "s006", 184.0f, 72.0f);
        assertSize(nodes, "s007", 192.0f, 70.0f);
        assertSize(nodes, "s008", 172.0f, 58.0f);
        assertSize(nodes, "s009", 204.0f, 84.0f);
        assertSize(nodes, "s010", 208.0f, 86.0f);
        assertSize(nodes, "s011", 172.0f, 58.0f);
        assertEquals(7, stepTypes(nodes).size(), "fixture must cover all seven PlanStep kinds");
    }

    @Test
    void expandedCycleLoopUsesTemplateSize() {
        PlanDagLayoutResult result = layout(DagLayoutMode.horizontalLr, Set.of("s009"), Map.of(), Set.of());
        DagNodeBox loop = Objects.requireNonNull(index(result.nodes()).get("s009"));
        assertNotNull(loop);
        assertTrue(loop.expandedLoop(), "s009 must be expanded");
        assertEquals(2, loop.innerLoopNodes().size(), "expanded loop must expose its two inner cards");
        assertEquals(288.0f, loop.width(), 0.001f, "12 + 128 + 8 + 128 + 12");
        assertEquals(90.0f, loop.height(), 0.001f, "26 top bar + 12 + 40 + 12");
    }

    @Test
    void manualOffsetsShiftOnlyTheTargetNode() {
        Map<String, FloatPos> offsets = Map.of("s005", new FloatPos(24.0, -12.0));
        PlanDagLayoutResult auto = layout(DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of());
        PlanDagLayoutResult shifted = layout(DagLayoutMode.horizontalLr, Set.of(), offsets, Set.of());
        Map<String, DagNodeBox> autoNodes = index(auto.nodes());
        Map<String, DagNodeBox> shiftedNodes = index(shifted.nodes());
        assertEquals(
            Objects.requireNonNull(autoNodes.get("s005")).x() + 24.0f,
            Objects.requireNonNull(shiftedNodes.get("s005")).x(),
            0.001f
        );
        assertEquals(
            Objects.requireNonNull(autoNodes.get("s005")).y() - 12.0f,
            Objects.requireNonNull(shiftedNodes.get("s005")).y(),
            0.001f
        );
        assertEquals(
            Objects.requireNonNull(autoNodes.get("s004")).x(),
            Objects.requireNonNull(shiftedNodes.get("s004")).x(),
            0.001f
        );
        assertEquals(
            Objects.requireNonNull(autoNodes.get("s004")).y(),
            Objects.requireNonNull(shiftedNodes.get("s004")).y(),
            0.001f
        );
    }

    @Test
    void criticalFlagsFollowTheProvidedStepSet() {
        PlanDagLayoutResult result = layout(DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of("s001", "s004"));
        int criticalEdges = 0;
        for (DagEdgeRoute edge : result.edges()) {
            if (edge.criticalPath()) {
                criticalEdges++;
            }
        }
        assertEquals(1, criticalEdges, "only the s001 -> s004 dependency connects two critical steps");
    }

    @Test
    void edgeKindsCoverEveryDependencySemantic() {
        PlanDagLayoutResult result = layout(DagLayoutMode.horizontalLr, Set.of("s009"), Map.of(), Set.of());
        Map<String, DagEdgeRoute> edges = new HashMap<>();
        for (DagEdgeRoute edge : result.edges()) {
            edges.put(edge.fromStepId() + "->" + edge.toStepId() + ":" + edge.kind(), edge);
        }
        assertTrue(edges.containsKey("s003->s007:machine"), "provision step must emit a machine edge");
        assertTrue(edges.containsKey("s008->s010:autoLine"), "line pull must emit an autoLine edge");
        assertTrue(edges.containsKey("s011->s004:catalyst"), "bound catalyst must emit a catalyst edge");
        DagEdgeRoute material = edges.get("s001->s004:material");
        assertNotNull(material, "iron ore dependency must emit a material edge");
        assertEquals(WorkbenchLayoutFixtures.ironOre, material.resource());
        assertEquals(64L, material.sourceTotalProduced());
        assertEquals(2L, material.amountFlow(), "2 batches of smelting consume 2 iron ore");
    }

    @Test
    void contentBoundsCoverEveryNodeAndAreRounded() {
        PlanDagLayoutResult result = layout(DagLayoutMode.horizontalLr, Set.of("s009"), Map.of(), Set.of());
        for (DagNodeBox node : result.nodes()) {
            assertTrue(node.x() >= result.contentMinX(), "node " + node.stepId() + " escapes content box");
            assertTrue(node.y() >= result.contentMinY(), "node " + node.stepId() + " escapes content box");
            assertTrue(node.maxX() <= result.contentMaxX(), "node " + node.stepId() + " escapes content box");
            assertTrue(node.maxY() <= result.contentMaxY(), "node " + node.stepId() + " escapes content box");
            assertEquals(node.x(), Math.round(node.x() * 100.0) / 100.0, 0.0001, "x must be rounded to 0.01");
            assertEquals(node.y(), Math.round(node.y() * 100.0) / 100.0, 0.0001, "y must be rounded to 0.01");
        }
        DagNodeBox loop = Objects.requireNonNull(index(result.nodes()).get("s009"));
        for (DagNodeBox inner : loop.innerLoopNodes()) {
            assertTrue(inner.maxX() <= loop.maxX(), "expanded compound box must contain its inner cards");
            assertTrue(inner.maxY() <= loop.maxY(), "expanded compound box must contain its inner cards");
        }
    }

    @Test
    void comparePlanProducesGhostNodes() {
        ProductionPlan narrowed = WorkbenchLayoutFixtures.plan(
            "plan-narrowed",
            WorkbenchLayoutFixtures.sevenTypeSteps().subList(0, 9),
            WorkbenchLayoutFixtures.preparation(),
            List.of()
        );
        PlanDagLayoutResult result = PlanDagLayout
                .compute(narrowed, plan, graph, DagLayoutMode.horizontalLr, Set.of(), Map.of(), Set.of());
        Map<String, DagNodeBox> nodes = index(result.nodes());
        assertEquals(DagNodeBox.DiffStatus.kept, Objects.requireNonNull(nodes.get("s001")).diffStatus());
        DagNodeBox ghost = nodes.get("s010");
        assertNotNull(ghost, "compare-only step must surface as a ghost node");
        assertEquals(DagNodeBox.DiffStatus.removed, ghost.diffStatus());
        assertEquals(11, result.nodes().size(), "10 current steps plus one ghost");
    }

    private static void assertSize(Map<String, DagNodeBox> nodes, String stepId, float width, float height) {
        DagNodeBox node = nodes.get(stepId);
        assertNotNull(node, "missing node " + stepId);
        assertEquals(width, node.width(), 0.001f, stepId + " width");
        assertEquals(height, node.height(), 0.001f, stepId + " height");
    }

    private static Map<String, DagNodeBox> index(List<DagNodeBox> nodes) {
        Map<String, DagNodeBox> byId = new HashMap<>();
        for (DagNodeBox node : nodes) {
            byId.put(node.stepId(), node);
        }
        return byId;
    }

    private static Set<Class<?>> stepTypes(Map<String, DagNodeBox> nodes) {
        Set<Class<?>> types = new HashSet<>();
        for (DagNodeBox node : nodes.values()) {
            PlanStep step = node.step();
            if (step instanceof AcquireStep
                    || step instanceof CraftStep
                    || step instanceof ConvertStep
                    || step instanceof CycleLoopStep
                    || step instanceof ProvisionMachineStep
                    || step instanceof SetupLineStep
                    || step instanceof LinePullStep) {
                types.add(step.getClass());
            }
        }
        return types;
    }

    static void assertNoOverlap(PlanDagLayoutResult result) {
        List<DagNodeBox> nodes = result.nodes();
        for (int left = 0; left < nodes.size(); left++) {
            for (int right = left + 1; right < nodes.size(); right++) {
                DagNodeBox a = nodes.get(left);
                DagNodeBox b = nodes.get(right);
                double width = Math.min(a.maxX(), b.maxX()) - Math.max(a.x(), b.x());
                double height = Math.min(a.maxY(), b.maxY()) - Math.max(a.y(), b.y());
                double area = width > 0.0 && height > 0.0 ? width * height : 0.0;
                assertEquals(0.0, area, 1e-9, "nodes " + a.stepId() + " and " + b.stepId() + " overlap by " + area);
            }
        }
    }
}

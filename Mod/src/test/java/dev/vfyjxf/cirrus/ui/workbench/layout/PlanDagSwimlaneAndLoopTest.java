package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanDagSwimlaneAndLoopTest {

    private static final ProductionPlan plan = WorkbenchLayoutFixtures.sevenTypePlan();
    private static final CompiledRecipeGraph graph = WorkbenchLayoutFixtures.graph();
    private static final Set<String> expanded = Set.of("s009");

    private static PlanDagLayoutResult swimlane() {
        return PlanDagLayout.compute(plan, null, graph, DagLayoutMode.systemSwimlane, expanded, Map.of(), Set.of());
    }

    @Test
    void swimlanesGroupStepsBySystem() {
        PlanDagLayoutResult result = swimlane();
        List<SwimlaneBand> bands = result.swimlanes();
        assertEquals(4, bands.size(), "acquire, machines plus two system lanes");

        assertEquals(PlanDagLayout.laneIdAcquire, bands.get(0).laneId());
        assertEquals(PlanDagLayout.laneTitleKeyAcquire, bands.get(0).titleKey());
        assertEquals(4, bands.get(0).stepCount(), "s001, s002, s011 and the line pull s008");

        assertEquals(PlanDagLayout.laneIdMachines, bands.get(1).laneId());
        assertEquals(PlanDagLayout.laneTitleKeyMachines, bands.get(1).titleKey());
        assertEquals(2, bands.get(1).stepCount(), "provision s003 and setup s007");

        assertEquals(WorkbenchLayoutFixtures.smelting, bands.get(2).laneId());
        assertEquals(WorkbenchLayoutFixtures.smelting, bands.get(2).titleKey());
        assertEquals(1, bands.get(2).stepCount());

        assertEquals(WorkbenchLayoutFixtures.crafting, bands.get(3).laneId());
        assertEquals(WorkbenchLayoutFixtures.crafting, bands.get(3).titleKey());
        assertEquals(4, bands.get(3).stepCount(), "s005, s006, the cycle loop s009 and s010");

        double expectedSmeltingCost = 8.0;
        double expectedCraftingCost = 1.0 + 0.5 + 10.0 + 2.0;
        assertEquals(expectedSmeltingCost, bands.get(2).totalH(), 1e-9);
        assertEquals(60.0, bands.get(2).totalP(), 1e-9);
        assertEquals(expectedCraftingCost, bands.get(3).totalH(), 1e-9);
        assertEquals(40.0, bands.get(3).totalP(), 1e-9);
    }

    @Test
    void lanesStackWithoutOverlapAndCarryTheirNodes() {
        PlanDagLayoutResult result = swimlane();
        List<SwimlaneBand> bands = result.swimlanes();
        for (int index = 0; index + 1 < bands.size(); index++) {
            assertTrue(
                bands.get(index).maxY() <= bands.get(index + 1).y(),
                "lane " + bands.get(index).laneId() + " must end before the next lane starts"
            );
        }
        Map<Integer, SwimlaneBand> byLane = new HashMap<>();
        for (int laneIndex = 0; laneIndex < bands.size(); laneIndex++) {
            byLane.put(laneIndex, bands.get(laneIndex));
        }
        for (DagNodeBox node : result.nodes()) {
            SwimlaneBand band = byLane.get(node.laneIndex());
            assertNotNull(band, "node " + node.stepId() + " has no swimlane");
            assertEquals(band.laneId(), node.swimlaneId(), "node " + node.stepId() + " swimlaneId mismatch");
            assertTrue(node.y() >= band.y(), "node " + node.stepId() + " escapes its band on top");
            assertTrue(node.maxY() <= band.maxY(), "node " + node.stepId() + " escapes its band at the bottom");
        }
    }

    @Test
    void expandedCycleLoopNestsItsInnerCards() {
        PlanDagLayoutResult result = swimlane();
        DagNodeBox loop = Objects.requireNonNull(index(result.nodes()).get("s009"));
        assertNotNull(loop);
        assertTrue(loop.expandedLoop(), "s009 must be expanded");
        assertEquals(2, loop.innerLoopNodes().size());

        List<DagNodeBox> inner = loop.innerLoopNodes();
        for (DagNodeBox card : inner) {
            assertTrue(card.x() >= loop.x(), "inner card escapes the left edge");
            assertTrue(card.y() >= loop.y(), "inner card escapes the top edge");
            assertTrue(card.maxX() <= loop.maxX(), "inner card escapes the right edge");
            assertTrue(card.maxY() <= loop.maxY(), "inner card escapes the bottom edge");
            assertEquals(128.0f, card.width(), 0.001f);
            assertEquals(40.0f, card.height(), 0.001f);
        }
        double innerArea = overlapArea(inner.get(0), inner.get(1));
        assertEquals(0.0, innerArea, 1e-9, "inner cards must not overlap");
    }

    @Test
    void expandedCompoundBoxAvoidsEveryOtherNode() {
        PlanDagLayoutResult result = swimlane();
        DagNodeBox loop = Objects.requireNonNull(index(result.nodes()).get("s009"));
        for (DagNodeBox node : result.nodes()) {
            if (node.stepId().equals(loop.stepId())) {
                continue;
            }
            assertEquals(0.0, overlapArea(loop, node), 1e-9, "expanded compound box overlaps " + node.stepId());
        }
        List<DagNodeBox> outer = new ArrayList<>(result.nodes());
        for (int left = 0; left < outer.size(); left++) {
            for (int right = left + 1; right < outer.size(); right++) {
                assertEquals(
                    0.0,
                    overlapArea(outer.get(left), outer.get(right)),
                    1e-9,
                    "nodes " + outer.get(left).stepId() + " and " + outer.get(right).stepId() + " overlap"
                );
            }
        }
    }

    @Test
    void expandedCycleLoopEmitsLoopBackEdge() {
        PlanDagLayoutResult result = swimlane();
        DagEdgeRoute loopBack = null;
        for (DagEdgeRoute edge : result.edges()) {
            if (edge.kind() == DagEdgeRoute.EdgeKind.loopBack) {
                loopBack = edge;
            }
        }
        assertNotNull(loopBack, "expanded loop must expose its LOOP_BACK arc");
        assertEquals("s009/t2", loopBack.fromStepId(), "loop arc leaves the last inner card");
        assertEquals("s009/t1", loopBack.toStepId(), "loop arc returns to the first inner card");
    }

    @Test
    void collapsedCycleLoopKeepsBlueprintSize() {
        PlanDagLayoutResult result = PlanDagLayout
                .compute(plan, null, graph, DagLayoutMode.systemSwimlane, Set.of(), Map.of(), Set.of());
        DagNodeBox loop = index(result.nodes()).get("s009");
        assertNotNull(loop);
        assertTrue(!loop.expandedLoop());
        assertEquals(204.0f, loop.width(), 0.001f);
        assertEquals(84.0f, loop.height(), 0.001f);
        assertEquals(0, loop.innerLoopNodes().size());
        for (DagEdgeRoute edge : result.edges()) {
            assertTrue(edge.kind() != DagEdgeRoute.EdgeKind.loopBack, "collapsed loop must not emit a LOOP_BACK arc");
        }
    }

    @Test
    void manualOffsetsSurviveInEveryMode() {
        for (DagLayoutMode mode : List
                .of(DagLayoutMode.horizontalLr, DagLayoutMode.verticalTb, DagLayoutMode.systemSwimlane)) {
            PlanDagLayoutResult auto = PlanDagLayout.compute(plan, null, graph, mode, Set.of(), Map.of(), Set.of());
            PlanDagLayoutResult shifted = PlanDagLayout
                    .compute(plan, null, graph, mode, Set.of(), Map.of("s004", new FloatPos(7.5, 3.25)), Set.of());
            DagNodeBox before = Objects.requireNonNull(index(auto.nodes()).get("s004"));
            DagNodeBox after = Objects.requireNonNull(index(shifted.nodes()).get("s004"));
            assertEquals(before.x() + 7.5f, after.x(), 0.001f, mode + " x offset");
            assertEquals(before.y() + 3.25f, after.y(), 0.001f, mode + " y offset");
        }
    }

    private static double overlapArea(DagNodeBox a, DagNodeBox b) {
        double width = Math.min(a.maxX(), b.maxX()) - Math.max(a.x(), b.x());
        double height = Math.min(a.maxY(), b.maxY()) - Math.max(a.y(), b.y());
        return width > 0.0 && height > 0.0 ? width * height : 0.0;
    }

    private static Map<String, DagNodeBox> index(List<DagNodeBox> nodes) {
        Map<String, DagNodeBox> byId = new HashMap<>();
        for (DagNodeBox node : nodes) {
            byId.put(node.stepId(), node);
        }
        return byId;
    }
}

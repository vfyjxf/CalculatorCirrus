package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.layout.PlanDagLayoutResult;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-22: marquee selection, five-axis cost aggregation and the single batch ban feedback.
 */
class SpatialDagMarqueeTest {

    @Test
    void marqueeSelectsThreeNodesAndAggregatesFiveAxes() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        DagHitTester.RectF bounds = boundsOf(layout);
        List<DagNodeBox> hits = DagSelectionMath.marqueeNodes(
            bounds.x() - 4.0f,
            bounds.y() - 4.0f,
            bounds.maxX() + 4.0f,
            bounds.maxY() + 4.0f,
            layout.nodes()
        );
        assertEquals(3, hits.size());

        Set<String> selected = DagSelectionMath.marqueeSelect(
            bounds.x() - 4.0f,
            bounds.y() - 4.0f,
            bounds.maxX() + 4.0f,
            bounds.maxY() + 4.0f,
            layout.nodes()
        );
        assertEquals(Set.of("c1", "c2", "c3"), selected);

        double human = 0.0;
        double passive = 0.0;
        double capital = 0.0;
        double scarcity = 0.0;
        double cognitive = 0.0;
        for (DagNodeBox node : hits) {
            human += node.step().stepCost().humanActiveSeconds();
            passive += node.step().stepCost().passiveWaitSeconds();
            capital += node.step().stepCost().machineSetupCapital();
            scarcity += node.step().stepCost().materialScarcity();
            cognitive += node.step().stepCost().cognitiveFriction();
        }
        DagSelectionMath.CostTotals totals = DagSelectionMath.aggregateNodes(hits);
        assertEquals(human, totals.human(), 1e-9);
        assertEquals(passive, totals.passive(), 1e-9);
        assertEquals(capital, totals.capital(), 1e-9);
        assertEquals(scarcity, totals.scarcity(), 1e-9);
        assertEquals(cognitive, totals.cognitive(), 1e-9);
        assertEquals(16.0, totals.human(), 1e-9);
        assertEquals(8.0, totals.passive(), 1e-9);
    }

    @Test
    void marqueeRectangleOutsideNodesSelectsNothing() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        Set<String> selected = DagSelectionMath.marqueeSelect(-4000.0f, -4000.0f, -3900.0f, -3900.0f, layout.nodes());
        assertTrue(selected.isEmpty());
    }

    @Test
    void batchBanSendsOneApplyRulesFeedbackWithEverySelectedRecipe() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        SpatialDagFixtures.RecordingSink sink = new SpatialDagFixtures.RecordingSink();
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create().feedbackSink(sink);
        canvas.actionBar().selection(layout.nodes());
        canvas.actionBar().batchBan();

        List<HumanFeedback> sent = sink.sent();
        assertEquals(1, sent.size(), "one batched feedback instead of one per node");
        assertTrue(sent.get(0) instanceof ApplyRulesFeedback);
        ApplyRulesFeedback feedback = (ApplyRulesFeedback) sent.get(0);
        List<GuidanceRule> rules = feedback.rules();
        assertEquals(3, rules.size());
        List<String> banned = new ArrayList<>();
        for (GuidanceRule rule : rules) {
            assertTrue(rule instanceof RecipeRule);
            RecipeRule recipeRule = (RecipeRule) rule;
            assertEquals(RuleStrength.hardBan, recipeRule.strength());
            banned.add(recipeRule.recipeKey().shortString());
        }
        assertEquals(3, Set.copyOf(banned).size());
        assertTrue(banned.contains(SpatialDagFixtures.r1.shortString()));
    }

    @Test
    void batchOwnDeclaresEveryDistinctMachineOnce() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        SpatialDagFixtures.RecordingSink sink = new SpatialDagFixtures.RecordingSink();
        SpatialDagCanvasWidget canvas = SpatialDagCanvasWidget.create().feedbackSink(sink);
        canvas.actionBar().selection(layout.nodes());
        canvas.actionBar().batchOwn();
        assertEquals(1, sink.sent().size(), "all three steps share the same machine");
    }

    @Test
    void alignmentOffsetsSnapSelectionToTheLeftEdge() {
        PlanDagLayoutResult layout = SpatialDagFixtures
                .layout(SpatialDagFixtures.chainPlan(), DagLayoutMode.horizontalLr);
        var offsets = DagSelectionMath.alignOffsets(layout.nodes(), DagSelectionMath.AlignKind.left);
        assertEquals(3, offsets.size());
        float minX = Float.MAX_VALUE;
        for (DagNodeBox node : layout.nodes()) {
            minX = Math.min(minX, node.x());
        }
        for (DagNodeBox node : layout.nodes()) {
            assertEquals(minX - node.x(), Objects.requireNonNull(offsets.get(node.stepId())).x, 0.001f);
        }
    }

    private static DagHitTester.RectF boundsOf(PlanDagLayoutResult layout) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (DagNodeBox node : layout.nodes()) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.maxX());
            maxY = Math.max(maxY, node.maxY());
        }
        return new DagHitTester.RectF(minX, minY, maxX - minX, maxY - minY);
    }
}

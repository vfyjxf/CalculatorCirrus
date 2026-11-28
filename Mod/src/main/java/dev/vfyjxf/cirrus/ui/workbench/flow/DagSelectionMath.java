package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagEdgeRoute;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cloudlib.api.math.FloatPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure marquee, cost aggregation, snapping and alignment math for the DAG canvas.
 */
public final class DagSelectionMath {

    public static final float grid = 8.0f;
    public static final float guideThreshold = 4.0f;

    private DagSelectionMath() {}

    public record CostTotals(double human, double passive, double capital, double scarcity, double cognitive) {

        public static final CostTotals zero = new CostTotals(0.0, 0.0, 0.0, 0.0, 0.0);

        public static CostTotals of(PlanStep step) {
            if (step == null) {
                return zero;
            }
            CostVector cost = step.stepCost();
            return new CostTotals(
                cost.humanActiveSeconds(),
                cost.passiveWaitSeconds(),
                cost.machineSetupCapital(),
                cost.materialScarcity(),
                cost.cognitiveFriction()
            );
        }

        public CostTotals plus(CostTotals other) {
            if (other == null) {
                return this;
            }
            return new CostTotals(
                human + other.human,
                passive + other.passive,
                capital + other.capital,
                scarcity + other.scarcity,
                cognitive + other.cognitive
            );
        }
    }

    public record GuideLine(boolean vertical, float position, float start, float end) {}

    public record TraceSets(Set<String> upstream, Set<String> downstream, Set<String> unrelated) {

        public TraceSets {
            upstream = Set.copyOf(upstream);
            downstream = Set.copyOf(downstream);
            unrelated = Set.copyOf(unrelated);
        }
    }

    public enum AlignKind {
        left, top, row
    }

    public interface FeedbackSink {

        void send(HumanFeedback feedback);
    }

    public static List<RecipeRule> banRules(Collection<DagNodeBox> nodes, GuidanceScope scope) {
        List<RecipeRule> rules = new ArrayList<>();
        if (nodes == null) {
            return rules;
        }
        for (DagNodeBox node : nodes) {
            if (node == null) {
                continue;
            }
            switch (node.step()) {
                case CraftStep craft -> rules.add(
                    new RecipeRule("flow-ban-" + craft.stepId(), scope, RuleStrength.hardBan, craft.recipeKey())
                );
                case ConvertStep convert -> rules.add(
                    new RecipeRule(
                        "flow-ban-" + convert.stepId(),
                        scope,
                        RuleStrength.hardBan,
                        convert.conversionRecipeKey()
                    )
                );
                default -> {}
            }
        }
        return rules;
    }

    public static int sendBatchBan(
        Collection<DagNodeBox> nodes,
        GuidanceScope scope,
        boolean persistToWorld,
        FeedbackSink sink
    ) {
        if (sink == null) {
            return 0;
        }
        List<RecipeRule> rules = banRules(nodes, scope);
        if (rules.isEmpty()) {
            return 0;
        }
        sink.send(new ApplyRulesFeedback(new ArrayList<GuidanceRule>(rules), persistToWorld));
        return 1;
    }

    public static int sendBatchOwn(Collection<DagNodeBox> nodes, FeedbackSink sink) {
        if (nodes == null || sink == null) {
            return 0;
        }
        Set<MachineEntry> machines = new TreeSet<>(MachineEntry.comparator);
        for (DagNodeBox node : nodes) {
            if (node == null) {
                continue;
            }
            switch (node.step()) {
                case ProvisionMachineStep provision -> {
                    if (!provision.machine().isEmpty()) {
                        machines.add(provision.machine());
                    }
                }
                case CraftStep craft -> craft.chosenMachine().filter(m -> !m.isEmpty()).ifPresent(machines::add);
                default -> {}
            }
        }
        int sent = 0;
        for (MachineEntry machine : machines) {
            sink.send(new DeclareOwnedMachineFeedback(machine, 1, false));
            sent++;
        }
        return sent;
    }

    public static List<GuidanceRule> asRules(Collection<DagNodeBox> nodes, GuidanceScope scope) {
        return List.copyOf(banRules(nodes, scope));
    }

    public static DagHitTester.RectF marqueeRect(double startX, double startY, double endX, double endY) {
        float x = (float) Math.min(startX, endX);
        float y = (float) Math.min(startY, endY);
        float width = (float) Math.abs(endX - startX);
        float height = (float) Math.abs(endY - startY);
        return new DagHitTester.RectF(x, y, width, height);
    }

    public static List<DagNodeBox> marqueeNodes(
        double startX,
        double startY,
        double endX,
        double endY,
        List<DagNodeBox> nodes
    ) {
        DagHitTester.RectF marquee = marqueeRect(startX, startY, endX, endY);
        List<DagNodeBox> hits = new ArrayList<>();
        if (nodes == null) {
            return hits;
        }
        for (DagNodeBox node : nodes) {
            if (marquee.intersects(new DagHitTester.RectF(node.x(), node.y(), node.width(), node.height()))) {
                hits.add(node);
            }
        }
        return hits;
    }

    public static Set<String> marqueeSelect(
        double startX,
        double startY,
        double endX,
        double endY,
        List<DagNodeBox> nodes
    ) {
        Set<String> ids = new TreeSet<>();
        for (DagNodeBox node : marqueeNodes(startX, startY, endX, endY, nodes)) {
            ids.add(node.stepId());
        }
        return ids;
    }

    public static CostTotals aggregate(Collection<? extends PlanStep> steps) {
        CostTotals total = CostTotals.zero;
        if (steps == null) {
            return total;
        }
        for (PlanStep step : steps) {
            total = total.plus(CostTotals.of(step));
        }
        return total;
    }

    public static CostTotals aggregateNodes(Collection<DagNodeBox> nodes) {
        CostTotals total = CostTotals.zero;
        if (nodes == null) {
            return total;
        }
        for (DagNodeBox node : nodes) {
            total = total.plus(CostTotals.of(node.step()));
        }
        return total;
    }

    public static float applyGridSnap(float raw, float grid, boolean snapEnabled) {
        if (!Float.isFinite(raw)) {
            return 0.0f;
        }
        if (!snapEnabled || !Float.isFinite(grid) || grid <= 0.0f) {
            return raw;
        }
        return Math.round(raw / grid) * grid;
    }

    public static FloatPos snappedOffset(double rawX, double rawY, float grid, boolean snapEnabled) {
        return new FloatPos(
            applyGridSnap((float) rawX, grid, snapEnabled),
            applyGridSnap((float) rawY, grid, snapEnabled)
        );
    }

    /**
     * Nudges the drag offset so the closest edge pair inside the threshold aligns exactly.
     */
    public static FloatPos magneticSnap(
        DagHitTester.RectF dragged,
        List<DagHitTester.RectF> others,
        FloatPos offset,
        float threshold
    ) {
        if (dragged == null || others == null || others.isEmpty() || offset == null) {
            return offset;
        }
        float bestX = Float.MAX_VALUE;
        float bestY = Float.MAX_VALUE;
        float adjustX = 0.0f;
        float adjustY = 0.0f;
        float limit = Math.max(0.0f, threshold);
        float[] draggedX = {dragged.x(), dragged.centerX(), dragged.maxX()};
        float[] draggedY = {dragged.y(), dragged.centerY(), dragged.maxY()};
        for (DagHitTester.RectF other : others) {
            float[] otherX = {other.x(), other.centerX(), other.maxX()};
            float[] otherY = {other.y(), other.centerY(), other.maxY()};
            for (float dx : draggedX) {
                for (float ox : otherX) {
                    float delta = Math.abs(dx - ox);
                    if (delta <= limit && delta < bestX) {
                        bestX = delta;
                        adjustX = ox - dx;
                    }
                }
            }
            for (float dy : draggedY) {
                for (float oy : otherY) {
                    float delta = Math.abs(dy - oy);
                    if (delta <= limit && delta < bestY) {
                        bestY = delta;
                        adjustY = oy - dy;
                    }
                }
            }
        }
        return new FloatPos(offset.x + adjustX, offset.y + adjustY);
    }

    public static List<GuideLine> guideLines(
        DagHitTester.RectF dragged,
        List<DagHitTester.RectF> others,
        float threshold
    ) {
        List<GuideLine> guides = new ArrayList<>();
        if (dragged == null || others == null || others.isEmpty()) {
            return guides;
        }
        float limit = Math.max(0.0f, threshold);
        float[] draggedX = {dragged.x(), dragged.centerX(), dragged.maxX()};
        float[] draggedY = {dragged.y(), dragged.centerY(), dragged.maxY()};
        Set<Long> seenVertical = new LinkedHashSet<>();
        Set<Long> seenHorizontal = new LinkedHashSet<>();
        for (DagHitTester.RectF other : others) {
            float[] otherX = {other.x(), other.centerX(), other.maxX()};
            float[] otherY = {other.y(), other.centerY(), other.maxY()};
            for (float dx : draggedX) {
                for (float ox : otherX) {
                    if (Math.abs(dx - ox) <= limit && seenVertical.add(Math.round(ox * 100.0f) * 31L + 7L)) {
                        guides.add(
                            new GuideLine(
                                true,
                                ox,
                                Math.min(dragged.y(), other.y()),
                                Math.max(dragged.maxY(), other.maxY())
                            )
                        );
                    }
                }
            }
            for (float dy : draggedY) {
                for (float oy : otherY) {
                    if (Math.abs(dy - oy) <= limit && seenHorizontal.add(Math.round(oy * 100.0f) * 37L + 11L)) {
                        guides.add(
                            new GuideLine(
                                false,
                                oy,
                                Math.min(dragged.x(), other.x()),
                                Math.max(dragged.maxX(), other.maxX())
                            )
                        );
                    }
                }
            }
        }
        return guides;
    }

    public static Map<String, FloatPos> alignOffsets(List<DagNodeBox> nodes, AlignKind kind) {
        Map<String, FloatPos> offsets = new TreeMap<>();
        if (nodes == null || nodes.isEmpty() || kind == null) {
            return offsets;
        }
        float anchorX = nodes.get(0).x();
        float anchorY = nodes.get(0).y();
        for (DagNodeBox node : nodes) {
            anchorX = Math.min(anchorX, node.x());
            anchorY = Math.min(anchorY, node.y());
        }
        if (kind == AlignKind.row) {
            int index = 0;
            for (DagNodeBox node : sortedByX(nodes)) {
                float targetX = anchorX + index * (node.width() + 16.0f);
                float targetY = anchorY;
                offsets.put(node.stepId(), new FloatPos(targetX - node.x(), targetY - node.y()));
                index++;
            }
            return offsets;
        }
        for (DagNodeBox node : nodes) {
            float dx = kind == AlignKind.left ? anchorX - node.x() : 0.0f;
            float dy = kind == AlignKind.top ? anchorY - node.y() : 0.0f;
            offsets.put(node.stepId(), new FloatPos(dx, dy));
        }
        return offsets;
    }

    public static TraceSets traceSets(String focusStepId, List<DagEdgeRoute> edges) {
        return traceSets(focusStepId, edges, Set.of());
    }

    public static TraceSets traceSets(String focusStepId, List<DagEdgeRoute> edges, Collection<String> allStepIds) {
        Set<String> upstream = new TreeSet<>();
        Set<String> downstream = new TreeSet<>();
        if (focusStepId == null) {
            return new TraceSets(upstream, downstream, Set.of());
        }
        if (edges == null) {
            return new TraceSets(upstream, downstream, Set.of());
        }
        buildTraceGraph(focusStepId, edges, upstream, downstream);
        return partitionTrace(focusStepId, upstream, downstream, allStepIds);
    }

    private static void buildTraceGraph(
        String focusStepId,
        List<DagEdgeRoute> edges,
        Set<String> upstream,
        Set<String> downstream
    ) {
        Map<String, Set<String>> preds = new TreeMap<>();
        Map<String, Set<String>> succs = new TreeMap<>();
        for (DagEdgeRoute edge : edges) {
            succs.computeIfAbsent(edge.fromStepId(), key -> new TreeSet<>()).add(edge.toStepId());
            preds.computeIfAbsent(edge.toStepId(), key -> new TreeSet<>()).add(edge.fromStepId());
        }
        walk(focusStepId, preds, upstream);
        walk(focusStepId, succs, downstream);
    }

    private static TraceSets partitionTrace(
        String focusStepId,
        Set<String> upstream,
        Set<String> downstream,
        Collection<String> allStepIds
    ) {
        Set<String> unrelated = new TreeSet<>(allStepIds == null ? Set.of() : allStepIds);
        unrelated.remove(focusStepId);
        unrelated.removeAll(upstream);
        unrelated.removeAll(downstream);
        return new TraceSets(upstream, downstream, unrelated);
    }

    private static void walk(String start, Map<String, Set<String>> graph, Set<String> visited) {
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : graph.getOrDefault(current, Set.of())) {
                if (!next.equals(start) && visited.add(next)) {
                    queue.add(next);
                }
            }
        }
    }

    private static List<DagNodeBox> sortedByX(List<DagNodeBox> nodes) {
        List<DagNodeBox> sorted = new ArrayList<>(nodes);
        sorted.sort((left, right) -> {
            int byX = Float.compare(left.x(), right.x());
            return byX != 0 ? byX : left.stepId().compareTo(right.stepId());
        });
        return sorted;
    }
}

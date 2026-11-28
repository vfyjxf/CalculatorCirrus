package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public final class PlanDagLayout {

    public static final String laneIdAcquire = "lane:acquire";
    public static final String laneIdMachines = "lane:machines";
    public static final String laneTitleKeyAcquire = "cirrus.ui.workbench.flow.lane.acquire_lines";
    public static final String laneTitleKeyMachines = "cirrus.ui.workbench.flow.lane.machines_lines";

    private static final int barycenterRounds = 12;
    private static final float controlMin = 28.0f;
    private static final float loopControlMin = 20.0f;
    private static final float loopSag = 24.0f;

    private PlanDagLayout() {}

    public static PlanDagLayoutResult compute(
        @Nullable ProductionPlan plan,
        @Nullable ProductionPlan comparePlan,
        CompiledRecipeGraph graph,
        DagLayoutMode mode,
        Set<String> expandedLoopStepIds,
        Map<String, FloatPos> manualOffsets,
        Set<String> criticalStepIds
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(mode, "mode");
        Set<String> expanded = copyIds(expandedLoopStepIds);
        Map<String, FloatPos> offsets = copyOffsets(manualOffsets);
        Set<String> critical = copyIds(criticalStepIds);
        if (plan == null) {
            return PlanDagLayoutResult.empty;
        }
        PlanDagLayoutResult base = layoutPlan(plan, graph, mode, expanded, offsets, critical);
        if (comparePlan == null) {
            return base;
        }
        PlanDagLayoutResult compare = layoutPlan(comparePlan, graph, mode, expanded, offsets, critical);
        return mergeDiff(base, compare);
    }

    static String innerStepId(String parentStepId, String templateStepId) {
        return parentStepId + "/" + templateStepId;
    }

    private static PlanDagLayoutResult mergeDiff(PlanDagLayoutResult base, PlanDagLayoutResult compare) {
        Set<String> currentIds = new HashSet<>();
        for (DagNodeBox node : base.nodes()) {
            currentIds.add(node.stepId());
        }
        Set<String> compareIds = new HashSet<>();
        for (DagNodeBox node : compare.nodes()) {
            compareIds.add(node.stepId());
        }
        Set<String> compareEdgeKeys = new HashSet<>();
        for (DagEdgeRoute edge : compare.edges()) {
            compareEdgeKeys.add(edgeKey(edge));
        }

        List<DagNodeBox> nodes = new ArrayList<>(base.nodes().size() + compare.nodes().size());
        for (DagNodeBox node : base.nodes()) {
            nodes.add(
                withStatus(
                    node,
                    compareIds.contains(node.stepId()) ? DagNodeBox.DiffStatus.kept : DagNodeBox.DiffStatus.added
                )
            );
        }
        Set<String> ghostIds = new TreeSet<>();
        for (DagNodeBox node : compare.nodes()) {
            if (!currentIds.contains(node.stepId())) {
                ghostIds.add(node.stepId());
            }
        }
        Map<String, DagNodeBox> compareById = new HashMap<>();
        for (DagNodeBox node : compare.nodes()) {
            compareById.put(node.stepId(), node);
        }
        for (String ghostId : ghostIds) {
            nodes.add(withStatus(Objects.requireNonNull(compareById.get(ghostId)), DagNodeBox.DiffStatus.removed));
        }

        List<DagEdgeRoute> edges = new ArrayList<>(base.edges().size() + compare.edges().size());
        for (DagEdgeRoute edge : base.edges()) {
            edges.add(
                withStatus(
                    edge,
                    compareEdgeKeys.contains(edgeKey(edge)) ? DagNodeBox.DiffStatus.kept : DagNodeBox.DiffStatus.added
                )
            );
        }
        for (DagEdgeRoute edge : compare.edges()) {
            if (ghostIds.contains(edge.fromStepId()) || ghostIds.contains(edge.toStepId())) {
                edges.add(withStatus(edge, DagNodeBox.DiffStatus.removed));
            }
        }
        return finish(nodes, edges, base.swimlanes());
    }

    private static String edgeKey(DagEdgeRoute edge) {
        return edge.fromStepId() + "|" + edge.toStepId() + "|" + edge.kind().name() + "|"
                + (edge.resource() == null ? "" : edge.resource().canonicalKey());
    }

    private static DagNodeBox withStatus(DagNodeBox node, DagNodeBox.DiffStatus status) {
        List<DagNodeBox> inner = new ArrayList<>(node.innerLoopNodes().size());
        for (DagNodeBox child : node.innerLoopNodes()) {
            inner.add(withStatus(child, status));
        }
        return new DagNodeBox(
            node.stepId(),
            node.step(),
            node.layer(),
            node.laneIndex(),
            node.swimlaneId(),
            node.x(),
            node.y(),
            node.width(),
            node.height(),
            node.expandedLoop(),
            inner,
            status
        );
    }

    private static DagEdgeRoute withStatus(DagEdgeRoute edge, DagNodeBox.DiffStatus status) {
        return new DagEdgeRoute(
            edge.fromStepId(),
            edge.toStepId(),
            edge.kind(),
            edge.resource(),
            edge.amountFlow(),
            edge.sourceTotalProduced(),
            edge.x0(),
            edge.y0(),
            edge.cx0(),
            edge.cy0(),
            edge.cx1(),
            edge.cy1(),
            edge.x1(),
            edge.y1(),
            edge.criticalPath(),
            status
        );
    }

    private static PlanDagLayoutResult layoutPlan(
        ProductionPlan plan,
        CompiledRecipeGraph graph,
        DagLayoutMode mode,
        Set<String> expanded,
        Map<String, FloatPos> offsets,
        Set<String> critical
    ) {
        LinkedHashMap<String, PlanStep> byId = indexSteps(plan.steps());
        if (byId.isEmpty()) {
            return PlanDagLayoutResult.empty;
        }
        Map<String, List<String>> preds = predecessorIndex(byId);
        Map<String, List<String>> succs = successorIndex(byId, preds);
        Map<String, Integer> layers = computeLayers(byId, preds, succs);
        Map<String, LayoutGeometry.NodeSize> sizes = sizesOf(byId, expanded);
        List<Lane> lanes = lanePlan(byId, layers);
        Map<String, Integer> laneOf = new HashMap<>();
        Map<String, String> laneIdOf = new HashMap<>();
        for (Lane lane : lanes) {
            for (String stepId : lane.stepIds()) {
                laneOf.put(stepId, lane.index());
                laneIdOf.put(stepId, lane.laneId());
            }
        }

        List<List<String>> layerRows = layerRows(byId, layers);
        reduceCrossings(layerRows, preds, succs, byId);
        Map<String, Integer> columnOrder = new HashMap<>();
        for (List<String> row : layerRows) {
            for (int i = 0; i < row.size(); i++) {
                columnOrder.put(row.get(i), i);
            }
        }

        Placement placement = switch (mode) {
            case horizontalLr -> placeHorizontal(layerRows, sizes, layers, preds, succs, false);
            case horizontalRl -> placeHorizontal(layerRows, sizes, layers, preds, succs, true);
            case verticalTb -> placeVertical(layerRows, sizes, layers, preds, succs, byId, false);
            case verticalBt -> placeVertical(layerRows, sizes, layers, succs, preds, byId, true);
            case systemSwimlane -> placeSwimlane(lanes, sizes, layers, layerRows, columnOrder);
        };
        applyOffsets(placement.rects(), offsets);

        List<DagNodeBox> nodes = buildNodes(byId, placement.rects(), layers, laneOf, laneIdOf, sizes);
        List<DagEdgeRoute> edges = buildEdges(byId, preds, nodes, graph, mode, critical, expanded);
        return finish(nodes, edges, placement.bands());
    }

    private static Set<String> copyIds(@Nullable Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        Set<String> copy = new TreeSet<>();
        for (String id : source) {
            if (id != null) {
                copy.add(id);
            }
        }
        return copy;
    }

    private static Map<String, FloatPos> copyOffsets(@Nullable Map<String, FloatPos> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        TreeMap<String, FloatPos> copy = new TreeMap<>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private static LinkedHashMap<String, PlanStep> indexSteps(List<PlanStep> steps) {
        LinkedHashMap<String, PlanStep> byId = new LinkedHashMap<>();
        for (PlanStep step : steps) {
            if (step != null) {
                byId.putIfAbsent(step.stepId(), step);
            }
        }
        return byId;
    }

    private static Map<String, List<String>> predecessorIndex(Map<String, PlanStep> byId) {
        Map<String, List<String>> preds = new TreeMap<>();
        for (PlanStep step : byId.values()) {
            SortedSet<String> deps = new TreeSet<>();
            for (String dep : step.dependsOnStepIds()) {
                if (dep != null && !dep.equals(step.stepId()) && byId.containsKey(dep)) {
                    deps.add(dep);
                }
            }
            preds.put(step.stepId(), List.copyOf(deps));
        }
        return preds;
    }

    private static Map<String, List<String>> successorIndex(
        Map<String, PlanStep> byId,
        Map<String, List<String>> preds
    ) {
        Map<String, SortedSet<String>> mutable = new TreeMap<>();
        for (String stepId : byId.keySet()) {
            mutable.put(stepId, new TreeSet<>());
        }
        for (var entry : preds.entrySet()) {
            for (String dep : entry.getValue()) {
                Objects.requireNonNull(mutable.get(dep)).add(entry.getKey());
            }
        }
        Map<String, List<String>> succs = new TreeMap<>();
        for (var entry : mutable.entrySet()) {
            succs.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return succs;
    }

    static Map<String, Integer> computeLayers(
        Map<String, PlanStep> byId,
        Map<String, List<String>> preds,
        Map<String, List<String>> succs
    ) {
        Map<String, Integer> indegree = new TreeMap<>();
        for (String stepId : byId.keySet()) {
            indegree.put(stepId, preds.getOrDefault(stepId, List.of()).size());
        }
        TreeSet<String> ready = new TreeSet<>();
        for (var entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        Map<String, Integer> layers = new TreeMap<>();
        while (!ready.isEmpty()) {
            String stepId = ready.pollFirst();
            int layer = 0;
            for (String dep : preds.getOrDefault(stepId, List.of())) {
                layer = Math.max(layer, layers.getOrDefault(dep, 0) + 1);
            }
            layers.put(stepId, layer);
            for (String successor : succs.getOrDefault(stepId, List.of())) {
                int remaining = Objects.requireNonNull(indegree.get(successor)) - 1;
                indegree.put(successor, remaining);
                if (remaining == 0) {
                    ready.add(successor);
                }
            }
        }
        for (String stepId : byId.keySet()) {
            if (!layers.containsKey(stepId)) {
                int layer = 0;
                for (String dep : preds.getOrDefault(stepId, List.of())) {
                    if (layers.containsKey(dep)) {
                        layer = Math.max(layer, layers.get(dep) + 1);
                    }
                }
                layers.put(stepId, layer);
            }
        }
        return layers;
    }

    private static Map<String, LayoutGeometry.NodeSize> sizesOf(Map<String, PlanStep> byId, Set<String> expanded) {
        Map<String, LayoutGeometry.NodeSize> sizes = new TreeMap<>();
        for (PlanStep step : byId.values()) {
            sizes.put(step.stepId(), LayoutGeometry.sizeOf(step, expanded.contains(step.stepId())));
        }
        return sizes;
    }

    private static Comparator<String> stepIdComparator(Map<String, PlanStep> byId) {
        return Comparator
                .comparingInt((String id) -> LayoutGeometry.stepTypeOrdinal(Objects.requireNonNull(byId.get(id))))
                .thenComparing(Comparator.naturalOrder());
    }

    private static List<List<String>> layerRows(Map<String, PlanStep> byId, Map<String, Integer> layers) {
        int maxLayer = 0;
        for (int layer : layers.values()) {
            maxLayer = Math.max(maxLayer, layer);
        }
        List<List<String>> rows = new ArrayList<>(maxLayer + 1);
        for (int layer = 0; layer <= maxLayer; layer++) {
            rows.add(new ArrayList<>());
        }
        for (PlanStep step : byId.values()) {
            rows.get(Objects.requireNonNull(layers.get(step.stepId()))).add(step.stepId());
        }
        Comparator<String> comparator = stepIdComparator(byId);
        for (List<String> row : rows) {
            row.sort(comparator);
        }
        return rows;
    }

    private static void reduceCrossings(
        List<List<String>> rows,
        Map<String, List<String>> primary,
        Map<String, List<String>> secondary,
        Map<String, PlanStep> byId
    ) {
        Comparator<String> comparator = stepIdComparator(byId);
        for (int round = 0; round < barycenterRounds; round++) {
            if (round % 2 == 0) {
                for (int r = 1; r < rows.size(); r++) {
                    reorderRow(rows.get(r), rows.get(r - 1), primary, comparator);
                    swapAdjacentCrossings(rows.get(r), rows.get(r - 1), primary);
                }
            } else {
                for (int r = rows.size() - 2; r >= 0; r--) {
                    reorderRow(rows.get(r), rows.get(r + 1), secondary, comparator);
                    swapAdjacentCrossings(rows.get(r), rows.get(r + 1), secondary);
                }
            }
        }
    }

    private static void swapAdjacentCrossings(
        List<String> row,
        List<String> reference,
        Map<String, List<String>> neighbors
    ) {
        if (row.size() < 2 || reference.isEmpty()) {
            return;
        }
        Map<String, Integer> refPos = new HashMap<>(reference.size() * 2);
        for (int i = 0; i < reference.size(); i++) {
            refPos.put(reference.get(i), i);
        }
        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 6) {
            improved = false;
            for (int i = 0; i < row.size() - 1; i++) {
                String a = row.get(i);
                String b = row.get(i + 1);
                int before = pairCrossings(a, b, neighbors, refPos);
                int after = pairCrossings(b, a, neighbors, refPos);
                if (after < before) {
                    row.set(i, b);
                    row.set(i + 1, a);
                    improved = true;
                }
            }
        }
    }

    private static int pairCrossings(
        String top,
        String bottom,
        Map<String, List<String>> neighbors,
        Map<String, Integer> refPos
    ) {
        List<String> topN = neighbors.getOrDefault(top, List.of());
        List<String> botN = neighbors.getOrDefault(bottom, List.of());
        if (topN.isEmpty() || botN.isEmpty()) {
            return 0;
        }
        int crossings = 0;
        for (String tn : topN) {
            Integer tp = refPos.get(tn);
            if (tp == null) {
                continue;
            }
            for (String bn : botN) {
                Integer bp = refPos.get(bn);
                if (bp != null && tp > bp) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static void reorderRow(
        List<String> row,
        List<String> reference,
        Map<String, List<String>> neighbors,
        Comparator<String> tieBreak
    ) {
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < reference.size(); i++) {
            positions.put(reference.get(i), i);
        }
        Map<String, Double> barycenter = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            String stepId = row.get(i);
            double sum = 0.0;
            int count = 0;
            for (String neighbor : neighbors.getOrDefault(stepId, List.of())) {
                Integer position = positions.get(neighbor);
                if (position != null) {
                    sum += position;
                    count++;
                }
            }
            barycenter.put(stepId, count == 0 ? i : sum / count);
        }
        row.sort(Comparator.comparingDouble((String id) -> barycenter.getOrDefault(id, 0.0)).thenComparing(tieBreak));
    }

    private static int maxLayerOf(Map<String, Integer> layers) {
        int maxLayer = 0;
        for (int layer : layers.values()) {
            maxLayer = Math.max(maxLayer, layer);
        }
        return maxLayer;
    }

    private static float[] columnOffsets(List<List<String>> rows, Map<String, LayoutGeometry.NodeSize> sizes) {
        int count = rows.size();
        float[] widths = new float[count];
        for (int layer = 0; layer < count; layer++) {
            for (String stepId : rows.get(layer)) {
                widths[layer] = Math.max(widths[layer], Objects.requireNonNull(sizes.get(stepId)).width());
            }
        }
        float[] offsets = new float[count];
        for (int layer = 1; layer < count; layer++) {
            offsets[layer] = offsets[layer - 1] + widths[layer - 1] + LayoutGeometry.layerGap;
        }
        return offsets;
    }

    private static float[] columnWidths(List<List<String>> rows, Map<String, LayoutGeometry.NodeSize> sizes) {
        float[] widths = new float[rows.size()];
        for (int layer = 0; layer < rows.size(); layer++) {
            for (String stepId : rows.get(layer)) {
                widths[layer] = Math.max(widths[layer], Objects.requireNonNull(sizes.get(stepId)).width());
            }
        }
        return widths;
    }

    private static Placement placeHorizontal(
        List<List<String>> rows,
        Map<String, LayoutGeometry.NodeSize> sizes,
        Map<String, Integer> layers,
        Map<String, List<String>> preds,
        Map<String, List<String>> succs,
        boolean reversed
    ) {
        int maxLayer = maxLayerOf(layers);
        List<List<String>> ordered = new ArrayList<>(maxLayer + 1);
        for (int col = 0; col <= maxLayer; col++) {
            ordered.add(rows.get(reversed ? maxLayer - col : col));
        }
        float[] offsets = columnOffsets(ordered, sizes);
        float[] widths = columnWidths(ordered, sizes);
        float[] heights = new float[maxLayer + 1];
        for (int col = 0; col <= maxLayer; col++) {
            int index = 0;
            for (String stepId : ordered.get(col)) {
                if (index++ > 0) {
                    heights[col] += LayoutGeometry.nodeGap;
                }
                heights[col] += Objects.requireNonNull(sizes.get(stepId)).height();
            }
        }
        float tallest = 0.0f;
        for (float height : heights) {
            tallest = Math.max(tallest, height);
        }
        Map<String, Rect> rects = new TreeMap<>();
        for (int col = 0; col <= maxLayer; col++) {
            float cursor = (tallest - heights[col]) * 0.5f;
            for (String stepId : ordered.get(col)) {
                LayoutGeometry.NodeSize size = Objects.requireNonNull(sizes.get(stepId));
                float x = offsets[col] + (widths[col] - size.width()) * 0.5f;
                rects.put(stepId, new Rect(x, cursor, size.width(), size.height()));
                cursor += size.height() + LayoutGeometry.nodeGap;
            }
        }
        alignHorizontalTracks(ordered, rects, preds, succs);
        return new Placement(rects, List.of());
    }

    private static void alignHorizontalTracks(
        List<List<String>> ordered,
        Map<String, Rect> rects,
        Map<String, List<String>> preds,
        Map<String, List<String>> succs
    ) {
        for (int pass = 0; pass < 3; pass++) {
            boolean forward = (pass % 2 == 0);
            int start = forward ? 1 : ordered.size() - 2;
            int end = forward ? ordered.size() : -1;
            int step = forward ? 1 : -1;
            for (int col = start; col != end; col += step) {
                List<String> column = ordered.get(col);
                if (column.isEmpty()) {
                    continue;
                }
                float[] desiredY = new float[column.size()];
                for (int i = 0; i < column.size(); i++) {
                    String id = column.get(i);
                    Rect self = rects.get(id);
                    if (self == null) {
                        continue;
                    }
                    float sumCenter = 0.0f;
                    int count = 0;
                    List<String> ref = forward ? preds.getOrDefault(id, List.of()) : succs.getOrDefault(id, List.of());
                    for (String nb : ref) {
                        Rect nr = rects.get(nb);
                        if (nr != null) {
                            sumCenter += nr.y + nr.height * 0.5f;
                            count++;
                        }
                    }
                    if (count > 0) {
                        float targetY = (sumCenter / count) - self.height * 0.5f;
                        desiredY[i] = self.y + (targetY - self.y) * 0.65f;
                    } else {
                        desiredY[i] = self.y;
                    }
                }
                // Enforce non-overlapping top-to-bottom spacing
                for (int i = 1; i < column.size(); i++) {
                    Rect prev = rects.get(column.get(i - 1));
                    if (prev != null) {
                        float minTop = desiredY[i - 1] + prev.height + LayoutGeometry.nodeGap;
                        if (desiredY[i] < minTop) {
                            desiredY[i] = minTop;
                        }
                    }
                }
                for (int i = 0; i < column.size(); i++) {
                    Rect self = rects.get(column.get(i));
                    if (self != null) {
                        self.y = desiredY[i];
                    }
                }
            }
        }
        float minY = Float.POSITIVE_INFINITY;
        for (Rect r : rects.values()) {
            minY = Math.min(minY, r.y);
        }
        if (Float.isFinite(minY) && Math.abs(minY) > 0.5f) {
            for (Rect r : rects.values()) {
                r.y -= minY;
            }
        }
    }

    private static Placement placeVertical(
        List<List<String>> layerRows,
        Map<String, LayoutGeometry.NodeSize> sizes,
        Map<String, Integer> layers,
        Map<String, List<String>> primaryNeighbors,
        Map<String, List<String>> secondaryNeighbors,
        Map<String, PlanStep> byId,
        boolean bottomToTop
    ) {
        int maxLayer = maxLayerOf(layers);
        List<List<String>> rows = new ArrayList<>(maxLayer + 1);
        for (int row = 0; row <= maxLayer; row++) {
            rows.add(new ArrayList<>(layerRows.get(bottomToTop ? maxLayer - row : row)));
        }
        reduceCrossings(rows, primaryNeighbors, secondaryNeighbors, byId);

        float[] rowWidths = new float[maxLayer + 1];
        float[] rowHeights = new float[maxLayer + 1];
        for (int row = 0; row <= maxLayer; row++) {
            int index = 0;
            for (String stepId : rows.get(row)) {
                LayoutGeometry.NodeSize size = Objects.requireNonNull(sizes.get(stepId));
                if (index++ > 0) {
                    rowWidths[row] += LayoutGeometry.colGap;
                }
                rowWidths[row] += size.width();
                rowHeights[row] = Math.max(rowHeights[row], size.height());
            }
        }
        float widest = 0.0f;
        for (float width : rowWidths) {
            widest = Math.max(widest, width);
        }
        float[] rowOffsets = new float[maxLayer + 1];
        for (int row = 1; row <= maxLayer; row++) {
            rowOffsets[row] = rowOffsets[row - 1] + rowHeights[row - 1] + LayoutGeometry.rowGap;
        }
        Map<String, Rect> rects = new TreeMap<>();
        for (int row = 0; row <= maxLayer; row++) {
            float cursor = (widest - rowWidths[row]) * 0.5f;
            for (String stepId : rows.get(row)) {
                LayoutGeometry.NodeSize size = Objects.requireNonNull(sizes.get(stepId));
                float y = rowOffsets[row] + (rowHeights[row] - size.height()) * 0.5f;
                rects.put(stepId, new Rect(cursor, y, size.width(), size.height()));
                cursor += size.width() + LayoutGeometry.colGap;
            }
        }
        return new Placement(rects, List.of());
    }

    private static Placement placeSwimlane(
        List<Lane> lanes,
        Map<String, LayoutGeometry.NodeSize> sizes,
        Map<String, Integer> layers,
        List<List<String>> layerRows,
        Map<String, Integer> columnOrder
    ) {
        int maxLayer = maxLayerOf(layers);
        float[] offsets = columnOffsets(layerRows, sizes);
        float[] widths = columnWidths(layerRows, sizes);
        Map<String, Rect> rects = new TreeMap<>();
        List<SwimlaneBand> bands = new ArrayList<>(lanes.size());
        float bandY = 0.0f;
        for (Lane lane : lanes) {
            TreeMap<Integer, List<String>> byLayer = new TreeMap<>();
            for (String stepId : lane.stepIds()) {
                byLayer.computeIfAbsent(layers.get(stepId), key -> new ArrayList<>()).add(stepId);
            }
            for (List<String> group : byLayer.values()) {
                group.sort(Comparator.comparingInt((String id) -> columnOrder.getOrDefault(id, 0)));
            }
            float contentHeight = 0.0f;
            for (List<String> group : byLayer.values()) {
                float height = 0.0f;
                int index = 0;
                for (String stepId : group) {
                    if (index++ > 0) {
                        height += LayoutGeometry.nodeGap;
                    }
                    height += Objects.requireNonNull(sizes.get(stepId)).height();
                }
                contentHeight = Math.max(contentHeight, height);
            }
            float bandHeight = contentHeight + 2.0f * LayoutGeometry.swimlanePadding;
            bands.add(
                new SwimlaneBand(
                    lane.laneId(),
                    lane.titleKey(),
                    lane.iconMachine(),
                    LayoutGeometry.round(bandY),
                    LayoutGeometry.round(bandHeight),
                    lane.stepIds().size(),
                    lane.totalH(),
                    lane.totalP()
                )
            );
            for (var entry : byLayer.entrySet()) {
                int layer = Math.min(entry.getKey(), maxLayer);
                float cursor = bandY + LayoutGeometry.swimlanePadding;
                for (String stepId : entry.getValue()) {
                    LayoutGeometry.NodeSize size = Objects.requireNonNull(sizes.get(stepId));
                    float x = offsets[layer] + (widths[layer] - size.width()) * 0.5f;
                    rects.put(stepId, new Rect(x, cursor, size.width(), size.height()));
                    cursor += size.height() + LayoutGeometry.nodeGap;
                }
            }
            bandY += bandHeight + LayoutGeometry.swimlaneGap;
        }
        return new Placement(rects, bands);
    }

    private static void applyOffsets(Map<String, Rect> rects, Map<String, FloatPos> offsets) {
        for (var entry : offsets.entrySet()) {
            Rect rect = rects.get(entry.getKey());
            FloatPos offset = entry.getValue();
            if (rect == null || offset == null || !Double.isFinite(offset.x) || !Double.isFinite(offset.y)) {
                continue;
            }
            rect.x += (float) offset.x;
            rect.y += (float) offset.y;
        }
    }

    private static List<DagNodeBox> buildNodes(
        Map<String, PlanStep> byId,
        Map<String, Rect> rects,
        Map<String, Integer> layers,
        Map<String, Integer> laneOf,
        Map<String, String> laneIdOf,
        Map<String, LayoutGeometry.NodeSize> sizes
    ) {
        List<DagNodeBox> nodes = new ArrayList<>(byId.size());
        for (PlanStep step : byId.values()) {
            String stepId = step.stepId();
            Rect rect = rects.get(stepId);
            if (rect == null) {
                continue;
            }
            LayoutGeometry.NodeSize size = Objects.requireNonNull(sizes.get(stepId));
            int layer = layers.getOrDefault(stepId, 0);
            int laneIndex = laneOf.getOrDefault(stepId, 0);
            String laneId = laneIdOf.get(stepId);
            List<DagNodeBox> inner = size.expanded() ? innerLoopNodes(step, rect, layer, laneIndex, laneId) : List.of();
            nodes.add(
                new DagNodeBox(
                    stepId,
                    step,
                    layer,
                    laneIndex,
                    laneId,
                    LayoutGeometry.round(rect.x),
                    LayoutGeometry.round(rect.y),
                    LayoutGeometry.round(rect.width),
                    LayoutGeometry.round(rect.height),
                    size.expanded(),
                    inner,
                    DagNodeBox.DiffStatus.kept
                )
            );
        }
        return nodes;
    }

    private static List<DagNodeBox> innerLoopNodes(
        PlanStep step,
        Rect rect,
        int layer,
        int laneIndex,
        @Nullable String laneId
    ) {
        if (!(step instanceof CycleLoopStep loop) || loop.singleIterationTemplate().isEmpty()) {
            return List.of();
        }
        List<CraftStep> template = loop.singleIterationTemplate();
        List<DagNodeBox> inner = new ArrayList<>(template.size());
        float y = rect.y + LayoutGeometry.innerTopBar + LayoutGeometry.innerPadding;
        for (int index = 0; index < template.size(); index++) {
            CraftStep card = template.get(index);
            float x = rect.x
                    + LayoutGeometry.innerPadding
                    + index * (LayoutGeometry.innerCardWidth + LayoutGeometry.innerCardGap);
            inner.add(
                new DagNodeBox(
                    innerStepId(step.stepId(), card.stepId()),
                    card,
                    layer,
                    laneIndex,
                    laneId,
                    LayoutGeometry.round(x),
                    LayoutGeometry.round(y),
                    LayoutGeometry.innerCardWidth,
                    LayoutGeometry.innerCardHeight,
                    false,
                    List.of(),
                    DagNodeBox.DiffStatus.kept
                )
            );
        }
        return inner;
    }

    private static List<DagEdgeRoute> buildEdges(
        Map<String, PlanStep> byId,
        Map<String, List<String>> preds,
        List<DagNodeBox> nodes,
        CompiledRecipeGraph graph,
        DagLayoutMode mode,
        Set<String> critical,
        Set<String> expanded
    ) {
        Map<String, DagNodeBox> boxes = new HashMap<>();
        for (DagNodeBox node : nodes) {
            boxes.put(node.stepId(), node);
            for (DagNodeBox inner : node.innerLoopNodes()) {
                boxes.put(inner.stepId(), inner);
            }
        }
        Flow flow = new Flow(graph, byId);
        List<PendingEdge> pending = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PlanStep step : byId.values()) {
            String to = step.stepId();
            for (String dep : preds.getOrDefault(to, List.of())) {
                PlanStep source = byId.get(dep);
                if (source == null) {
                    continue;
                }
                collectPendingEdge(pending, seen, boxes, dep, to, dependencyEdge(flow, source, dep, to));
            }
            if (step instanceof CraftStep craft && !craft.catalystBindings().isEmpty()) {
                for (var catalyst : craft.catalystBindings().entrySet()) {
                    ComponentStackKey resource = catalyst.getValue();
                    if (resource == null) {
                        continue;
                    }
                    String producer = flow.producerOf(resource, preds.getOrDefault(to, List.of()), to);
                    if (producer == null) {
                        continue;
                    }
                    collectPendingEdge(
                        pending,
                        seen,
                        boxes,
                        producer,
                        to,
                        new EdgeSpec(
                            DagEdgeRoute.EdgeKind.catalyst,
                            resource,
                            flow.amount(producer, to, resource),
                            flow.producedTotal(producer, resource)
                        )
                    );
                }
            }
        }
        for (PlanStep step : byId.values()) {
            if (!(step instanceof CycleLoopStep loop) || !expanded.contains(step.stepId())) {
                continue;
            }
            List<DagNodeBox> inner = boxes.get(step.stepId()) == null
                    ? List.of()
                    : boxes.get(step.stepId()).innerLoopNodes();
            if (inner.size() < 2) {
                continue;
            }
            DagNodeBox first = inner.get(0);
            DagNodeBox last = inner.get(inner.size() - 1);
            collectPendingEdge(
                pending,
                seen,
                boxes,
                last.stepId(),
                first.stepId(),
                new EdgeSpec(DagEdgeRoute.EdgeKind.loopBack, null, 0L, 0L)
            );
        }
        return realizeEdges(pending, boxes, mode, critical);
    }

    private static EdgeSpec dependencyEdge(Flow flow, PlanStep source, String from, String to) {
        if (source instanceof LinePullStep pull) {
            return new EdgeSpec(
                DagEdgeRoute.EdgeKind.autoLine,
                pull.resource(),
                flow.amount(from, to, pull.resource()),
                flow.producedTotal(from, pull.resource())
            );
        }
        if (source instanceof ProvisionMachineStep) {
            return new EdgeSpec(DagEdgeRoute.EdgeKind.machine, null, 0L, 0L);
        }
        ComponentStackKey link = flow.link(from, to);
        long total = flow.producedTotal(from, link);
        long amount = link == null ? total : flow.amount(from, to, link);
        return new EdgeSpec(DagEdgeRoute.EdgeKind.material, link, amount, total);
    }

    private record PendingEdge(String from, String to, EdgeSpec spec) {}

    private static void collectPendingEdge(
        List<PendingEdge> pending,
        Set<String> seen,
        Map<String, DagNodeBox> boxes,
        String from,
        String to,
        EdgeSpec spec
    ) {
        DagNodeBox source = boxes.get(from);
        DagNodeBox target = boxes.get(to);
        if (source == null || target == null || from.equals(to)) {
            return;
        }
        String key = from + "|" + to + "|" + spec.kind().name() + "|"
                + (spec.resource() == null ? "" : spec.resource().canonicalKey());
        if (!seen.add(key)) {
            return;
        }
        pending.add(new PendingEdge(from, to, spec));
    }

    private static List<DagEdgeRoute> realizeEdges(
        List<PendingEdge> pending,
        Map<String, DagNodeBox> boxes,
        DagLayoutMode mode,
        Set<String> critical
    ) {
        Map<String, List<PendingEdge>> outgoing = new HashMap<>();
        Map<String, List<PendingEdge>> incoming = new HashMap<>();
        for (PendingEdge edge : pending) {
            if (edge.spec().kind() == DagEdgeRoute.EdgeKind.loopBack) {
                continue;
            }
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), ignored -> new ArrayList<>()).add(edge);
        }
        boolean vertical = mode.isVertical();
        Comparator<PendingEdge> byTargetPos = Comparator.comparingDouble((PendingEdge e) -> {
            DagNodeBox target = Objects.requireNonNull(boxes.get(e.to()));
            return vertical ? target.centerX() : target.centerY();
        }).thenComparing(PendingEdge::to).thenComparing(e -> e.spec().kind().ordinal())
                .thenComparing(e -> e.spec().resource() == null ? "" : e.spec().resource().canonicalKey());
        Comparator<PendingEdge> bySourcePos = Comparator.comparingDouble((PendingEdge e) -> {
            DagNodeBox source = Objects.requireNonNull(boxes.get(e.from()));
            return vertical ? source.centerX() : source.centerY();
        }).thenComparing(PendingEdge::from).thenComparing(e -> e.spec().kind().ordinal())
                .thenComparing(e -> e.spec().resource() == null ? "" : e.spec().resource().canonicalKey());
        for (List<PendingEdge> list : outgoing.values()) {
            list.sort(byTargetPos);
        }
        for (List<PendingEdge> list : incoming.values()) {
            list.sort(bySourcePos);
        }

        List<DagEdgeRoute> edges = new ArrayList<>(pending.size());
        for (PendingEdge item : pending) {
            DagNodeBox source = Objects.requireNonNull(boxes.get(item.from()));
            DagNodeBox target = Objects.requireNonNull(boxes.get(item.to()));
            List<PendingEdge> outList = outgoing.getOrDefault(item.from(), List.of());
            List<PendingEdge> inList = incoming.getOrDefault(item.to(), List.of());
            int outIndex = Math.max(0, outList.indexOf(item));
            int outCount = Math.max(1, outList.size());
            int inIndex = Math.max(0, inList.indexOf(item));
            int inCount = Math.max(1, inList.size());
            float[] geometry = edgeGeometry(
                mode,
                item.spec().kind(),
                source,
                target,
                outIndex,
                outCount,
                inIndex,
                inCount
            );
            edges.add(
                new DagEdgeRoute(
                    item.from(),
                    item.to(),
                    item.spec().kind(),
                    item.spec().resource(),
                    item.spec().amountFlow(),
                    item.spec().sourceTotalProduced(),
                    LayoutGeometry.round(geometry[0]),
                    LayoutGeometry.round(geometry[1]),
                    LayoutGeometry.round(geometry[2]),
                    LayoutGeometry.round(geometry[3]),
                    LayoutGeometry.round(geometry[4]),
                    LayoutGeometry.round(geometry[5]),
                    LayoutGeometry.round(geometry[6]),
                    LayoutGeometry.round(geometry[7]),
                    critical.contains(item.from()) && critical.contains(item.to()),
                    DagNodeBox.DiffStatus.kept
                )
            );
        }
        return edges;
    }

    private static float distributedPort(
        float start,
        float length,
        float leadInset,
        float tailInset,
        float idealStep,
        int index,
        int count
    ) {
        if (count <= 1) {
            return start + length * 0.5f;
        }
        float usableStart = start + leadInset;
        float usableSpan = Math.max(6.0f, length - leadInset - tailInset);
        float totalSpread = Math.min(usableSpan, (count - 1) * idealStep);
        float origin = usableStart + (usableSpan - totalSpread) * 0.5f;
        return origin + totalSpread * (index / (float) (count - 1));
    }

    private static float[] edgeGeometry(
        DagLayoutMode mode,
        DagEdgeRoute.EdgeKind kind,
        DagNodeBox source,
        DagNodeBox target,
        int outIndex,
        int outCount,
        int inIndex,
        int inCount
    ) {
        if (kind == DagEdgeRoute.EdgeKind.loopBack) {
            float x0 = source.centerX();
            float y0 = source.maxY();
            float x1 = target.centerX();
            float y1 = target.maxY();
            float bend = Math.max(loopControlMin, Math.abs(x1 - x0) * 0.25f);
            return new float[]{x0, y0, x0 + bend, y0 + loopSag, x1 - bend, y1 + loopSag, x1, y1};
        }
        if (mode == DagLayoutMode.verticalTb) {
            float x0 = distributedPort(source.x(), source.width(), 22f, 22f, 26f, outIndex, outCount);
            float y0 = source.maxY();
            float x1 = distributedPort(target.x(), target.width(), 22f, 22f, 26f, inIndex, inCount);
            float y1 = target.y();
            float control = Math.max(controlMin, Math.abs(y1 - y0) * 0.48f);
            return new float[]{x0, y0, x0, y0 + control, x1, y1 - control, x1, y1};
        }
        if (mode == DagLayoutMode.verticalBt) {
            float x0 = distributedPort(source.x(), source.width(), 22f, 22f, 26f, outIndex, outCount);
            float y0 = source.y();
            float x1 = distributedPort(target.x(), target.width(), 22f, 22f, 26f, inIndex, inCount);
            float y1 = target.maxY();
            float control = Math.max(controlMin, Math.abs(y1 - y0) * 0.48f);
            return new float[]{x0, y0, x0, y0 - control, x1, y1 + control, x1, y1};
        }
        if (mode == DagLayoutMode.horizontalRl) {
            float x0 = source.x();
            float y0 = distributedPort(source.y(), source.height(), 18f, 12f, 14f, outIndex, outCount);
            float x1 = target.maxX();
            float y1 = distributedPort(target.y(), target.height(), 18f, 12f, 14f, inIndex, inCount);
            float control = Math.max(controlMin, Math.abs(x1 - x0) * 0.48f);
            return new float[]{x0, y0, x0 - control, y0, x1 + control, y1, x1, y1};
        }
        float x0 = source.maxX();
        float y0 = distributedPort(source.y(), source.height(), 18f, 12f, 14f, outIndex, outCount);
        float x1 = target.x();
        float y1 = distributedPort(target.y(), target.height(), 18f, 12f, 14f, inIndex, inCount);
        float control = Math.max(controlMin, Math.abs(x1 - x0) * 0.48f);
        return new float[]{x0, y0, x0 + control, y0, x1 - control, y1, x1, y1};
    }

    private static PlanDagLayoutResult finish(
        List<DagNodeBox> nodes,
        List<DagEdgeRoute> edges,
        List<SwimlaneBand> bands
    ) {
        if (nodes.isEmpty()) {
            return new PlanDagLayoutResult(nodes, edges, bands, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (DagNodeBox node : nodes) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.maxX());
            maxY = Math.max(maxY, node.maxY());
        }
        for (DagEdgeRoute edge : edges) {
            minX = Math.min(minX, Math.min(Math.min(edge.x0(), edge.cx0()), Math.min(edge.cx1(), edge.x1())));
            minY = Math.min(minY, Math.min(Math.min(edge.y0(), edge.cy0()), Math.min(edge.cy1(), edge.y1())));
            maxX = Math.max(maxX, Math.max(Math.max(edge.x0(), edge.cx0()), Math.max(edge.cx1(), edge.x1())));
            maxY = Math.max(maxY, Math.max(Math.max(edge.y0(), edge.cy0()), Math.max(edge.cy1(), edge.y1())));
        }
        return new PlanDagLayoutResult(
            nodes,
            edges,
            bands,
            LayoutGeometry.round(minX),
            LayoutGeometry.round(minY),
            LayoutGeometry.round(maxX),
            LayoutGeometry.round(maxY)
        );
    }

    private static List<Lane> lanePlan(Map<String, PlanStep> byId, Map<String, Integer> layers) {
        List<String> sources = new ArrayList<>();
        List<String> machines = new ArrayList<>();
        TreeMap<String, List<String>> bySystem = new TreeMap<>();
        for (PlanStep step : byId.values()) {
            String stepId = step.stepId();
            switch (step) {
                case AcquireStep ignored -> sources.add(stepId);
                case LinePullStep ignored -> sources.add(stepId);
                case ProvisionMachineStep ignored -> machines.add(stepId);
                case SetupLineStep ignored -> machines.add(stepId);
                default -> {
                    Namespace systemId = LayoutGeometry.systemIdOf(step);
                    if (systemId != null) {
                        bySystem.computeIfAbsent(systemId.toString(), key -> new ArrayList<>()).add(stepId);
                    } else if (step instanceof CycleLoopStep loop) {
                        bySystem.computeIfAbsent(loop.macroKey().macroId(), key -> new ArrayList<>()).add(stepId);
                    } else {
                        sources.add(stepId);
                    }
                }
            }
        }
        Comparator<String> comparator = stepIdComparator(byId);
        sources.sort(Comparator.comparingInt((String id) -> layers.getOrDefault(id, 0)).thenComparing(comparator));
        machines.sort(Comparator.comparingInt((String id) -> layers.getOrDefault(id, 0)).thenComparing(comparator));

        List<Lane> lanes = new ArrayList<>();
        if (!sources.isEmpty()) {
            lanes.add(
                new Lane(
                    lanes.size(),
                    laneIdAcquire,
                    laneTitleKeyAcquire,
                    null,
                    sources,
                    totalHuman(byId, sources),
                    totalPassive(byId, sources)
                )
            );
        }
        if (!machines.isEmpty()) {
            lanes.add(
                new Lane(
                    lanes.size(),
                    laneIdMachines,
                    laneTitleKeyMachines,
                    firstMachine(byId, machines),
                    machines,
                    totalHuman(byId, machines),
                    totalPassive(byId, machines)
                )
            );
        }
        List<Map.Entry<String, List<String>>> systemLanes = new ArrayList<>(bySystem.entrySet());
        for (Map.Entry<String, List<String>> entry : systemLanes) {
            entry.getValue()
                    .sort(Comparator.comparingInt((String id) -> layers.getOrDefault(id, 0)).thenComparing(comparator));
        }
        systemLanes.sort(
            Comparator
                    .comparingDouble((Map.Entry<String, List<String>> entry) -> averageLayer(entry.getValue(), layers))
                    .thenComparing(Map.Entry::getKey)
        );
        for (Map.Entry<String, List<String>> entry : systemLanes) {
            List<String> stepIds = entry.getValue();
            lanes.add(
                new Lane(
                    lanes.size(),
                    entry.getKey(),
                    entry.getKey(),
                    firstMachine(byId, stepIds),
                    stepIds,
                    totalHuman(byId, stepIds),
                    totalPassive(byId, stepIds)
                )
            );
        }
        return lanes;
    }

    private static double averageLayer(List<String> stepIds, Map<String, Integer> layers) {
        if (stepIds.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        for (String stepId : stepIds) {
            sum += layers.getOrDefault(stepId, 0);
        }
        return (double) sum / stepIds.size();
    }

    private static double totalHuman(Map<String, PlanStep> byId, List<String> stepIds) {
        double total = 0.0;
        for (String stepId : stepIds) {
            total += Objects.requireNonNull(byId.get(stepId)).stepCost().humanActiveSeconds();
        }
        return total;
    }

    private static double totalPassive(Map<String, PlanStep> byId, List<String> stepIds) {
        double total = 0.0;
        for (String stepId : stepIds) {
            total += Objects.requireNonNull(byId.get(stepId)).stepCost().passiveWaitSeconds();
        }
        return total;
    }

    @Nullable
    private static MachineEntry firstMachine(Map<String, PlanStep> byId, List<String> stepIds) {
        for (String stepId : stepIds) {
            MachineEntry machine = LayoutGeometry.primaryMachineOf(Objects.requireNonNull(byId.get(stepId)));
            if (machine != null && !machine.isEmpty()) {
                return machine;
            }
        }
        return null;
    }

    private record Lane(
        int index,
        String laneId,
        String titleKey,
        @Nullable MachineEntry iconMachine,
        List<String> stepIds,
        double totalH,
        double totalP
    ) {
        private Lane {
            stepIds = List.copyOf(stepIds);
        }
    }

    private record Placement(Map<String, Rect> rects, List<SwimlaneBand> bands) {}

    private record EdgeSpec(
        DagEdgeRoute.EdgeKind kind,
        @Nullable ComponentStackKey resource,
        long amountFlow,
        long sourceTotalProduced
    ) {}

    private static final class Rect {
        private float x;
        private float y;
        private final float width;
        private final float height;

        private Rect(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private float maxX() {
            return x + width;
        }

        private float maxY() {
            return y + height;
        }
    }

    private static final class Flow {

        private final Map<String, Map<ComponentStackKey, Long>> produced = new TreeMap<>();
        private final Map<String, Map<ComponentStackKey, Long>> consumed = new TreeMap<>();

        private Flow(CompiledRecipeGraph graph, Map<String, PlanStep> byId) {
            for (PlanStep step : byId.values()) {
                produced.put(step.stepId(), producedOf(graph, step));
                consumed.put(step.stepId(), consumedOf(graph, step));
            }
        }

        private long producedTotal(@Nullable String stepId, @Nullable ComponentStackKey resource) {
            Map<ComponentStackKey, Long> amounts = stepId == null ? null : produced.get(stepId);
            if (amounts == null) {
                return 0L;
            }
            if (resource != null) {
                return amounts.getOrDefault(resource, 0L);
            }
            long total = 0L;
            for (long amount : amounts.values()) {
                total += amount;
            }
            return total;
        }

        private @Nullable ComponentStackKey link(String from, String to) {
            Map<ComponentStackKey, Long> source = produced.get(from);
            Map<ComponentStackKey, Long> target = consumed.get(to);
            if (source == null || target == null || source.isEmpty() || target.isEmpty()) {
                return null;
            }
            for (ComponentStackKey key : source.keySet()) {
                if (target.containsKey(key)) {
                    return key;
                }
            }
            for (ComponentStackKey key : source.keySet()) {
                for (ComponentStackKey candidate : target.keySet()) {
                    if (key.sameContent(candidate)) {
                        return key;
                    }
                }
            }
            return null;
        }

        private long amount(String from, String to, @Nullable ComponentStackKey resource) {
            long available = producedTotal(from, resource);
            if (resource == null) {
                return available;
            }
            Map<ComponentStackKey, Long> target = consumed.get(to);
            if (target == null) {
                return available;
            }
            Long needed = null;
            for (var entry : target.entrySet()) {
                if (entry.getKey().sameContent(resource)) {
                    needed = entry.getValue();
                    break;
                }
            }
            if (needed == null || needed <= 0L) {
                return available;
            }
            return Math.min(available, needed);
        }

        private @Nullable String producerOf(ComponentStackKey resource, List<String> preferred, String excluded) {
            for (String stepId : preferred) {
                if (!stepId.equals(excluded) && holds(stepId, resource)) {
                    return stepId;
                }
            }
            for (String stepId : produced.keySet()) {
                if (!stepId.equals(excluded) && holds(stepId, resource)) {
                    return stepId;
                }
            }
            return null;
        }

        private boolean holds(String stepId, ComponentStackKey resource) {
            Map<ComponentStackKey, Long> amounts = produced.get(stepId);
            if (amounts == null) {
                return false;
            }
            if (amounts.containsKey(resource)) {
                return true;
            }
            for (ComponentStackKey key : amounts.keySet()) {
                if (key.sameContent(resource)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Map<ComponentStackKey, Long> producedOf(CompiledRecipeGraph graph, PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> single(acquire.resource(), acquire.amount());
            case LinePullStep pull -> single(pull.resource(), pull.amount());
            case CraftStep craft -> recipeOutputs(graph, craft.recipeKey(), craft.batches());
            case ConvertStep convert -> recipeOutputs(graph, convert.conversionRecipeKey(), convert.batches());
            case CycleLoopStep loop -> cycleProduced(loop);
            case ProvisionMachineStep ignored -> Map.of();
            case SetupLineStep ignored -> Map.of();
        };
    }

    private static Map<ComponentStackKey, Long> consumedOf(CompiledRecipeGraph graph, PlanStep step) {
        return switch (step) {
            case CraftStep craft -> recipeInputs(graph, craft.recipeKey(), craft.slotBindings(), craft.batches());
            case ConvertStep convert ->
                recipeInputs(graph, convert.conversionRecipeKey(), convert.slotBindings(), convert.batches());
            case CycleLoopStep loop -> cycleConsumed(graph, loop);
            case AcquireStep ignored -> Map.of();
            case LinePullStep ignored -> Map.of();
            case ProvisionMachineStep ignored -> Map.of();
            case SetupLineStep ignored -> Map.of();
        };
    }

    private static Map<ComponentStackKey, Long> single(ComponentStackKey resource, long amount) {
        if (amount <= 0L) {
            return Map.of();
        }
        TreeMap<ComponentStackKey, Long> out = new TreeMap<>();
        out.put(resource, amount);
        return out;
    }

    private static Map<ComponentStackKey, Long> recipeOutputs(
        CompiledRecipeGraph graph,
        CompiledRecipeKey key,
        long batches
    ) {
        Optional<CompiledRecipe> recipe = graph.recipeByKey(key);
        if (recipe == null || recipe.isEmpty() || batches <= 0L) {
            return Map.of();
        }
        TreeMap<ComponentStackKey, Long> out = new TreeMap<>();
        for (OutputSlot slot : recipe.get().outputs()) {
            long amount = slot.amountPerBatch() * batches;
            if (amount > 0L) {
                out.merge(slot.resource(), amount, Long::sum);
            }
        }
        return out;
    }

    private static Map<ComponentStackKey, Long> recipeInputs(
        CompiledRecipeGraph graph,
        CompiledRecipeKey key,
        Map<Integer, SlotBinding> bindings,
        long batches
    ) {
        Optional<CompiledRecipe> recipe = graph.recipeByKey(key);
        if (recipe == null || recipe.isEmpty() || batches <= 0L) {
            return Map.of();
        }
        TreeMap<ComponentStackKey, Long> out = new TreeMap<>();
        for (InputSlot slot : recipe.get().inputs()) {
            ComponentStackKey chosen = resolveInput(bindings, slot);
            if (chosen == null) {
                continue;
            }
            long amount = slot.amountPerBatch() * batches;
            if (amount > 0L) {
                out.merge(chosen, amount, Long::sum);
            }
        }
        return out;
    }

    @Nullable
    private static ComponentStackKey resolveInput(Map<Integer, SlotBinding> bindings, InputSlot slot) {
        SlotBinding binding = bindings.get(slot.slotIndex());
        if (binding != null) {
            return binding.chosenResource();
        }
        return slot.candidates().size() == 1 ? slot.candidates().get(0) : null;
    }

    private static Map<ComponentStackKey, Long> cycleProduced(CycleLoopStep loop) {
        TreeMap<ComponentStackKey, Long> out = new TreeMap<>();
        for (var entry : loop.netDeltaPerIteration().entrySet()) {
            long amount = entry.getValue() * loop.iterations();
            if (amount > 0L) {
                out.merge(entry.getKey(), amount, Long::sum);
            }
        }
        return out;
    }

    private static Map<ComponentStackKey, Long> cycleConsumed(CompiledRecipeGraph graph, CycleLoopStep loop) {
        TreeMap<ComponentStackKey, Long> out = new TreeMap<>();
        for (CraftStep template : loop.singleIterationTemplate()) {
            Map<ComponentStackKey, Long> perBatch = recipeInputs(
                graph,
                template.recipeKey(),
                template.slotBindings(),
                template.batches()
            );
            for (var entry : perBatch.entrySet()) {
                out.merge(entry.getKey(), entry.getValue() * loop.iterations(), Long::sum);
            }
        }
        return out;
    }
}

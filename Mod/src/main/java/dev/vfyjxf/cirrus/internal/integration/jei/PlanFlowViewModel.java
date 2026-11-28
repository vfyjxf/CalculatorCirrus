package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure headless view-model computations for the JEI Plan Flow tooltip and recipe layout:
 * adaptive graphics-first DAG layout with port distribution, unified material & progress dock, and workstation prep.
 */
final class PlanFlowViewModel {

    static final String handCraftStationId = "cirrus:hand_craft";
    static final int nodeHeight = 38;
    static final int leafPodWidth = 36;
    static final int machinePodWidth = 38;

    private PlanFlowViewModel() {}

    enum QuantityUnitKind {
        itemStack, fluidBucket, plain
    }

    record ResourceUnitSpec(QuantityUnitKind kind, long unitSize) {
        static final ResourceUnitSpec defaultItem = new ResourceUnitSpec(QuantityUnitKind.itemStack, 64L);
        static final ResourceUnitSpec fluid = new ResourceUnitSpec(QuantityUnitKind.fluidBucket, 1000L);
        static final ResourceUnitSpec plain = new ResourceUnitSpec(QuantityUnitKind.plain, 1L);

        boolean canConvert() {
            return kind == QuantityUnitKind.fluidBucket || (kind == QuantityUnitKind.itemStack && unitSize > 1L);
        }
    }

    enum MaterialFilter {
        all, missing, steps;

        MaterialFilter next() {
            return switch (this) {
                case all -> missing;
                case missing -> steps;
                case steps -> all;
            };
        }
    }

    enum MaterialSort {
        missingFirst, amountDesc, readyFirst;

        MaterialSort next() {
            return switch (this) {
                case missingFirst -> amountDesc;
                case amountDesc -> readyFirst;
                case readyFirst -> missingFirst;
            };
        }
    }

    enum EdgeKind {
        material, machine, autoLine
    }

    record DagNodeLayout(
        CirrusPlanBookmarkStore.StepLine step,
        int layer,
        int worldX,
        int worldY,
        int w,
        int h,
        boolean finalTarget,
        boolean hasInputs,
        boolean hasOutputs
    ) {}

    record DagEdgeLayout(
        DagNodeLayout from,
        DagNodeLayout to,
        float x0,
        float y0,
        float x1,
        float y1,
        float channelRatio,
        EdgeKind kind,
        @Nullable ComponentStackKey resource
    ) {}

    record DagLayout(
        List<DagNodeLayout> nodes,
        List<DagEdgeLayout> edges,
        int stageCount,
        int worldW,
        int worldH,
        boolean vertical
    ) {}

    record MaterialRow(
        ComponentStackKey resource,
        long needed,
        long have,
        long missing,
        double ratio
    ) {
        boolean ready() {
            return missing == 0L;
        }
    }

    record StepProgressRow(
        int orderIndex,
        int stageIndex,
        CirrusPlanBookmarkStore.StepLine step,
        long scaledAmount,
        long scaledBatches,
        boolean done
    ) {}

    record MaterialDockModel(
        List<MaterialRow> visibleMaterials,
        List<StepProgressRow> steps,
        int totalMaterials,
        int readyMaterials,
        int missingMaterials,
        double coverageRatio,
        int checkedSteps,
        int totalSteps
    ) {}

    record StationGroup(
        @Nullable MachineEntry machine,
        boolean handCraft,
        ComponentStackKey iconResource,
        int stepCount,
        long totalBatches,
        double totalWorkSeconds,
        double totalWaitSeconds,
        int toBuildCount,
        boolean automated,
        List<ComponentStackKey> sampleOutputs,
        List<String> stepIds
    ) {
        double totalSeconds() {
            return totalWorkSeconds + totalWaitSeconds;
        }
    }

    record StationsPrepSummary(
        List<StationGroup> stations,
        List<ComponentStackKey> catalysts,
        List<CirrusPlanBookmarkStore.MaterialLine> surplusByproducts,
        List<String> automatedLines,
        int machinesToBuildTotal
    ) {}

    static long scaleAmount(long base, int scaleMultiplier) {
        long safeBase = Math.max(1L, base);
        int mult = Math.max(1, scaleMultiplier);
        if (mult == 1) {
            return safeBase;
        }
        try {
            return Math.multiplyExact(safeBase, mult);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    static double scaleDuration(double seconds, int scaleMultiplier) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) {
            return 0.0;
        }
        return seconds * Math.max(1, scaleMultiplier);
    }

    static String formatStackBreakdown(long amount, ResourceUnitSpec spec, String stackUnitLabel) {
        long safe = Math.abs(amount);
        if (spec.kind() == QuantityUnitKind.fluidBucket) {
            long unit = Math.max(1L, spec.unitSize());
            if (safe < unit) {
                return "";
            }
            long buckets = safe / unit;
            long rem = safe % unit;
            if (rem == 0L) {
                return WorkbenchFormatters.formatAmount(buckets) + "B";
            }
            if (unit == 1000L && rem % 100L == 0L && buckets < 1000L) {
                return buckets + "." + (rem / 100L) + "B";
            }
            return WorkbenchFormatters.formatAmount(buckets) + "B+" + rem;
        }
        if (spec.kind() == QuantityUnitKind.itemStack && spec.unitSize() > 1L && safe >= spec.unitSize()) {
            long stacks = safe / spec.unitSize();
            long rem = safe % spec.unitSize();
            String unit = (stackUnitLabel == null || stackUnitLabel.isEmpty())
                    ? ("\u00d7" + spec.unitSize())
                    : stackUnitLabel;
            String base = WorkbenchFormatters.formatAmount(stacks) + unit;
            return rem == 0L ? base : (base + "+" + rem);
        }
        return "";
    }

    static String formatRawQuantity(long amount, ResourceUnitSpec spec, boolean compact) {
        long safe = Math.abs(amount);
        if (spec.kind() == QuantityUnitKind.fluidBucket) {
            String num = safe < 1_000_000L ? Long.toString(safe) : WorkbenchFormatters.formatAmount(safe);
            return compact ? (num + "mB") : (num + " mB");
        }
        return WorkbenchFormatters.formatAmount(safe);
    }

    static String formatSlotQuantity(long amount, ResourceUnitSpec spec, boolean stackMode, String stackUnitLabel) {
        if (stackMode) {
            String stacked = formatStackBreakdown(amount, spec, stackUnitLabel);
            if (!stacked.isEmpty()) {
                return stacked;
            }
        }
        return formatRawQuantity(amount, spec, true);
    }

    static String formatTooltipQuantity(long amount, ResourceUnitSpec spec, boolean stackMode, String stackUnitLabel) {
        String raw = formatRawQuantity(amount, spec, false);
        String stacked = formatStackBreakdown(amount, spec, stackUnitLabel);
        if (stacked.isEmpty()) {
            return raw;
        }
        String stackDetail = (spec.kind() == QuantityUnitKind.itemStack && stackUnitLabel != null && !stackUnitLabel.isEmpty())
                ? (stacked + " \u00b7 \u00d7" + spec.unitSize())
                : stacked;
        return stackMode ? (stackDetail + " (" + raw + ")") : (raw + " (" + stackDetail + ")");
    }

    static int preferredNodeWidth(CirrusPlanBookmarkStore.StepLine step) {
        String kind = step.kind();
        if ("AcquireStep".equals(kind) || "LinePullStep".equals(kind)) {
            return leafPodWidth;
        }
        if ("ProvisionMachineStep".equals(kind) || "SetupLineStep".equals(kind)) {
            return machinePodWidth;
        }
        int inputs = step.inputs().size();
        if (inputs <= 0) {
            return machinePodWidth;
        }
        int shownCols = Math.min(3, inputs);
        int inputMatrixW = 18 + (shownCols - 1) * 17;
        // 5px left + inputMatrix + 20px machine/arrow bridge + 22px output slot + 5px right
        return 5 + inputMatrixW + 20 + 22 + 5;
    }

    static DagLayout computeDagLayout(List<CirrusPlanBookmarkStore.StepLine> steps, boolean vertical) {
        if (steps.isEmpty()) {
            return new DagLayout(List.of(), List.of(), 0, 120, 60, vertical);
        }
        Map<String, CirrusPlanBookmarkStore.StepLine> byId = new LinkedHashMap<>();
        boolean hasExplicitDeps = false;
        for (CirrusPlanBookmarkStore.StepLine s : steps) {
            byId.put(s.stepId(), s);
            if (!s.dependsOn().isEmpty()) {
                hasExplicitDeps = true;
            }
        }

        Map<String, Integer> layerMemo = computeLayers(steps, byId, hasExplicitDeps);
        int maxLayer = 0;
        for (int l : layerMemo.values()) {
            if (l > maxLayer) {
                maxLayer = l;
            }
        }

        List<List<CirrusPlanBookmarkStore.StepLine>> layers = new ArrayList<>(maxLayer + 1);
        for (int l = 0; l <= maxLayer; l++) {
            layers.add(new ArrayList<>());
        }
        for (CirrusPlanBookmarkStore.StepLine s : steps) {
            layers.get(layerMemo.getOrDefault(s.stepId(), 0)).add(s);
        }
        List<List<CirrusPlanBookmarkStore.StepLine>> compactLayers = new ArrayList<>();
        for (List<CirrusPlanBookmarkStore.StepLine> layer : layers) {
            if (!layer.isEmpty()) {
                compactLayers.add(new ArrayList<>(layer));
            }
        }

        List<String[]> rawEdges = new ArrayList<>();
        Map<String, List<String>> preds = new HashMap<>();
        Map<String, List<String>> succs = new HashMap<>();
        Set<String> hasIn = new HashSet<>();
        Set<String> hasOut = new HashSet<>();
        if (hasExplicitDeps) {
            for (CirrusPlanBookmarkStore.StepLine dst : steps) {
                for (String depId : dst.dependsOn()) {
                    if (byId.containsKey(depId) && !depId.equals(dst.stepId())) {
                        rawEdges.add(new String[]{depId, dst.stepId()});
                        preds.computeIfAbsent(dst.stepId(), k -> new ArrayList<>()).add(depId);
                        succs.computeIfAbsent(depId, k -> new ArrayList<>()).add(dst.stepId());
                        hasOut.add(depId);
                        hasIn.add(dst.stepId());
                    }
                }
            }
        } else {
            for (int li = 0; li < compactLayers.size() - 1; li++) {
                List<CirrusPlanBookmarkStore.StepLine> cur = compactLayers.get(li);
                List<CirrusPlanBookmarkStore.StepLine> next = compactLayers.get(li + 1);
                for (int i = 0; i < cur.size(); i++) {
                    String srcId = cur.get(i).stepId();
                    String dstId = next.get(Math.min(i, next.size() - 1)).stepId();
                    rawEdges.add(new String[]{srcId, dstId});
                    preds.computeIfAbsent(dstId, k -> new ArrayList<>()).add(srcId);
                    succs.computeIfAbsent(srcId, k -> new ArrayList<>()).add(dstId);
                    hasOut.add(srcId);
                    hasIn.add(dstId);
                }
            }
        }

        for (int round = 0; round < 6; round++) {
            Map<String, Integer> order = buildIndexMap(compactLayers);
            for (int li = 1; li < compactLayers.size(); li++) {
                sortLayerByBarycenter(compactLayers.get(li), preds, order);
                order = buildIndexMap(compactLayers);
            }
            for (int li = compactLayers.size() - 2; li >= 0; li--) {
                sortLayerByBarycenter(compactLayers.get(li), succs, order);
                order = buildIndexMap(compactLayers);
            }
        }

        int pad = 12;
        Map<String, Integer> nodeWMap = new HashMap<>(steps.size());
        for (CirrusPlanBookmarkStore.StepLine s : steps) {
            nodeWMap.put(s.stepId(), preferredNodeWidth(s));
        }

        int layerCount = compactLayers.size();
        int[] layerMajorStart = new int[layerCount];
        int[] layerMajorSpan = new int[layerCount];
        int majorGap = vertical ? 24 : 30;
        int minorGap = vertical ? 12 : 10;

        int cursorMajor = pad;
        for (int li = 0; li < layerCount; li++) {
            int span = nodeHeight;
            if (!vertical) {
                span = leafPodWidth;
                for (CirrusPlanBookmarkStore.StepLine s : compactLayers.get(li)) {
                    span = Math.max(span, nodeWMap.getOrDefault(s.stepId(), leafPodWidth));
                }
            }
            layerMajorStart[li] = cursorMajor;
            layerMajorSpan[li] = span;
            cursorMajor += span + (li + 1 < layerCount ? majorGap : 0);
        }
        int worldMajor = cursorMajor + pad;

        int maxLayerMinorSpan = 0;
        int[] layerMinorSpans = new int[layerCount];
        for (int li = 0; li < layerCount; li++) {
            List<CirrusPlanBookmarkStore.StepLine> layer = compactLayers.get(li);
            int total = 0;
            for (int i = 0; i < layer.size(); i++) {
                total += vertical ? nodeWMap.getOrDefault(layer.get(i).stepId(), leafPodWidth) : nodeHeight;
                if (i + 1 < layer.size()) {
                    total += minorGap;
                }
            }
            layerMinorSpans[li] = total;
            maxLayerMinorSpan = Math.max(maxLayerMinorSpan, total);
        }

        Map<String, Integer> minorPos = new HashMap<>(steps.size());
        for (int li = 0; li < layerCount; li++) {
            List<CirrusPlanBookmarkStore.StepLine> layer = compactLayers.get(li);
            int cur = pad + (maxLayerMinorSpan - layerMinorSpans[li]) / 2;
            for (CirrusPlanBookmarkStore.StepLine s : layer) {
                minorPos.put(s.stepId(), cur);
                cur += (vertical ? nodeWMap.getOrDefault(s.stepId(), leafPodWidth) : nodeHeight) + minorGap;
            }
        }

        for (int pass = 0; pass < 3; pass++) {
            for (int li = 1; li < layerCount; li++) {
                alignMinorAxis(compactLayers.get(li), preds, minorPos, nodeWMap, vertical, minorGap, pad);
            }
            for (int li = layerCount - 2; li >= 0; li--) {
                alignMinorAxis(compactLayers.get(li), succs, minorPos, nodeWMap, vertical, minorGap, pad);
            }
        }

        int minMinor = Integer.MAX_VALUE;
        int maxMinor = Integer.MIN_VALUE;
        for (CirrusPlanBookmarkStore.StepLine s : steps) {
            int p = minorPos.getOrDefault(s.stepId(), pad);
            int sz = vertical ? nodeWMap.getOrDefault(s.stepId(), leafPodWidth) : nodeHeight;
            minMinor = Math.min(minMinor, p);
            maxMinor = Math.max(maxMinor, p + sz);
        }
        int shiftMinor = minMinor == Integer.MAX_VALUE ? 0 : (pad - minMinor);
        int worldMinor = Math.max(maxLayerMinorSpan + pad * 2, (maxMinor + shiftMinor) + pad);
        int worldW = vertical ? worldMinor : worldMajor;
        int worldH = vertical ? worldMajor : worldMinor;

        String lastStepId = steps.get(steps.size() - 1).stepId();
        List<DagNodeLayout> nodes = new ArrayList<>(steps.size());
        Map<String, DagNodeLayout> nodeById = new HashMap<>(steps.size());
        for (int li = 0; li < layerCount; li++) {
            for (CirrusPlanBookmarkStore.StepLine step : compactLayers.get(li)) {
                int w = nodeWMap.getOrDefault(step.stepId(), leafPodWidth);
                int h = nodeHeight;
                int minorCoord = minorPos.getOrDefault(step.stepId(), pad) + shiftMinor;
                int x = vertical ? minorCoord : layerMajorStart[li] + (layerMajorSpan[li] - w) / 2;
                int y = vertical ? layerMajorStart[li] : minorCoord;
                boolean isTarget = step.stepId().equals(lastStepId)
                        || (li == layerCount - 1 && !hasOut.contains(step.stepId()));
                DagNodeLayout layout = new DagNodeLayout(
                    step,
                    li,
                    x,
                    y,
                    w,
                    h,
                    isTarget,
                    hasIn.contains(step.stepId()),
                    hasOut.contains(step.stepId())
                );
                nodes.add(layout);
                nodeById.put(step.stepId(), layout);
            }
        }

        List<DagEdgeLayout> edges = buildRoutedEdges(rawEdges, nodeById, vertical);
        return new DagLayout(
            Collections.unmodifiableList(nodes),
            Collections.unmodifiableList(edges),
            layerCount,
            worldW,
            worldH,
            vertical
        );
    }

    private static List<DagEdgeLayout> buildRoutedEdges(
        List<String[]> rawEdges,
        Map<String, DagNodeLayout> nodeById,
        boolean vertical
    ) {
        if (rawEdges.isEmpty()) {
            return List.of();
        }
        Map<String, List<String[]>> outgoing = new HashMap<>();
        Map<String, List<String[]>> incoming = new HashMap<>();
        for (String[] pair : rawEdges) {
            if (nodeById.containsKey(pair[0]) && nodeById.containsKey(pair[1])) {
                outgoing.computeIfAbsent(pair[0], k -> new ArrayList<>()).add(pair);
                incoming.computeIfAbsent(pair[1], k -> new ArrayList<>()).add(pair);
            }
        }
        Comparator<String[]> byDstCenter = Comparator.comparingInt(pair -> {
            DagNodeLayout dst = nodeById.get(pair[1]);
            return dst == null ? 0 : (vertical ? dst.worldX() + dst.w() / 2 : dst.worldY() + dst.h() / 2);
        });
        Comparator<String[]> bySrcCenter = Comparator.comparingInt(pair -> {
            DagNodeLayout src = nodeById.get(pair[0]);
            return src == null ? 0 : (vertical ? src.worldX() + src.w() / 2 : src.worldY() + src.h() / 2);
        });
        for (List<String[]> list : outgoing.values()) {
            list.sort(byDstCenter);
        }
        for (List<String[]> list : incoming.values()) {
            list.sort(bySrcCenter);
        }

        // Group edges by corridor so vertical/horizontal channel bends fan out cleanly without overlap.
        Map<Integer, List<String[]>> corridors = new HashMap<>();
        for (String[] pair : rawEdges) {
            DagNodeLayout src = nodeById.get(pair[0]);
            DagNodeLayout dst = nodeById.get(pair[1]);
            if (src != null && dst != null) {
                int key = src.layer() * 37 + dst.layer();
                corridors.computeIfAbsent(key, k -> new ArrayList<>()).add(pair);
            }
        }
        Map<String[], Float> channelRatioMap = new HashMap<>();
        for (List<String[]> bucket : corridors.values()) {
            bucket.sort(bySrcCenter.thenComparing(byDstCenter));
            int count = bucket.size();
            if (count == 1) {
                channelRatioMap.put(bucket.get(0), 0.50f);
            } else {
                float span = Math.min(0.60f, (count - 1) * 0.14f);
                float start = 0.50f - span * 0.5f;
                for (int i = 0; i < count; i++) {
                    channelRatioMap.put(bucket.get(i), start + span * (i / (float) (count - 1)));
                }
            }
        }

        List<DagEdgeLayout> edges = new ArrayList<>(rawEdges.size());
        for (String[] pair : rawEdges) {
            DagNodeLayout src = nodeById.get(pair[0]);
            DagNodeLayout dst = nodeById.get(pair[1]);
            if (src == null || dst == null) {
                continue;
            }
            List<String[]> outList = outgoing.getOrDefault(pair[0], List.of());
            List<String[]> inList = incoming.getOrDefault(pair[1], List.of());
            float outOff = portOffset(outList.indexOf(pair), outList.size(), vertical ? src.w() : src.h());
            float inOff = portOffset(inList.indexOf(pair), inList.size(), vertical ? dst.w() : dst.h());

            float x0 = vertical ? (src.worldX() + src.w() * 0.5f + outOff) : (src.worldX() + src.w());
            float y0 = vertical ? (src.worldY() + src.h()) : (src.worldY() + src.h() * 0.5f + outOff);
            float x1 = vertical ? (dst.worldX() + dst.w() * 0.5f + inOff) : dst.worldX();
            float y1 = vertical ? dst.worldY() : (dst.worldY() + dst.h() * 0.5f + inOff);

            EdgeKind kind = edgeKindFor(src.step());
            float ratio = channelRatioMap.getOrDefault(pair, 0.50f);
            edges.add(new DagEdgeLayout(src, dst, x0, y0, x1, y1, ratio, kind, src.step().output()));
        }
        return edges;
    }

    private static float portOffset(int index, int count, int edgeSpan) {
        if (count <= 1 || index < 0) {
            return 0f;
        }
        float maxSpread = Math.min(12f, Math.max(4f, edgeSpan * 0.36f));
        float step = Math.min(6f, (maxSpread * 2f) / (count - 1));
        float total = step * (count - 1);
        return -total * 0.5f + index * step;
    }

    private static EdgeKind edgeKindFor(CirrusPlanBookmarkStore.StepLine src) {
        if ("ProvisionMachineStep".equals(src.kind()) || "SetupLineStep".equals(src.kind())) {
            return EdgeKind.machine;
        }
        if ("LinePullStep".equals(src.kind()) || src.auto()) {
            return EdgeKind.autoLine;
        }
        return EdgeKind.material;
    }

    static MaterialDockModel buildMaterials(
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
        InventorySnapshot inv,
        int scaleMultiplier,
        MaterialFilter filter,
        MaterialSort sort
    ) {
        int mult = Math.max(1, scaleMultiplier);
        List<CirrusPlanBookmarkStore.MaterialLine> raw = entry.rawMaterials();
        List<MaterialRow> allRows = new ArrayList<>(raw.size());
        int readyCount = 0;
        long totalNeededUnits = 0L;
        long coveredUnits = 0L;
        for (CirrusPlanBookmarkStore.MaterialLine m : raw) {
            long needed = scaleAmount(m.needed(), mult);
            long have = Math.max(0L, inv.usableAmount(m.resource()));
            long missing = Math.max(0L, needed - have);
            double ratio = Math.max(0.0, Math.min(1.0, (double) have / (double) needed));
            if (missing == 0L) {
                readyCount++;
            }
            totalNeededUnits = safeAdd(totalNeededUnits, needed);
            coveredUnits = safeAdd(coveredUnits, Math.min(have, needed));
            allRows.add(new MaterialRow(m.resource(), needed, have, missing, ratio));
        }

        double coverage = totalNeededUnits <= 0L
                ? 1.0
                : Math.max(0.0, Math.min(1.0, (double) coveredUnits / (double) totalNeededUnits));

        List<MaterialRow> filtered = new ArrayList<>(allRows.size());
        for (MaterialRow row : allRows) {
            if (filter != MaterialFilter.missing || row.missing() > 0L) {
                filtered.add(row);
            }
        }
        Comparator<MaterialRow> comparator = switch (sort) {
            case missingFirst -> Comparator.comparingLong(MaterialRow::missing).reversed()
                    .thenComparingDouble(MaterialRow::ratio)
                    .thenComparing(Comparator.comparingLong(MaterialRow::needed).reversed())
                    .thenComparing(MaterialRow::resource);
            case amountDesc -> Comparator.comparingLong(MaterialRow::needed).reversed()
                    .thenComparing(Comparator.comparingLong(MaterialRow::missing).reversed())
                    .thenComparing(MaterialRow::resource);
            case readyFirst -> Comparator.comparing(MaterialRow::ready).reversed()
                    .thenComparing(Comparator.comparingDouble(MaterialRow::ratio).reversed())
                    .thenComparing(Comparator.comparingLong(MaterialRow::needed).reversed())
                    .thenComparing(MaterialRow::resource);
        };
        filtered.sort(comparator);

        Map<String, Integer> stageById = computeStageMap(entry.steps());
        Set<String> checked = entry.checkedStepIds();
        List<CirrusPlanBookmarkStore.StepLine> orderedSteps = new ArrayList<>(entry.steps());
        orderedSteps.sort(
            Comparator.comparingInt((CirrusPlanBookmarkStore.StepLine s) -> stageById.getOrDefault(s.stepId(), 0))
                    .thenComparing(CirrusPlanBookmarkStore.StepLine::stepId)
        );
        List<StepProgressRow> stepRows = new ArrayList<>(orderedSteps.size());
        int checkedCount = 0;
        for (int i = 0; i < orderedSteps.size(); i++) {
            CirrusPlanBookmarkStore.StepLine s = orderedSteps.get(i);
            boolean done = checked.contains(s.stepId());
            if (done) {
                checkedCount++;
            }
            stepRows.add(
                new StepProgressRow(
                    i + 1,
                    stageById.getOrDefault(s.stepId(), 0),
                    s,
                    scaleAmount(s.amount(), mult),
                    scaleAmount(s.batches(), mult),
                    done
                )
            );
        }

        return new MaterialDockModel(
            Collections.unmodifiableList(filtered),
            Collections.unmodifiableList(stepRows),
            raw.size(),
            readyCount,
            Math.max(0, raw.size() - readyCount),
            coverage,
            checkedCount,
            orderedSteps.size()
        );
    }

    static StationsPrepSummary buildStationsPrep(
        CirrusPlanBookmarkStore.PlanBookmarkEntry entry,
        int scaleMultiplier
    ) {
        int mult = Math.max(1, scaleMultiplier);
        Map<MachineEntry, Integer> toBuildByMachine = new LinkedHashMap<>();
        int totalToBuild = 0;
        for (CirrusPlanBookmarkStore.MachineLine ml : entry.machinesToProvision()) {
            if (ml.countToBuild() > 0 && !ml.machine().isEmpty()) {
                toBuildByMachine.merge(ml.machine(), ml.countToBuild(), Integer::sum);
                totalToBuild += ml.countToBuild();
            }
        }

        final class Acc {
            final @Nullable MachineEntry machine;
            final boolean handCraft;
            final ComponentStackKey iconResource;
            int stepCount;
            long totalBatches;
            double totalWork;
            double totalWait;
            int toBuild;
            boolean automated;
            final LinkedHashSet<ComponentStackKey> outputs = new LinkedHashSet<>();
            final List<String> stepIds = new ArrayList<>();

            Acc(@Nullable MachineEntry machine, boolean handCraft, ComponentStackKey iconResource) {
                this.machine = machine;
                this.handCraft = handCraft;
                this.iconResource = iconResource;
            }
        }

        Map<String, Acc> groups = new LinkedHashMap<>();
        for (CirrusPlanBookmarkStore.StepLine step : entry.steps()) {
            if ("AcquireStep".equals(step.kind())) {
                continue;
            }
            boolean isProvision = "ProvisionMachineStep".equals(step.kind());
            boolean isLine = "LinePullStep".equals(step.kind()) || "SetupLineStep".equals(step.kind());
            MachineEntry machine = step.machine();
            if ((machine == null || machine.isEmpty()) && isProvision && step.output() != null) {
                machine = MachineEntry.fromKey(step.output());
            }
            boolean handCraft = machine == null || machine.isEmpty();
            String stationKey = handCraft ? handCraftStationId : machine.canonicalKey();
            MachineEntry resolvedMachine = handCraft ? null : machine;
            ComponentStackKey iconKey = handCraft
                    ? craftingTableIcon()
                    : resolvedMachine.contentKey().orElseGet(PlanFlowViewModel::craftingTableIcon);
            Acc acc = groups.computeIfAbsent(stationKey, k -> new Acc(resolvedMachine, handCraft, iconKey));
            acc.stepCount++;
            acc.totalBatches = safeAdd(acc.totalBatches, scaleAmount(step.batches(), mult));
            acc.totalWork += scaleDuration(step.workSeconds(), mult);
            acc.totalWait += scaleDuration(step.waitSeconds(), mult);
            if (step.auto() || isLine) {
                acc.automated = true;
            }
            if (isProvision) {
                acc.toBuild = Math.max(acc.toBuild, (int) Math.min(Integer.MAX_VALUE, step.batches()));
            }
            if (step.output() != null && acc.outputs.size() < 8) {
                acc.outputs.add(step.output());
            }
            acc.stepIds.add(step.stepId());
        }

        for (Map.Entry<MachineEntry, Integer> buildEntry : toBuildByMachine.entrySet()) {
            MachineEntry machine = buildEntry.getKey();
            int count = buildEntry.getValue();
            ComponentStackKey iconKey = machine.contentKey().orElseGet(PlanFlowViewModel::craftingTableIcon);
            Acc acc = groups.computeIfAbsent(machine.canonicalKey(), k -> new Acc(machine, false, iconKey));
            acc.toBuild = Math.max(acc.toBuild, count);
        }

        List<StationGroup> stations = new ArrayList<>(groups.size());
        for (Acc acc : groups.values()) {
            stations.add(
                new StationGroup(
                    acc.machine,
                    acc.handCraft,
                    acc.iconResource,
                    acc.stepCount,
                    acc.totalBatches,
                    acc.totalWork,
                    acc.totalWait,
                    acc.toBuild,
                    acc.automated,
                    List.copyOf(acc.outputs),
                    List.copyOf(acc.stepIds)
                )
            );
        }
        stations.sort(
            Comparator.comparing((StationGroup s) -> s.toBuildCount() > 0).reversed()
                    .thenComparing(StationGroup::handCraft)
                    .thenComparing(Comparator.comparingDouble(StationGroup::totalSeconds).reversed())
                    .thenComparing(Comparator.comparingLong(StationGroup::totalBatches).reversed())
                    .thenComparing(StationGroup::machine, Comparator.nullsLast(MachineEntry.comparator))
        );

        List<CirrusPlanBookmarkStore.MaterialLine> scaledSurplus = new ArrayList<>(entry.surplusByproducts().size());
        for (CirrusPlanBookmarkStore.MaterialLine surp : entry.surplusByproducts()) {
            scaledSurplus.add(new CirrusPlanBookmarkStore.MaterialLine(surp.resource(), scaleAmount(surp.needed(), mult)));
        }

        return new StationsPrepSummary(
            Collections.unmodifiableList(stations),
            entry.catalysts(),
            Collections.unmodifiableList(scaledSurplus),
            entry.automatedLines(),
            totalToBuild
        );
    }

    private static ComponentStackKey craftingTableIcon() {
        return ComponentStackKey.ofItem(Items.CRAFTING_TABLE);
    }

    private static Map<String, Integer> computeStageMap(List<CirrusPlanBookmarkStore.StepLine> steps) {
        Map<String, CirrusPlanBookmarkStore.StepLine> byId = new LinkedHashMap<>();
        boolean hasExplicitDeps = false;
        for (CirrusPlanBookmarkStore.StepLine s : steps) {
            byId.put(s.stepId(), s);
            if (!s.dependsOn().isEmpty()) {
                hasExplicitDeps = true;
            }
        }
        return computeLayers(steps, byId, hasExplicitDeps);
    }

    private static Map<String, Integer> computeLayers(
        List<CirrusPlanBookmarkStore.StepLine> steps,
        Map<String, CirrusPlanBookmarkStore.StepLine> byId,
        boolean hasExplicitDeps
    ) {
        Map<String, Integer> memo = new HashMap<>(steps.size());
        if (hasExplicitDeps) {
            Set<String> visiting = new HashSet<>();
            for (CirrusPlanBookmarkStore.StepLine s : steps) {
                resolveLayer(s, byId, memo, visiting);
            }
        } else {
            for (int i = 0; i < steps.size(); i++) {
                CirrusPlanBookmarkStore.StepLine s = steps.get(i);
                int stage = (i == steps.size() - 1 && steps.size() > 1) ? 3 : stageOf(s.kind());
                memo.put(s.stepId(), stage);
            }
        }
        return memo;
    }

    private static int resolveLayer(
        CirrusPlanBookmarkStore.StepLine step,
        Map<String, CirrusPlanBookmarkStore.StepLine> byId,
        Map<String, Integer> memo,
        Set<String> visiting
    ) {
        Integer cached = memo.get(step.stepId());
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(step.stepId())) {
            return 0;
        }
        int best = 0;
        for (String depId : step.dependsOn()) {
            CirrusPlanBookmarkStore.StepLine dep = byId.get(depId);
            if (dep != null) {
                int depLayer = resolveLayer(dep, byId, memo, visiting) + 1;
                if (depLayer > best) {
                    best = depLayer;
                }
            }
        }
        visiting.remove(step.stepId());
        memo.put(step.stepId(), best);
        return best;
    }

    private static Map<String, Integer> buildIndexMap(List<List<CirrusPlanBookmarkStore.StepLine>> layers) {
        Map<String, Integer> map = new HashMap<>();
        for (List<CirrusPlanBookmarkStore.StepLine> layer : layers) {
            for (int i = 0; i < layer.size(); i++) {
                map.put(layer.get(i).stepId(), i);
            }
        }
        return map;
    }

    private static void sortLayerByBarycenter(
        List<CirrusPlanBookmarkStore.StepLine> layer,
        Map<String, List<String>> neighbors,
        Map<String, Integer> indexMap
    ) {
        if (layer.size() <= 1) {
            return;
        }
        Map<String, Double> score = new HashMap<>(layer.size());
        for (int i = 0; i < layer.size(); i++) {
            String id = layer.get(i).stepId();
            List<String> adj = neighbors.get(id);
            if (adj == null || adj.isEmpty()) {
                score.put(id, (double) i);
            } else {
                double sum = 0.0;
                int count = 0;
                for (String n : adj) {
                    Integer r = indexMap.get(n);
                    if (r != null) {
                        sum += r;
                        count++;
                    }
                }
                score.put(id, count == 0 ? (double) i : sum / count);
            }
        }
        layer.sort(Comparator.comparingDouble(s -> score.getOrDefault(s.stepId(), 0.0)));
    }

    private static void alignMinorAxis(
        List<CirrusPlanBookmarkStore.StepLine> layer,
        Map<String, List<String>> neighbors,
        Map<String, Integer> posMap,
        Map<String, Integer> widthMap,
        boolean vertical,
        int minorGap,
        int minPos
    ) {
        int prevEnd = minPos - minorGap;
        for (CirrusPlanBookmarkStore.StepLine step : layer) {
            String id = step.stepId();
            int size = vertical ? widthMap.getOrDefault(id, leafPodWidth) : nodeHeight;
            int cur = posMap.getOrDefault(id, minPos);
            List<String> adj = neighbors.get(id);
            int target = cur;
            if (adj != null && !adj.isEmpty()) {
                double sumCenter = 0.0;
                int count = 0;
                for (String n : adj) {
                    Integer p = posMap.get(n);
                    if (p != null) {
                        int nSize = vertical ? widthMap.getOrDefault(n, leafPodWidth) : nodeHeight;
                        sumCenter += p + nSize * 0.5;
                        count++;
                    }
                }
                if (count > 0) {
                    target = (int) Math.round(sumCenter / count - size * 0.5);
                }
            }
            int placed = Math.max(prevEnd + minorGap, target);
            posMap.put(id, placed);
            prevEnd = placed + size;
        }
    }

    private static int stageOf(String kind) {
        if ("AcquireStep".equals(kind) || "LinePullStep".equals(kind)) {
            return 0;
        }
        if ("ProvisionMachineStep".equals(kind) || "SetupLineStep".equals(kind)) {
            return 1;
        }
        return 2;
    }

    private static long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}

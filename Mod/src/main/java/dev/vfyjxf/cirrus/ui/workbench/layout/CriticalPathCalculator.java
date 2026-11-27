package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class CriticalPathCalculator {

    private static final double minDuration = 0.5;
    private static final double slackEpsilon = 0.01;
    private static final double bottleneckPassiveThreshold = 20.0;
    private static final double roundScale = 10_000.0;
    private static final double timeEpsilon = 1e-9;

    private CriticalPathCalculator() {}

    public record GanttEntry(
        String stepId,
        PlanStep step,
        int trackIndex,
        double earliestStart,
        double activeEnd,
        double earliestFinish,
        double latestStart,
        double slack,
        boolean critical,
        @Nullable String bottleneckMachineId,
        int suggestedOwnedCount
    ) {

        public GanttEntry {
            Objects.requireNonNull(stepId, "stepId");
            Objects.requireNonNull(step, "step");
            if (trackIndex < 0) {
                throw new IllegalArgumentException("trackIndex must be >= 0, got " + trackIndex);
            }
        }
    }

    public record CriticalPathSchedule(
        List<GanttEntry> entries,
        double makespanSeconds,
        double totalHumanSeconds,
        double totalPassiveSeconds,
        Set<String> criticalStepIds,
        @Nullable GanttEntry topBottleneck
    ) {

        public CriticalPathSchedule {
            Objects.requireNonNull(entries, "entries");
            Objects.requireNonNull(criticalStepIds, "criticalStepIds");
            entries = List.copyOf(entries);
            criticalStepIds = Collections.unmodifiableSet(new TreeSet<>(criticalStepIds));
        }

        public int trackCount() {
            int count = 0;
            for (GanttEntry entry : entries) {
                count = Math.max(count, entry.trackIndex() + 1);
            }
            return count;
        }
    }

    public static CriticalPathSchedule compute(@Nullable ProductionPlan plan) {
        if (plan == null) {
            return empty();
        }
        LinkedHashMap<String, PlanStep> byId = new LinkedHashMap<>();
        for (PlanStep step : plan.steps()) {
            if (step != null) {
                byId.putIfAbsent(step.stepId(), step);
            }
        }
        if (byId.isEmpty()) {
            return empty();
        }
        Map<String, List<String>> preds = predecessorIndex(byId);
        Map<String, List<String>> succs = successorIndex(byId, preds);
        List<String> order = topologicalOrder(byId, preds, succs);

        Map<String, Double> durations = new TreeMap<>();
        Map<String, Double> earliestStart = new TreeMap<>();
        Map<String, Double> earliestFinish = new TreeMap<>();
        Map<String, Double> latestStart = new TreeMap<>();
        Map<String, Double> latestFinish = new TreeMap<>();
        Map<String, Double> slack = new TreeMap<>();
        for (PlanStep step : byId.values()) {
            durations.put(step.stepId(), LayoutGeometry.duration(step));
        }

        double makespan = 0.0;
        for (String stepId : order) {
            double start = 0.0;
            for (String dep : preds.getOrDefault(stepId, List.of())) {
                start = Math.max(start, earliestFinish.getOrDefault(dep, 0.0));
            }
            double finish = start + Objects.requireNonNull(durations.get(stepId));
            earliestStart.put(stepId, start);
            earliestFinish.put(stepId, finish);
            makespan = Math.max(makespan, finish);
        }

        for (int index = order.size() - 1; index >= 0; index--) {
            String stepId = order.get(index);
            double finish = makespan;
            boolean hasSuccessor = false;
            for (String successor : succs.getOrDefault(stepId, List.of())) {
                double candidate = latestStart.getOrDefault(successor, makespan);
                finish = hasSuccessor ? Math.min(finish, candidate) : candidate;
                hasSuccessor = true;
            }
            latestFinish.put(stepId, finish);
            latestStart.put(stepId, finish - Objects.requireNonNull(durations.get(stepId)));
        }

        double makespanRounded = round(makespan);
        double totalHuman = 0.0;
        double totalPassive = 0.0;
        Set<String> criticalIds = new TreeSet<>();
        for (PlanStep step : byId.values()) {
            String stepId = step.stepId();
            double value = latestStart.getOrDefault(stepId, 0.0) - earliestStart.getOrDefault(stepId, 0.0);
            slack.put(stepId, value);
            if (value <= slackEpsilon) {
                criticalIds.add(stepId);
            }
            totalHuman += step.stepCost().humanActiveSeconds();
            totalPassive += step.stepCost().passiveWaitSeconds();
        }

        Map<String, Integer> tracks = assignTracks(byId, earliestStart, earliestFinish);
        List<GanttEntry> entries = new ArrayList<>(byId.size());
        for (PlanStep step : byId.values()) {
            String stepId = step.stepId();
            double start = round(earliestStart.getOrDefault(stepId, 0.0));
            double activeEnd = round(start + step.stepCost().humanActiveSeconds());
            double finish = round(earliestFinish.getOrDefault(stepId, 0.0));
            double latest = round(latestStart.getOrDefault(stepId, 0.0));
            double slackValue = round(slack.getOrDefault(stepId, 0.0));
            entries.add(
                new GanttEntry(
                    stepId,
                    step,
                    tracks.getOrDefault(stepId, 0),
                    start,
                    activeEnd,
                    finish,
                    latest,
                    slackValue,
                    criticalIds.contains(stepId),
                    LayoutGeometry.primaryMachineIdOf(step),
                    step.stepCost().passiveWaitSeconds() > bottleneckPassiveThreshold ? 2 : 1
                )
            );
        }
        entries.sort(
            Comparator.comparingInt(GanttEntry::trackIndex).thenComparingDouble(GanttEntry::earliestStart)
                    .thenComparing(GanttEntry::stepId)
        );

        GanttEntry topBottleneck = null;
        double bestPassive = -1.0;
        for (GanttEntry entry : entries) {
            double passive = entry.step().stepCost().passiveWaitSeconds();
            if (passive > bestPassive + timeEpsilon) {
                bestPassive = passive;
                topBottleneck = entry;
            }
        }
        if (bestPassive <= 0.0) {
            topBottleneck = null;
        }

        return new CriticalPathSchedule(
            entries,
            makespanRounded,
            round(totalHuman),
            round(totalPassive),
            criticalIds,
            topBottleneck
        );
    }

    private static CriticalPathSchedule empty() {
        return new CriticalPathSchedule(List.of(), 0.0, 0.0, 0.0, Set.of(), null);
    }

    private static double round(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.round(value * roundScale) / roundScale;
    }

    private static Map<String, Integer> assignTracks(
        Map<String, PlanStep> byId,
        Map<String, Double> earliestStart,
        Map<String, Double> earliestFinish
    ) {
        List<String> order = new ArrayList<>(byId.keySet());
        order.sort(
            Comparator.comparingDouble((String id) -> earliestStart.getOrDefault(id, 0.0))
                    .thenComparing(Comparator.naturalOrder())
        );
        List<Double> trackEnds = new ArrayList<>();
        Map<String, Integer> tracks = new TreeMap<>();
        for (String stepId : order) {
            double start = earliestStart.getOrDefault(stepId, 0.0);
            int chosen = -1;
            for (int index = 0; index < trackEnds.size(); index++) {
                if (trackEnds.get(index) <= start + timeEpsilon) {
                    chosen = index;
                    break;
                }
            }
            if (chosen < 0) {
                chosen = trackEnds.size();
                trackEnds.add(earliestFinish.getOrDefault(stepId, 0.0));
            } else {
                trackEnds.set(chosen, earliestFinish.getOrDefault(stepId, 0.0));
            }
            tracks.put(stepId, chosen);
        }
        return tracks;
    }

    private static Map<String, List<String>> predecessorIndex(Map<String, PlanStep> byId) {
        Map<String, List<String>> preds = new TreeMap<>();
        for (PlanStep step : byId.values()) {
            TreeSet<String> deps = new TreeSet<>();
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
        Map<String, TreeSet<String>> mutable = new TreeMap<>();
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

    private static List<String> topologicalOrder(
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
        List<String> order = new ArrayList<>(byId.size());
        Set<String> visited = new TreeSet<>();
        while (!ready.isEmpty()) {
            String stepId = ready.pollFirst();
            order.add(stepId);
            visited.add(stepId);
            for (String successor : succs.getOrDefault(stepId, List.of())) {
                int remaining = Objects.requireNonNull(indegree.get(successor)) - 1;
                indegree.put(successor, remaining);
                if (remaining == 0) {
                    ready.add(successor);
                }
            }
        }
        for (String stepId : byId.keySet()) {
            if (!visited.contains(stepId)) {
                order.add(stepId);
            }
        }
        return order;
    }
}

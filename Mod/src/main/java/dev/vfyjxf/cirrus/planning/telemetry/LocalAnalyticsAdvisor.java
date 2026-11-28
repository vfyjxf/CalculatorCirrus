package dev.vfyjxf.cirrus.planning.telemetry;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.spi.PlanningTelemetrySink;
import dev.vfyjxf.cirrus.api.planning.spi.ScoringContributor;
import dev.vfyjxf.cirrus.api.planning.telemetry.PlanningTelemetryEvent;
import dev.vfyjxf.cirrus.planning.core.learn.ExecutionFailureTracker;
import dev.vfyjxf.cirrus.planning.core.learn.OnlinePreferenceLearner;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Online Local Analytics Advisor:
 * subscribes to {@link PlanningTelemetryBus} and exposes bounded soft guidance via {@link ScoringContributor}:
 * - Empirical-Bayes online preference calibration ({@link OnlinePreferenceLearner})
 * - Execution failure trust decay ({@link ExecutionFailureTracker})
 * - EMA observed step-time calibration per system (alpha = 0.15)
 * - Frequent crafted-target tracking for Base promotion suggestions
 * - Machine co-occurrence tracking for facility inventory suggestions.
 */
public final class LocalAnalyticsAdvisor implements PlanningTelemetrySink, ScoringContributor {

    private static final double emaAlpha = 0.15;

    private final OnlinePreferenceLearner preferenceLearner = new OnlinePreferenceLearner();
    private final ExecutionFailureTracker failureTracker = new ExecutionFailureTracker();
    private final ConcurrentHashMap<Namespace, Double> emaSecondsPerBatchBySystem = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ComponentStackKey, Long> targetCraftFrequency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ownedMachineUpdates = new ConcurrentHashMap<>();
    private final AtomicLong logicClock = new AtomicLong(0L);

    @Override
    public String sinkId() {
        return "cirrus:local_analytics_advisor";
    }

    @Override
    public String contributorId() {
        return "cirrus:local_analytics_advisor";
    }

    public OnlinePreferenceLearner preferenceLearner() {
        return preferenceLearner;
    }

    public ExecutionFailureTracker failureTracker() {
        return failureTracker;
    }

    public long currentLogicTick() {
        return logicClock.get();
    }

    @Override
    public synchronized void onEvent(PlanningTelemetryEvent event) {
        long tick = logicClock.incrementAndGet();
        switch (event) {
            case PlanningTelemetryEvent.PlanInteractionEvent pie -> {
                targetCraftFrequency.merge(pie.primaryTarget(), 1L, Long::sum);
                preferenceLearner.recordChoice(
                    pie.eventId(),
                    pie.chosenTemplateFeatures(),
                    pie.chosenBaseObjectiveJ(),
                    pie.rejectedTemplateFeatures(),
                    pie.rejectedBaseObjectivesJ(),
                    tick
                );
            }
            case PlanningTelemetryEvent.CraftingExecutionEvent cee -> {
                ExecutionFailureTracker.Key key = new ExecutionFailureTracker.Key(
                    cee.recipeSemanticKey(),
                    cee.conditionSignature(),
                    cee.adapterVersion()
                );
                failureTracker.recordExecutionOutcome(
                    cee.eventId(),
                    key,
                    cee.succeeded(),
                    cee.failureCategory(),
                    tick
                );
                if (cee.succeeded() && Double.isFinite(cee.observedSecondsPerBatch()) && cee.observedSecondsPerBatch() > 0.0) {
                    emaSecondsPerBatchBySystem.merge(
                        cee.systemId(),
                        cee.observedSecondsPerBatch(),
                        (prev, obs) -> (1.0 - emaAlpha) * prev + emaAlpha * obs
                    );
                }
            }
            case PlanningTelemetryEvent.FacilityEvolutionEvent fee -> {
                if ("machine_owned".equals(fee.changeKind())) {
                    ownedMachineUpdates.merge(fee.subjectId(), (long) fee.countDelta(), Long::sum);
                }
            }
            default -> {}
        }
    }

    public @Nullable Double calibratedSecondsPerBatchOrNull(Namespace systemId) {
        return emaSecondsPerBatchBySystem.get(systemId);
    }

    /** Returns top frequently queried targets that are good candidates for Base promotion. */
    public List<ComponentStackKey> suggestFrequentBaseCandidates(long minQueryCount, int limit) {
        return targetCraftFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= minQueryCount)
                .sorted(Comparator.<Map.Entry<ComponentStackKey, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(0, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public synchronized double recipeSoftBiasSeconds(CompiledRecipe recipe, PlanningContext context) {
        String sysFeat = "sys:" + recipe.key().systemId();
        Map<String, Double> feat = Map.of(sysFeat, 1.0);
        // Bounded raw habit mismatch in [0, 2.0s] (further capped by ScoreBreakdown g_B).
        return preferenceLearner.habitMismatchSeconds(feat, 2.0, logicClock.get());
    }
}

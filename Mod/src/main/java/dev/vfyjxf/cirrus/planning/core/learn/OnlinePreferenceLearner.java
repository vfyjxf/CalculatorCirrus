package dev.vfyjxf.cirrus.planning.core.learn;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Discounted diagonal Laplace / online-Newton Bradley-Terry preference learner.
 *
 * <p>Guarantees:
 * - Single-step relative update bounded by |Delta h_j| <= d_h (0.10) and |h_j| <= h_max (1.0).
 * - Logical-time exponential forgetting with half-life t_{1/2} = 100 toward shared group prior mu_g.
 * - Idempotent by eventId (replaying the same event ID never double-counts).
 * - Non-negative bounded habit mismatch score S_habit = B_raw * (h_max - x(P)^T h) / (2 * h_max) in [0, B_raw].
 * - Deterministic exploration rotation preventing self-reinforcement lock-in.
 */
public final class OnlinePreferenceLearner {

    public static final double defaultTemperatureT = 0.20;
    public static final double defaultPriorPrecisionTau0 = 4.0;
    public static final double defaultGroupShrinkKappa0 = 8.0;
    public static final double defaultHMax = 1.0;
    public static final double defaultHGroupCap = 0.5;
    public static final double defaultMaxSingleStepDh = 0.10;
    public static final double defaultHalfLifeRounds = 100.0;
    public static final int maxProcessedEventIds = 8192;

    private static final class DimState {
        double precisionD;
        double naturalN;
        double weightH;
        long lastLogicTime;

        DimState(double d, double n, double h, long t) {
            this.precisionD = d;
            this.naturalN = n;
            this.weightH = h;
            this.lastLogicTime = t;
        }
    }

    private final Map<String, DimState> dims = new TreeMap<>();
    private final Map<String, Double> groupPriors = new HashMap<>();
    private final LinkedHashSet<String> processedEventIds = new LinkedHashSet<>();
    private long currentLogicTime = 0L;

    public OnlinePreferenceLearner() {}

    public void setGroupPrior(String groupId, double priorMean) {
        double clamped = Math.max(-defaultHGroupCap, Math.min(defaultHGroupCap, priorMean));
        groupPriors.put(Objects.requireNonNull(groupId, "groupId"), clamped);
    }

    public long currentLogicTime() {
        return currentLogicTime;
    }

    /**
     * Records a single pairwise user choice event (chosen vs rejected alternatives).
     * Total weight across all rejected comparisons in one screen is <= 1.0.
     * Returns true if applied, false if duplicate eventId or invalid input.
     */
    public boolean recordChoice(
        String eventId,
        Map<String, Double> chosenFeatures,
        double chosenBaseJ,
        List<Map<String, Double>> rejectedFeaturesList,
        List<Double> rejectedBaseJs,
        long logicTimeStep
    ) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(chosenFeatures, "chosenFeatures");
        Objects.requireNonNull(rejectedFeaturesList, "rejectedFeaturesList");
        Objects.requireNonNull(rejectedBaseJs, "rejectedBaseJs");
        if (rejectedFeaturesList.isEmpty() || rejectedFeaturesList.size() != rejectedBaseJs.size()) {
            return false;
        }
        if (!processedEventIds.add(eventId)) {
            return false; // Idempotent replay protection.
        }
        if (processedEventIds.size() > maxProcessedEventIds) {
            var it = processedEventIds.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        long t = Math.max(currentLogicTime, logicTimeStep);
        currentLogicTime = t;

        int numRej = rejectedFeaturesList.size();
        double sampleWeightA = 1.0 / numRej; // Total evidence per screen <= 1.0.

        // Collect touched feature dimensions and decay lazily to t.
        Set<String> touched = new TreeSet<>(chosenFeatures.keySet());
        for (Map<String, Double> rej : rejectedFeaturesList) {
            touched.addAll(rej.keySet());
        }
        Map<String, Double> hPre = new HashMap<>(touched.size() * 2);
        Map<String, Double> dPre = new HashMap<>(touched.size() * 2);
        for (String feat : touched) {
            DimState st = decayTo(feat, t);
            hPre.put(feat, st.weightH);
            dPre.put(feat, st.precisionD);
        }

        // Accumulate gradient g and Hessian diagonal w using frozen hPre.
        Map<String, Double> gradAcc = new HashMap<>(touched.size() * 2);
        Map<String, Double> hessAcc = new HashMap<>(touched.size() * 2);

        for (int k = 0; k < numRej; k++) {
            Map<String, Double> rejFeat = rejectedFeaturesList.get(k);
            double rejBaseJ = rejectedBaseJs.get(k);
            double dotDeltaH = 0.0;
            for (String feat : touched) {
                double dx = chosenFeatures.getOrDefault(feat, 0.0) - rejFeat.getOrDefault(feat, 0.0);
                if (dx != 0.0) {
                    dotDeltaH += dx * hPre.getOrDefault(feat, 0.0);
                }
            }
            double logit = -(chosenBaseJ - rejBaseJ) / defaultTemperatureT + dotDeltaH;
            double prob = sigmoid(logit);
            for (String feat : touched) {
                double dx = chosenFeatures.getOrDefault(feat, 0.0) - rejFeat.getOrDefault(feat, 0.0);
                if (dx == 0.0) continue;
                double g = sampleWeightA * (1.0 - prob) * dx;
                double w = sampleWeightA * prob * (1.0 - prob) * dx * dx;
                gradAcc.merge(feat, g, Double::sum);
                hessAcc.merge(feat, w, Double::sum);
            }
        }

        // Diagonal online-Newton update with strict single-step |delta| <= d_h and |h| <= h_max.
        for (String feat : touched) {
            DimState st = Objects.requireNonNull(dims.get(feat), "dimension state");
            double dNext = Objects.requireNonNull(dPre.get(feat), "prior precision")
                    + hessAcc.getOrDefault(feat, 0.0);
            double rawStep = gradAcc.getOrDefault(feat, 0.0) / Math.max(defaultPriorPrecisionTau0, dNext);
            double clampedStep = Math.max(-defaultMaxSingleStepDh, Math.min(defaultMaxSingleStepDh, rawStep));
            double hNext = Math.max(
                -defaultHMax,
                Math.min(defaultHMax, Objects.requireNonNull(hPre.get(feat), "prior weight") + clampedStep)
            );
            st.precisionD = dNext;
            st.weightH = hNext;
            st.naturalN = dNext * hNext;
            st.lastLogicTime = t;
        }
        return true;
    }

    public double weightOf(String featureId, long atLogicTime) {
        DimState st = dims.get(featureId);
        if (st == null) {
            return groupPriors.getOrDefault(groupOf(featureId), 0.0);
        }
        long dt = Math.max(0L, atLogicTime - st.lastLogicTime);
        if (dt == 0L) {
            return st.weightH;
        }
        double muG = groupPriors.getOrDefault(groupOf(featureId), 0.0);
        double lambda = StrictMath.pow(2.0, -dt / defaultHalfLifeRounds);
        double dMinus = defaultPriorPrecisionTau0 + lambda * (st.precisionD - defaultPriorPrecisionTau0);
        double nMinus = defaultPriorPrecisionTau0 * muG + lambda * (st.naturalN - defaultPriorPrecisionTau0 * muG);
        return Math.max(-defaultHMax, Math.min(defaultHMax, nMinus / dMinus));
    }

    /**
     * Non-negative bounded habit mismatch cost S_habit in [0, bRawSeconds]:
     * S_habit = B_raw * (h_max - x(P)^T h) / (2 * h_max).
     * When all h == 0 (cold start), every candidate receives the exact same score (zero bias).
     */
    public double habitMismatchSeconds(Map<String, Double> l1NormalizedFeatures, double bRawSeconds, long atLogicTime) {
        if (bRawSeconds <= 0.0) {
            return 0.0;
        }
        double dot = 0.0;
        double normSum = 0.0;
        for (var e : l1NormalizedFeatures.entrySet()) {
            double x = Math.max(0.0, e.getValue());
            normSum += x;
            dot += x * weightOf(e.getKey(), atLogicTime);
        }
        double normalizedDot = normSum > 1e-12 ? dot / normSum : 0.0;
        double clampedDot = Math.max(-defaultHMax, Math.min(defaultHMax, normalizedDot));
        return bRawSeconds * (defaultHMax - clampedDot) / (2.0 * defaultHMax);
    }

    /**
     * Deterministic reserved exploration slot rotation:
     * ensures among {@code r} eligible alternative regime signatures, each receives exposure
     * at least once every {@code r} queries with a spare display slot.
     */
    public static int pickExplorationSlotIndex(int eligibleAlternativeCount, long exposureQueryIndex) {
        if (eligibleAlternativeCount <= 0) {
            return -1;
        }
        return Math.floorMod(exposureQueryIndex, eligibleAlternativeCount);
    }

    public Map<String, Double> snapshotWeights() {
        TreeMap<String, Double> snap = new TreeMap<>();
        for (var e : dims.entrySet()) {
            snap.put(e.getKey(), e.getValue().weightH);
        }
        return Collections.unmodifiableMap(snap);
    }

    private DimState decayTo(String featureId, long t) {
        double muG = groupPriors.getOrDefault(groupOf(featureId), 0.0);
        DimState st = dims.get(featureId);
        if (st == null) {
            st = new DimState(defaultPriorPrecisionTau0, defaultPriorPrecisionTau0 * muG, muG, t);
            dims.put(featureId, st);
            return st;
        }
        long dt = Math.max(0L, t - st.lastLogicTime);
        if (dt > 0L) {
            double lambda = StrictMath.pow(2.0, -dt / defaultHalfLifeRounds);
            st.precisionD = defaultPriorPrecisionTau0 + lambda * (st.precisionD - defaultPriorPrecisionTau0);
            st.naturalN = defaultPriorPrecisionTau0 * muG + lambda * (st.naturalN - defaultPriorPrecisionTau0 * muG);
            st.weightH = Math.max(-defaultHMax, Math.min(defaultHMax, st.naturalN / st.precisionD));
            st.lastLogicTime = t;
        }
        return st;
    }

    private static String groupOf(String featureId) {
        int colon = featureId.indexOf(':');
        return colon > 0 ? featureId.substring(0, colon) : "global";
    }

    private static double sigmoid(double z) {
        if (z >= 0.0) {
            double e = StrictMath.exp(-z);
            return 1.0 / (1.0 + e);
        }
        double e = StrictMath.exp(z);
        return e / (1.0 + e);
    }
}

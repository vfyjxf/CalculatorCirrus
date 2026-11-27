package dev.vfyjxf.cirrus.planning.core.human;

import dev.vfyjxf.cirrus.api.planning.human.VoIEstimate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * M9.1 Single-Pass Counterfactual Expected Value of Sample Information (EVSI) Engine (Pure JDK 21).
 *
 * <p>Given answer priors p_b and loss matrix L_{a,b} (in base-equivalent seconds s_e) over feasible
 * actions a and mutually exclusive answers b:
 *   EVSI(q) = min_a sum_b p_b L_{a,b} - sum_b p_b min_a L_{a,b} >= 0
 *   Delta J(q) = (EVSI(q) - c_ask) / A
 * Guarantees EVSI == 0 whenever the same action is optimal under every answer.
 */
public final class VoICounterfactualEngine {

    public static final double defaultAskCostSeconds = 3.0;
    public static final double defaultDisjointAggregationRho = 0.25;

    private VoICounterfactualEngine() {}

    /**
     * Exact EVSI evaluation with bounded loss uncertainty intervals [L^-_{a,b}, L^+_{a,b}].
     *
     * @param answerPriors probabilities p_b (size B >= 1, summing to 1)
     * @param lossMatrix   L[a][b] nominal loss in s_e (A actions x B answers)
     * @param lossLower    optional lower-bound matrix L^-[a][b] (may be null -> exact nominal)
     * @param lossUpper    optional upper-bound matrix L^+[a][b] (may be null -> exact nominal)
     * @param askCostSec   interaction cost c_ask (default 3.0 s_e)
     * @param queryScaleA  frozen query scale A (>= 10.0 s_e)
     */
    public static VoIEstimate evaluate(
        double[] answerPriors,
        double[][] lossMatrix,
        double @Nullable [][] lossLower,
        double @Nullable [][] lossUpper,
        double askCostSec,
        double queryScaleA
    ) {
        Objects.requireNonNull(answerPriors, "answerPriors");
        Objects.requireNonNull(lossMatrix, "lossMatrix");
        if (answerPriors.length == 0 || lossMatrix.length == 0) {
            return VoIEstimate.zero;
        }
        int numActions = lossMatrix.length;
        int numAnswers = answerPriors.length;
        double[] p = normalizePriors(answerPriors);
        double aScale = Math.max(10.0, queryScaleA);
        double cAsk = Math.max(0.0, askCostSec);

        double gross = computeEvsi(p, lossMatrix, numActions, numAnswers);
        double evsiLo = gross;
        double evsiHi = gross;
        String status = "EMPIRICAL";

        if (lossLower != null && lossUpper != null) {
            // Conservative lower bound: pre-info uses L^-, post-info uses L^+; upper bound reverses.
            double preMinLo = minExpectedLoss(p, lossLower, numActions, numAnswers);
            double postExpHi = expectedMinLoss(p, lossUpper, numActions, numAnswers);
            evsiLo = Math.max(0.0, preMinLo - postExpHi);

            double preMinHi = minExpectedLoss(p, lossUpper, numActions, numAnswers);
            double postExpLo = expectedMinLoss(p, lossLower, numActions, numAnswers);
            evsiHi = Math.max(evsiLo, preMinHi - postExpLo);
        }

        double entropy = normalizedEntropy(p);
        double netDeltaJ = (gross - cAsk) / aScale;
        return new VoIEstimate(gross, cAsk, netDeltaJ, entropy, evsiLo, evsiHi, status);
    }

    public static VoIEstimate evaluateExact(
        double[] answerPriors,
        double[][] lossMatrix,
        double askCostSec,
        double queryScaleA
    ) {
        return evaluate(answerPriors, lossMatrix, null, null, askCostSec, queryScaleA);
    }

    /**
     * Same-system priority aggregation (M9.1): max V_q + rho * sum_{disjoint} V_q
     * where V_q = max(0, EVSI_lo - c_ask).
     */
    public static double aggregateSystemGroupPriority(List<VoIEstimate> disjointEstimates, double rho) {
        if (disjointEstimates == null || disjointEstimates.isEmpty()) {
            return 0.0;
        }
        double maxV = 0.0;
        double sumV = 0.0;
        for (VoIEstimate e : disjointEstimates) {
            double v = e.conservativeNetSeconds();
            if (v > maxV) {
                maxV = v;
            }
            sumV += v;
        }
        double rest = Math.max(0.0, sumV - maxV);
        return maxV + Math.max(0.0, rho) * rest;
    }

    private static double computeEvsi(double[] p, double[][] l, int numActions, int numAnswers) {
        double preInfoMin = minExpectedLoss(p, l, numActions, numAnswers);
        double postInfoExp = expectedMinLoss(p, l, numActions, numAnswers);
        return Math.max(0.0, preInfoMin - postInfoExp);
    }

    private static double minExpectedLoss(double[] p, double[][] l, int numActions, int numAnswers) {
        double best = Double.POSITIVE_INFINITY;
        for (int a = 0; a < numActions; a++) {
            double exp = 0.0;
            for (int b = 0; b < numAnswers; b++) {
                exp += p[b] * l[a][b];
            }
            if (exp < best) {
                best = exp;
            }
        }
        return best;
    }

    private static double expectedMinLoss(double[] p, double[][] l, int numActions, int numAnswers) {
        double sum = 0.0;
        for (int b = 0; b < numAnswers; b++) {
            double bestForB = Double.POSITIVE_INFINITY;
            for (int a = 0; a < numActions; a++) {
                if (l[a][b] < bestForB) {
                    bestForB = l[a][b];
                }
            }
            sum += p[b] * bestForB;
        }
        return sum;
    }

    private static double[] normalizePriors(double[] raw) {
        double sum = 0.0;
        for (double v : raw) {
            if (Double.isFinite(v) && v > 0.0) {
                sum += v;
            }
        }
        double[] out = new double[raw.length];
        if (sum <= 0.0) {
            double u = 1.0 / raw.length;
            for (int i = 0; i < raw.length; i++) out[i] = u;
            return out;
        }
        for (int i = 0; i < raw.length; i++) {
            out[i] = (Double.isFinite(raw[i]) && raw[i] > 0.0) ? raw[i] / sum : 0.0;
        }
        return out;
    }

    private static double normalizedEntropy(double[] p) {
        if (p.length <= 1) {
            return 0.0;
        }
        double h = 0.0;
        for (double prob : p) {
            if (prob > 1e-12) {
                h -= prob * StrictMath.log(prob);
            }
        }
        double maxH = StrictMath.log(p.length);
        return maxH <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, h / maxH));
    }
}

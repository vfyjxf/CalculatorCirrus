package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.PreferenceModifiers;
import dev.vfyjxf.cirrus.api.planning.cost.ScoreBreakdown;
import dev.vfyjxf.cirrus.api.planning.cost.StageDistribution;

import java.util.Objects;

/**
 * M5 Bernstein stage evolution and diminishing-returns machine capital amortization (Pure JDK 21).
 *
 * <p>K_eff(Q) = K_m * [eta_K + (1 - eta_K) / (1 + r_m * (1 - 1/Q))].
 * Properties:
 * - Q = 1 -> K_eff = K_m (full upfront charge).
 * - First discrete difference <= 0 (monotone decreasing in Q).
 * - Second discrete difference >= 0 (discrete convex / diminishing marginal discount).
 * - Floor strictly >= eta_K * K_m.
 */
public final class StageAndCapitalModel {

    public static final double defaultEtaK = 0.30;
    public static final double defaultREarly = 0.25;
    public static final double defaultRMid = 1.00;
    public static final double defaultRLate = 3.00;
    public static final double defaultReuseN0 = 4.0;
    public static final double defaultQRef = 256.0;

    private StageAndCapitalModel() {}

    public static StageDistribution inferStage(
        double facilityCoverageX1,
        double networkStorageX2,
        double medianOrderQ50,
        PreferenceModifiers modifiers
    ) {
        StageDistribution base = StageDistribution.fromObservations(
            facilityCoverageX1,
            networkStorageX2,
            medianOrderQ50,
            defaultQRef
        );
        if (modifiers == null) {
            return base;
        }
        return base.blendWithOverride(modifiers.stageOverride(), modifiers.stageOverrideWeight());
    }

    public static double reuseIntensity(StageDistribution stage, long completedIndependentOrders) {
        Objects.requireNonNull(stage, "stage");
        long n = Math.max(0L, completedIndependentOrders);
        double stageFactor = defaultREarly * stage.early()
                + defaultRMid * stage.mid()
                + defaultRLate * stage.late();
        return stageFactor * (n / (n + defaultReuseN0));
    }

    public static double amortizedCapital(
        double rawCapitalUnits,
        long orderQuantityQ,
        StageDistribution stage,
        long completedIndependentOrders
    ) {
        return amortizedCapital(rawCapitalUnits, orderQuantityQ, reuseIntensity(stage, completedIndependentOrders), defaultEtaK);
    }

    public static double amortizedCapital(
        double rawCapitalUnits,
        long orderQuantityQ,
        double rM,
        double etaK
    ) {
        if (rawCapitalUnits <= 0.0) {
            return 0.0;
        }
        long q = Math.max(1L, orderQuantityQ);
        double r = Math.max(0.0, rM);
        double eta = Math.max(0.0, Math.min(1.0, etaK));
        if (q == 1L || r == 0.0) {
            return rawCapitalUnits;
        }
        double denom = 1.0 + r * (1.0 - 1.0 / q);
        return rawCapitalUnits * (eta + (1.0 - eta) / denom);
    }

    public static double queryScaleA(double baselineOrderEquivalentSeconds) {
        if (!Double.isFinite(baselineOrderEquivalentSeconds) || baselineOrderEquivalentSeconds <= 0.0) {
            return ScoreBreakdown.defaultAMinSeconds;
        }
        return Math.max(ScoreBreakdown.defaultAMinSeconds, baselineOrderEquivalentSeconds);
    }
}

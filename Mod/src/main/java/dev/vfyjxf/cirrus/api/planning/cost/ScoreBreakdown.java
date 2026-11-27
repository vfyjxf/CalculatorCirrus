package dev.vfyjxf.cirrus.api.planning.cost;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Auditable two-layer score breakdown (M1.3):
 * physical 6D cost vector + frozen query scale A + 4 bounded soft accounts (g_B(s) = B tanh(s/B))
 * + final dimensionless decision objective J(P) + optional certified dual lower bound L.
 */
public record ScoreBreakdown(
    CostVector physicalCost,
    double queryScaleA,
    double physicalDecisionSecondsU,
    double amortizedCapitalUnits,
    double stockSoftRaw,
    double consSoftRaw,
    double trustSoftRaw,
    double habitSoftRaw,
    double stockSoftCapped,
    double consSoftCapped,
    double trustSoftCapped,
    double habitSoftCapped,
    double totalObjectiveJ,
    double searchGuidanceScore,
    OptionalDouble dualLowerBoundL,
    String boundStatus
) {

    public static final double defaultBStock = 0.30;
    public static final double defaultBCons = 0.10;
    public static final double defaultBTrust = 0.20;
    public static final double defaultBHabit = 0.04;
    public static final double defaultAMinSeconds = 10.0;
    public static final double scoreQuantum = 1e-4;

    public static final ScoreBreakdown empty = new ScoreBreakdown(
        CostVector.zero,
        defaultAMinSeconds,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        OptionalDouble.empty(),
        "NO_NONTRIVIAL_BOUND"
    );

    public ScoreBreakdown {
        Objects.requireNonNull(physicalCost, "physicalCost");
        Objects.requireNonNull(dualLowerBoundL, "dualLowerBoundL");
        Objects.requireNonNull(boundStatus, "boundStatus");
        if (!Double.isFinite(queryScaleA) || queryScaleA <= 0.0) {
            throw new IllegalArgumentException("queryScaleA must be > 0, got " + queryScaleA);
        }
    }

    public static double cappedSoft(double rawSeconds, double capB) {
        if (rawSeconds <= 0.0 || capB <= 0.0) {
            return 0.0;
        }
        return capB * StrictMath.tanh(rawSeconds / capB);
    }

    public static ScoreBreakdown compute(
        CostVector physicalCost,
        CostWeights effectiveWeights,
        double amortizedCapitalUnits,
        double queryScaleA,
        double stockSoftRaw,
        double consSoftRaw,
        double trustSoftRaw,
        double habitSoftRaw,
        double searchGuidanceDelta
    ) {
        double a = Math.max(defaultAMinSeconds, queryScaleA);
        double u = effectiveWeights.scalarizeWithAmortizedCapital(physicalCost, amortizedCapitalUnits);
        double sStock = Math.max(0.0, stockSoftRaw);
        double sCons = Math.max(0.0, consSoftRaw);
        double sTrust = Math.max(0.0, trustSoftRaw);
        double sHabit = Math.max(0.0, habitSoftRaw);

        double cStock = cappedSoft(sStock, defaultBStock * a);
        double cCons = cappedSoft(sCons, defaultBCons * a);
        double cTrust = cappedSoft(sTrust, defaultBTrust * a);
        double cHabit = cappedSoft(sHabit, defaultBHabit * a);

        double j = (u + cStock + cCons + cTrust + cHabit) / a;
        return new ScoreBreakdown(
            physicalCost,
            a,
            u,
            amortizedCapitalUnits,
            sStock,
            sCons,
            sTrust,
            sHabit,
            cStock,
            cCons,
            cTrust,
            cHabit,
            j,
            j + searchGuidanceDelta,
            OptionalDouble.empty(),
            "NO_NONTRIVIAL_BOUND"
        );
    }

    public double totalCappedSoftSeconds() {
        return stockSoftCapped + consSoftCapped + trustSoftCapped + habitSoftCapped;
    }

    public ScoreBreakdown withDualLowerBound(double lowerBoundL, String status) {
        return new ScoreBreakdown(
            physicalCost,
            queryScaleA,
            physicalDecisionSecondsU,
            amortizedCapitalUnits,
            stockSoftRaw,
            consSoftRaw,
            trustSoftRaw,
            habitSoftRaw,
            stockSoftCapped,
            consSoftCapped,
            trustSoftCapped,
            habitSoftCapped,
            totalObjectiveJ,
            searchGuidanceScore,
            lowerBoundL > 0.0 ? OptionalDouble.of(lowerBoundL) : OptionalDouble.empty(),
            status
        );
    }

    public long quantizedObjective() {
        return Math.round(totalObjectiveJ / scoreQuantum);
    }
}

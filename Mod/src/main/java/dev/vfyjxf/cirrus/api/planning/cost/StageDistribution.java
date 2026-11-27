package dev.vfyjxf.cirrus.api.planning.cost;

/**
 * Bernstein-simplex game stage distribution theta = ((1-s)^2, 2s(1-s), s^2) (M5).
 */
public record StageDistribution(double early, double mid, double late, double maturityS) {

    public static final StageDistribution earlyPure = new StageDistribution(1.0, 0.0, 0.0, 0.0);
    public static final StageDistribution midBalanced = new StageDistribution(0.25, 0.50, 0.25, 0.5);
    public static final StageDistribution latePure = new StageDistribution(0.0, 0.0, 1.0, 1.0);

    public StageDistribution {
        if (!Double.isFinite(early) || !Double.isFinite(mid) || !Double.isFinite(late) || !Double.isFinite(maturityS)) {
            throw new IllegalArgumentException("StageDistribution components must be finite");
        }
        if (early < -1e-12 || mid < -1e-12 || late < -1e-12) {
            throw new IllegalArgumentException("StageDistribution components must be >= 0");
        }
        double sum = early + mid + late;
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalArgumentException("StageDistribution must sum to 1, got " + sum);
        }
        early = Math.max(0.0, early);
        mid = Math.max(0.0, mid);
        late = Math.max(0.0, late);
        maturityS = Math.max(0.0, Math.min(1.0, maturityS));
    }

    public static StageDistribution fromMaturity(double s) {
        double clamped = Double.isFinite(s) ? Math.max(0.0, Math.min(1.0, s)) : 0.0;
        double om = 1.0 - clamped;
        return new StageDistribution(om * om, 2.0 * clamped * om, clamped * clamped, clamped);
    }

    public static StageDistribution fromObservations(
        double facilityCoverageX1,
        double networkStorageX2,
        double medianOrderQ50,
        double qRef
    ) {
        double x1 = clamp01(facilityCoverageX1);
        double x2 = clamp01(networkStorageX2);
        double ref = Math.max(2.0, qRef);
        double q = Math.max(0.0, medianOrderQ50);
        double x3 = clamp01(StrictMath.log(1.0 + q) / StrictMath.log(1.0 + ref));
        double s = 0.45 * x1 + 0.25 * x2 + 0.30 * x3;
        return fromMaturity(s);
    }

    public StageDistribution blendWithOverride(PreferenceModifiers.StageOverride override, double omega) {
        if (override == null || override == PreferenceModifiers.StageOverride.auto || omega <= 0.0) {
            return this;
        }
        double w = clamp01(omega);
        StageDistribution target = switch (override) {
            case early -> earlyPure;
            case mid -> new StageDistribution(0.0, 1.0, 0.0, 0.5);
            case late -> latePure;
            case auto -> this;
        };
        double e = (1.0 - w) * early + w * target.early;
        double m = (1.0 - w) * mid + w * target.mid;
        double l = (1.0 - w) * late + w * target.late;
        double s = (1.0 - w) * maturityS + w * target.maturityS;
        return new StageDistribution(e, m, l, s);
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}

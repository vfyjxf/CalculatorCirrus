package dev.vfyjxf.cirrus.planning.core.trust;

import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M8 Contamination-Proof Structural Fidelity & Cost-Floor Lifter (Pure JDK 21, zero wordlists).
 *
 * <p>Anchors sibling unit cost via lineage-deduplicated group medians with strict layer weights:
 * Layer A (verified native/execution) total weight = 1.0, Layer B <= 0.25, Layer C (JEI display) = 0.0.
 * Thus 1 Layer-A anchor strictly outvotes 200 duplicated or scattered Layer-C JEI displays.
 */
public final class FidelityScorer {

    public static final double defaultSMin = 0.25;
    public static final double defaultZ0 = 2.0;
    public static final double defaultZMax = 8.0;
    public static final double defaultKappa = 0.50;
    public static final double defaultSuspectThreshold = 0.60;
    public static final double defaultLiftNu = 1.0;
    public static final double defaultScoreQuantum = 1e-4;
    public static final double defaultLayerBMaxTotalWeight = 0.25;
    public static final double defaultCMin = 1e-3;
    public static final double defaultCRef = 1.0;

    private static final double wSource = 0.25;
    private static final double wInputCompleteness = 0.20;
    private static final double wOutputCompleteness = 0.20;
    private static final double wGateDescribable = 0.25;
    private static final double wIndependentRepro = 0.10;

    public record SiblingSample(
        String lineageGroup,
        RecipeEvidenceTier tier,
        double unitCostSeconds,
        boolean feasibleAnchor
    ) {
        public SiblingSample {
            Objects.requireNonNull(lineageGroup, "lineageGroup");
            Objects.requireNonNull(tier, "tier");
        }
    }

    public record StructuralFeatures(
        double sourceQuality,
        double inputCompleteness,
        double outputCompleteness,
        double gateDescribable,
        double independentRepro
    ) {
        public double weightedSum() {
            return clamp01(sourceQuality) * wSource
                    + clamp01(inputCompleteness) * wInputCompleteness
                    + clamp01(outputCompleteness) * wOutputCompleteness
                    + clamp01(gateDescribable) * wGateDescribable
                    + clamp01(independentRepro) * wIndependentRepro;
        }
    }

    public enum AnchorStatus {
        anchoredTierA,
        singleAnchorTierA,
        weakAnchorTierB,
        noAnchor
    }

    public record SiblingAnchor(
        double logMedianX,
        double robustScaleS,
        double medianUnitCost,
        double minFeasibleAnchorCost,
        boolean hasFeasibleAnchor,
        AnchorStatus status
    ) {
        public static final SiblingAnchor none = new SiblingAnchor(
            0.0,
            defaultSMin,
            0.0,
            Double.POSITIVE_INFINITY,
            false,
            AnchorStatus.noAnchor
        );
    }

    public record FidelityAssessment(
        double structuralBaseScore,
        double oneSidedZMinus,
        double phiStruct,
        boolean suspect,
        double liftedGuidanceUnitCost,
        AnchorStatus anchorStatus
    ) {}

    private record WeightedLogPoint(double logVal, double weight) {}

    private FidelityScorer() {}

    /**
     * Computes contamination-proof sibling baseline anchor (M8):
     * collapses duplicate recipes inside each lineage group via median, then weights Layer A = 1.0,
     * Layer B <= 0.25 (only when A exists, else B alone as weak anchor), Layer C = 0.0.
     */
    public static SiblingAnchor computeSiblingAnchor(List<SiblingSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return SiblingAnchor.none;
        }
        Map<String, List<Double>> tierAGroups = new LinkedHashMap<>();
        Map<String, List<Double>> tierBGroups = new LinkedHashMap<>();
        double minFeasibleAnchor = Double.POSITIVE_INFINITY;

        for (SiblingSample s : samples) {
            if (!Double.isFinite(s.unitCostSeconds()) || s.unitCostSeconds() < 0.0) {
                continue;
            }
            if (s.tier() == RecipeEvidenceTier.tierA) {
                tierAGroups.computeIfAbsent(s.lineageGroup(), k -> new ArrayList<>()).add(logCost(s.unitCostSeconds()));
                if (s.feasibleAnchor() && s.unitCostSeconds() < minFeasibleAnchor) {
                    minFeasibleAnchor = s.unitCostSeconds();
                }
            } else if (s.tier() == RecipeEvidenceTier.tierB) {
                tierBGroups.computeIfAbsent(s.lineageGroup(), k -> new ArrayList<>()).add(logCost(s.unitCostSeconds()));
                if (s.feasibleAnchor() && s.unitCostSeconds() < minFeasibleAnchor) {
                    minFeasibleAnchor = s.unitCostSeconds();
                }
            }
            // Layer C explicitly receives 0.0 anchor weight (cannot contaminate anchor even at 200:1).
        }

        List<WeightedLogPoint> points = new ArrayList<>();
        AnchorStatus status;
        if (!tierAGroups.isEmpty()) {
            double wA = 1.0 / tierAGroups.size();
            for (List<Double> vals : tierAGroups.values()) {
                points.add(new WeightedLogPoint(median(vals), wA));
            }
            if (!tierBGroups.isEmpty()) {
                double totalB = Math.min(defaultLayerBMaxTotalWeight, tierBGroups.size() * 0.125);
                double wB = totalB / tierBGroups.size();
                for (List<Double> vals : tierBGroups.values()) {
                    points.add(new WeightedLogPoint(median(vals), wB));
                }
            }
            status = tierAGroups.size() == 1 ? AnchorStatus.singleAnchorTierA : AnchorStatus.anchoredTierA;
        } else if (!tierBGroups.isEmpty()) {
            double wB = 1.0 / tierBGroups.size();
            for (List<Double> vals : tierBGroups.values()) {
                points.add(new WeightedLogPoint(median(vals), wB));
            }
            status = AnchorStatus.weakAnchorTierB;
        } else {
            return SiblingAnchor.none;
        }

        double wMed = weightedMedian(points);
        List<WeightedLogPoint> absDevs = new ArrayList<>(points.size());
        for (WeightedLogPoint pt : points) {
            absDevs.add(new WeightedLogPoint(Math.abs(pt.logVal() - wMed), pt.weight()));
        }
        double mad = weightedMedian(absDevs);
        double scale = Math.max(defaultSMin, 1.4826 * mad);
        if (status == AnchorStatus.weakAnchorTierB) {
            scale = Math.max(scale, 0.50);
        }
        double medianCost = defaultCRef * StrictMath.exp(wMed);
        return new SiblingAnchor(
            wMed,
            scale,
            medianCost,
            minFeasibleAnchor,
            Double.isFinite(minFeasibleAnchor),
            status
        );
    }

    public static FidelityAssessment assess(
        StructuralFeatures features,
        RecipeEvidenceTier tier,
        double apparentUnitCost,
        boolean missingOutputOrGate,
        SiblingAnchor anchor
    ) {
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(tier, "tier");
        SiblingAnchor a = anchor == null ? SiblingAnchor.none : anchor;

        double sStruct = features.weightedSum();
        double zMinus = 0.0;
        if (a.status() != AnchorStatus.noAnchor && Double.isFinite(apparentUnitCost) && apparentUnitCost >= 0.0) {
            double xR = logCost(apparentUnitCost);
            zMinus = Math.max(0.0, (a.logMedianX() - xR) / a.robustScaleS() - defaultZ0);
        }

        double phi;
        if (tier == RecipeEvidenceTier.tierA && !missingOutputOrGate) {
            // Fully verified native recipes pass at structural quality; outlier is audit-only.
            phi = clamp01(sStruct);
        } else {
            phi = clamp01(sStruct * StrictMath.exp(-defaultKappa * Math.min(zMinus, defaultZMax)));
        }

        boolean suspect = (phi < defaultSuspectThreshold) || missingOutputOrGate;
        double lifted = liftGuidanceUnitCost(apparentUnitCost, phi, suspect, a);
        return new FidelityAssessment(sStruct, zMinus, phi, suspect, lifted, a.status());
    }

    /**
     * M8 Cost-floor lifting:
     * c_r^up = max(c_r, (1 - phi)^nu * c_tilde, 1_{suspect & feasibleAnchor} * (c_a + delta)).
     */
    public static double liftGuidanceUnitCost(
        double apparentUnitCost,
        double phi,
        boolean suspect,
        SiblingAnchor anchor
    ) {
        double c = Math.max(0.0, apparentUnitCost);
        if (anchor == null || anchor.status() == AnchorStatus.noAnchor) {
            return c;
        }
        double penaltyFloor = StrictMath.pow(Math.max(0.0, 1.0 - clamp01(phi)), defaultLiftNu) * anchor.medianUnitCost();
        c = Math.max(c, penaltyFloor);
        if (suspect && anchor.hasFeasibleAnchor()) {
            double ca = anchor.minFeasibleAnchorCost();
            double delta = Math.max(defaultScoreQuantum, 0.01 * ca);
            c = Math.max(c, ca + delta);
        }
        return c;
    }

    private static double logCost(double unitCost) {
        return StrictMath.log(Math.max(unitCost, defaultCMin) / defaultCRef);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        if (n == 0) return 0.0;
        return (n % 2 == 1) ? sorted.get(n / 2) : 0.5 * (sorted.get(n / 2 - 1) + sorted.get(n / 2));
    }

    private static double weightedMedian(List<WeightedLogPoint> points) {
        List<WeightedLogPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(WeightedLogPoint::logVal));
        double totalW = 0.0;
        for (WeightedLogPoint p : sorted) {
            totalW += p.weight();
        }
        if (totalW <= 0.0) {
            return 0.0;
        }
        double half = totalW * 0.5;
        double acc = 0.0;
        for (int i = 0; i < sorted.size(); i++) {
            acc += sorted.get(i).weight();
            if (acc >= half - 1e-12) {
                if (Math.abs(acc - half) <= 1e-12 && i + 1 < sorted.size()) {
                    return 0.5 * (sorted.get(i).logVal() + sorted.get(i + 1).logVal());
                }
                return sorted.get(i).logVal();
            }
        }
        return sorted.get(sorted.size() - 1).logVal();
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}

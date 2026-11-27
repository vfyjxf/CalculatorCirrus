package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.cost.ScoreBreakdown;

import java.util.Map;
import java.util.Objects;

/**
 * M3 Material Family Stability & Consistency Soft Cost Model (Pure JDK 21).
 *
 * <p>S_cons = sum_{g: N_g > 0} w_g * { lambda_switch * [(1 - zeta)*(1 - max_f p_{gf}) + zeta * T_g]
 *                                      + 1_{a_g known} * lambda_drift * (1 - p_{g, a_g}) }
 * Properties:
 * - Order-independent over sibling subtrees (depends only on normalized domain shares p_{gf}).
 * - Soft constraint (never absolute): breaks cleanly at the exact boundary
 *   Delta_save > g_B(S_cons(P')) - g_B(S_cons(P)).
 */
public final class MaterialConsistencyModel {

    public static final double defaultLambdaSwitch = 4.0;
    public static final double defaultLambdaDrift = 3.0;
    public static final double defaultCausalZeta = 0.25;
    public static final double defaultStage1AnchorFactor = 0.25;

    public record DomainUsage(
        String domainId,
        double domainWeight,
        Map<String, Double> massByFamily,
        String anchorFamilyOrNull,
        double causalTransitionPenaltyT
    ) {
        public DomainUsage {
            Objects.requireNonNull(domainId, "domainId");
            massByFamily = Map.copyOf(Objects.requireNonNull(massByFamily, "massByFamily"));
            if (!Double.isFinite(domainWeight) || domainWeight < 0.0) {
                throw new IllegalArgumentException("domainWeight must be >= 0");
            }
        }
    }

    private MaterialConsistencyModel() {}

    public static double rawConsistencySeconds(Iterable<DomainUsage> domains) {
        return rawConsistencySeconds(domains, defaultLambdaSwitch, defaultLambdaDrift, defaultCausalZeta);
    }

    public static double rawConsistencySeconds(
        Iterable<DomainUsage> domains,
        double lambdaSwitch,
        double lambdaDrift,
        double zeta
    ) {
        double totalWeight = 0.0;
        for (DomainUsage d : domains) {
            if (totalMass(d.massByFamily()) > 0.0) {
                totalWeight += d.domainWeight();
            }
        }
        if (totalWeight <= 0.0) {
            return 0.0;
        }
        double z = Math.max(0.0, Math.min(0.5, zeta));
        double sum = 0.0;
        for (DomainUsage d : domains) {
            double nG = totalMass(d.massByFamily());
            if (nG <= 0.0) continue;
            double wNorm = d.domainWeight() / totalWeight;
            double maxShare = 0.0;
            for (var e : d.massByFamily().entrySet()) {
                double share = Math.max(0.0, e.getValue()) / nG;
                // Unknown family cannot earn a consistency discount by masking mixed materials.
                if (!"UNKNOWN".equals(e.getKey()) && !"AMBIGUOUS".equals(e.getKey()) && share > maxShare) {
                    maxShare = share;
                }
            }
            double shareTerm = 1.0 - maxShare;
            double switchPart = d.causalTransitionPenaltyT() > 0.0
                    ? (1.0 - z) * shareTerm + z * Math.min(1.0, d.causalTransitionPenaltyT())
                    : shareTerm;

            double driftPart = 0.0;
            if (d.anchorFamilyOrNull() != null && !d.anchorFamilyOrNull().isBlank()) {
                double anchorMass = Math.max(0.0, d.massByFamily().getOrDefault(d.anchorFamilyOrNull(), 0.0));
                driftPart = lambdaDrift * (1.0 - anchorMass / nG);
            }
            sum += wNorm * (lambdaSwitch * switchPart + driftPart);
        }
        return sum;
    }

    /**
     * Exact M3 switching boundary test: returns true iff mixed plan P' strictly beats unified plan P
     * after accounting for capped consistency cost g_B(S_cons).
     */
    public static boolean mixedPlanBeatsUnified(
        double otherSavingsSecondsDeltaSave,
        double unifiedRawConsistencyS,
        double mixedRawConsistencyS,
        double queryScaleA
    ) {
        double capB = ScoreBreakdown.defaultBCons * Math.max(ScoreBreakdown.defaultAMinSeconds, queryScaleA);
        double deltaCappedCons = ScoreBreakdown.cappedSoft(mixedRawConsistencyS, capB)
                - ScoreBreakdown.cappedSoft(unifiedRawConsistencyS, capB);
        return otherSavingsSecondsDeltaSave > deltaCappedCons + 1e-12;
    }

    private static double totalMass(Map<String, Double> massByFamily) {
        double total = 0.0;
        for (double v : massByFamily.values()) {
            if (Double.isFinite(v) && v > 0.0) {
                total += v;
            }
        }
        return total;
    }
}

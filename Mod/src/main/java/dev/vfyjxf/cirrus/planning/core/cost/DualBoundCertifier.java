package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M1.3 Optional Solver-Free Certified Dual Lower Bound L <= J* <= U_B (Pure JDK 21).
 *
 * <p>Given a non-negative seed price vector p (s_e / unit) and ALL admissible actions (recipes,
 * external supplies, free sources, macros), computes global scaling t such that for every action r:
 *   Delta_r(pi) = sum_v b_{rv} pi_v - sum_s a_{rs} min_{u in A_{rs}} pi_u <= c_r^lo,
 * yielding valid telescoping bound L = max(0, (pi . D - pi . I) / A).
 */
public final class DualBoundCertifier {

    public record ActionBoundConstraint(
        String actionId,
        Map<ResourceKey, Long> guaranteedOutputs,
        List<SlotMinDemand> slotDemands,
        double provableLowerCostSeconds
    ) {
        public ActionBoundConstraint {
            Objects.requireNonNull(actionId, "actionId");
            guaranteedOutputs = Map.copyOf(Objects.requireNonNull(guaranteedOutputs, "guaranteedOutputs"));
            slotDemands = List.copyOf(Objects.requireNonNull(slotDemands, "slotDemands"));
            if (!Double.isFinite(provableLowerCostSeconds) || provableLowerCostSeconds < 0.0) {
                throw new IllegalArgumentException("provableLowerCostSeconds must be >= 0");
            }
        }
    }

    public record SlotMinDemand(long amount, List<ResourceKey> candidates) {
        public SlotMinDemand {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            if (amount < 0L) {
                throw new IllegalArgumentException("amount must be >= 0");
            }
        }
    }

    public record DualBoundCertificate(
        double lowerBoundL,
        double scaleFactorT,
        String status,
        String limitingActionId
    ) {
        public boolean isNontrivial() {
            return lowerBoundL > 0.0;
        }
    }

    private DualBoundCertifier() {}

    public static DualBoundCertificate certify(
        Map<ResourceKey, Double> seedPrices,
        List<ActionBoundConstraint> allAllowedActions,
        Map<ResourceKey, Long> demand,
        Map<ResourceKey, Long> totalInventory,
        double queryScaleA
    ) {
        Objects.requireNonNull(seedPrices, "seedPrices");
        Objects.requireNonNull(allAllowedActions, "allAllowedActions");
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(totalInventory, "totalInventory");
        if (!Double.isFinite(queryScaleA) || queryScaleA <= 0.0) {
            throw new IllegalArgumentException("queryScaleA must be > 0");
        }

        double tMax = Double.POSITIVE_INFINITY;
        String limiter = "";

        for (ActionBoundConstraint act : allAllowedActions) {
            double outValue = 0.0;
            for (var e : act.guaranteedOutputs().entrySet()) {
                double p = Math.max(0.0, seedPrices.getOrDefault(e.getKey(), 0.0));
                outValue += e.getValue() * p;
            }
            double inMinValue = 0.0;
            for (SlotMinDemand slot : act.slotDemands()) {
                if (slot.amount() == 0L) continue;
                double minCand = Double.POSITIVE_INFINITY;
                for (ResourceKey cand : slot.candidates()) {
                    double p = Math.max(0.0, seedPrices.getOrDefault(cand, 0.0));
                    if (p < minCand) {
                        minCand = p;
                    }
                }
                if (!Double.isFinite(minCand)) {
                    minCand = 0.0;
                }
                inMinValue += slot.amount() * minCand;
            }
            double delta = outValue - inMinValue;
            if (delta > 1e-12) {
                if (act.provableLowerCostSeconds() <= 0.0) {
                    // Free positive-margin edge forces t = 0 -> no nontrivial dual bound exists.
                    return new DualBoundCertificate(0.0, 0.0, "NO_NONTRIVIAL_BOUND", act.actionId());
                }
                double ratio = act.provableLowerCostSeconds() / delta;
                if (ratio < tMax) {
                    tMax = ratio;
                    limiter = act.actionId();
                }
            }
        }

        if (!Double.isFinite(tMax) || tMax <= 0.0) {
            return new DualBoundCertificate(0.0, 0.0, "NO_NONTRIVIAL_BOUND", limiter);
        }

        // Conservative rounding margin against floating-point drift.
        double t = tMax * (1.0 - 1e-12);
        double demandDot = 0.0;
        for (var e : demand.entrySet()) {
            double p = Math.max(0.0, seedPrices.getOrDefault(e.getKey(), 0.0)) * t;
            demandDot += e.getValue() * p;
        }
        double invDot = 0.0;
        for (var e : totalInventory.entrySet()) {
            double p = Math.max(0.0, seedPrices.getOrDefault(e.getKey(), 0.0)) * t;
            invDot += e.getValue() * p;
        }
        double bound = Math.max(0.0, (demandDot - invDot) / queryScaleA);
        if (bound <= 1e-12) {
            return new DualBoundCertificate(0.0, t, "NO_NONTRIVIAL_BOUND", limiter);
        }
        return new DualBoundCertificate(bound, t, "CERTIFIED_DUAL_BOUND", limiter);
    }
}

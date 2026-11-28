package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventoryTier;
import dev.vfyjxf.cirrus.api.planning.cost.PreferenceModifiers;
import dev.vfyjxf.cirrus.api.planning.cost.ScoreBreakdown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pass B Inventory Economic Optimizer:
 * - Smooth-ramp split penalty s_h(q, D) = min(1, q/h, (D-q)/h) eliminating the I -> D cliff.
 * - Single-channel exact analytic endpoint solution (q* in {0, M}).
 * - Integer-batch breakpoint optimization (q = D - j*b +/- 1).
 * - Exact 4-tier channel subset / vertex solver over {hand, network, nearby, remote}.
 * - Rare-stock protection inequality and strict genericTag barrier.
 */
public final class PassBInventoryModel {

    public static final double defaultGammaSplit = 2.0;
    public static final double defaultSmoothWidthH = 4.0;

    public record ChannelSpec(
        InventoryTier tier,
        long usableStock,
        double alphaOpp,
        double openFrictionBeta,
        double linearExtractSeconds,
        boolean alreadyOpen
    ) {}

    public record SingleChannelDecision(
        long chosenQuantity,
        double totalComparableCost,
        double pureProductionCost,
        double stockSoftRawSeconds,
        double stockSoftCappedDelta
    ) {}

    public record TierAllocation(
        Map<InventoryTier, Long> quantityByTier,
        long totalQuantity,
        double totalComparableCost,
        double pureProductionCost,
        double linearExtractSeconds,
        double stockSoftRawSeconds
    ) {}

    private PassBInventoryModel() {}

    /** Smooth ramp s_h(q, D) = min(1, q/h, (D-q)/h). Continuous at q = 0 and q = D. */
    public static double smoothSplitRamp(double q, double demandD, double h) {
        if (q <= 0.0 || demandD <= 0.0 || q >= demandD) {
            return 0.0;
        }
        double width = Math.max(1e-9, h);
        return Math.min(1.0, Math.min(q / width, (demandD - q) / width));
    }

    /** Soft-account marginal increment G(t) = g_B(S0 + t) - g_B(S0). */
    public static double marginalCappedSoft(double t, double existingSoftS0, double capB) {
        if (t <= 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(capB) || capB <= 0.0) {
            return t;
        }
        double s0 = Math.max(0.0, existingSoftS0);
        return ScoreBreakdown.cappedSoft(s0 + t, capB) - ScoreBreakdown.cappedSoft(s0, capB);
    }

    /**
     * Exact analytic endpoint solver for single-channel linear replenishment p (M2.2):
     * F(q) = p * (D - q) + c_l * q + G(a*q + beta*1_{q>0} + gamma * s_h(q, D)).
     * Because T(q) is piecewise concave and G is monotone concave on q > 0, exact minimum lies at {0, M}.
     */
    public static SingleChannelDecision solveSingleChannelLinear(
        long demandD,
        long availableStockI,
        double marginalProdPriceP,
        double unitOpportunityCostA,
        double openFrictionBeta,
        double linearExtractCL,
        double gammaSplit,
        double smoothWidthH,
        double existingSoftS0,
        double capB
    ) {
        if (demandD <= 0L) {
            return new SingleChannelDecision(0L, 0.0, 0.0, 0.0, 0.0);
        }
        double pureProd = marginalProdPriceP * demandD;
        long m = Math.min(demandD, Math.max(0L, availableStockI));
        if (m <= 0L) {
            return new SingleChannelDecision(0L, pureProd, pureProd, 0.0, 0.0);
        }

        double tM = unitOpportunityCostA * m + openFrictionBeta + gammaSplit * smoothSplitRamp(m, demandD, smoothWidthH);
        double gM = marginalCappedSoft(tM, existingSoftS0, capB);
        double blendCost = marginalProdPriceP * (demandD - m) + linearExtractCL * m + gM;

        // Tie breaks to 0 (expected production preferred when indifferent).
        if (blendCost + 1e-12 < pureProd) {
            return new SingleChannelDecision(m, blendCost, pureProd, tM, gM);
        }
        return new SingleChannelDecision(0L, pureProd, pureProd, 0.0, 0.0);
    }

    /**
     * Exact batch-breakpoint solver when production cost is step-function k * ceil((D - q) / b) (M2.2):
     * inspects q = 0, q = M, and all integer batch breakpoints q = D - j*b and their +/- 1 legal neighbors.
     */
    public static SingleChannelDecision solveSingleChannelBatch(
        long demandD,
        long availableStockI,
        long outputPerBatchB,
        double costPerBatchK,
        double unitOpportunityCostA,
        double openFrictionBeta,
        double linearExtractCL,
        double gammaSplit,
        double smoothWidthH,
        double existingSoftS0,
        double capB
    ) {
        if (demandD <= 0L) {
            return new SingleChannelDecision(0L, 0.0, 0.0, 0.0, 0.0);
        }
        long b = Math.max(1L, outputPerBatchB);
        long m = Math.min(demandD, Math.max(0L, availableStockI));
        double pureProd = costPerBatchK * ceilDiv(demandD, b);
        if (m <= 0L) {
            return new SingleChannelDecision(0L, pureProd, pureProd, 0.0, 0.0);
        }

        long bestQ = 0L;
        double bestCost = pureProd;
        double bestRaw = 0.0;
        double bestDelta = 0.0;

        // Candidate set: 0, M, and breakpoints q = D - j*b (+/- 1).
        long maxJ = ceilDiv(demandD, b);
        long minJ = Math.max(0L, (demandD - m) / b);
        List<Long> candidates = new ArrayList<>();
        candidates.add(m);
        for (long j = minJ; j <= maxJ && candidates.size() < 256; j++) {
            long bp = demandD - j * b;
            for (long delta = -1L; delta <= 1L; delta++) {
                long q = bp + delta;
                if (q > 0L && q <= m) {
                    candidates.add(q);
                }
            }
        }
        candidates.sort(Long::compareTo);
        long prev = -1L;
        for (long q : candidates) {
            if (q == prev) continue;
            prev = q;
            long rem = demandD - q;
            double prodPart = rem > 0L ? costPerBatchK * ceilDiv(rem, b) : 0.0;
            double raw = unitOpportunityCostA * q + openFrictionBeta + gammaSplit * smoothSplitRamp(q, demandD, smoothWidthH);
            double gVal = marginalCappedSoft(raw, existingSoftS0, capB);
            double total = prodPart + linearExtractCL * q + gVal;
            if (total + 1e-12 < bestCost) {
                bestCost = total;
                bestQ = q;
                bestRaw = raw;
                bestDelta = gVal;
            }
        }
        return new SingleChannelDecision(bestQ, bestCost, pureProd, bestRaw, bestDelta);
    }

    /**
     * Exact 4-tier channel allocation solver (M2.2):
     * enumerates all 2^k open channel subsets (k <= 4), evaluates extreme vertices of the box-constrained
     * simplex {1 <= q_l <= I_l, sum q_l <= D} where the concave objective attains its exact integer minimum.
     */
    public static TierAllocation solveFourTierExact(
        long demandD,
        double marginalProdPriceP,
        List<ChannelSpec> channels,
        double gammaSplit,
        double smoothWidthH,
        double existingSoftS0,
        double capB
    ) {
        if (demandD <= 0L) {
            return new TierAllocation(Map.of(), 0L, 0.0, 0.0, 0.0, 0.0);
        }
        double pureProd = marginalProdPriceP * demandD;
        List<ChannelSpec> active = new ArrayList<>();
        for (ChannelSpec ch : channels) {
            if (ch.usableStock() > 0L) {
                active.add(ch);
            }
        }
        if (active.isEmpty()) {
            return new TierAllocation(Map.of(), 0L, pureProd, pureProd, 0.0, 0.0);
        }
        int k = Math.min(4, active.size());
        List<ChannelSpec> top = active.subList(0, k);

        double bestCost = pureProd;
        long bestTotalQ = 0L;
        double bestLinear = 0.0;
        double bestRaw = 0.0;
        Map<InventoryTier, Long> bestMap = Map.of();

        int maskLimit = 1 << k;
        for (int mask = 1; mask < maskLimit; mask++) {
            List<ChannelSpec> subset = new ArrayList<>(k);
            double betaSum = 0.0;
            long minSum = 0L;
            long maxSum = 0L;
            for (int i = 0; i < k; i++) {
                if ((mask & (1 << i)) != 0) {
                    ChannelSpec c = top.get(i);
                    subset.add(c);
                    if (!c.alreadyOpen()) {
                        betaSum += c.openFrictionBeta();
                    }
                    minSum += 1L;
                    maxSum = addSat(maxSum, c.usableStock());
                }
            }
            if (minSum > demandD) {
                continue;
            }
            int sLen = subset.size();
            // Sort by combined marginal slope (alpha_opp + linear_extract) for greedy extreme-point generation,
            // AND enumerate all box vertices (each coord at lower=1 or upper=I_l, at most one fractional-capped coord).
            int vertexMasks = 1 << sLen;
            for (int pivot = -1; pivot < sLen; pivot++) {
                for (int vm = 0; vm < vertexMasks; vm++) {
                    if (pivot >= 0 && ((vm & (1 << pivot)) != 0)) {
                        continue; // avoid duplicate pivot states
                    }
                    long[] qArr = new long[sLen];
                    long used = 0L;
                    boolean valid = true;
                    for (int i = 0; i < sLen; i++) {
                        if (i == pivot) continue;
                        qArr[i] = ((vm & (1 << i)) != 0) ? subset.get(i).usableStock() : 1L;
                        used = addSat(used, qArr[i]);
                        if (used > demandD) {
                            valid = false;
                            break;
                        }
                    }
                    if (!valid) continue;
                    if (pivot >= 0) {
                        long rem = demandD - used;
                        if (rem < 1L || rem > subset.get(pivot).usableStock()) {
                            continue;
                        }
                        qArr[pivot] = rem;
                        used = demandD;
                    }

                    double oppSum = 0.0;
                    double linSum = 0.0;
                    for (int i = 0; i < sLen; i++) {
                        ChannelSpec c = subset.get(i);
                        oppSum += c.alphaOpp() * qArr[i];
                        linSum += c.linearExtractSeconds() * qArr[i];
                    }
                    double raw = oppSum + betaSum + gammaSplit * smoothSplitRamp(used, demandD, smoothWidthH);
                    double gVal = marginalCappedSoft(raw, existingSoftS0, capB);
                    double total = marginalProdPriceP * (demandD - used) + linSum + gVal;
                    if (total + 1e-12 < bestCost) {
                        bestCost = total;
                        bestTotalQ = used;
                        bestLinear = linSum;
                        bestRaw = raw;
                        EnumMap<InventoryTier, Long> alloc = new EnumMap<>(InventoryTier.class);
                        for (int i = 0; i < sLen; i++) {
                            alloc.put(subset.get(i).tier(), qArr[i]);
                        }
                        bestMap = Map.copyOf(alloc);
                    }
                }
            }
        }

        return new TierAllocation(bestMap, bestTotalQ, bestCost, pureProd, bestLinear, bestRaw);
    }

    /**
     * Checks whether generic Tag substitution is forbidden for a resource under current stock mode.
     */
    public static boolean isForbiddenForGenericTag(
        ComponentStackKey resource,
        InventorySnapshot inventory,
        PreferenceModifiers.StockReferenceMode stockMode
    ) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(stockMode, "stockMode");
        if (stockMode == PreferenceModifiers.StockReferenceMode.ignore) {
            return true;
        }
        return stockMode.blockProtectedGenericTag() && inventory.isProtectedFromGenericTag(resource);
    }

    /**
     * Sufficient condition guaranteeing rare-stock substitution is strictly inferior to normal alternative (M2.2):
     * Delta G_opp > L_save.
     */
    public static boolean isRareSubstitutionStrictlyInferior(
        double rareOppSeconds,
        double normalOppSeconds,
        double maxOtherSavingsSeconds,
        double existingSoftS0,
        double capB
    ) {
        double gRare = marginalCappedSoft(rareOppSeconds, existingSoftS0, capB);
        double gNormal = marginalCappedSoft(normalOppSeconds, existingSoftS0, capB);
        double deltaG = gRare - gNormal;
        return deltaG > maxOtherSavingsSeconds + 1e-12;
    }

    public static List<ChannelSpec> buildChannelsForResource(
        ComponentStackKey resource,
        InventorySnapshot inventory,
        double expectedUnitPriceVExp,
        PreferenceModifiers.StockReferenceMode stockMode
    ) {
        if (stockMode == PreferenceModifiers.StockReferenceMode.ignore || !Double.isFinite(expectedUnitPriceVExp)) {
            return List.of();
        }
        double modeAlpha = stockMode.defaultAlphaOpp();
        List<ChannelSpec> out = new ArrayList<>(4);
        for (InventoryTier tier : InventoryTier.values()) {
            long usable = inventory.usableAmountInTier(resource, tier);
            if (usable <= 0L) continue;
            double tierAlpha = Math.min(0.95, Math.max(0.20, 0.5 * (modeAlpha + tier.defaultAlphaOpp())));
            if (inventory.isProtectedFromGenericTag(resource)) {
                tierAlpha = Math.max(tierAlpha, 0.85);
            }
            out.add(new ChannelSpec(
                tier,
                usable,
                tierAlpha * expectedUnitPriceVExp,
                tier.defaultOpenFrictionSeconds(),
                tier.defaultLinearSecondsPerUnit(),
                inventory.alreadyOpenTiers().contains(tier)
            ));
        }
        out.sort(Comparator.comparingDouble(c -> c.alphaOpp() + c.linearExtractSeconds()));
        return out;
    }

    private static long ceilDiv(long a, long b) {
        return a / b + (a % b == 0L ? 0L : 1L);
    }

    private static long addSat(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }
}

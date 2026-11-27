package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.spi.AcquisitionEstimatorSpi;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * M4 Pure-Core 2+1 Acquisition Oracle & 3-Layer Base Boundary Resolver (Pure JDK 21, ZERO wordlists).
 *
 * <p>Implements:
 * 1. Pragmatic 2+1 modes: baseSupply, manualGather, or forbidden (Optional.empty()).
 * 2. Automatic protection gate banning high-value / protected / normal-processed items from auto-gathering.
 * 3. Honest DATA_GAP reporting when a 0-producer item lacks certified natural evidence.
 * 4. Cold-start logistic Base proposal score b(v) = sigma(-1.5 + 1.2h + 0.8l + 1.4nu + 0.6s - 1.4p - u) >= 0.65.
 * 5. Pluggable {@link AcquisitionEstimatorSpi} slot reserved for [TODO-PLAN-MC-01] JER Monte Carlo.
 */
public final class V3AcquisitionOracle implements AcquisitionOracle {

    public static final String keyBaseContract = "cirrus.acquire.base_contract";
    public static final String keyPlayerBase = "cirrus.acquire.player_base";
    public static final String keyCostOverride = "cirrus.acquire.cost_override";
    public static final String keyCertifiedNatural = "cirrus.acquire.certified_natural";
    public static final String keySpiEstimator = "cirrus.acquire.spi";

    public static final double defaultRareProtectionPercentile = 0.95;
    public static final double defaultBaseProposalThreshold = 0.65;

    public enum LeafDiagnosis {
        baseSupplyAvailable,
        manualGatherCertified,
        forbiddenProtectedRare,
        forbiddenHasProcessing,
        forbiddenByUser,
        dataGapUncertifiedLeaf
    }

    public record BaseProposal(ResourceKey resource, double score, boolean recommended) {}

    private final Set<ResourceKey> hasNormalProcessing;
    private final Set<ResourceKey> unresolvedOutputs;
    private final BaseBoundarySnapshot baseBoundary;
    private final Map<ResourceKey, Double> adjustedBaseUnitPrices;
    private final Map<ResourceKey, Double> pricePercentiles;
    private final List<AcquisitionEstimatorSpi> spiEstimators;
    private final @Nullable HumanGuidanceView guidance;
    private final @Nullable PlanningContext spiContext;

    public V3AcquisitionOracle(
        CompiledRecipeGraph graph,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance,
        List<AcquisitionEstimatorSpi> spiEstimators,
        @Nullable PlanningContext spiContext
    ) {
        Objects.requireNonNull(graph, "graph");
        this.baseBoundary = baseBoundary == null ? BaseBoundarySnapshot.empty : baseBoundary;
        this.guidance = guidance;
        this.spiContext = spiContext;
        this.unresolvedOutputs = graph.unresolvedOutputs();
        this.hasNormalProcessing = computeNormalProcessedSet(graph);
        this.spiEstimators = spiEstimators == null
                ? List.of()
                : spiEstimators.stream()
                        .sorted(Comparator.comparingInt(AcquisitionEstimatorSpi::priority).reversed()
                                .thenComparing(AcquisitionEstimatorSpi::estimatorId))
                        .toList();

        Map<ResourceKey, Double> rawBasePrices = new HashMap<>();
        for (var e : this.baseBoundary.contracts().entrySet()) {
            rawBasePrices.put(e.getKey(), e.getValue().unitPriceForDemand(1L));
        }
        this.adjustedBaseUnitPrices = this.baseBoundary.enforcePartialOrder(rawBasePrices);
        this.pricePercentiles = computePercentiles(this.adjustedBaseUnitPrices);
    }

    public static Set<ResourceKey> computeNormalProcessedSet(CompiledRecipeGraph graph) {
        Set<ResourceKey> processed = new HashSet<>();
        int count = graph.stats().compiledRecipes();
        for (int cid = 0; cid < count; cid++) {
            CompiledRecipe rec = graph.recipeByCompactId(cid).orElse(null);
            if (rec == null) continue;
            if (rec.canonicalTags().contains(CanonicalRecipeTag.junkOrDuplicate)
                    || rec.canonicalTags().contains(CanonicalRecipeTag.packingUnpacking)
                    || rec.canonicalTags().contains(CanonicalRecipeTag.recolor)
                    || rec.canonicalTags().contains(CanonicalRecipeTag.variantSwap)
                    || rec.canonicalTags().contains(CanonicalRecipeTag.containerTransfer)
                    || rec.canonicalTags().contains(CanonicalRecipeTag.recycling)) {
                continue;
            }
            if (rec.inputs().isEmpty() || rec.isSuspiciousJei()) {
                continue;
            }
            for (var o : rec.outputs()) {
                processed.add(o.resource());
            }
        }
        return Set.copyOf(processed);
    }

    public LeafDiagnosis diagnose(ResourceKey key) {
        Objects.requireNonNull(key, "key");
        if (guidance != null && guidance.isResourceBanned(key)) {
            return LeafDiagnosis.forbiddenByUser;
        }
        if (guidance != null && (guidance.isResourceTreatedAsBase(key) || guidance.overriddenUnitCost(key).isPresent())) {
            return LeafDiagnosis.baseSupplyAvailable;
        }
        if (baseBoundary.activeContractFor(key).isPresent()) {
            return LeafDiagnosis.baseSupplyAvailable;
        }
        if (baseBoundary.isProtectedRare(key)) {
            return LeafDiagnosis.forbiddenProtectedRare;
        }
        if (pricePercentiles.getOrDefault(key, 0.0) >= defaultRareProtectionPercentile) {
            return LeafDiagnosis.forbiddenProtectedRare;
        }
        if (hasNormalProcessing.contains(key)) {
            return LeafDiagnosis.forbiddenHasProcessing;
        }
        if (unresolvedOutputs.contains(key) || !baseBoundary.isCertifiedNatural(key)) {
            return LeafDiagnosis.dataGapUncertifiedLeaf;
        }
        return LeafDiagnosis.manualGatherCertified;
    }

    @Override
    public Optional<AcquisitionOption> assess(ResourceKey key, long missingAmount, ProgressionState progression) {
        Objects.requireNonNull(key, "key");
        long qty = Math.max(1L, missingAmount);

        if (guidance != null) {
            if (guidance.isResourceBanned(key)) {
                return Optional.empty();
            }
            Optional<CostVector> override = guidance.overriddenUnitCost(key);
            if (override.isPresent()) {
                CostVector v = override.get();
                return Optional.of(new AcquisitionOption(
                    v.tripActiveSeconds() > 0.0 ? AcquisitionKind.manualGather : AcquisitionKind.baseSupply,
                    v.tripActiveSeconds() > 0.0 ? v.tripActiveSeconds() : v.humanActiveSeconds(),
                    v.passiveWaitSeconds(),
                    0.0,
                    v.materialScarcity(),
                    keyCostOverride
                ));
            }
            if (guidance.isResourceTreatedAsBase(key)) {
                double unitPrice = adjustedBaseUnitPrices.getOrDefault(key, 0.5);
                return Optional.of(new AcquisitionOption(
                    AcquisitionKind.baseSupply,
                    unitPrice,
                    0.0,
                    0.0,
                    unitPrice * 0.25,
                    keyPlayerBase
                ));
            }
        }

        // 1. Layer-1 certified Base contract (positive calibrated price unless explicitlyFree).
        Optional<BaseContract> contractOpt = baseBoundary.activeContractFor(key);
        if (contractOpt.isPresent()) {
            BaseContract c = contractOpt.get();
            if (qty > c.capacityLimit()) {
                return Optional.empty();
            }
            double unitSec = c.explicitlyFree()
                    ? 0.0
                    : Math.max(c.unitPriceForDemand(qty), adjustedBaseUnitPrices.getOrDefault(key, 0.0));
            return Optional.of(new AcquisitionOption(
                AcquisitionKind.baseSupply,
                unitSec,
                0.0,
                c.explicitlyFree() ? 0.0 : c.fixedDeliverySeconds(),
                c.scarcityPremiumPerUnit(),
                unitSec,
                0.0,
                c.capacityLimit(),
                "",
                keyBaseContract
            ));
        }

        // 2. Pluggable external SPI estimators (reserved for [TODO-PLAN-MC-01] JER Monte Carlo).
        if (!spiEstimators.isEmpty() && spiContext != null) {
            for (AcquisitionEstimatorSpi estimator : spiEstimators) {
                Optional<AcquisitionEstimate> est = estimator.estimate(key, qty, spiContext);
                if (est.isPresent()) {
                    AcquisitionEstimate e = est.get();
                    if (e.guaranteedQuantity() < qty) {
                        continue;
                    }
                    return Optional.of(e.toOption(qty, keySpiEstimator + "." + estimator.estimatorId()));
                }
            }
        }

        // 3. Automatic protection gate (M4): rare / high-percentile / normal-processed -> forbidden.
        if (baseBoundary.isProtectedRare(key)) {
            return Optional.empty();
        }
        if (pricePercentiles.getOrDefault(key, 0.0) >= defaultRareProtectionPercentile) {
            return Optional.empty();
        }
        if (hasNormalProcessing.contains(key)) {
            return Optional.empty();
        }
        if (unresolvedOutputs.contains(key)) {
            return Optional.empty();
        }

        // 4. Positive certified natural evidence required for manualGather (otherwise DATA_GAP -> empty).
        if (!baseBoundary.isCertifiedNatural(key)) {
            return Optional.empty();
        }
        return Optional.of(new AcquisitionOption(
            AcquisitionKind.manualGather,
            1.5,
            0.0,
            6.0,
            1.0,
            1.5,
            0.0,
            Long.MAX_VALUE,
            "trip:" + key.resourceId(),
            keyCertifiedNatural
        ));
    }

    /**
     * M4 Layer-2 Cold-Start Logistic Base Proposal Score (proposal only, never auto-authorizes free supply):
     * b(v) = sigma(-1.5 + 1.2*h + 0.8*l + 1.4*nu + 0.6*s - 1.4*p - u).
     */
    public static BaseProposal scoreColdStartBaseProposal(
        ResourceKey resource,
        double consumerHubRankH,
        int shallowestNaturalDepthD,
        double upstreamNaturalSystemShareNu,
        double normalizedStableStockS,
        double reliableProcessingChainP,
        double unknownPremiseRatioU
    ) {
        double h = clamp01(consumerHubRankH);
        double l = shallowestNaturalDepthD < 0 ? 0.0 : 1.0 / (1.0 + shallowestNaturalDepthD);
        double nu = clamp01(upstreamNaturalSystemShareNu);
        double s = clamp01(normalizedStableStockS);
        double p = clamp01(reliableProcessingChainP);
        double u = clamp01(unknownPremiseRatioU);
        double z = -1.5 + 1.2 * h + 0.8 * l + 1.4 * nu + 0.6 * s - 1.4 * p - u;
        double prob = 1.0 / (1.0 + StrictMath.exp(-z));
        return new BaseProposal(resource, prob, prob >= defaultBaseProposalThreshold);
    }

    private static Map<ResourceKey, Double> computePercentiles(Map<ResourceKey, Double> prices) {
        if (prices.size() < 2) {
            return Map.of();
        }
        List<Map.Entry<ResourceKey, Double>> entries = new ArrayList<>(prices.entrySet());
        entries.sort(Comparator.comparingDouble(Map.Entry::getValue));
        Map<ResourceKey, Double> pct = new HashMap<>(entries.size() * 2);
        int n = entries.size();
        for (int i = 0; i < n; i++) {
            pct.put(entries.get(i).getKey(), (double) i / (n - 1));
        }
        return pct;
    }

    private static double clamp01(double v) {
        if (!Double.isFinite(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}

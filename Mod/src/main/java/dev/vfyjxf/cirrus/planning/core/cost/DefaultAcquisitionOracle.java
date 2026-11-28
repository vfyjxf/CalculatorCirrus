package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.spi.AcquisitionEstimatorSpi;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Unified domain-agnostic {@link AcquisitionOracle} (zero hardcoded Minecraft/mod wordlists).
 *
 * <p>Evaluates acquisition in strict priority order:
 * <ol>
 *   <li>Explicit human guidance (ban, cost override, treat-as-base).</li>
 *   <li>Active {@link BaseContract} declared in {@link BaseBoundarySnapshot} (with capacity limit,
 *       non-double-counted fixed delivery fee, and scarcity premium).</li>
 *   <li>Registered {@link AcquisitionEstimatorSpi} extensions (ordered by descending priority).</li>
 *   <li>Certified natural sources (protected rare natural drops carry steep scarcity & trip costs).</li>
 *   <li>Pure-topology fallback for 0-producer non-unresolved leaves when {@code allowFallbackLeafGather} is enabled.</li>
 * </ol>
 */
public final class DefaultAcquisitionOracle implements AcquisitionOracle {

    public static final String keyFree = "cirrus.acquire.player_free";
    public static final String keyOverride = "cirrus.acquire.cost_override";
    public static final String keyBase = "cirrus.acquire.base";
    public static final String keyNaturalLeaf = "cirrus.acquire.natural";
    public static final String keyRareNatural = "cirrus.acquire.rare_natural";
    public static final String keyEstimatorSpi = "cirrus.acquire.estimator_spi";

    private static final AcquisitionOption playerBaseOpt = new AcquisitionOption(
        AcquisitionKind.baseSupply,
        0.5,
        0.0,
        0.0,
        0.1,
        keyFree
    );
    private static final AcquisitionOption leafGatherOpt = new AcquisitionOption(
        AcquisitionKind.manualGather,
        3.0,
        0.0,
        10.0,
        2.0,
        keyNaturalLeaf
    );
    private static final AcquisitionOption rareNaturalOpt = new AcquisitionOption(
        AcquisitionKind.manualGather,
        30.0,
        0.0,
        120.0,
        200.0,
        keyRareNatural
    );

    private final Set<ComponentStackKey> producibleResources;
    private final Set<ComponentStackKey> unresolvedOutputs;
    private final Set<ComponentStackKey> dormantResources;
    private final BaseBoundarySnapshot baseBoundary;
    private final @Nullable HumanGuidanceView guidance;
    private final List<AcquisitionEstimatorSpi> estimatorSpis;
    private final @Nullable PlanningContext spiContext;
    private final boolean allowFallbackLeafGather;

    public DefaultAcquisitionOracle(CompiledRecipeGraph graph) {
        this(graph, BaseBoundarySnapshot.empty, null, List.of(), null, true);
    }

    public DefaultAcquisitionOracle(CompiledRecipeGraph graph, HumanGuidanceView guidance) {
        this(graph, BaseBoundarySnapshot.empty, guidance, List.of(), null, true);
    }

    public DefaultAcquisitionOracle(
        CompiledRecipeGraph graph,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance
    ) {
        this(graph, baseBoundary, guidance, List.of(), null, true);
    }

    public DefaultAcquisitionOracle(
        CompiledRecipeGraph graph,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance,
        boolean allowFallbackLeafGather
    ) {
        this(graph, baseBoundary, guidance, List.of(), null, allowFallbackLeafGather);
    }

    public DefaultAcquisitionOracle(
        CompiledRecipeGraph graph,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance,
        List<AcquisitionEstimatorSpi> estimatorSpis,
        @Nullable PlanningContext spiContext,
        boolean allowFallbackLeafGather
    ) {
        Objects.requireNonNull(graph, "graph");
        this.producibleResources = graph.allProducibleResources();
        this.unresolvedOutputs = graph.unresolvedOutputs();
        this.dormantResources = Set.of();
        this.baseBoundary = baseBoundary == null ? BaseBoundarySnapshot.empty : baseBoundary;
        this.guidance = guidance;
        this.estimatorSpis = sortSpis(estimatorSpis);
        this.spiContext = spiContext;
        this.allowFallbackLeafGather = allowFallbackLeafGather;
    }

    public DefaultAcquisitionOracle(Set<ComponentStackKey> producibleResources) {
        this.producibleResources = Set.copyOf(Objects.requireNonNull(producibleResources, "producibleResources"));
        this.unresolvedOutputs = Set.of();
        this.dormantResources = Set.of();
        this.baseBoundary = BaseBoundarySnapshot.empty;
        this.guidance = null;
        this.estimatorSpis = List.of();
        this.spiContext = null;
        this.allowFallbackLeafGather = true;
    }

    private DefaultAcquisitionOracle(
        Set<ComponentStackKey> producibleResources,
        Set<ComponentStackKey> unresolvedOutputs,
        Set<ComponentStackKey> dormantResources,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance,
        List<AcquisitionEstimatorSpi> estimatorSpis,
        @Nullable PlanningContext spiContext,
        boolean allowFallbackLeafGather
    ) {
        this.producibleResources = producibleResources;
        this.unresolvedOutputs = unresolvedOutputs;
        this.dormantResources = dormantResources;
        this.baseBoundary = baseBoundary;
        this.guidance = guidance;
        this.estimatorSpis = estimatorSpis;
        this.spiContext = spiContext;
        this.allowFallbackLeafGather = allowFallbackLeafGather;
    }

    private static List<AcquisitionEstimatorSpi> sortSpis(@Nullable List<AcquisitionEstimatorSpi> spis) {
        if (spis == null || spis.isEmpty()) {
            return List.of();
        }
        List<AcquisitionEstimatorSpi> sorted = new ArrayList<>(spis);
        sorted.sort(
            Comparator.comparingInt(AcquisitionEstimatorSpi::priority)
                    .reversed()
                    .thenComparing(AcquisitionEstimatorSpi::estimatorId)
        );
        return List.copyOf(sorted);
    }

    public DefaultAcquisitionOracle withGuidance(@Nullable HumanGuidanceView newGuidance) {
        return new DefaultAcquisitionOracle(
            producibleResources,
            unresolvedOutputs,
            dormantResources,
            baseBoundary,
            newGuidance,
            estimatorSpis,
            spiContext,
            allowFallbackLeafGather
        );
    }

    public DefaultAcquisitionOracle withDormantResources(@Nullable Set<ComponentStackKey> dormant) {
        Set<ComponentStackKey> copy = (dormant == null || dormant.isEmpty()) ? Set.of() : Set.copyOf(dormant);
        return new DefaultAcquisitionOracle(
            producibleResources,
            unresolvedOutputs,
            copy,
            baseBoundary,
            guidance,
            estimatorSpis,
            spiContext,
            allowFallbackLeafGather
        );
    }

    public DefaultAcquisitionOracle withSpiContext(@Nullable PlanningContext context) {
        return new DefaultAcquisitionOracle(
            producibleResources,
            unresolvedOutputs,
            dormantResources,
            baseBoundary,
            guidance,
            estimatorSpis,
            context,
            allowFallbackLeafGather
        );
    }

    public BaseBoundarySnapshot baseBoundary() {
        return baseBoundary;
    }

    public boolean allowFallbackLeafGather() {
        return allowFallbackLeafGather;
    }

    public static AcquisitionOption fromUnitCost(CostVector unitCost, @Nullable String translationKey) {
        Objects.requireNonNull(unitCost, "unitCost");
        boolean isTrip = unitCost.tripActiveSeconds() > 0.0;
        return new AcquisitionOption(
            isTrip ? AcquisitionKind.manualGather : AcquisitionKind.baseSupply,
            isTrip ? unitCost.tripActiveSeconds() : unitCost.humanActiveSeconds(),
            unitCost.passiveWaitSeconds(),
            0.0,
            unitCost.materialScarcity(),
            translationKey == null ? keyOverride : translationKey
        );
    }

    public boolean isDeclaredBaseOrNatural(ComponentStackKey key) {
        return baseBoundary.activeContractFor(key).isPresent() || baseBoundary.isCertifiedNatural(key);
    }

    @Override
    public Optional<AcquisitionOption> assess(ComponentStackKey key, long missingAmount, ProgressionState progression) {
        Objects.requireNonNull(key, "key");

        // 1. Human guidance rules take top priority.
        if (guidance != null) {
            if (guidance.isResourceBanned(key)) {
                return Optional.empty();
            }
            Optional<CostVector> override = guidance.overriddenUnitCost(key);
            if (override.isPresent()) {
                return Optional.of(fromUnitCost(override.get(), keyOverride));
            }
            if (guidance.isResourceTreatedAsBase(key)) {
                return Optional.of(playerBaseOpt);
            }
        }

        // 2. Active BaseContract (zero double-counting: baseUnitSeconds, fixedDeliverySeconds, scarcity each once).
        Optional<BaseContract> contractOpt = baseBoundary.activeContractFor(key);
        if (contractOpt.isPresent()) {
            BaseContract c = contractOpt.get();
            if (c.capacityLimit() > 0L) {
                double unitSec = c.baseUnitSeconds();
                double fixedDeliv = c.fixedDeliverySeconds();
                double scarcity = c.scarcityPremiumPerUnit();
                return Optional.of(new AcquisitionOption(
                    AcquisitionKind.baseSupply,
                    unitSec,
                    0.0,
                    fixedDeliv,
                    scarcity,
                    unitSec,
                    0.0,
                    c.capacityLimit(),
                    "",
                    keyBase
                ));
            }
        }

        // 3. Registered AcquisitionEstimatorSpi extensions.
        if (!estimatorSpis.isEmpty() && spiContext != null) {
            long queryQty = Math.max(1L, missingAmount);
            for (AcquisitionEstimatorSpi spi : estimatorSpis) {
                Optional<AcquisitionEstimate> estOpt = spi.estimate(key, queryQty, spiContext);
                if (estOpt.isPresent()) {
                    AcquisitionEstimate est = estOpt.get();
                    if (est.guaranteedQuantity() > 0L) {
                        return Optional.of(est.toOption(queryQty, keyEstimatorSpi));
                    }
                }
            }
        }

        // 4. Certified natural resources (protected rare natural drops remain feasible with steep penalty).
        if (baseBoundary.isCertifiedNatural(key)) {
            if (baseBoundary.isProtectedRare(key)) {
                return Optional.of(rareNaturalOpt);
            }
            return Optional.of(leafGatherOpt);
        }

        // 5. Protected rare resources that are not certified natural cannot be directly gathered.
        if (baseBoundary.isProtectedRare(key)) {
            return Optional.empty();
        }

        // 6. Producible resources (unless dormant) must be crafted rather than gathered directly.
        boolean hasActiveProducer = producibleResources.contains(key) && !dormantResources.contains(key);
        if (hasActiveProducer) {
            return Optional.empty();
        }

        // 7. Unresolved outputs cannot be fabricated out of thin air.
        if (unresolvedOutputs.contains(key)) {
            return Optional.empty();
        }

        // 8. Pure-topology leaf fallback (disabled in strict boundary-only mode).
        if (!allowFallbackLeafGather) {
            return Optional.empty();
        }
        return Optional.of(leafGatherOpt);
    }

    /** Exact diagnostic mirror of {@link #assess(ComponentStackKey, long, ProgressionState)}. */
    public String diagnose(ComponentStackKey key, long missingAmount, ProgressionState progression) {
        Objects.requireNonNull(key, "key");
        if (guidance != null) {
            if (guidance.isResourceBanned(key)) return "banned_by_guidance";
            if (guidance.overriddenUnitCost(key).isPresent()) return "acquirable";
            if (guidance.isResourceTreatedAsBase(key)) return "acquirable";
        }
        Optional<BaseContract> contractOpt = baseBoundary.activeContractFor(key);
        if (contractOpt.isPresent() && contractOpt.get().capacityLimit() > 0L) {
            return "acquirable";
        }
        if (!estimatorSpis.isEmpty() && spiContext != null) {
            long queryQty = Math.max(1L, missingAmount);
            for (AcquisitionEstimatorSpi spi : estimatorSpis) {
                Optional<AcquisitionEstimate> estOpt = spi.estimate(key, queryQty, spiContext);
                if (estOpt.isPresent() && estOpt.get().guaranteedQuantity() > 0L) {
                    return "acquirable";
                }
            }
        }
        if (baseBoundary.isCertifiedNatural(key)) {
            return "acquirable";
        }
        if (baseBoundary.isProtectedRare(key)) {
            return "protected_rare";
        }
        if (producibleResources.contains(key) && !dormantResources.contains(key)) {
            return "has_producer_not_base";
        }
        if (unresolvedOutputs.contains(key)) {
            return "unresolved_output";
        }
        return allowFallbackLeafGather ? "acquirable" : "no_base_contract_or_natural_source";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefaultAcquisitionOracle other)) return false;
        return allowFallbackLeafGather == other.allowFallbackLeafGather
                && Objects.equals(baseBoundary, other.baseBoundary)
                && Objects.equals(guidance, other.guidance)
                && Objects.equals(dormantResources, other.dormantResources)
                && Objects.equals(estimatorSpis, other.estimatorSpis)
                && Objects.equals(producibleResources, other.producibleResources)
                && Objects.equals(unresolvedOutputs, other.unresolvedOutputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            baseBoundary,
            guidance,
            dormantResources,
            estimatorSpis,
            allowFallbackLeafGather,
            producibleResources.size(),
            unresolvedOutputs.size()
        );
    }
}

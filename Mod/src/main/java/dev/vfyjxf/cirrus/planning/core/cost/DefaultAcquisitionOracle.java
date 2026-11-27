package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure domain-agnostic default AcquisitionOracle (ZERO hardcoded Minecraft/mod/item wordlists!).
 *
 * <p>Delegates to {@link V3AcquisitionOracle} when a {@link BaseBoundarySnapshot} is supplied,
 * and falls back to clean pure-topology rules (0-producer non-unresolved leaves may be gathered
 * unless protected/banned; producible items require crafting unless explicitly declared Base).
 */
public final class DefaultAcquisitionOracle implements AcquisitionOracle {

    public static final String keyFree = "cirrus.acquire.player_free";
    public static final String keyOverride = "cirrus.acquire.cost_override";
    public static final String keyBase = "cirrus.acquire.base";
    public static final String keyNaturalLeaf = "cirrus.acquire.natural";

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

    private final Set<ResourceKey> producibleResources;
    private final Set<ResourceKey> unresolvedOutputs;
    private final Set<ResourceKey> dormantResources;
    private final BaseBoundarySnapshot baseBoundary;
    private final @Nullable HumanGuidanceView guidance;
    private final @Nullable V3AcquisitionOracle v3Delegate;

    public DefaultAcquisitionOracle(CompiledRecipeGraph graph) {
        this(graph, BaseBoundarySnapshot.empty, null);
    }

    public DefaultAcquisitionOracle(CompiledRecipeGraph graph, HumanGuidanceView guidance) {
        this(graph, BaseBoundarySnapshot.empty, guidance);
    }

    public DefaultAcquisitionOracle(
        CompiledRecipeGraph graph,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance
    ) {
        Objects.requireNonNull(graph, "graph");
        this.producibleResources = graph.allProducibleResources();
        this.unresolvedOutputs = graph.unresolvedOutputs();
        this.dormantResources = Set.of();
        this.baseBoundary = baseBoundary == null ? BaseBoundarySnapshot.empty : baseBoundary;
        this.guidance = guidance;
        this.v3Delegate = this.baseBoundary == BaseBoundarySnapshot.empty
                ? null
                : new V3AcquisitionOracle(graph, this.baseBoundary, guidance, List.of(), null);
    }

    public DefaultAcquisitionOracle(Set<ResourceKey> producibleResources) {
        this.producibleResources = Objects.requireNonNull(producibleResources, "producibleResources");
        this.unresolvedOutputs = Set.of();
        this.dormantResources = Set.of();
        this.baseBoundary = BaseBoundarySnapshot.empty;
        this.guidance = null;
        this.v3Delegate = null;
    }

    private DefaultAcquisitionOracle(
        Set<ResourceKey> producibleResources,
        Set<ResourceKey> unresolvedOutputs,
        Set<ResourceKey> dormantResources,
        BaseBoundarySnapshot baseBoundary,
        @Nullable HumanGuidanceView guidance,
        @Nullable V3AcquisitionOracle v3Delegate
    ) {
        this.producibleResources = producibleResources;
        this.unresolvedOutputs = unresolvedOutputs;
        this.dormantResources = dormantResources;
        this.baseBoundary = baseBoundary;
        this.guidance = guidance;
        this.v3Delegate = v3Delegate;
    }

    public DefaultAcquisitionOracle withGuidance(HumanGuidanceView newGuidance) {
        return new DefaultAcquisitionOracle(
            producibleResources,
            unresolvedOutputs,
            dormantResources,
            baseBoundary,
            newGuidance,
            v3Delegate
        );
    }

    public DefaultAcquisitionOracle withDormantResources(Set<ResourceKey> dormant) {
        return new DefaultAcquisitionOracle(
            producibleResources,
            unresolvedOutputs,
            dormant == null ? Set.of() : dormant,
            baseBoundary,
            guidance,
            v3Delegate
        );
    }

    public static AcquisitionOption fromUnitCost(CostVector unitCost, String translationKey) {
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

    /** Zero-hardcode predicate: true only when declared in a BaseBoundarySnapshot. */
    public boolean isDeclaredBaseOrNatural(ResourceKey key) {
        return baseBoundary.activeContractFor(key).isPresent() || baseBoundary.isCertifiedNatural(key);
    }

    @Override
    public Optional<AcquisitionOption> assess(ResourceKey key, long missingAmount, ProgressionState progression) {
        Objects.requireNonNull(key, "key");
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
        if (v3Delegate != null) {
            return v3Delegate.assess(key, missingAmount, progression);
        }
        if (baseBoundary.isProtectedRare(key)) {
            return Optional.empty();
        }
        boolean hasActiveProducer = producibleResources.contains(key) && !dormantResources.contains(key);
        if (hasActiveProducer) {
            return Optional.empty();
        }
        if (unresolvedOutputs.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(leafGatherOpt);
    }
}

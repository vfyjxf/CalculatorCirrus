package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.ContextAttributeView;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.spi.BaseBoundaryProvider;
import dev.vfyjxf.cirrus.api.planning.spi.ContextContributor;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Assembles extensible planning contexts from Cirrus StorageApi, WorldFacilityRegistry,
 * externalized DefaultDomainProfileProvider, and all registered plugin SPIs.
 */
public final class PlanningContextFactory {

    private final CompiledRecipeGraph graph;
    private final DefaultAcquisitionOracle baseOracle;

    public PlanningContextFactory(CompiledRecipeGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.baseOracle = new DefaultAcquisitionOracle(graph, DefaultDomainProfileProvider.defaultSnapshot(), null, true);
    }

    public CompiledRecipeGraph graph() {
        return graph;
    }

    public DefaultAcquisitionOracle baseOracle() {
        return baseOracle;
    }

    public PlanningContext createContext(
        @Nullable Player player,
        @Nullable WorldFacilityRegistry facilityRegistry,
        @Nullable HumanGuidanceStore guidanceStore
    ) {
        return createContext(
            player,
            facilityRegistry,
            guidanceStore,
            StockLedger.empty,
            ProgressionState.overworldDefault,
            List.of(),
            List.of()
        );
    }

    public PlanningContext createContext(
        @Nullable Player player,
        @Nullable WorldFacilityRegistry facilityRegistry,
        @Nullable HumanGuidanceStore guidanceStore,
        StockLedger reserved,
        ProgressionState progression,
        List<GuidanceRule> sessionOverrides,
        List<GuidanceRule> queryOverrides
    ) {
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(progression, "progression");

        BaseBoundarySnapshot boundary = DefaultDomainProfileProvider.defaultSnapshot();
        InventorySnapshot inventory = StorageInventorySnapshotAdapter.capture(
            player,
            reserved,
            boundary.protectedRareResources()
        );
        FacilitySnapshot facilities = facilityRegistry != null
                ? facilityRegistry.facilitySnapshot()
                : FacilitySnapshot.empty;

        HumanGuidanceView guidance;
        if (guidanceStore != null) {
            guidance = guidanceStore.snapshotMergedView(
                sessionOverrides == null ? List.of() : sessionOverrides,
                queryOverrides == null ? List.of() : queryOverrides
            );
        } else {
            List<GuidanceRule> combined = new ArrayList<>();
            if (sessionOverrides != null) combined.addAll(sessionOverrides);
            if (queryOverrides != null) combined.addAll(queryOverrides);
            guidance = combined.isEmpty() ? DefaultHumanGuidanceView.empty() : DefaultHumanGuidanceView.of(combined);
        }

        // Merge plugin BaseBoundaryProviders and ContextContributors.
        PlanningExtensionRegistry ext = PlanningExtensionRegistry.instance();
        PlanningContext prelim = new PlanningContext(
            inventory,
            facilities,
            boundary,
            baseOracle,
            guidance,
            progression,
            ContextAttributeView.empty
        );
        for (BaseBoundaryProvider provider : ext.baseProviders()) {
            try {
                boundary = boundary.mergeWith(provider.provide(graph, prelim));
            } catch (RuntimeException ignored) {}
        }

        ContextAttributeView.Builder attrBuilder = ContextAttributeView.builder();
        for (ContextContributor contributor : ext.contextContributors()) {
            try {
                contributor.contribute(attrBuilder, prelim);
            } catch (RuntimeException ignored) {}
        }
        ContextAttributeView attrs = attrBuilder.build();

        boolean boundaryChanged = boundary != DefaultDomainProfileProvider.defaultSnapshot();
        var estimators = ext.acquisitionEstimators();
        AcquisitionOracle oracle = estimators.isEmpty()
                ? (boundaryChanged
                        ? new DefaultAcquisitionOracle(graph, boundary, guidance, true)
                        : (guidance.allRules().isEmpty() ? baseOracle : baseOracle.withGuidance(guidance)))
                : new DefaultAcquisitionOracle(
                    graph,
                    boundary,
                    guidance,
                    estimators,
                    prelim.withBaseBoundary(boundary).withAttributes(attrs),
                    true
                );

        return new PlanningContext(inventory, facilities, boundary, oracle, guidance, progression, attrs);
    }

    public PlanningRequest createRequest(
        ComponentStackKey target,
        long amount,
        @Nullable Player player,
        @Nullable WorldFacilityRegistry facilityRegistry,
        @Nullable HumanGuidanceStore guidanceStore
    ) {
        return createRequest(
            List.of(new TargetDemand(target, amount)),
            player,
            facilityRegistry,
            guidanceStore,
            PlanningProfile.defaultAutoSmart(),
            PlanningBudget.defaultInteractive()
        );
    }

    public PlanningRequest createRequest(
        List<TargetDemand> targets,
        @Nullable Player player,
        @Nullable WorldFacilityRegistry facilityRegistry,
        @Nullable HumanGuidanceStore guidanceStore,
        PlanningProfile profile,
        PlanningBudget budget
    ) {
        PlanningContext ctx = createContext(player, facilityRegistry, guidanceStore);
        return new PlanningRequest(targets, ctx, profile, budget);
    }

    public static InventorySnapshot excludeTargetStock(InventorySnapshot inventory, List<TargetDemand> targets) {
        if (inventory == null || targets == null || targets.isEmpty()) {
            return inventory == null ? InventorySnapshot.empty : inventory;
        }
        Set<ComponentStackKey> excluded = new HashSet<>(targets.size() * 2);
        for (TargetDemand target : targets) {
            excluded.add(target.resource());
        }
        return inventory.withoutResources(excluded);
    }
}

package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Composable 3-layer Base boundary snapshot (M4):
 * certified base contracts, user force-expand overrides, protected/forbidden-gather sets,
 * and declared relative price partial-order constraints (cheaper <= moreExpensive + delta).
 */
public record BaseBoundarySnapshot(
    Map<ResourceKey, BaseContract> contracts,
    Set<ResourceKey> forceExpandResources,
    Set<ResourceKey> protectedRareResources,
    Set<ResourceKey> certifiedNaturalResources,
    List<PartialOrderConstraint> pricePartialOrders
) {

    public record PartialOrderConstraint(ResourceKey cheaper, ResourceKey moreExpensive, double minDeltaSeconds) {
        public PartialOrderConstraint {
            Objects.requireNonNull(cheaper, "cheaper");
            Objects.requireNonNull(moreExpensive, "moreExpensive");
            if (!Double.isFinite(minDeltaSeconds) || minDeltaSeconds < 0.0) {
                throw new IllegalArgumentException("minDeltaSeconds must be >= 0");
            }
        }
    }

    public static final BaseBoundarySnapshot empty = new BaseBoundarySnapshot(
        Map.of(),
        Set.of(),
        Set.of(),
        Set.of(),
        List.of()
    );

    public BaseBoundarySnapshot {
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(forceExpandResources, "forceExpandResources");
        Objects.requireNonNull(protectedRareResources, "protectedRareResources");
        Objects.requireNonNull(certifiedNaturalResources, "certifiedNaturalResources");
        Objects.requireNonNull(pricePartialOrders, "pricePartialOrders");

        contracts = contracts.isEmpty() ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(contracts));
        forceExpandResources = forceExpandResources.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(forceExpandResources));
        protectedRareResources = protectedRareResources.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(protectedRareResources));
        certifiedNaturalResources = certifiedNaturalResources.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(certifiedNaturalResources));
        pricePartialOrders = List.copyOf(pricePartialOrders);
    }

    public Optional<BaseContract> activeContractFor(ResourceKey resource) {
        if (forceExpandResources.contains(resource)) {
            return Optional.empty();
        }
        return Optional.ofNullable(contracts.get(resource));
    }

    public boolean isProtectedRare(ResourceKey resource) {
        return protectedRareResources.contains(resource);
    }

    public boolean isCertifiedNatural(ResourceKey resource) {
        return certifiedNaturalResources.contains(resource);
    }

    /**
     * Enforces declared partial order c_j >= c_i + delta_ij bottom-up on the DAG (M4).
     */
    public Map<ResourceKey, Double> enforcePartialOrder(Map<ResourceKey, Double> baseUnitPrices) {
        TreeMap<ResourceKey, Double> adjusted = new TreeMap<>(baseUnitPrices);
        if (pricePartialOrders.isEmpty()) {
            return adjusted;
        }
        // Bellman-Ford style bounded relaxation over partial-order DAG (guards against accidental cycles).
        int maxRounds = Math.min(64, pricePartialOrders.size() + 1);
        for (int round = 0; round < maxRounds; round++) {
            boolean changed = false;
            for (PartialOrderConstraint edge : pricePartialOrders) {
                Double cheapPrice = adjusted.get(edge.cheaper());
                if (cheapPrice == null || !Double.isFinite(cheapPrice)) {
                    continue;
                }
                double required = cheapPrice + edge.minDeltaSeconds();
                Double curExpensive = adjusted.get(edge.moreExpensive());
                if (curExpensive == null || curExpensive + 1e-12 < required) {
                    adjusted.put(edge.moreExpensive(), required);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return Collections.unmodifiableMap(adjusted);
    }

    public BaseBoundarySnapshot mergeWith(BaseBoundarySnapshot overlay) {
        if (overlay == null || overlay == empty) {
            return this;
        }
        TreeMap<ResourceKey, BaseContract> mergedContracts = new TreeMap<>(this.contracts);
        mergedContracts.putAll(overlay.contracts);

        TreeSet<ResourceKey> mergedExpand = new TreeSet<>(this.forceExpandResources);
        mergedExpand.addAll(overlay.forceExpandResources);

        TreeSet<ResourceKey> mergedRare = new TreeSet<>(this.protectedRareResources);
        mergedRare.addAll(overlay.protectedRareResources);

        TreeSet<ResourceKey> mergedNat = new TreeSet<>(this.certifiedNaturalResources);
        mergedNat.addAll(overlay.certifiedNaturalResources);

        Set<PartialOrderConstraint> seen = new HashSet<>(this.pricePartialOrders);
        seen.addAll(overlay.pricePartialOrders);
        List<PartialOrderConstraint> mergedOrders = seen.stream()
                .sorted((a, b) -> {
                    int c = a.cheaper().compareTo(b.cheaper());
                    if (c != 0) return c;
                    return a.moreExpensive().compareTo(b.moreExpensive());
                })
                .toList();

        return new BaseBoundarySnapshot(mergedContracts, mergedExpand, mergedRare, mergedNat, mergedOrders);
    }
}

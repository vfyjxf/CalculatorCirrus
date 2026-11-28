package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Composable base-supply boundary snapshot:
 * certified base contracts, user force-expand overrides, protected rare sets,
 * certified natural resources, and relative price partial-order constraints.
 */
public record BaseBoundarySnapshot(
    Map<ComponentStackKey, BaseContract> contracts,
    Set<ComponentStackKey> forceExpandResources,
    Set<ComponentStackKey> protectedRareResources,
    Set<ComponentStackKey> certifiedNaturalResources,
    List<PartialOrderConstraint> pricePartialOrders
) {

    public record PartialOrderConstraint(
        ComponentStackKey cheaper,
        ComponentStackKey moreExpensive,
        double minDeltaSeconds
    ) {
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

    public Optional<BaseContract> activeContractFor(ComponentStackKey resource) {
        if (forceExpandResources.contains(resource)) {
            return Optional.empty();
        }
        return Optional.ofNullable(contracts.get(resource));
    }

    public boolean isProtectedRare(ComponentStackKey resource) {
        return protectedRareResources.contains(resource);
    }

    public boolean isCertifiedNatural(ComponentStackKey resource) {
        return certifiedNaturalResources.contains(resource);
    }

    /**
     * Enforces declared partial order c_j >= c_i + delta_ij bottom-up on the DAG.
     */
    public Map<ComponentStackKey, Double> enforcePartialOrder(Map<ComponentStackKey, Double> baseUnitPrices) {
        TreeMap<ComponentStackKey, Double> adjusted = new TreeMap<>(baseUnitPrices);
        if (pricePartialOrders.isEmpty()) {
            return adjusted;
        }
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
        if (overlay == null || overlay.equals(empty)) {
            return this;
        }
        TreeMap<ComponentStackKey, BaseContract> mergedContracts = new TreeMap<>(this.contracts);
        mergedContracts.putAll(overlay.contracts);

        TreeSet<ComponentStackKey> mergedExpand = new TreeSet<>(this.forceExpandResources);
        mergedExpand.addAll(overlay.forceExpandResources);

        TreeSet<ComponentStackKey> mergedRare = new TreeSet<>(this.protectedRareResources);
        mergedRare.addAll(overlay.protectedRareResources);

        TreeSet<ComponentStackKey> mergedNat = new TreeSet<>(this.certifiedNaturalResources);
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

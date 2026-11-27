package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Multi-tier inventory snapshot (M1.1 / M2.2):
 * preserves per-tier available/reserved amounts, already-open channels (beta = 0),
 * and resources protected from automatic generic Tag substitution (d^{genericTag}_{v,l} = 0).
 */
public record InventorySnapshot(
    StockLedger available,
    StockLedger reserved,
    Map<InventoryTier, StockLedger> availableByTier,
    Map<InventoryTier, StockLedger> reservedByTier,
    Set<InventoryTier> alreadyOpenTiers,
    Set<ResourceKey> protectedFromGenericTags
) {

    public static final InventorySnapshot empty = new InventorySnapshot(
        StockLedger.empty,
        StockLedger.empty,
        Map.of(),
        Map.of(),
        Set.of(),
        Set.of()
    );

    public InventorySnapshot(StockLedger available, StockLedger reserved) {
        this(
            available,
            reserved,
            available == null || available.isEmpty() ? Map.of() : Map.of(InventoryTier.hand, available),
            reserved == null || reserved.isEmpty() ? Map.of() : Map.of(InventoryTier.hand, reserved),
            Set.of(),
            Set.of()
        );
    }

    public InventorySnapshot {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(reserved, "reserved");
        Objects.requireNonNull(availableByTier, "availableByTier");
        Objects.requireNonNull(reservedByTier, "reservedByTier");
        Objects.requireNonNull(alreadyOpenTiers, "alreadyOpenTiers");
        Objects.requireNonNull(protectedFromGenericTags, "protectedFromGenericTags");

        EnumMap<InventoryTier, StockLedger> availCopy = new EnumMap<>(InventoryTier.class);
        for (var e : availableByTier.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty()) {
                availCopy.put(e.getKey(), e.getValue());
            }
        }
        availableByTier = availCopy.isEmpty() ? Map.of() : Collections.unmodifiableMap(availCopy);

        EnumMap<InventoryTier, StockLedger> resCopy = new EnumMap<>(InventoryTier.class);
        for (var e : reservedByTier.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && !e.getValue().isEmpty()) {
                resCopy.put(e.getKey(), e.getValue());
            }
        }
        reservedByTier = resCopy.isEmpty() ? Map.of() : Collections.unmodifiableMap(resCopy);

        alreadyOpenTiers = alreadyOpenTiers.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(alreadyOpenTiers));

        if (protectedFromGenericTags.isEmpty()) {
            protectedFromGenericTags = Set.of();
        } else {
            TreeSet<ResourceKey> sorted = new TreeSet<>(protectedFromGenericTags);
            protectedFromGenericTags = Collections.unmodifiableSet(sorted);
        }
    }

    public static InventorySnapshot fromTiers(
        Map<InventoryTier, StockLedger> availableByTier,
        Map<InventoryTier, StockLedger> reservedByTier,
        Set<InventoryTier> alreadyOpenTiers,
        Set<ResourceKey> protectedFromGenericTags
    ) {
        TreeMap<ResourceKey, Long> mergedAvail = new TreeMap<>();
        if (availableByTier != null) {
            for (StockLedger ledger : availableByTier.values()) {
                if (ledger == null) continue;
                for (var e : ledger.entries().entrySet()) {
                    mergedAvail.merge(e.getKey(), e.getValue(), (a, b) -> {
                        try {
                            return Math.addExact(a, b);
                        } catch (ArithmeticException ex) {
                            return Long.MAX_VALUE;
                        }
                    });
                }
            }
        }
        TreeMap<ResourceKey, Long> mergedRes = new TreeMap<>();
        if (reservedByTier != null) {
            for (StockLedger ledger : reservedByTier.values()) {
                if (ledger == null) continue;
                for (var e : ledger.entries().entrySet()) {
                    mergedRes.merge(e.getKey(), e.getValue(), (a, b) -> {
                        try {
                            return Math.addExact(a, b);
                        } catch (ArithmeticException ex) {
                            return Long.MAX_VALUE;
                        }
                    });
                }
            }
        }
        return new InventorySnapshot(
            mergedAvail.isEmpty() ? StockLedger.empty : new StockLedger(mergedAvail),
            mergedRes.isEmpty() ? StockLedger.empty : new StockLedger(mergedRes),
            availableByTier == null ? Map.of() : availableByTier,
            reservedByTier == null ? Map.of() : reservedByTier,
            alreadyOpenTiers == null ? Set.of() : alreadyOpenTiers,
            protectedFromGenericTags == null ? Set.of() : protectedFromGenericTags
        );
    }

    public long usableAmount(ResourceKey key) {
        if (!availableByTier.isEmpty()) {
            long total = 0L;
            for (InventoryTier tier : InventoryTier.values()) {
                long u = usableAmountInTier(key, tier);
                try {
                    total = Math.addExact(total, u);
                } catch (ArithmeticException ex) {
                    return Long.MAX_VALUE;
                }
            }
            return total;
        }
        return Math.max(0L, available.amountOf(key) - reserved.amountOf(key));
    }

    public long usableAmountInTier(ResourceKey key, InventoryTier tier) {
        StockLedger a = availableByTier.get(tier);
        if (a == null || a.isEmpty()) {
            return 0L;
        }
        StockLedger r = reservedByTier.get(tier);
        long avail = a.amountOf(key);
        long res = r == null ? 0L : r.amountOf(key);
        return Math.max(0L, avail - res);
    }

    public boolean hasNetworkStorage() {
        StockLedger net = availableByTier.get(InventoryTier.network);
        return net != null && !net.isEmpty();
    }

    public boolean isProtectedFromGenericTag(ResourceKey key) {
        return protectedFromGenericTags.contains(key);
    }

    public Map<ResourceKey, Long> allUsable() {
        if (available.isEmpty() && availableByTier.isEmpty()) {
            return Map.of();
        }
        TreeMap<ResourceKey, Long> out = new TreeMap<>();
        if (!availableByTier.isEmpty()) {
            for (StockLedger ledger : availableByTier.values()) {
                for (ResourceKey k : ledger.entries().keySet()) {
                    out.putIfAbsent(k, usableAmount(k));
                }
            }
            out.values().removeIf(v -> v <= 0L);
        } else {
            for (var e : available.entries().entrySet()) {
                long usable = Math.max(0L, e.getValue() - reserved.amountOf(e.getKey()));
                if (usable > 0L) {
                    out.put(e.getKey(), usable);
                }
            }
        }
        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }
}

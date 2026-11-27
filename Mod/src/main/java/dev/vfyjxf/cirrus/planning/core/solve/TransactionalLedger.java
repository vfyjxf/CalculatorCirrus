package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventoryTier;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * M1.2 / M7.1 Transactional Two-Pool FIFO Ledger with Exact Checkpoint / Rollback (Pure JDK 21).
 *
 * <p>Maintains per-resource ordinaryPool L^o and byproductPool L^b with lot provenance,
 * per-tier finite inventory pulls (never exceeding I_{v,l} - R_{v,l}), non-consumable lock exclusivity,
 * and byte-equivalent rollback upon branch failure.
 */
public final class TransactionalLedger {

    public enum PoolKind {
        ordinary,
        byproduct
    }

    public record Lot(
        long lotId,
        PoolKind pool,
        ResourceKey material,
        int sourceEventIndex,
        int streamIndex,
        long remainingQuantity
    ) {}

    public record LotSlice(long lotId, PoolKind pool, ResourceKey material, int sourceEventIndex, long quantity) {}

    public record SnapshotCheckpoint(
        int eventSeq,
        long nextLotId,
        Map<InventoryTier, Map<ResourceKey, Long>> remainingTierStock,
        Map<ResourceKey, List<Lot>> ordinaryLots,
        Map<ResourceKey, List<Lot>> byproductLots,
        Map<ResourceKey, Long> lockedCounts,
        Set<String> availableMachines
    ) {}

    private int eventSeq = 0;
    private long nextLotId = 1L;
    private final EnumMap<InventoryTier, Map<ResourceKey, Long>> remainingTierStock = new EnumMap<>(InventoryTier.class);
    private final Map<ResourceKey, Deque<Lot>> ordinaryPools = new HashMap<>();
    private final Map<ResourceKey, Deque<Lot>> byproductPools = new HashMap<>();
    private final Map<ResourceKey, Long> lockedCounts = new HashMap<>();
    private final Set<String> availableMachines = new HashSet<>();

    public TransactionalLedger(InventorySnapshot initialInventory, Set<String> ownedMachines) {
        Objects.requireNonNull(initialInventory, "initialInventory");
        for (InventoryTier tier : InventoryTier.values()) {
            Map<ResourceKey, Long> map = new TreeMap<>();
            var avail = initialInventory.availableByTier().get(tier);
            if (avail != null) {
                for (ResourceKey k : avail.entries().keySet()) {
                    long usable = initialInventory.usableAmountInTier(k, tier);
                    if (usable > 0L) {
                        map.put(k, usable);
                    }
                }
            }
            remainingTierStock.put(tier, map);
        }
        if (ownedMachines != null) {
            availableMachines.addAll(ownedMachines);
        }
    }

    public int currentEventSeq() {
        return eventSeq;
    }

    public int advanceEventSeq() {
        return ++eventSeq;
    }

    public boolean hasMachine(String machineId) {
        return availableMachines.contains(machineId);
    }

    public void installMachine(String machineId) {
        availableMachines.add(Objects.requireNonNull(machineId, "machineId"));
    }

    public long remainingTierStock(InventoryTier tier, ResourceKey resource) {
        return remainingTierStock.getOrDefault(tier, Map.of()).getOrDefault(resource, 0L);
    }

    /** Pulls authorized stock from a specific inventory tier into ordinaryPool (never exceeding finite balance). */
    public Lot pullFromTier(InventoryTier tier, ResourceKey resource, long quantity, int eventIndex) {
        if (quantity <= 0L) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        Map<ResourceKey, Long> map = remainingTierStock.get(tier);
        long avail = map == null ? 0L : map.getOrDefault(resource, 0L);
        if (map == null || quantity > avail) {
            throw new IllegalStateException("Insufficient stock in tier " + tier + " for " + resource + ": need " + quantity + ", have " + avail);
        }
        long next = avail - quantity;
        if (next == 0L) {
            map.remove(resource);
        } else {
            map.put(resource, next);
        }
        return publishLot(PoolKind.ordinary, resource, eventIndex, 0, quantity);
    }

    /** Publishes a completed output or external supply lot into ordinaryPool or byproductPool. */
    public Lot publishLot(PoolKind pool, ResourceKey resource, int sourceEventIndex, int streamIndex, long quantity) {
        if (quantity <= 0L) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        Lot lot = new Lot(nextLotId++, pool, resource, sourceEventIndex, streamIndex, quantity);
        Map<ResourceKey, Deque<Lot>> pools = pool == PoolKind.byproduct ? byproductPools : ordinaryPools;
        pools.computeIfAbsent(resource, k -> new ArrayDeque<>()).addLast(lot);
        return lot;
    }

    public long totalFreePublished(ResourceKey resource) {
        long sum = 0L;
        for (Lot l : byproductPools.getOrDefault(resource, new ArrayDeque<>())) {
            sum = Math.addExact(sum, l.remainingQuantity());
        }
        for (Lot l : ordinaryPools.getOrDefault(resource, new ArrayDeque<>())) {
            sum = Math.addExact(sum, l.remainingQuantity());
        }
        long locked = lockedCounts.getOrDefault(resource, 0L);
        return Math.max(0L, sum - locked);
    }

    /**
     * Consumes concrete material FIFO (byproductPool first, then ordinaryPool), rejecting any lot
     * with sourceEventIndex >= consumerEventIndex (constructive proof against byproduct reverse edges!).
     */
    public List<LotSlice> consumeFifo(ResourceKey resource, long quantity, int consumerEventIndex) {
        if (quantity <= 0L) {
            return List.of();
        }
        long free = totalFreePublished(resource);
        if (free < quantity) {
            throw new IllegalStateException("Insufficient free published lots for " + resource + ": need " + quantity + ", free " + free);
        }
        List<LotSlice> slices = new ArrayList<>();
        long rem = consumeFromPool(byproductPools.get(resource), quantity, consumerEventIndex, slices);
        if (rem > 0L) {
            rem = consumeFromPool(ordinaryPools.get(resource), rem, consumerEventIndex, slices);
        }
        if (rem > 0L) {
            throw new IllegalStateException("Future lot violation or shortage for " + resource + ", remaining " + rem);
        }
        return slices;
    }

    private long consumeFromPool(@Nullable Deque<Lot> queue, long needed, int consumerEventIndex, List<LotSlice> outSlices) {
        if (queue == null || queue.isEmpty() || needed <= 0L) {
            return needed;
        }
        long rem = needed;
        while (rem > 0L && !queue.isEmpty()) {
            Lot head = queue.peekFirst();
            if (head.sourceEventIndex() >= consumerEventIndex) {
                // Strictly forbid consuming a lot produced at or after consumerEventIndex.
                break;
            }
            long take = Math.min(rem, head.remainingQuantity());
            queue.pollFirst();
            outSlices.add(new LotSlice(head.lotId(), head.pool(), head.material(), head.sourceEventIndex(), take));
            long left = head.remainingQuantity() - take;
            if (left > 0L) {
                queue.addFirst(new Lot(head.lotId(), head.pool(), head.material(), head.sourceEventIndex(), head.streamIndex(), left));
            }
            rem -= take;
        }
        return rem;
    }

    /** Locks non-consumable catalysts exclusively (cannot simultaneously be consumed). */
    public void lockCatalyst(ResourceKey resource, long count) {
        if (count <= 0L) return;
        long free = totalFreePublished(resource);
        if (free < count) {
            throw new IllegalStateException("Cannot lock " + count + " of " + resource + ", free=" + free);
        }
        lockedCounts.merge(resource, count, Math::addExact);
    }

    public void unlockCatalyst(ResourceKey resource, long count) {
        if (count <= 0L) return;
        long cur = lockedCounts.getOrDefault(resource, 0L);
        if (cur < count) {
            throw new IllegalStateException("Unlock underflow for " + resource);
        }
        long next = cur - count;
        if (next == 0L) {
            lockedCounts.remove(resource);
        } else {
            lockedCounts.put(resource, next);
        }
    }

    public SnapshotCheckpoint checkpoint() {
        EnumMap<InventoryTier, Map<ResourceKey, Long>> stockCopy = new EnumMap<>(InventoryTier.class);
        for (var e : remainingTierStock.entrySet()) {
            stockCopy.put(e.getKey(), new TreeMap<>(e.getValue()));
        }
        Map<ResourceKey, List<Lot>> ordCopy = new TreeMap<>();
        for (var e : ordinaryPools.entrySet()) {
            if (!e.getValue().isEmpty()) {
                ordCopy.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        Map<ResourceKey, List<Lot>> bypCopy = new TreeMap<>();
        for (var e : byproductPools.entrySet()) {
            if (!e.getValue().isEmpty()) {
                bypCopy.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        return new SnapshotCheckpoint(
            eventSeq,
            nextLotId,
            stockCopy,
            ordCopy,
            bypCopy,
            new TreeMap<>(lockedCounts),
            new HashSet<>(availableMachines)
        );
    }

    public void rollback(SnapshotCheckpoint cp) {
        Objects.requireNonNull(cp, "cp");
        this.eventSeq = cp.eventSeq();
        this.nextLotId = cp.nextLotId();
        this.remainingTierStock.clear();
        for (var e : cp.remainingTierStock().entrySet()) {
            this.remainingTierStock.put(e.getKey(), new TreeMap<>(e.getValue()));
        }
        this.ordinaryPools.clear();
        for (var e : cp.ordinaryLots().entrySet()) {
            this.ordinaryPools.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        }
        this.byproductPools.clear();
        for (var e : cp.byproductLots().entrySet()) {
            this.byproductPools.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        }
        this.lockedCounts.clear();
        this.lockedCounts.putAll(cp.lockedCounts());
        this.availableMachines.clear();
        this.availableMachines.addAll(cp.availableMachines());
    }

    public String canonicalStateSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append("seq=").append(eventSeq).append("|lot=").append(nextLotId).append("|stock=");
        for (InventoryTier t : InventoryTier.values()) {
            sb.append(t.name()).append(':').append(new TreeMap<>(remainingTierStock.getOrDefault(t, Map.of()))).append(';');
        }
        sb.append("|ord=").append(new TreeMap<>(ordinaryPools));
        sb.append("|byp=").append(new TreeMap<>(byproductPools));
        sb.append("|lock=").append(new TreeMap<>(lockedCounts));
        List<String> mach = new ArrayList<>(availableMachines);
        mach.sort(String::compareTo);
        sb.append("|mach=").append(mach);
        return sb.toString();
    }
}

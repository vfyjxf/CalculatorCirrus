package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.AcquireRow;
import dev.vfyjxf.cirrus.ui.workbench.layout.PreparationLedgerModel.LedgerSortKey;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Pure sort keys and What-If inventory derivations for the preparation ledger.
 */
public final class LedgerSorting {

    public static final int sortKeyCount = 5;
    public static final long minSimulatedAmount = 0L;
    public static final long maxSimulatedAmount = 65_536L;
    public static final int sectionCount = 4;

    private static final LedgerSortKey[] keys = LedgerSortKey.values();

    private LedgerSorting() {}

    public static LedgerSortKey keyAt(int index) {
        int wrapped = Math.floorMod(index, keys.length);
        return keys[wrapped];
    }

    public static int indexOf(LedgerSortKey key) {
        Objects.requireNonNull(key, "key");
        return key.ordinal();
    }

    public static LedgerSortKey nextKey(LedgerSortKey key) {
        return keyAt(indexOf(key) + 1);
    }

    public static InventorySnapshot overlay(InventorySnapshot base, Map<ComponentStackKey, Long> simulated) {
        InventorySnapshot source = base == null ? InventorySnapshot.empty : base;
        if (simulated == null || simulated.isEmpty()) {
            return source;
        }
        TreeMap<ComponentStackKey, Long> available = new TreeMap<>(source.available().entries());
        for (Map.Entry<ComponentStackKey, Long> entry : simulated.entrySet()) {
            ComponentStackKey resource = entry.getKey();
            Long amount = entry.getValue();
            if (resource == null || amount == null) {
                continue;
            }
            long clamped = Math.max(minSimulatedAmount, Math.min(maxSimulatedAmount, amount));
            if (clamped <= 0L) {
                available.remove(resource);
            } else {
                available.put(resource, clamped);
            }
        }
        return new InventorySnapshot(
            new StockLedger(available),
            source.reserved(),
            source.inChest(),
            source.inStorageNetwork()
        );
    }

    public static Map<ComponentStackKey, Long> simulate(
        Map<ComponentStackKey, Long> current,
        ComponentStackKey resource,
        long amount
    ) {
        Objects.requireNonNull(resource, "resource");
        Map<ComponentStackKey, Long> next = current == null ? new TreeMap<>() : new TreeMap<>(current);
        long clamped = Math.max(minSimulatedAmount, Math.min(maxSimulatedAmount, amount));
        next.put(resource, clamped);
        return next;
    }

    public static long effectiveAvailable(AcquireRow row, InventorySnapshot overlaid) {
        if (row == null) {
            return 0L;
        }
        InventorySnapshot snapshot = overlaid == null ? InventorySnapshot.empty : overlaid;
        return snapshot.usableAmount(row.resource());
    }

    public static long effectiveShortfall(AcquireRow row, InventorySnapshot overlaid) {
        if (row == null) {
            return 0L;
        }
        return Math.max(0L, row.neededAmount() - effectiveAvailable(row, overlaid));
    }

    public static long sliderMax(@Nullable AcquireRow row) {
        if (row == null) {
            return 1L;
        }
        long needed = Math.max(0L, row.neededAmount());
        long available = Math.max(0L, row.availableStock());
        return Math.max(1L, Math.min(maxSimulatedAmount, Math.max(needed, available) * 2L));
    }

    public static long groupCount(long amount) {
        return Math.max(0L, amount) / WorkbenchFormatters.itemsPerStack;
    }

    public static long groupRemainder(long amount) {
        return Math.max(0L, amount) % WorkbenchFormatters.itemsPerStack;
    }

    public static double costShare(@Nullable AcquireRow row) {
        if (row == null || !Double.isFinite(row.costShare())) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, row.costShare()));
    }

    public static int sectionColumns(WorkbenchBreakpoint breakpoint) {
        return breakpoint == WorkbenchBreakpoint.wide ? 2 : 1;
    }

    public static boolean sectionsAsTabs(WorkbenchBreakpoint breakpoint) {
        return breakpoint != WorkbenchBreakpoint.wide;
    }

    public static double coverage(@Nullable PreparationLedgerModel model) {
        if (model == null || !Double.isFinite(model.stockCoverageRatio())) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, model.stockCoverageRatio()));
    }
}

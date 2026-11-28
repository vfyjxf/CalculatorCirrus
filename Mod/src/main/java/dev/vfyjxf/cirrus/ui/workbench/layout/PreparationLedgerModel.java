package dev.vfyjxf.cirrus.ui.workbench.layout;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record PreparationLedgerModel(
    List<AcquireRow> acquireRows,
    List<AcquireRow> stockConsumedRows,
    List<MachinePrepRow> machinesToProvision,
    Set<String> automatedLinesUsed,
    Set<ComponentStackKey> catalystsRequired,
    List<ByproductEntry> surplusByproducts,
    double stockCoverageRatio
) {

    public enum LedgerSortKey {
        missingFirst, availableFirst, amount, name, id
    }

    public record AcquireRow(
        ComponentStackKey resource,
        long neededAmount,
        long availableStock,
        long reservedStock,
        double costShare,
        List<String> stepIds
    ) {

        public AcquireRow {
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(stepIds, "stepIds");
            stepIds = List.copyOf(stepIds);
        }

        public long usableStock() {
            return Math.max(0L, availableStock - reservedStock);
        }

        public long shortfall() {
            return Math.max(0L, neededAmount - usableStock());
        }
    }

    public record MachinePrepRow(MachineEntry machine, int countToBuild, List<String> stepIds) {

        public MachinePrepRow {
            Objects.requireNonNull(machine, "machine");
            Objects.requireNonNull(stepIds, "stepIds");
            stepIds = List.copyOf(stepIds);
        }
    }

    public PreparationLedgerModel {
        Objects.requireNonNull(acquireRows, "acquireRows");
        Objects.requireNonNull(stockConsumedRows, "stockConsumedRows");
        Objects.requireNonNull(machinesToProvision, "machinesToProvision");
        Objects.requireNonNull(automatedLinesUsed, "automatedLinesUsed");
        Objects.requireNonNull(catalystsRequired, "catalystsRequired");
        Objects.requireNonNull(surplusByproducts, "surplusByproducts");
        acquireRows = List.copyOf(acquireRows);
        stockConsumedRows = List.copyOf(stockConsumedRows);
        machinesToProvision = List.copyOf(machinesToProvision);
        automatedLinesUsed = Collections.unmodifiableSet(new TreeSet<>(automatedLinesUsed));
        catalystsRequired = Collections.unmodifiableSet(new TreeSet<>(catalystsRequired));
        surplusByproducts = List.copyOf(surplusByproducts);
        if (!Double.isFinite(stockCoverageRatio)) {
            stockCoverageRatio = 0.0;
        }
        stockCoverageRatio = Math.max(0.0, Math.min(1.0, stockCoverageRatio));
    }

    public static PreparationLedgerModel build(
        @Nullable ProductionPlan plan,
        @Nullable InventorySnapshot inventory,
        @Nullable LedgerSortKey sortKey,
        boolean ascending,
        @Nullable String query
    ) {
        if (plan == null) {
            return new PreparationLedgerModel(List.of(), List.of(), List.of(), Set.of(), Set.of(), List.of(), 0.0);
        }
        InventorySnapshot snapshot = inventory == null ? InventorySnapshot.empty : inventory;
        PreparationSummary preparation = plan.preparation();
        Map<ComponentStackKey, Double> resourceCost = resourceCosts(plan.steps());
        double totalCost = 0.0;
        for (double value : resourceCost.values()) {
            totalCost += value;
        }

        List<AcquireRow> acquireRows = acquireRows(
            preparation.toAcquireFromWorld(),
            snapshot,
            plan.steps(),
            resourceCost,
            totalCost
        );
        List<AcquireRow> stockConsumedRows = acquireRows(
            preparation.consumedFromInventory(),
            snapshot,
            plan.steps(),
            resourceCost,
            totalCost
        );
        List<MachinePrepRow> machineRows = machineRows(preparation, plan.steps());

        LedgerSortKey effectiveKey = sortKey == null ? LedgerSortKey.missingFirst : sortKey;
        List<AcquireRow> filteredAcquire = sortAndFilter(acquireRows, effectiveKey, ascending, query);
        List<AcquireRow> filteredConsumed = sortAndFilter(stockConsumedRows, effectiveKey, ascending, query);

        List<ByproductEntry> byproducts = new ArrayList<>(plan.surplusByproducts());
        byproducts.sort(
            Comparator.comparing(ByproductEntry::resource).thenComparing(ByproductEntry::producedByStepId)
                    .thenComparingLong(ByproductEntry::amount)
        );

        return new PreparationLedgerModel(
            filteredAcquire,
            filteredConsumed,
            machineRows,
            new TreeSet<>(preparation.existingLinesUsed()),
            new TreeSet<>(preparation.catalystsRequired()),
            byproducts,
            coverageRatio(acquireRows, snapshot)
        );
    }

    private static Map<ComponentStackKey, Double> resourceCosts(List<PlanStep> steps) {
        Map<ComponentStackKey, Double> costs = new TreeMap<>();
        for (PlanStep step : steps) {
            ComponentStackKey resource = referencedResource(step);
            if (resource == null) {
                continue;
            }
            double cost = step.stepCost().humanActiveSeconds() + step.stepCost().passiveWaitSeconds();
            costs.merge(resource, cost, Double::sum);
        }
        return costs;
    }

    @Nullable
    private static ComponentStackKey referencedResource(PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> acquire.resource();
            case LinePullStep pull -> pull.resource();
            default -> null;
        };
    }

    private static List<AcquireRow> acquireRows(
        StockLedger ledger,
        InventorySnapshot snapshot,
        List<PlanStep> steps,
        Map<ComponentStackKey, Double> resourceCost,
        double totalCost
    ) {
        List<AcquireRow> rows = new ArrayList<>(ledger.entries().size());
        for (var entry : ledger.entries().entrySet()) {
            ComponentStackKey resource = entry.getKey();
            double cost = resourceCost.getOrDefault(resource, 0.0);
            double share = totalCost <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, cost / totalCost));
            rows.add(
                new AcquireRow(
                    resource,
                    entry.getValue(),
                    snapshot.available().amountOf(resource),
                    snapshot.reserved().amountOf(resource),
                    share,
                    stepIdsFor(steps, resource)
                )
            );
        }
        return rows;
    }

    private static List<String> stepIdsFor(List<PlanStep> steps, ComponentStackKey resource) {
        TreeSet<String> ids = new TreeSet<>();
        for (PlanStep step : steps) {
            if (resource.equals(referencedResource(step))) {
                ids.add(step.stepId());
            }
        }
        return List.copyOf(ids);
    }

    private static List<MachinePrepRow> machineRows(PreparationSummary preparation, List<PlanStep> steps) {
        List<MachinePrepRow> rows = new ArrayList<>(preparation.machinesToProvision().size());
        for (var entry : preparation.machinesToProvision().entrySet()) {
            TreeSet<String> ids = new TreeSet<>();
            for (PlanStep step : steps) {
                if (step instanceof ProvisionMachineStep provision && entry.getKey().equals(provision.machine())) {
                    ids.add(provision.stepId());
                }
            }
            rows.add(new MachinePrepRow(entry.getKey(), entry.getValue(), List.copyOf(ids)));
        }
        return rows;
    }

    private static List<AcquireRow> sortAndFilter(
        List<AcquireRow> rows,
        LedgerSortKey sortKey,
        boolean ascending,
        @Nullable String query
    ) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<AcquireRow> filtered = new ArrayList<>(rows.size());
        for (AcquireRow row : rows) {
            if (needle.isEmpty() || row.resource().displayId().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(row);
            }
        }
        Comparator<AcquireRow> base = baseComparator(sortKey);
        Comparator<AcquireRow> ordered = (left, right) -> {
            int primary = ascending ? base.compare(left, right) : base.compare(right, left);
            if (primary != 0) {
                return primary;
            }
            return left.resource().compareTo(right.resource());
        };
        filtered.sort(ordered);
        return filtered;
    }

    private static Comparator<AcquireRow> baseComparator(LedgerSortKey sortKey) {
        return switch (sortKey) {
            case missingFirst -> Comparator.comparingLong(AcquireRow::shortfall).reversed();
            case availableFirst -> Comparator.comparingLong(AcquireRow::usableStock).reversed();
            case amount -> Comparator.comparingLong(AcquireRow::neededAmount);
            case name -> Comparator.comparing((AcquireRow row) -> row.resource().displayId());
            case id -> Comparator.comparing(AcquireRow::resource);
        };
    }

    private static double coverageRatio(List<AcquireRow> rows, InventorySnapshot snapshot) {
        long neededTotal = 0L;
        long covered = 0L;
        for (AcquireRow row : rows) {
            if (row.neededAmount() <= 0L) {
                continue;
            }
            neededTotal += row.neededAmount();
            covered += Math.min(snapshot.usableAmount(row.resource()), row.neededAmount());
        }
        if (neededTotal <= 0L) {
            return 0.0;
        }
        double ratio = (double) covered / (double) neededTotal;
        if (!Double.isFinite(ratio)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, ratio));
    }
}

package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure target-basket merges: set as target, append to the basket, change the primary target's count.
 */
public final class ChromeTargets {

    private ChromeTargets() {}

    public static long amountOf(List<TargetDemand> current, ResourceKey resource) {
        if (current == null || resource == null) {
            return 0L;
        }
        for (TargetDemand demand : current) {
            if (demand.resource().equals(resource)) {
                return demand.amount();
            }
        }
        return 0L;
    }

    public static List<TargetDemand> merged(List<TargetDemand> current, ResourceKey resource, boolean addToBasket) {
        Objects.requireNonNull(resource, "resource");
        List<TargetDemand> source = current == null ? List.of() : current;
        long existing = amountOf(source, resource);
        List<TargetDemand> next = new ArrayList<>(source.size() + 1);
        if (!addToBasket) {
            next.add(new TargetDemand(resource, Math.max(1L, existing)));
            return List.copyOf(next);
        }
        boolean found = false;
        for (TargetDemand demand : source) {
            if (demand.resource().equals(resource)) {
                next.add(new TargetDemand(resource, demand.amount() + 1L));
                found = true;
            } else {
                next.add(demand);
            }
        }
        if (!found) {
            next.add(new TargetDemand(resource, 1L));
        }
        return List.copyOf(next);
    }

    public static List<TargetDemand> withPrimaryAmount(List<TargetDemand> current, long amount) {
        List<TargetDemand> source = current == null ? List.of() : current;
        if (source.isEmpty()) {
            return List.of();
        }
        List<TargetDemand> next = new ArrayList<>(source);
        TargetDemand first = source.get(0);
        next.set(0, new TargetDemand(first.resource(), Math.max(1L, amount)));
        return List.copyOf(next);
    }

    public static List<TargetDemand> withAmount(List<TargetDemand> current, int rowIndex, long amount) {
        List<TargetDemand> source = current == null ? List.of() : current;
        if (rowIndex < 0 || rowIndex >= source.size()) {
            return source;
        }
        List<TargetDemand> next = new ArrayList<>(source);
        TargetDemand demand = source.get(rowIndex);
        next.set(rowIndex, new TargetDemand(demand.resource(), Math.max(1L, amount)));
        return List.copyOf(next);
    }

    public static List<TargetDemand> removedAt(List<TargetDemand> current, int rowIndex) {
        List<TargetDemand> source = current == null ? List.of() : current;
        if (rowIndex < 0 || rowIndex >= source.size()) {
            return source;
        }
        List<TargetDemand> next = new ArrayList<>(source);
        next.remove(rowIndex);
        return List.copyOf(next);
    }
}

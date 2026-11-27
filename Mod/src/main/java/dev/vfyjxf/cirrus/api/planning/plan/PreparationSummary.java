package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record PreparationSummary(
    StockLedger consumedFromInventory,
    StockLedger toAcquireFromWorld,
    Map<String, Integer> machinesToProvision,
    List<String> existingLinesUsed,
    List<ResourceKey> catalystsRequired
) {

    public static final PreparationSummary empty = new PreparationSummary(
        StockLedger.empty,
        StockLedger.empty,
        Map.of(),
        List.of(),
        List.of()
    );

    public PreparationSummary {
        Objects.requireNonNull(consumedFromInventory, "consumedFromInventory");
        Objects.requireNonNull(toAcquireFromWorld, "toAcquireFromWorld");
        Objects.requireNonNull(machinesToProvision, "machinesToProvision");
        Objects.requireNonNull(existingLinesUsed, "existingLinesUsed");
        Objects.requireNonNull(catalystsRequired, "catalystsRequired");
        if (machinesToProvision.isEmpty()) {
            machinesToProvision = Map.of();
        } else {
            TreeMap<String, Integer> sorted = new TreeMap<>();
            for (var e : machinesToProvision.entrySet()) {
                sorted.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
            }
            machinesToProvision = Collections.unmodifiableMap(sorted);
        }
        existingLinesUsed = List.copyOf(existingLinesUsed);
        catalystsRequired = List.copyOf(catalystsRequired);
    }
}

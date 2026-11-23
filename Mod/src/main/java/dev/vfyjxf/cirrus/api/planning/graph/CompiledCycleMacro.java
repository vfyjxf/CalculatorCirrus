package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record CompiledCycleMacro(
        CompiledCycleMacroKey macroKey,
        List<CompiledRecipe> innerRecipes,
        List<Long> innerMultipliers,
        Map<ResourceKey, Long> workingSeeds,
        Map<ResourceKey, Long> netDeltaPerIter
) {

    public CompiledCycleMacro {
        Objects.requireNonNull(macroKey, "macroKey");
        Objects.requireNonNull(innerRecipes, "innerRecipes");
        Objects.requireNonNull(innerMultipliers, "innerMultipliers");
        Objects.requireNonNull(workingSeeds, "workingSeeds");
        Objects.requireNonNull(netDeltaPerIter, "netDeltaPerIter");
        innerRecipes = List.copyOf(innerRecipes);
        innerMultipliers = List.copyOf(innerMultipliers);
        if (innerRecipes.isEmpty() || innerRecipes.size() != innerMultipliers.size()) {
            throw new IllegalArgumentException("innerRecipes and innerMultipliers must be non-empty and same size");
        }
        for (Long m : innerMultipliers) {
            if (m <= 0L) {
                throw new IllegalArgumentException("innerMultiplier must be > 0, got " + m);
            }
        }
        TreeMap<ResourceKey, Long> sortedSeeds = new TreeMap<>();
        for (var e : workingSeeds.entrySet()) {
            ResourceKey k = Objects.requireNonNull(e.getKey());
            Long v = Objects.requireNonNull(e.getValue());
            if (v <= 0L) {
                throw new IllegalArgumentException("workingSeed must be > 0 for " + k);
            }
            sortedSeeds.put(k, v);
        }
        workingSeeds = sortedSeeds.isEmpty() ? Map.of() : Collections.unmodifiableMap(sortedSeeds);
        TreeMap<ResourceKey, Long> sortedDelta = new TreeMap<>();
        for (var e : netDeltaPerIter.entrySet()) {
            sortedDelta.put(Objects.requireNonNull(e.getKey()), Objects.requireNonNull(e.getValue()));
        }
        netDeltaPerIter = sortedDelta.isEmpty() ? Map.of() : Collections.unmodifiableMap(sortedDelta);
    }
}

package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ProductionVector {

    public final long[] need;
    public final long[] net;
    public final long[] gross;
    public final long[] presenceNeed;
    public final long[] batches;
    public final long[] convertBatches;
    public final long[] acq;
    public final long[] line;
    public final int[] chosenSlotBind;
    public final int[] chosenCatBind;
    public final String[] chosenMachine;
    public final Map<CompiledCycleMacroKey, CompiledCycleMacro> usedMacros = new TreeMap<>();
    public final Map<CompiledCycleMacroKey, Long> macroIters = new TreeMap<>();
    public final Map<String, ProvisionMeta> provisionedMachines = new TreeMap<>();
    public final Set<String> playerPreparedMachines = new TreeSet<>();
    public final Map<String, SetupLineMeta> setupLines = new TreeMap<>();
    public final Set<Integer> automatedLocalRecipes = new TreeSet<>();
    public final Map<ResourceKey, String> unmetReasons = new TreeMap<>();
    public final Set<String> hitRuleIds = new TreeSet<>();
    public boolean exploded;

    public record ProvisionMeta(String machineId, String systemId, MachineCapabilityId capability, int count) {
    }

    public record SetupLineMeta(String systemId, String primaryMachineId, ResourceKey targetOutput,
                                MachineCapabilityId capability) {
    }

    public ProductionVector(int nodeCount, int recipeCount, int totalInSlots, int totalCatSlots) {
        this.need = new long[nodeCount];
        this.net = new long[nodeCount];
        this.gross = new long[nodeCount];
        this.presenceNeed = new long[nodeCount];
        this.batches = new long[recipeCount];
        this.convertBatches = new long[recipeCount];
        this.acq = new long[nodeCount];
        this.line = new long[nodeCount];
        this.chosenSlotBind = new int[totalInSlots];
        this.chosenCatBind = new int[totalCatSlots];
        this.chosenMachine = new String[recipeCount];
    }

    public boolean isFeasible() {
        return !exploded && unmetReasons.isEmpty();
    }

    public Map<Integer, Long> totalProducedByLocalRes() {
        Map<Integer, Long> out = new LinkedHashMap<>();
        for (int u = 0; u < need.length; u++) {
            long prod = gross[u];
            if (prod > 0L) {
                out.put(u, prod);
            }
        }
        return out;
    }
}

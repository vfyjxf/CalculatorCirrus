package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.Comparator;
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
    public final MachineEntry[] chosenMachine;
    public final Map<CompiledCycleMacroKey, CompiledCycleMacro> usedMacros = new TreeMap<>();
    public final Map<CompiledCycleMacroKey, Long> macroIters = new TreeMap<>();
    public final Map<MachineEntry, ProvisionMeta> provisionedMachines = new TreeMap<>(MachineEntry.comparator);
    public final Set<MachineEntry> playerPreparedMachines = new TreeSet<>(MachineEntry.comparator);
    public final Map<Namespace, SetupLineMeta> setupLines = new TreeMap<>(Comparator.comparing(Namespace::toString));
    public final Set<Integer> automatedLocalRecipes = new TreeSet<>();
    public final Map<ComponentStackKey, String> unmetReasons = new TreeMap<>();
    public final Set<String> hitRuleIds = new TreeSet<>();
    private boolean exploded;

    public record ProvisionMeta(
        MachineEntry machine,
        Namespace systemId,
        Set<Namespace> systemTags,
        MachineCapabilityId capability,
        int count
    ) {}

    public record SetupLineMeta(
        Namespace systemId,
        Set<Namespace> systemTags,
        MachineEntry primaryMachine,
        ComponentStackKey targetOutput,
        MachineCapabilityId capability
    ) {}

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
        this.chosenMachine = new MachineEntry[recipeCount];
    }

    public boolean isFeasible() {
        return !exploded && unmetReasons.isEmpty();
    }

    public boolean exploded() {
        return exploded;
    }

    public void markExploded() {
        exploded = true;
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

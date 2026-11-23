package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.planning.core.lang.Fnv1a64;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class ChoiceTable {

    public record BatchSplit(int localRecipe, long batches) {
    }

    public final Potentials.SourceChoice[] sourceByRes;
    public final int[] slotRepOverride;
    public final TreeMap<Integer, List<BatchSplit>> batchMix;
    public final TreeMap<Integer, String> machineOverride;
    public final TreeSet<Integer> automatedRecipes;

    public ChoiceTable(Potentials potentials) {
        this.sourceByRes = Arrays.copyOf(potentials.prodChoice, potentials.prodChoice.length);
        this.slotRepOverride = Arrays.copyOf(potentials.bestSlotRep, potentials.bestSlotRep.length);
        this.batchMix = new TreeMap<>();
        this.machineOverride = new TreeMap<>();
        this.automatedRecipes = new TreeSet<>();
    }

    private ChoiceTable(
            Potentials.SourceChoice[] sourceByRes,
            int[] slotRepOverride,
            TreeMap<Integer, List<BatchSplit>> batchMix,
            TreeMap<Integer, String> machineOverride,
            TreeSet<Integer> automatedRecipes
    ) {
        this.sourceByRes = sourceByRes;
        this.slotRepOverride = slotRepOverride;
        this.batchMix = batchMix;
        this.machineOverride = machineOverride;
        this.automatedRecipes = automatedRecipes;
    }

    public ChoiceTable copy() {
        TreeMap<Integer, List<BatchSplit>> mixCopy = new TreeMap<>(batchMix);
        TreeMap<Integer, String> machCopy = new TreeMap<>(machineOverride);
        TreeSet<Integer> autoCopy = new TreeSet<>(automatedRecipes);
        return new ChoiceTable(
                Arrays.copyOf(sourceByRes, sourceByRes.length),
                Arrays.copyOf(slotRepOverride, slotRepOverride.length),
                mixCopy, machCopy, autoCopy
        );
    }

    public long fingerprint() {
        long h = Fnv1a64.initial();
        for (int i = 0; i < sourceByRes.length; i++) {
            Potentials.SourceChoice sc = sourceByRes[i];
            if (sc == null || sc.kind() == Potentials.SourceKind.NONE) continue;
            h = Fnv1a64.mixLong(h, i);
            h = Fnv1a64.mixByte(h, sc.kind().ordinal());
            if (sc.localRecipe() >= 0) h = Fnv1a64.mixLong(h, sc.localRecipe());
            if (sc.macro() != null) h = Fnv1a64.mixString(h, sc.macro().macroKey().macroId());
            if (sc.chosenMachine() != null) h = Fnv1a64.mixString(h, sc.chosenMachine());
        }
        h = Fnv1a64.mixByte(h, '|');
        for (var e : batchMix.entrySet()) {
            h = Fnv1a64.mixLong(h, e.getKey());
            for (BatchSplit bs : e.getValue()) {
                h = Fnv1a64.mixLong(h, bs.localRecipe());
                h = Fnv1a64.mixLong(h, bs.batches());
            }
        }
        h = Fnv1a64.mixByte(h, '|');
        for (var e : machineOverride.entrySet()) {
            h = Fnv1a64.mixLong(h, e.getKey());
            h = Fnv1a64.mixString(h, e.getValue());
        }
        h = Fnv1a64.mixByte(h, '|');
        for (int r : automatedRecipes) {
            h = Fnv1a64.mixLong(h, r);
        }
        return h;
    }
}

package dev.vfyjxf.cirrus.api.planning.plan;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Prerequisite regime signature and atomic premise formula:
 * determines whether plan A's prerequisites are entailed by plan B's environment,
 * preventing incompatible tech trees from falsely Pareto-dominating each other.
 */
public record RegimeSignature(
    Set<MachineEntry> newHeavyMachines,
    Set<String> techClusters,
    boolean requiresFieldTrip,
    Set<String> premiseAtoms
) implements Comparable<RegimeSignature> {

    public static final RegimeSignature baseline = new RegimeSignature(Set.of(), Set.of(), false, Set.of());

    public RegimeSignature {
        Objects.requireNonNull(newHeavyMachines, "newHeavyMachines");
        Objects.requireNonNull(techClusters, "techClusters");
        Objects.requireNonNull(premiseAtoms, "premiseAtoms");
        newHeavyMachines = sortedMachineSet(newHeavyMachines);
        techClusters = sortedSet(techClusters);
        premiseAtoms = sortedSet(premiseAtoms);
    }

    private static Set<MachineEntry> sortedMachineSet(Set<MachineEntry> src) {
        if (src.isEmpty()) return Set.of();
        TreeSet<MachineEntry> copy = new TreeSet<>(MachineEntry.comparator);
        for (MachineEntry entry : src) {
            copy.add(Objects.requireNonNull(entry, "machine"));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Set<String> sortedSet(Set<String> src) {
        if (src.isEmpty()) return Set.of();
        TreeSet<String> copy = new TreeSet<>(src);
        return Collections.unmodifiableSet(copy);
    }

    /**
     * Sufficient condition for E(this) => E(other):
     * if a player satisfies all premises of {@code this}, they automatically satisfy all premises of {@code other}.
     * Equivalently: {@code other}'s required heavy machines, tech clusters, field-trip requirement,
     * and premise atoms are a subset of {@code this}'s requirements.
     */
    public boolean entails(RegimeSignature other) {
        Objects.requireNonNull(other, "other");
        if (other.requiresFieldTrip && !this.requiresFieldTrip) {
            return false;
        }
        if (!this.newHeavyMachines.containsAll(other.newHeavyMachines)) {
            return false;
        }
        if (!this.techClusters.containsAll(other.techClusters)) {
            return false;
        }
        return this.premiseAtoms.containsAll(other.premiseAtoms);
    }

    public String canonicalKey() {
        String machines = newHeavyMachines.stream()
            .map(MachineEntry::canonicalKey)
            .collect(Collectors.joining(","));
        return "M=" + machines
                + "|C=" + String.join(",", techClusters)
                + "|T=" + (requiresFieldTrip ? "1" : "0")
                + "|E=" + String.join(",", premiseAtoms);
    }

    @Override
    public int compareTo(RegimeSignature other) {
        return this.canonicalKey().compareTo(other.canonicalKey());
    }
}

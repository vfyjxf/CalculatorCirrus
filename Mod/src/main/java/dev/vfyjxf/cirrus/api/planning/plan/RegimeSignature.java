package dev.vfyjxf.cirrus.api.planning.plan;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Prerequisite regime signature R(P) and atomic premise formula E(P) (M6):
 * determines whether plan A's prerequisites are entailed by plan B's environment (E(B) => E(A)),
 * preventing incompatible tech trees from falsely Pareto-dominating each other.
 */
public record RegimeSignature(
    Set<String> newHeavyMachines,
    Set<String> techClusters,
    boolean requiresFieldTrip,
    Set<String> premiseAtoms
) implements Comparable<RegimeSignature> {

    public static final RegimeSignature baseline = new RegimeSignature(Set.of(), Set.of(), false, Set.of());

    public RegimeSignature {
        Objects.requireNonNull(newHeavyMachines, "newHeavyMachines");
        Objects.requireNonNull(techClusters, "techClusters");
        Objects.requireNonNull(premiseAtoms, "premiseAtoms");
        newHeavyMachines = sortedSet(newHeavyMachines);
        techClusters = sortedSet(techClusters);
        premiseAtoms = sortedSet(premiseAtoms);
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
        return "M=" + String.join(",", newHeavyMachines)
                + "|C=" + String.join(",", techClusters)
                + "|T=" + (requiresFieldTrip ? "1" : "0")
                + "|E=" + String.join(",", premiseAtoms);
    }

    @Override
    public int compareTo(RegimeSignature other) {
        return this.canonicalKey().compareTo(other.canonicalKey());
    }
}

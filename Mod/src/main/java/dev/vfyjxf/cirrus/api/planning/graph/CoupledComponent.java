package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record CoupledComponent(
    CoupledKind kind,
    Set<ComponentStackKey> resources,
    Set<Integer> recipeCompactIds,
    Set<MachineEntry> candidateNewMachines
) {

    public CoupledComponent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(recipeCompactIds, "recipeCompactIds");
        Objects.requireNonNull(candidateNewMachines, "candidateNewMachines");
        resources = sortedSet(resources);
        recipeCompactIds = sortedSet(recipeCompactIds);
        if (candidateNewMachines.isEmpty()) {
            candidateNewMachines = Set.of();
        } else {
            TreeSet<MachineEntry> copy = new TreeSet<>(MachineEntry.comparator);
            for (MachineEntry m : candidateNewMachines) {
                copy.add(Objects.requireNonNull(m));
            }
            candidateNewMachines = Collections.unmodifiableSet(copy);
        }
    }

    private static <T extends Comparable<T>> Set<T> sortedSet(Set<T> source) {
        if (source.isEmpty()) return Set.of();
        TreeSet<T> copy = new TreeSet<>();
        for (T item : source) {
            copy.add(Objects.requireNonNull(item));
        }
        return Collections.unmodifiableSet(copy);
    }
}

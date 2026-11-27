package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record CoupledComponent(
    CoupledKind kind,
    Set<ResourceKey> resources,
    Set<Integer> recipeCompactIds,
    Set<String> candidateNewMachines
) {

    public CoupledComponent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(recipeCompactIds, "recipeCompactIds");
        Objects.requireNonNull(candidateNewMachines, "candidateNewMachines");
        resources = sortedSet(resources);
        recipeCompactIds = sortedSet(recipeCompactIds);
        candidateNewMachines = sortedSet(candidateNewMachines);
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

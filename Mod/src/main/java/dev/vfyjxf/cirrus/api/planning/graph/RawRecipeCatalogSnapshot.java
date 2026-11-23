package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record RawRecipeCatalogSnapshot(
        long version,
        List<RawRecipeRecord> recipes,
        Set<ResourceKey> unresolvedOutputs
) {

    public RawRecipeCatalogSnapshot(long version, List<RawRecipeRecord> recipes) {
        this(version, recipes, Set.of());
    }

    public RawRecipeCatalogSnapshot {
        Objects.requireNonNull(recipes, "recipes");
        Objects.requireNonNull(unresolvedOutputs, "unresolvedOutputs");
        recipes = List.copyOf(recipes);
        if (unresolvedOutputs.isEmpty()) {
            unresolvedOutputs = Set.of();
        } else {
            TreeSet<ResourceKey> sorted = new TreeSet<>();
            for (ResourceKey key : unresolvedOutputs) {
                sorted.add(Objects.requireNonNull(key));
            }
            unresolvedOutputs = Collections.unmodifiableSet(sorted);
        }
    }
}

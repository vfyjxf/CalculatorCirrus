package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record ProgressionState(Set<String> unlockedStages, Set<String> accessibleDimensions) {

    public static final ProgressionState OVERWORLD_DEFAULT =
            new ProgressionState(Set.of(), Set.of("minecraft:overworld"));

    public ProgressionState {
        Objects.requireNonNull(unlockedStages, "unlockedStages");
        Objects.requireNonNull(accessibleDimensions, "accessibleDimensions");
        unlockedStages = sortedSet(unlockedStages);
        accessibleDimensions = sortedSet(accessibleDimensions);
    }

    private static Set<String> sortedSet(Set<String> source) {
        if (source.isEmpty()) return Set.of();
        TreeSet<String> copy = new TreeSet<>();
        for (String s : source) {
            copy.add(Objects.requireNonNull(s));
        }
        return Collections.unmodifiableSet(copy);
    }
}

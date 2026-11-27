package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EquivalenceClass(ResourceKey representative, List<ResourceKey> members, Optional<String> sourceTagId) {

    public EquivalenceClass {
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(sourceTagId, "sourceTagId");
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("EquivalenceClass members must not be empty");
        }
    }
}

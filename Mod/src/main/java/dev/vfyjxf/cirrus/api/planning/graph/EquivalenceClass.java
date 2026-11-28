package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EquivalenceClass(
    ComponentStackKey representative,
    List<ComponentStackKey> members,
    Optional<TagKey<?>> sourceTagKey
) {

    public EquivalenceClass {
        Objects.requireNonNull(representative, "representative");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(sourceTagKey, "sourceTagKey");
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("EquivalenceClass members must not be empty");
        }
    }
}

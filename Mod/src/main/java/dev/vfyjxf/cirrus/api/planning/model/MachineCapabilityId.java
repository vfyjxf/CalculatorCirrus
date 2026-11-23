package dev.vfyjxf.cirrus.api.planning.model;

import java.util.Objects;

public record MachineCapabilityId(String id) implements Comparable<MachineCapabilityId> {

    public MachineCapabilityId {
        Objects.requireNonNull(id, "id");
        if (id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
    }

    @Override
    public int compareTo(MachineCapabilityId other) {
        return id.compareTo(other.id);
    }
}

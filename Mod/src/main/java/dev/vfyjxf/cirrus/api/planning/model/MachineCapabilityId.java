package dev.vfyjxf.cirrus.api.planning.model;

import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.Objects;

public record MachineCapabilityId(Namespace id) implements Comparable<MachineCapabilityId> {

    public MachineCapabilityId {
        Objects.requireNonNull(id, "id");
    }

    @Override
    public int compareTo(MachineCapabilityId other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}

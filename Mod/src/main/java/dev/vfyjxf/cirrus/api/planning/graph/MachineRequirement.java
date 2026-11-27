package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MachineRequirement(
    Optional<MachineCapabilityId> capabilityId,
    List<String> compatibleMachineIds,
    boolean handCraftable
) {

    public MachineRequirement {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(compatibleMachineIds, "compatibleMachineIds");
        compatibleMachineIds = List.copyOf(compatibleMachineIds);
    }
}

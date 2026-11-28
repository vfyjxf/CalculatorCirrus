package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MachineRequirement(
    Optional<MachineCapabilityId> capabilityId,
    List<MachineEntry> compatibleMachines,
    boolean handCraftable
) {

    public MachineRequirement {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(compatibleMachines, "compatibleMachines");
        compatibleMachines = List.copyOf(compatibleMachines);
    }
}

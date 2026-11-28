package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.Objects;

public record OwnedMachineGroup(
    MachineEntry machine,
    MachineCapabilityId capability,
    int count,
    double passiveSpeedMultiplier,
    boolean automatedFeeding
) {

    public OwnedMachineGroup {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(capability, "capability");
        if (machine.isEmpty()) {
            throw new IllegalArgumentException("machine must not be empty");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, got " + count);
        }
        if (!Double.isFinite(passiveSpeedMultiplier) || passiveSpeedMultiplier <= 0.0) {
            throw new IllegalArgumentException("passiveSpeedMultiplier must be > 0, got " + passiveSpeedMultiplier);
        }
    }
}

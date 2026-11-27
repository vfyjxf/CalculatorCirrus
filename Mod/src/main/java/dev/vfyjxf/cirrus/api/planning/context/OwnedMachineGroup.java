package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.Objects;

public record OwnedMachineGroup(
    String machineId,
    MachineCapabilityId capability,
    int count,
    double passiveSpeedMultiplier,
    boolean automatedFeeding
) {

    public OwnedMachineGroup {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(capability, "capability");
        if (machineId.isEmpty()) {
            throw new IllegalArgumentException("machineId must not be empty");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, got " + count);
        }
        if (!Double.isFinite(passiveSpeedMultiplier) || passiveSpeedMultiplier <= 0.0) {
            throw new IllegalArgumentException("passiveSpeedMultiplier must be > 0, got " + passiveSpeedMultiplier);
        }
    }
}

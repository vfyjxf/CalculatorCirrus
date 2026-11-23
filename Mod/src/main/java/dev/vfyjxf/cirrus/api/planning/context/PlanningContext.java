package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;

import java.util.Objects;

public record PlanningContext(
        InventorySnapshot inventory,
        FacilitySnapshot facilities,
        AcquisitionOracle acquisitionOracle,
        HumanGuidanceView guidance,
        ProgressionState progression
) {

    public PlanningContext {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(acquisitionOracle, "acquisitionOracle");
        Objects.requireNonNull(guidance, "guidance");
        Objects.requireNonNull(progression, "progression");
    }
}

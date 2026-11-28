package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;

import java.util.Objects;

public record PlanningContext(
    InventorySnapshot inventory,
    FacilitySnapshot facilities,
    BaseBoundarySnapshot baseBoundary,
    AcquisitionOracle acquisitionOracle,
    HumanGuidanceView guidance,
    ProgressionState progression,
    ContextAttributeView attributes
) {

    public PlanningContext(
        InventorySnapshot inventory,
        FacilitySnapshot facilities,
        AcquisitionOracle acquisitionOracle,
        HumanGuidanceView guidance,
        ProgressionState progression
    ) {
        this(
            inventory,
            facilities,
            BaseBoundarySnapshot.empty,
            acquisitionOracle,
            guidance,
            progression,
            ContextAttributeView.empty
        );
    }

    public PlanningContext {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(baseBoundary, "baseBoundary");
        Objects.requireNonNull(acquisitionOracle, "acquisitionOracle");
        Objects.requireNonNull(guidance, "guidance");
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(attributes, "attributes");
    }

    public PlanningContext withInventory(InventorySnapshot newInventory) {
        return new PlanningContext(newInventory, facilities, baseBoundary, acquisitionOracle, guidance, progression, attributes);
    }

    public PlanningContext withFacilities(FacilitySnapshot newFacilities) {
        return new PlanningContext(inventory, newFacilities, baseBoundary, acquisitionOracle, guidance, progression, attributes);
    }

    public PlanningContext withOracleAndGuidance(AcquisitionOracle newOracle, HumanGuidanceView newGuidance) {
        return new PlanningContext(inventory, facilities, baseBoundary, newOracle, newGuidance, progression, attributes);
    }

    public PlanningContext withBaseBoundary(BaseBoundarySnapshot newBoundary) {
        return new PlanningContext(inventory, facilities, newBoundary, acquisitionOracle, guidance, progression, attributes);
    }

    public PlanningContext withAttributes(ContextAttributeView newAttributes) {
        return new PlanningContext(inventory, facilities, baseBoundary, acquisitionOracle, guidance, progression, newAttributes);
    }
}

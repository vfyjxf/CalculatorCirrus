package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.Objects;

public record DeclareOwnedMachineFeedback(MachineEntry machine, int ownedCount, boolean persistToWorld)
        implements
            HumanFeedback {

    public DeclareOwnedMachineFeedback {
        Objects.requireNonNull(machine, "machine");
        if (ownedCount < 0) {
            throw new IllegalArgumentException("ownedCount must be >= 0, got " + ownedCount);
        }
    }
}

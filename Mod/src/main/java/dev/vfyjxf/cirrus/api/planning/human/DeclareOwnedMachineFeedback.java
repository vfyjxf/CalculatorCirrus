package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

public record DeclareOwnedMachineFeedback(
        String machineId,
        int ownedCount,
        boolean persistToWorld
) implements HumanFeedback {

    public DeclareOwnedMachineFeedback {
        Objects.requireNonNull(machineId, "machineId");
        if (ownedCount < 0) {
            throw new IllegalArgumentException("ownedCount must be >= 0, got " + ownedCount);
        }
    }
}

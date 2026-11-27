package dev.vfyjxf.cirrus.api.planning.context;

import java.util.Objects;

/**
 * 3-tier machine readiness classification (M5):
 * owned (already possessed / covered by compatible owned machine),
 * trivialToBuild (<= 4 steps, <= 45s, <= 0.25 task scale, no unowned heavy deps),
 * heavyInvestment (significant setup / unknown ownership / uncertified build).
 */
public enum MachineReadiness {
    owned,
    trivialToBuild,
    heavyInvestment;

    public enum UncertaintyTag {
        certifiedOwned,
        knownBuild,
        unknownOwnership,
        noBuildCert
    }

    public record Classification(
        String machineId,
        MachineReadiness readiness,
        int buildSteps,
        double buildCostSeconds,
        UncertaintyTag uncertaintyTag
    ) {
        public Classification {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(readiness, "readiness");
            Objects.requireNonNull(uncertaintyTag, "uncertaintyTag");
        }
    }
}

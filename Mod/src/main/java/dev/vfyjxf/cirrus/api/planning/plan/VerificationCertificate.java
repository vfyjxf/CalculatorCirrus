package dev.vfyjxf.cirrus.api.planning.plan;

import java.util.List;
import java.util.Objects;

public record VerificationCertificate(
        boolean valid,
        boolean dagAcyclic,
        boolean massBalanced,
        boolean machinesAndCatalystsSatisfied,
        boolean integerBatchesValid,
        boolean surplusConsistent,
        List<String> violations
) {

    public VerificationCertificate {
        Objects.requireNonNull(violations, "violations");
        violations = List.copyOf(violations);
    }
}

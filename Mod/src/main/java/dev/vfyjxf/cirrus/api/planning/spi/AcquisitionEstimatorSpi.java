package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;

import java.util.Optional;

/**
 * Pluggable acquisition cost estimator SPI.
 * Phase 1 uses pragmatic deterministic rules; extensions may plug in stochastic/Monte Carlo estimators
 * returning (mean, p90, CV, guaranteedQuantity) without altering core solver code.
 */
public interface AcquisitionEstimatorSpi {
    String estimatorId();

    int priority();

    Optional<AcquisitionEstimate> estimate(ComponentStackKey resource, long quantity, PlanningContext context);
}

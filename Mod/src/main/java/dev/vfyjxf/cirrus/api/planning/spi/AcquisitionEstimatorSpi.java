package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Optional;

/**
 * SPI 4: Pluggable acquisition cost estimator (M4).
 * Phase 1 uses pragmatic deterministic rules; Phase 2+ plugs in [TODO-PLAN-MC-01] JER Monte Carlo
 * returning (mean, p90, CV, guaranteedQuantity) without altering core solver code.
 */
public interface AcquisitionEstimatorSpi {
    String estimatorId();

    int priority();

    Optional<AcquisitionEstimate> estimate(ResourceKey resource, long quantity, PlanningContext context);
}

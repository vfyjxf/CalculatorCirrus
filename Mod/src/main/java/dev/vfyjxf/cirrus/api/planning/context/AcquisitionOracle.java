package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Optional;

public interface AcquisitionOracle {

    Optional<AcquisitionOption> assess(ResourceKey key, long missingAmount, ProgressionState progression);
}

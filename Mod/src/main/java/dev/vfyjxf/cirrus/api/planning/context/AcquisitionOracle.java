package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Optional;

public interface AcquisitionOracle {

    Optional<AcquisitionOption> assess(ComponentStackKey key, long missingAmount, ProgressionState progression);
}

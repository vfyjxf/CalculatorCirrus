package dev.vfyjxf.cirrus.api.planning.context;

/**
 * Pragmatic 2+1 acquisition modes (Planning v3 M4):
 * baseSupply (in-base / automated / base contract, H_trip = 0) and
 * manualGather (requires field trip / manual gathering, H_trip > 0).
 * Forbidden resources return {@code Optional.empty()} from {@link AcquisitionOracle}.
 */
public enum AcquisitionKind {
    baseSupply,
    manualGather
}

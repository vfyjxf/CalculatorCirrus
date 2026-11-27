package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

/**
 * Layer-1 certified Base supply contract (M4):
 * represents a proven stable in-base replenishment source with positive calibrated unit price
 * (never zero unless explicitly declared free) and finite/unbounded deliverable capacity.
 */
public record BaseContract(
    ResourceKey resource,
    double calibratedUnitSeconds,
    double minUnitFloorSeconds,
    double scarcityPremiumPerUnit,
    double fixedDeliverySeconds,
    long capacityLimit,
    boolean explicitlyFree,
    String sourceId
) {

    public BaseContract {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(sourceId, "sourceId");
        requireNonNegFinite(calibratedUnitSeconds, "calibratedUnitSeconds");
        requireNonNegFinite(minUnitFloorSeconds, "minUnitFloorSeconds");
        requireNonNegFinite(scarcityPremiumPerUnit, "scarcityPremiumPerUnit");
        requireNonNegFinite(fixedDeliverySeconds, "fixedDeliverySeconds");
        if (capacityLimit <= 0L) {
            throw new IllegalArgumentException("capacityLimit must be > 0, got " + capacityLimit);
        }
    }

    private static void requireNonNegFinite(double v, String name) {
        if (!Double.isFinite(v) || v < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0, got " + v);
        }
    }

    /** M4 Base non-zero pricing formula V_base(v, D). */
    public double unitPriceForDemand(long demand) {
        if (explicitlyFree) {
            return 0.0;
        }
        long d = Math.max(1L, demand);
        double unit = Math.max(minUnitFloorSeconds, calibratedUnitSeconds) + scarcityPremiumPerUnit;
        return (fixedDeliverySeconds + d * unit) / d;
    }
}

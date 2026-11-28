package dev.vfyjxf.cirrus.api.planning.context;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;

import java.util.Objects;

/**
 * Certified base-supply contract representing a stable in-base replenishment source with
 * positive calibrated unit price (never zero unless explicitly declared free) and deliverable capacity.
 */
public record BaseContract(
    ComponentStackKey resource,
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

    /** Base unit human active time (excluding fixed delivery and scarcity premium). */
    public double baseUnitSeconds() {
        return explicitlyFree ? 0.0 : Math.max(minUnitFloorSeconds, calibratedUnitSeconds);
    }

    /** Total effective unit price for demand D including amortized fixed delivery and scarcity premium. */
    public double unitPriceForDemand(long demand) {
        if (explicitlyFree) {
            return 0.0;
        }
        long d = Math.max(1L, demand);
        double unit = baseUnitSeconds() + scarcityPremiumPerUnit;
        return (fixedDeliverySeconds + d * unit) / d;
    }
}

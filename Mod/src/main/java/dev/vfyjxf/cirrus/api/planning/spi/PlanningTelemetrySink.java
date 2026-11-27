package dev.vfyjxf.cirrus.api.planning.spi;

import dev.vfyjxf.cirrus.api.planning.telemetry.PlanningTelemetryEvent;

/** SPI 7: Extensible subscriber for planning, decision, facility, and execution telemetry events. */
public interface PlanningTelemetrySink {
    String sinkId();

    void onEvent(PlanningTelemetryEvent event);
}

package dev.vfyjxf.cirrus.planning.telemetry;

import dev.vfyjxf.cirrus.api.planning.spi.PlanningTelemetrySink;
import dev.vfyjxf.cirrus.api.planning.telemetry.PlanningTelemetryEvent;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous non-blocking telemetry bus dispatching all 5 planning data domains + custom plugin events
 * to registered {@link PlanningTelemetrySink} subscribers with zero main-thread stutter.
 */
public final class PlanningTelemetryBus implements AutoCloseable {

    private static final PlanningTelemetryBus shared = new PlanningTelemetryBus(false);

    private final CopyOnWriteArrayList<PlanningTelemetrySink> sinks = new CopyOnWriteArrayList<>();
    private final boolean synchronousMode;
    private final ExecutorService worker;

    public PlanningTelemetryBus(boolean synchronousMode) {
        this.synchronousMode = synchronousMode;
        this.worker = synchronousMode
                ? null
                : Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "cirrus-planning-telemetry");
                    t.setDaemon(true);
                    return t;
                });
    }

    public static PlanningTelemetryBus shared() {
        return shared;
    }

    public void addSink(PlanningTelemetrySink sink) {
        sinks.addIfAbsent(Objects.requireNonNull(sink, "sink"));
    }

    public void removeSink(PlanningTelemetrySink sink) {
        sinks.remove(sink);
    }

    public List<PlanningTelemetrySink> sinks() {
        return List.copyOf(sinks);
    }

    public void publish(PlanningTelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        if (sinks.isEmpty()) {
            return;
        }
        if (synchronousMode || worker == null) {
            dispatchAll(event);
        } else {
            worker.execute(() -> dispatchAll(event));
        }
    }

    private void dispatchAll(PlanningTelemetryEvent event) {
        for (PlanningTelemetrySink sink : sinks) {
            try {
                sink.onEvent(event);
            } catch (RuntimeException ignored) {}
        }
    }

    @Override
    public void close() {
        if (worker != null) {
            worker.shutdown();
        }
    }
}

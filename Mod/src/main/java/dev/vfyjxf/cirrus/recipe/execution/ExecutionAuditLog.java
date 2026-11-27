package dev.vfyjxf.cirrus.recipe.execution;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks live execution diagnostics, safety stops, telemetry rates/ETA, and timestamped audit log events.
 */
public final class ExecutionAuditLog {

    public static final int maxEvents = 64;
    private static final long rateWindowMillis = 10_000L;
    private static final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public enum Severity {
        info, warn, error
    }

    public enum DiagnosticKind {
        missingMaterial, wrongStation, inventoryFull, planUnavailable, breakpointHit, safetyStop, runtimeError, stageCompleted, queueChanged, info
    }

    public record Event(
        long id,
        long timestampMillis,
        Severity severity,
        DiagnosticKind kind,
        int stageIndex,
        Component title,
        @Nullable Component detail
    ) {
        public String formattedTime() {
            return timeFmt.format(Instant.ofEpochMilli(timestampMillis));
        }
    }

    private final AtomicLong idGen = new AtomicLong(1L);
    private final Deque<Event> events = new ArrayDeque<>();
    private final Deque<Long> recentStepCompletions = new ArrayDeque<>();
    private @Nullable Event activeDiagnostic;
    private long sessionStartedMillis;
    private int totalCompletedStages;

    public void startSession() {
        sessionStartedMillis = System.currentTimeMillis();
        recentStepCompletions.clear();
        activeDiagnostic = null;
    }

    public void recordStepAdvance(int deltaSteps) {
        if (deltaSteps <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (sessionStartedMillis <= 0L) {
            sessionStartedMillis = now;
        }
        for (int i = 0; i < Math.min(deltaSteps, 32); i++) {
            recentStepCompletions.addLast(now);
        }
        pruneOldSteps(now);
    }

    public void recordStageCompleted(int stageIndex, Component title) {
        totalCompletedStages++;
        record(Severity.info, DiagnosticKind.stageCompleted, stageIndex, title, null);
    }

    public void setActiveDiagnostic(
        Severity severity,
        DiagnosticKind kind,
        int stageIndex,
        Component title,
        @Nullable Component detail
    ) {
        Event cur = activeDiagnostic;
        if (cur != null
                && cur.kind() == kind
                && cur.stageIndex() == stageIndex
                && cur.title().getString().equals(title.getString())) {
            return;
        }
        Event next = new Event(
            idGen.getAndIncrement(),
            System.currentTimeMillis(),
            severity,
            kind,
            stageIndex,
            title,
            detail
        );
        activeDiagnostic = next;
        appendEvent(next);
    }

    public void clearActiveDiagnostic() {
        activeDiagnostic = null;
    }

    public @Nullable Event activeDiagnostic() {
        return activeDiagnostic;
    }

    public void record(
        Severity severity,
        DiagnosticKind kind,
        int stageIndex,
        Component title,
        @Nullable Component detail
    ) {
        Event last = events.peekFirst();
        if (last != null
                && last.kind() == kind
                && last.stageIndex() == stageIndex
                && last.title().getString().equals(title.getString())
                && System.currentTimeMillis() - last.timestampMillis() < 2000L) {
            return;
        }
        appendEvent(
            new Event(idGen.getAndIncrement(), System.currentTimeMillis(), severity, kind, stageIndex, title, detail)
        );
    }

    private void appendEvent(Event event) {
        events.addFirst(event);
        while (events.size() > maxEvents) {
            events.removeLast();
        }
    }

    public List<Event> events(@Nullable Severity filter) {
        if (filter == null) {
            return List.copyOf(events);
        }
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (e.severity() == filter) {
                out.add(e);
            }
        }
        return out;
    }

    public int errorAndWarnCount() {
        int count = 0;
        for (Event e : events) {
            if (e.severity() == Severity.error || e.severity() == Severity.warn) {
                count++;
            }
        }
        return count;
    }

    public int totalCompletedStages() {
        return totalCompletedStages;
    }

    public long elapsedSeconds() {
        if (sessionStartedMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - sessionStartedMillis) / 1000L);
    }

    public double observedStepsPerSecond() {
        long now = System.currentTimeMillis();
        pruneOldSteps(now);
        if (recentStepCompletions.isEmpty()) {
            return 0.0d;
        }
        long oldest = recentStepCompletions.peekFirst();
        double spanSec = Math.max(1.0d, (now - oldest) / 1000.0d);
        return recentStepCompletions.size() / spanSec;
    }

    public long estimateRemainingSeconds(int remainingSteps, double configuredRate) {
        if (remainingSteps <= 0) {
            return 0L;
        }
        double obs = observedStepsPerSecond();
        double effectiveRate = obs > 0.25d ? obs : Math.max(0.25d, configuredRate);
        return Math.max(1L, Math.round(remainingSteps / effectiveRate));
    }

    public void clearEvents() {
        events.clear();
        activeDiagnostic = null;
    }

    public void resetAll() {
        events.clear();
        recentStepCompletions.clear();
        activeDiagnostic = null;
        sessionStartedMillis = 0L;
        totalCompletedStages = 0;
    }

    private void pruneOldSteps(long now) {
        while (!recentStepCompletions.isEmpty() && now - recentStepCompletions.peekFirst() > rateWindowMillis) {
            recentStepCompletions.removeFirst();
        }
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0L) {
            return "0s";
        }
        if (seconds < 60L) {
            return seconds + "s";
        }
        long mins = seconds / 60L;
        long secs = seconds % 60L;
        return mins + "m" + (secs > 0 ? " " + secs + "s" : "");
    }
}

package dev.vfyjxf.cirrus.ui.execution;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

final class TargetTimesDebouncer<T> {
    record Commit<T>(T target, int value) {}

    private final int delayTicks;
    private @Nullable T pendingTarget;
    private int pendingValue;
    private int ticksRemaining;

    TargetTimesDebouncer(int delayTicks) {
        this.delayTicks = Math.max(1, delayTicks);
    }

    void request(T target, int value, int currentValue) {
        int normalized = Math.max(1, value);
        if (normalized == currentValue) {
            clear();
            return;
        }
        pendingTarget = Objects.requireNonNull(target, "target");
        pendingValue = normalized;
        ticksRemaining = delayTicks;
    }

    int visibleValue(@Nullable T currentTarget, int currentValue) {
        if (!pendingFor(currentTarget)) {
            return currentValue;
        }
        return pendingValue;
    }

    @Nullable
    Commit<T> tick(@Nullable T currentTarget, int currentValue) {
        if (!pendingFor(currentTarget)) {
            clear();
            return null;
        }
        if (pendingValue == currentValue) {
            clear();
            return null;
        }
        ticksRemaining--;
        if (ticksRemaining > 0) {
            return null;
        }
        return flush(currentTarget, currentValue);
    }

    @Nullable
    Commit<T> flush(@Nullable T currentTarget, int currentValue) {
        if (!pendingFor(currentTarget)) {
            clear();
            return null;
        }
        if (pendingValue == currentValue) {
            clear();
            return null;
        }
        Commit<T> commit = new Commit<>(Objects.requireNonNull(pendingTarget), pendingValue);
        clear();
        return commit;
    }

    void clear() {
        pendingTarget = null;
        pendingValue = 0;
        ticksRemaining = 0;
    }

    boolean pending() {
        return pendingTarget != null;
    }

    int ticksRemaining() {
        return ticksRemaining;
    }

    private boolean pendingFor(@Nullable T target) {
        return pendingTarget != null && Objects.equals(pendingTarget, target);
    }
}

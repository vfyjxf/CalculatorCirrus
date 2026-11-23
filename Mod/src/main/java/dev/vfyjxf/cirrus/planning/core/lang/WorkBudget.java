package dev.vfyjxf.cirrus.planning.core.lang;

public final class WorkBudget {

    private final long softDeadlineNanos;
    private final long hardDeadlineNanos;
    private final long maxWorkUnits;
    private long workUnitsUsed;
    private boolean softTimeHit;
    private boolean hardTimeHit;
    private boolean workCapHit;

    public WorkBudget(long startNanos, long softNanos, long hardNanos, long maxWorkUnits) {
        this.softDeadlineNanos = startNanos + Math.max(0L, softNanos);
        this.hardDeadlineNanos = startNanos + Math.max(0L, hardNanos);
        this.maxWorkUnits = Math.max(0L, maxWorkUnits);
    }

    public boolean consumeWork() {
        workUnitsUsed++;
        if (workUnitsUsed > maxWorkUnits) {
            workCapHit = true;
            return false;
        }
        return true;
    }

    public boolean isSoftTimeExceeded() {
        if (softTimeHit) {
            return true;
        }
        if (System.nanoTime() >= softDeadlineNanos) {
            softTimeHit = true;
            return true;
        }
        return false;
    }

    public boolean isHardTimeExceeded() {
        if (hardTimeHit) {
            return true;
        }
        if (System.nanoTime() >= hardDeadlineNanos) {
            hardTimeHit = true;
            softTimeHit = true;
            return true;
        }
        return false;
    }

    public long workUnitsUsed() {
        return workUnitsUsed;
    }

    public boolean softTimeHit() {
        return softTimeHit;
    }

    public boolean hardTimeHit() {
        return hardTimeHit;
    }

    public boolean workCapHit() {
        return workCapHit;
    }

    public boolean anyTimeHit() {
        return softTimeHit || hardTimeHit;
    }

    public long softDeadlineNanos() {
        return softDeadlineNanos;
    }

    public long hardDeadlineNanos() {
        return hardDeadlineNanos;
    }
}

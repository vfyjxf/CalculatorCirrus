package dev.vfyjxf.cirrus.planning.core.solve;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * M7.3 Deterministic Budgeted 5-Operator ALNS Scheduler (Pure JDK 21, ZERO RNG):
 * 1. FamilyUnifyMove
 * 2. MachineSetCoverMove
 * 3. ProducerSwapAndBypassMove
 * 4. InventorySplitCleanupMove
 * 5. LineSetupEvaluationMove
 *
 * <p>First pass runs all 5 operators in fixed order; subsequent passes use integer EMA reward/work
 * ratio (alpha = 1/4) with minimum service guarantee per window of 10 calls. Accepts strictly lower
 * total order (quantizedJ, stepFriction, canonicalBytes) — guaranteed monotone descent & termination.
 */
public final class DeterministicAlnsScheduler {

    public enum OperatorKind {
        familyUnifyMove,
        machineSetCoverMove,
        producerSwapAndBypassMove,
        inventorySplitCleanupMove,
        lineSetupEvaluationMove
    }

    public record CandidateState<T>(
        T payload,
        long quantizedObjectiveJ,
        double stepFrictionF,
        String canonicalBytes
    ) implements Comparable<CandidateState<T>> {
        @Override
        public int compareTo(CandidateState<T> other) {
            int c = Long.compare(this.quantizedObjectiveJ, other.quantizedObjectiveJ);
            if (c != 0) return c;
            c = Double.compare(this.stepFrictionF, other.stepFrictionF);
            if (c != 0) return c;
            return this.canonicalBytes.compareTo(other.canonicalBytes);
        }
    }

    public record MoveAttempt<T>(@Nullable CandidateState<T> proposedOrNull, long workUnitsConsumed) {}

    public record AlnsOutcome<T>(
        CandidateState<T> incumbent,
        int iterationsRun,
        int acceptedMoves,
        long totalWorkConsumed,
        int[] callsPerOperator
    ) {}

    public static final int defaultServiceWindow = 10;

    private DeterministicAlnsScheduler() {}

    public static <T> AlnsOutcome<T> refine(
        CandidateState<T> initialIncumbent,
        List<Function<CandidateState<T>, MoveAttempt<T>>> fiveOperators,
        long maxWorkBudget
    ) {
        Objects.requireNonNull(initialIncumbent, "initialIncumbent");
        Objects.requireNonNull(fiveOperators, "fiveOperators");
        if (fiveOperators.size() != 5 || maxWorkBudget <= 0L) {
            return new AlnsOutcome<>(initialIncumbent, 0, 0, 0L, new int[5]);
        }

        CandidateState<T> incumbent = initialIncumbent;
        long workUsed = 0L;
        int iterations = 0;
        int accepted = 0;
        int[] calls = new int[5];
        int[] lastWindowCalls = new int[5];
        // Fixed-point integer EMA scaled by 1024; initial score = 1024.
        long[] emaRewardPerWork = {1024L, 1024L, 1024L, 1024L, 1024L};

        // Pass 0: run all 5 operators once in canonical order.
        boolean improvedInRound = false;
        for (int op = 0; op < 5 && workUsed < maxWorkBudget; op++) {
            MoveAttempt<T> attempt = fiveOperators.get(op).apply(incumbent);
            long w = Math.max(1L, attempt.workUnitsConsumed());
            workUsed += w;
            calls[op]++;
            lastWindowCalls[op]++;
            iterations++;
            long gain = 0L;
            if (attempt.proposedOrNull() != null && attempt.proposedOrNull().compareTo(incumbent) < 0) {
                gain = Math.max(1L, incumbent.quantizedObjectiveJ() - attempt.proposedOrNull().quantizedObjectiveJ());
                incumbent = attempt.proposedOrNull();
                accepted++;
                improvedInRound = true;
            }
            updateEma(emaRewardPerWork, op, gain, w);
        }

        // Subsequent passes: deterministic weighted round-robin with minimum service guarantee per window.
        int consecutiveNoImprove = improvedInRound ? 0 : 5;
        while (workUsed < maxWorkBudget && consecutiveNoImprove < 5) {
            if (iterations % defaultServiceWindow == 0) {
                for (int i = 0; i < 5; i++) lastWindowCalls[i] = 0;
            }
            int op = pickNextOperator(emaRewardPerWork, lastWindowCalls);
            MoveAttempt<T> attempt = fiveOperators.get(op).apply(incumbent);
            long w = Math.max(1L, attempt.workUnitsConsumed());
            workUsed += w;
            calls[op]++;
            lastWindowCalls[op]++;
            iterations++;

            long gain = 0L;
            if (attempt.proposedOrNull() != null && attempt.proposedOrNull().compareTo(incumbent) < 0) {
                gain = Math.max(1L, incumbent.quantizedObjectiveJ() - attempt.proposedOrNull().quantizedObjectiveJ());
                incumbent = attempt.proposedOrNull();
                accepted++;
                consecutiveNoImprove = 0;
            } else {
                consecutiveNoImprove++;
            }
            updateEma(emaRewardPerWork, op, gain, w);
        }

        return new AlnsOutcome<>(incumbent, iterations, accepted, workUsed, calls);
    }

    private static int pickNextOperator(long[] ema, int[] windowCalls) {
        // Ensure every operator receives at least 1 call per service window (deterministically by table order).
        for (int i = 0; i < 5; i++) {
            if (windowCalls[i] == 0) {
                return i;
            }
        }
        int best = 0;
        for (int i = 1; i < 5; i++) {
            if (ema[i] > ema[best]) {
                best = i;
            }
        }
        return best;
    }

    /** Integer EMA with alpha = 1/4: ema_next = (3 * ema + sample) / 4. */
    private static void updateEma(long[] ema, int op, long gain, long work) {
        long sample = Math.min(1_000_000L, (gain * 1024L) / Math.max(1L, work));
        ema[op] = (3L * ema[op] + sample) >>> 2;
    }
}

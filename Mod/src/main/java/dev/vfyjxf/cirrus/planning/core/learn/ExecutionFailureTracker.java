package dev.vfyjxf.cirrus.planning.core.learn;

import dev.vfyjxf.cirrus.api.planning.telemetry.ExecutionFailureCategory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M9.2 Attributed Execution Failure Tracker & Runtime Fidelity Decay (Pure JDK 21).
 *
 * <p>Per (recipeSemanticKey, conditionSignature, adapterVersion):
 * - Exponential forgetting with half-life t_f = 40 logical rounds.
 * - Attributed adapter failure (wrongBinding / hiddenRequirement) adds 1.0 (capped at f_max = 8.0).
 * - USER_CANCEL, MACHINE_BUSY, OUT_OF_STOCK add 0.0 (never unfairly penalize recipes!).
 * - Observed execution success scales failure burden by (1 - xi) = 0.5.
 * - Runtime fidelity: phi_run = phi_struct * exp(-k_f * f') with k_f = 0.35.
 * - Explicit user verification on the same version/condition resets f = 0.
 */
public final class ExecutionFailureTracker {

    public static final double defaultHalfLifeTf = 40.0;
    public static final double defaultDecayKf = 0.35;
    public static final double defaultFMax = 8.0;
    public static final double defaultSuccessRecoveryXi = 0.50;

    public record Key(String recipeSemanticKey, String conditionSignature, String adapterVersion) {
        public Key {
            Objects.requireNonNull(recipeSemanticKey, "recipeSemanticKey");
            Objects.requireNonNull(conditionSignature, "conditionSignature");
            Objects.requireNonNull(adapterVersion, "adapterVersion");
        }
    }

    private static final class BurdenState {
        double failureBurden;
        long lastLogicTime;
        boolean semanticCertRevoked;
        boolean userVerified;

        BurdenState(double f, long t) {
            this.failureBurden = f;
            this.lastLogicTime = t;
        }
    }

    private final Map<Key, BurdenState> states = new HashMap<>();
    private final Set<String> seenExecutionIds = new HashSet<>();

    public ExecutionFailureTracker() {}

    public boolean recordExecutionOutcome(
        String executionEventId,
        Key key,
        boolean succeeded,
        ExecutionFailureCategory failureCategory,
        long logicTime
    ) {
        Objects.requireNonNull(executionEventId, "executionEventId");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(failureCategory, "failureCategory");
        if (!seenExecutionIds.add(executionEventId)) {
            return false; // Idempotent replay protection.
        }
        BurdenState st = states.computeIfAbsent(key, k -> new BurdenState(0.0, logicTime));
        double fMinus = decayedBurden(st, logicTime);
        if (succeeded) {
            st.failureBurden = (1.0 - defaultSuccessRecoveryXi) * fMinus;
        } else {
            double weight = failureCategory.failureWeight();
            st.failureBurden = Math.min(defaultFMax, fMinus + weight);
            if (failureCategory.revokesSemanticCert()) {
                st.semanticCertRevoked = true;
                st.userVerified = false;
            }
        }
        st.lastLogicTime = Math.max(st.lastLogicTime, logicTime);
        return true;
    }

    public void recordExplicitUserVerification(Key key, long logicTime) {
        Objects.requireNonNull(key, "key");
        BurdenState st = states.computeIfAbsent(key, k -> new BurdenState(0.0, logicTime));
        st.failureBurden = 0.0;
        st.semanticCertRevoked = false;
        st.userVerified = true;
        st.lastLogicTime = Math.max(st.lastLogicTime, logicTime);
    }

    public double currentFailureBurden(Key key, long atLogicTime) {
        BurdenState st = states.get(key);
        return st == null ? 0.0 : decayedBurden(st, atLogicTime);
    }

    public double runtimeFidelity(double phiStruct, Key key, long atLogicTime) {
        double clampedStruct = Math.max(0.0, Math.min(1.0, phiStruct));
        double f = currentFailureBurden(key, atLogicTime);
        return Math.max(0.0, Math.min(1.0, clampedStruct * StrictMath.exp(-defaultDecayKf * f)));
    }

    public boolean isSemanticCertRevoked(Key key) {
        BurdenState st = states.get(key);
        return st != null && st.semanticCertRevoked;
    }

    private static double decayedBurden(BurdenState st, long atLogicTime) {
        long dt = Math.max(0L, atLogicTime - st.lastLogicTime);
        if (dt == 0L) {
            return st.failureBurden;
        }
        return st.failureBurden * StrictMath.pow(2.0, -dt / defaultHalfLifeTf);
    }
}

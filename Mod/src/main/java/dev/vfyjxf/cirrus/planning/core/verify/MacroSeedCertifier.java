package dev.vfyjxf.cirrus.planning.core.verify;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * M7.2 Exact 1-4 Step Cycle Macro Seed & Net-Delta Certifier (Pure JDK 21).
 *
 * <p>For micro-batch sequence (a_1, b_1), ..., (a_m, b_m) with d_i = b_i - a_i:
 * - Single-iteration exact initial seed requirement:
 *     s_v = max(0, max_i { a_{iv} + lock_{iv} - sum_{j < i} d_{jv} })
 * - Total net delta per iteration: Delta_v = sum_i d_{iv}
 * - k consecutive iterations without intermediate replenishment require exact initial amount:
 *     S_v(k) = s_v + (k - 1) * max(0, -Delta_v)
 */
public final class MacroSeedCertifier {

    public record MicroStep(
        Map<ResourceKey, Long> consumed,
        Map<ResourceKey, Long> produced,
        Map<ResourceKey, Long> lockedCatalysts
    ) {
        public MicroStep(Map<ResourceKey, Long> consumed, Map<ResourceKey, Long> produced) {
            this(consumed, produced, Map.of());
        }

        public MicroStep {
            consumed = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(consumed, "consumed")));
            produced = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(produced, "produced")));
            lockedCatalysts = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(lockedCatalysts, "lockedCatalysts")));
        }
    }

    public record MacroCertificate(
        boolean validPositiveMacro,
        Map<ResourceKey, Long> singleIterationSeeds,
        Map<ResourceKey, Long> netDeltaPerIteration,
        Map<ResourceKey, Long> externalInputsPerIteration,
        String reason
    ) {
        /** Exact initial material required to run {@code iterations} consecutive repetitions without mid-replenishment. */
        public Map<ResourceKey, Long> initialRequiredForIterations(long iterations) {
            if (iterations <= 0L) {
                return Map.of();
            }
            TreeMap<ResourceKey, Long> req = new TreeMap<>();
            TreeSet<ResourceKey> keys = new TreeSet<>(singleIterationSeeds.keySet());
            keys.addAll(netDeltaPerIteration.keySet());
            for (ResourceKey v : keys) {
                long s = singleIterationSeeds.getOrDefault(v, 0L);
                long delta = netDeltaPerIteration.getOrDefault(v, 0L);
                long negDelta = delta < 0L ? Math.negateExact(delta) : 0L;
                long total = Math.addExact(s, Math.multiplyExact(iterations - 1L, negDelta));
                if (total > 0L) {
                    req.put(v, total);
                }
            }
            return Collections.unmodifiableMap(req);
        }
    }

    private MacroSeedCertifier() {}

    public static MacroCertificate certify(List<MicroStep> steps, Set<ResourceKey> carrierSet) {
        Objects.requireNonNull(steps, "steps");
        Objects.requireNonNull(carrierSet, "carrierSet");
        if (steps.isEmpty() || steps.size() > 4) {
            return new MacroCertificate(false, Map.of(), Map.of(), Map.of(), "MACRO_LENGTH_OUT_OF_RANGE");
        }

        TreeSet<ResourceKey> allKeys = new TreeSet<>(carrierSet);
        for (MicroStep st : steps) {
            allKeys.addAll(st.consumed().keySet());
            allKeys.addAll(st.produced().keySet());
            allKeys.addAll(st.lockedCatalysts().keySet());
        }

        TreeMap<ResourceKey, Long> seeds = new TreeMap<>();
        TreeMap<ResourceKey, Long> netDelta = new TreeMap<>();
        TreeMap<ResourceKey, Long> extInputs = new TreeMap<>();

        for (ResourceKey v : allKeys) {
            long prefixNet = 0L;
            long maxNeed = 0L;
            for (MicroStep st : steps) {
                long a = st.consumed().getOrDefault(v, 0L);
                long lock = st.lockedCatalysts().getOrDefault(v, 0L);
                long b = st.produced().getOrDefault(v, 0L);
                long neededBeforeStep = Math.subtractExact(Math.addExact(a, lock), prefixNet);
                if (neededBeforeStep > maxNeed) {
                    maxNeed = neededBeforeStep;
                }
                prefixNet = Math.addExact(prefixNet, Math.subtractExact(b, a));
            }
            if (maxNeed > 0L) {
                seeds.put(v, maxNeed);
            }
            netDelta.put(v, prefixNet);
            if (prefixNet < 0L) {
                extInputs.put(v, Math.negateExact(prefixNet));
            }
        }

        // Carriers must be non-decreasing (Delta|_C >= 0) and at least one output strictly net-positive.
        for (ResourceKey carrier : carrierSet) {
            if (netDelta.getOrDefault(carrier, 0L) < 0L) {
                return new MacroCertificate(false, seeds, netDelta, extInputs, "CARRIER_NET_LOSS");
            }
        }
        boolean anyPositiveOutput = false;
        for (long d : netDelta.values()) {
            if (d > 0L) {
                anyPositiveOutput = true;
                break;
            }
        }
        if (!anyPositiveOutput) {
            return new MacroCertificate(false, seeds, netDelta, extInputs, "NO_STRICTLY_POSITIVE_OUTPUT");
        }
        return new MacroCertificate(
            true,
            Collections.unmodifiableMap(seeds),
            Collections.unmodifiableMap(netDelta),
            Collections.unmodifiableMap(extInputs),
            "CERTIFIED_MACRO"
        );
    }
}

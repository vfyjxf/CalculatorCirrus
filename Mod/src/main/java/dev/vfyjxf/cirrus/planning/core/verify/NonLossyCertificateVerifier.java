package dev.vfyjxf.cirrus.planning.core.verify;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * M7.2 Solver-Free Global Non-Lossy Integer Certificate (Pure JDK 21):
 *
 * <p>Given expanded production/conversion net-delta columns d_j = b_j - a_j (integer vectors),
 * searches for a non-negative BigInteger vector w >= 0 such that w^T d_j >= 1 for every column j.
 *
 * <p>Theorem (Farkas sufficient condition): If w >= 0 and w^T d_j >= 1 for all j, then NO non-empty
 * non-negative integer combination n != 0 can satisfy D * n <= 0 (which would imply
 * 0 >= w^T D n >= sum(n_j) >= 1, contradiction). Thus ALL lossy or zero-net cycles of arbitrary length
 * (including cross-macro combinations) are strictly ruled out!
 */
public final class NonLossyCertificateVerifier {

    public static final int defaultMaxRounds = 16;
    public static final int defaultMaxBitLength = 256;

    public record NetColumn(String modeId, Map<String, Long> deltaByStateKey) {
        public NetColumn {
            Objects.requireNonNull(modeId, "modeId");
            deltaByStateKey = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(deltaByStateKey, "deltaByStateKey")));
        }
    }

    public record CertificateResult(
        boolean certified,
        Map<String, BigInteger> weightsW,
        String statusCode,
        String failingModeId
    ) {}

    private NonLossyCertificateVerifier() {}

    public static CertificateResult certify(List<NetColumn> rawColumns) {
        return certify(rawColumns, defaultMaxRounds, defaultMaxBitLength);
    }

    public static CertificateResult certify(List<NetColumn> rawColumns, int maxRounds, int maxBitLength) {
        Objects.requireNonNull(rawColumns, "rawColumns");
        if (rawColumns.isEmpty()) {
            return new CertificateResult(true, Map.of(), "CERTIFIED_TRIVIAL", "");
        }

        // Deduplicate identical net delta columns deterministically.
        Map<String, NetColumn> unique = new LinkedHashMap<>();
        List<NetColumn> sorted = new ArrayList<>(rawColumns);
        sorted.sort((a, b) -> a.modeId().compareTo(b.modeId()));
        for (NetColumn col : sorted) {
            String sig = columnSignature(col.deltaByStateKey());
            unique.putIfAbsent(sig, col);
        }
        List<NetColumn> columns = List.copyOf(unique.values());

        // Immediate check: any column with no strictly positive component cannot satisfy w^T d_j >= 1 for w >= 0.
        TreeMap<String, BigInteger> w = new TreeMap<>();
        for (NetColumn col : columns) {
            boolean hasPositive = false;
            for (var e : col.deltaByStateKey().entrySet()) {
                w.putIfAbsent(e.getKey(), BigInteger.ZERO);
                if (e.getValue() > 0L) {
                    hasPositive = true;
                }
            }
            if (!hasPositive) {
                return new CertificateResult(false, Map.of(), "NO_POSITIVE_OUTPUT_COMPONENT", col.modeId());
            }
        }

        // Iterative greedy certificate construction (strictly bounded by rounds & bit length).
        int rounds = Math.max(1, maxRounds);
        int bitCap = Math.max(32, maxBitLength);
        String lastFail = "";

        for (int round = 0; round < rounds; round++) {
            boolean allSatisfied = true;
            for (NetColumn col : columns) {
                BigInteger dot = innerProduct(w, col.deltaByStateKey());
                if (dot.compareTo(BigInteger.ONE) >= 0) {
                    continue;
                }
                allSatisfied = false;
                lastFail = col.modeId();
                // Pick canonical positive output component (largest net gain, then smallest stable ID).
                String bestKey = null;
                long bestPos = 0L;
                for (var e : col.deltaByStateKey().entrySet()) {
                    long dv = e.getValue();
                    if (dv > 0L && (bestKey == null || dv > bestPos || (dv == bestPos && e.getKey().compareTo(bestKey) < 0))) {
                        bestKey = e.getKey();
                        bestPos = dv;
                    }
                }
                if (bestKey == null) {
                    return new CertificateResult(false, Map.of(), "CYCLE_POLICY_LIMIT", col.modeId());
                }
                BigInteger deficit = BigInteger.ONE.subtract(dot); // >= 1
                BigInteger denom = BigInteger.valueOf(bestPos);
                BigInteger[] qr = deficit.divideAndRemainder(denom);
                BigInteger bump = qr[1].signum() == 0 ? qr[0] : qr[0].add(BigInteger.ONE);
                BigInteger nextW = w.getOrDefault(bestKey, BigInteger.ZERO).add(bump);
                if (nextW.bitLength() > bitCap) {
                    return new CertificateResult(false, Map.of(), "CYCLE_POLICY_LIMIT", col.modeId());
                }
                w.put(bestKey, nextW);
            }
            if (allSatisfied) {
                break;
            }
        }

        // Independent scratch verification of EVERY column (M7.2 requirement).
        if (verifyWeights(columns, w)) {
            return new CertificateResult(true, Collections.unmodifiableMap(w), "CERTIFIED_NON_LOSSY", "");
        }
        return new CertificateResult(false, Map.of(), "CYCLE_POLICY_LIMIT", lastFail);
    }

    public static boolean verifyWeights(List<NetColumn> columns, Map<String, BigInteger> w) {
        if (w == null) return false;
        for (BigInteger val : w.values()) {
            if (val == null || val.signum() < 0) {
                return false;
            }
        }
        for (NetColumn col : columns) {
            if (innerProduct(w, col.deltaByStateKey()).compareTo(BigInteger.ONE) < 0) {
                return false;
            }
        }
        return true;
    }

    private static BigInteger innerProduct(Map<String, BigInteger> w, Map<String, Long> delta) {
        BigInteger sum = BigInteger.ZERO;
        for (var e : delta.entrySet()) {
            long dv = e.getValue();
            if (dv == 0L) continue;
            BigInteger wv = w.getOrDefault(e.getKey(), BigInteger.ZERO);
            if (wv.signum() != 0) {
                sum = sum.add(wv.multiply(BigInteger.valueOf(dv)));
            }
        }
        return sum;
    }

    private static String columnSignature(Map<String, Long> delta) {
        StringBuilder sb = new StringBuilder();
        for (var e : delta.entrySet()) {
            if (e.getValue() != 0L) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
            }
        }
        return sb.toString();
    }
}

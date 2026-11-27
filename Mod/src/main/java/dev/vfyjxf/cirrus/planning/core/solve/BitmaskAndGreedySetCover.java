package dev.vfyjxf.cirrus.planning.core.solve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class BitmaskAndGreedySetCover {

    public record CandidateMachine(String machineId, BitSet coverBits, double setupScalarCost) {
        public CandidateMachine {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(coverBits, "coverBits");
            coverBits = (BitSet) coverBits.clone();
            if (!Double.isFinite(setupScalarCost) || setupScalarCost < 0.0) {
                throw new IllegalArgumentException("setupScalarCost must be finite >= 0");
            }
        }
    }

    private static final int exactDpMaxSteps = 14;
    private static final int exactDpMaxMachines = 64;

    public BitmaskAndGreedySetCover() {}

    public Set<String> solve(int kSteps, List<CandidateMachine> candidates) {
        if (kSteps <= 0 || candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        List<CandidateMachine> sorted = new ArrayList<>(candidates.size());
        for (CandidateMachine cm : candidates) {
            if (cm != null && cm.coverBits().nextSetBit(0) >= 0 && cm.coverBits().nextSetBit(0) < kSteps) {
                BitSet clipped = cm.coverBits().get(0, kSteps);
                if (!clipped.isEmpty()) {
                    sorted.add(new CandidateMachine(cm.machineId(), clipped, cm.setupScalarCost()));
                }
            }
        }
        if (sorted.isEmpty()) {
            return Set.of();
        }
        sorted.sort(Comparator.comparing(CandidateMachine::machineId));

        BitSet union = new BitSet(kSteps);
        for (CandidateMachine cm : sorted) {
            union.or(cm.coverBits());
        }
        if (union.cardinality() < kSteps) {
            return Set.of();
        }

        if (kSteps <= exactDpMaxSteps && sorted.size() <= exactDpMaxMachines) {
            return solveExactDp(kSteps, sorted);
        }
        return solveGreedyBitSetLocalSearch(kSteps, sorted);
    }

    private static Set<String> solveExactDp(int kSteps, List<CandidateMachine> cands) {
        int fullMask = (1 << kSteps) - 1;
        int m = cands.size();
        int[] masks = new int[m];
        double[] costs = new double[m];
        for (int i = 0; i < m; i++) {
            int mask = 0;
            BitSet bs = cands.get(i).coverBits();
            for (int bit = bs.nextSetBit(0); bit >= 0 && bit < kSteps; bit = bs.nextSetBit(bit + 1)) {
                mask |= (1 << bit);
            }
            masks[i] = mask;
            costs[i] = cands.get(i).setupScalarCost();
        }

        double[] dp = new double[fullMask + 1];
        int[] prevMask = new int[fullMask + 1];
        int[] pickedIdx = new int[fullMask + 1];
        Arrays.fill(dp, Double.POSITIVE_INFINITY);
        Arrays.fill(prevMask, -1);
        Arrays.fill(pickedIdx, -1);
        dp[0] = 0.0;

        for (int i = 0; i < m; i++) {
            int cov = masks[i];
            double cost = costs[i];
            if (cov == 0) continue;
            for (int mask = fullMask; mask >= 0; mask--) {
                double base = dp[mask];
                if (!Double.isFinite(base)) continue;
                int next = mask | cov;
                double candCost = base + cost;
                if (candCost + 1e-12 < dp[next]) {
                    dp[next] = candCost;
                    prevMask[next] = mask;
                    pickedIdx[next] = i;
                }
            }
        }

        if (!Double.isFinite(dp[fullMask])) {
            return Set.of();
        }
        TreeSet<String> chosen = new TreeSet<>();
        int cur = fullMask;
        while (cur != 0) {
            int idx = pickedIdx[cur];
            if (idx < 0) break;
            chosen.add(cands.get(idx).machineId());
            cur = prevMask[cur];
        }
        return Collections.unmodifiableSet(chosen);
    }

    private static Set<String> solveGreedyBitSetLocalSearch(int kSteps, List<CandidateMachine> cands) {
        int m = cands.size();
        BitSet uncovered = new BitSet(kSteps);
        uncovered.set(0, kSteps);
        boolean[] selected = new boolean[m];

        // Weighted greedy phase.
        while (!uncovered.isEmpty()) {
            int bestIdx = -1;
            double bestRatio = Double.POSITIVE_INFINITY;
            int bestGain = 0;
            for (int i = 0; i < m; i++) {
                if (selected[i]) continue;
                BitSet inter = (BitSet) cands.get(i).coverBits().clone();
                inter.and(uncovered);
                int gain = inter.cardinality();
                if (gain == 0) continue;
                double ratio = cands.get(i).setupScalarCost() / gain;
                if (ratio + 1e-12 < bestRatio || (Math.abs(ratio - bestRatio) <= 1e-12 && gain > bestGain)) {
                    bestRatio = ratio;
                    bestGain = gain;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) {
                return Set.of();
            }
            selected[bestIdx] = true;
            uncovered.andNot(cands.get(bestIdx).coverBits());
        }

        // Redundancy pruning + 1-for-2 local search.
        boolean improved = true;
        int rounds = 0;
        while (improved && rounds++ < 64) {
            improved = false;
            // Try removing any single redundant machine.
            for (int i = 0; i < m; i++) {
                if (!selected[i]) continue;
                selected[i] = false;
                if (coversAll(kSteps, cands, selected)) {
                    improved = true;
                } else {
                    selected[i] = true;
                }
            }
            // Try 1-for-2 replacement.
            int[] selIdx = selectedIndices(selected);
            outer : for (int aPos = 0; aPos < selIdx.length; aPos++) {
                int a = selIdx[aPos];
                for (int bPos = aPos + 1; bPos < selIdx.length; bPos++) {
                    int b = selIdx[bPos];
                    double pairCost = cands.get(a).setupScalarCost() + cands.get(b).setupScalarCost();
                    selected[a] = false;
                    selected[b] = false;
                    BitSet missing = missingBits(kSteps, cands, selected);
                    int bestRepl = -1;
                    double bestReplCost = pairCost - 1e-9;
                    for (int r = 0; r < m; r++) {
                        if (selected[r] || r == a || r == b) continue;
                        if (cands.get(r).setupScalarCost() >= bestReplCost) continue;
                        BitSet cov = cands.get(r).coverBits();
                        if (containsAll(cov, missing)) {
                            bestRepl = r;
                            bestReplCost = cands.get(r).setupScalarCost();
                        }
                    }
                    if (bestRepl >= 0) {
                        selected[bestRepl] = true;
                        improved = true;
                        break outer;
                    }
                    selected[a] = true;
                    selected[b] = true;
                }
            }
        }

        TreeSet<String> result = new TreeSet<>();
        for (int i = 0; i < m; i++) {
            if (selected[i]) {
                result.add(cands.get(i).machineId());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static int[] selectedIndices(boolean[] selected) {
        int count = 0;
        for (boolean b : selected) if (b) count++;
        int[] out = new int[count];
        int w = 0;
        for (int i = 0; i < selected.length; i++) if (selected[i]) out[w++] = i;
        return out;
    }

    private static boolean coversAll(int kSteps, List<CandidateMachine> cands, boolean[] selected) {
        BitSet cov = new BitSet(kSteps);
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) cov.or(cands.get(i).coverBits());
        }
        return cov.cardinality() == kSteps;
    }

    private static BitSet missingBits(int kSteps, List<CandidateMachine> cands, boolean[] selected) {
        BitSet missing = new BitSet(kSteps);
        missing.set(0, kSteps);
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) missing.andNot(cands.get(i).coverBits());
        }
        return missing;
    }

    private static boolean containsAll(BitSet superset, BitSet subset) {
        for (int bit = subset.nextSetBit(0); bit >= 0; bit = subset.nextSetBit(bit + 1)) {
            if (!superset.get(bit)) return false;
        }
        return true;
    }
}

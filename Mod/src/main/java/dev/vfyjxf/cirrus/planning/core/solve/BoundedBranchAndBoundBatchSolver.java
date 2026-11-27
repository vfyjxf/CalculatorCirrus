package dev.vfyjxf.cirrus.planning.core.solve;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class BoundedBranchAndBoundBatchSolver implements BatchCouplingSolver {

    private static final int maxCandidates = 16;
    private static final long maxDemand = 256L;
    private static final int maxNodes = 2_000;

    public BoundedBranchAndBoundBatchSolver() {}

    @Override
    public Optional<Map<CompiledRecipeKey, Long>> refine(
        BatchCouplingComponent component,
        PlanningRequest request,
        long deadlineNanos
    ) {
        List<BatchCouplingComponent.CandidateRecipe> raw = component.candidates();
        if (raw.size() < 2 || raw.size() > maxCandidates) {
            return Optional.empty();
        }
        boolean anyMulti = false;
        for (var c : raw) {
            if (c.multiOutput()) {
                anyMulti = true;
                break;
            }
        }
        if (component.demand() > maxDemand && !anyMulti) {
            return Optional.empty();
        }

        List<BatchCouplingComponent.CandidateRecipe> cands = new ArrayList<>(raw);
        cands.sort(
            Comparator
                    .comparingDouble(
                        (BatchCouplingComponent.CandidateRecipe c) -> c.perBatchCost() / c.outputPerBatch()
                    )
                    .thenComparing(
                        Comparator.comparingLong(BatchCouplingComponent.CandidateRecipe::outputPerBatch).reversed()
                    ).thenComparing(BatchCouplingComponent.CandidateRecipe::recipeKey)
        );

        int n = cands.size();
        long[] outs = new long[n];
        double[] costs = new double[n];
        for (int i = 0; i < n; i++) {
            outs[i] = cands.get(i).outputPerBatch();
            costs[i] = cands.get(i).perBatchCost();
        }

        double[] suffixMinUnit = new double[n + 1];
        suffixMinUnit[n] = Double.POSITIVE_INFINITY;
        for (int i = n - 1; i >= 0; i--) {
            suffixMinUnit[i] = Math.min(suffixMinUnit[i + 1], costs[i] / outs[i]);
        }

        SearchState st = new SearchState(n, outs, costs, suffixMinUnit, component.seedUpperBoundCost(), deadlineNanos);
        st.dfs(0, component.demand(), 0.0, new long[n]);
        if (!st.improved) {
            return Optional.empty();
        }

        TreeMap<CompiledRecipeKey, Long> result = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            if (st.bestBatches[i] > 0L) {
                result.put(cands.get(i).recipeKey(), st.bestBatches[i]);
            }
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(Collections.unmodifiableMap(result));
    }

    private static final class SearchState {

        private final int n;
        private final long[] outs;
        private final double[] costs;
        private final double[] suffixMinUnit;
        private final long deadlineNanos;
        private double bestCost;
        private final long[] bestBatches;
        private boolean improved;
        private int visitedNodes;

        SearchState(int n, long[] outs, double[] costs, double[] suffixMinUnit, double upperBound, long deadlineNanos) {
            this.n = n;
            this.outs = outs.clone();
            this.costs = costs.clone();
            this.suffixMinUnit = suffixMinUnit.clone();
            this.bestCost = upperBound;
            this.bestBatches = new long[n];
            this.deadlineNanos = deadlineNanos;
        }

        void dfs(int idx, long rem, double curCost, long[] cur) {
            if (visitedNodes >= maxNodes) {
                return;
            }
            if (deadlineNanos > 0L && (visitedNodes & 63) == 0 && System.nanoTime() >= deadlineNanos) {
                visitedNodes = maxNodes;
                return;
            }
            visitedNodes++;

            if (rem <= 0L) {
                if (curCost + 1e-9 < bestCost) {
                    bestCost = curCost;
                    System.arraycopy(cur, 0, bestBatches, 0, n);
                    improved = true;
                }
                return;
            }
            if (idx >= n) {
                return;
            }
            double lb = curCost + rem * suffixMinUnit[idx];
            if (lb >= bestCost - 1e-9) {
                return;
            }

            long out = outs[idx];
            double cost = costs[idx];
            if (idx == n - 1) {
                long needBatches = SafeMath.ceilDiv(rem, out);
                double leafCost = curCost + needBatches * cost;
                if (leafCost + 1e-9 < bestCost) {
                    bestCost = leafCost;
                    cur[idx] = needBatches;
                    System.arraycopy(cur, 0, bestBatches, 0, n);
                    cur[idx] = 0L;
                    improved = true;
                }
                return;
            }

            long maxB = SafeMath.ceilDiv(rem, out);
            long floorB = rem / out;
            // Prioritize floor(rem/out) (exact mix) -> ceil -> 0 -> remaining descending.
            long[] priority = new long[]{floorB, maxB, 0L};
            for (int p = 0; p < priority.length; p++) {
                long b = priority[p];
                if (b < 0L || b > maxB) continue;
                boolean dup = false;
                for (int q = 0; q < p; q++) {
                    if (priority[q] == b) {
                        dup = true;
                        break;
                    }
                }
                if (dup) continue;
                double nextCost = curCost + b * cost;
                if (nextCost >= bestCost - 1e-9) continue;
                cur[idx] = b;
                dfs(idx + 1, Math.max(0L, rem - b * out), nextCost, cur);
                cur[idx] = 0L;
            }
            for (long b = floorB - 1L; b >= 1L && visitedNodes < maxNodes; b--) {
                double nextCost = curCost + b * cost;
                if (nextCost >= bestCost - 1e-9) continue;
                cur[idx] = b;
                dfs(idx + 1, Math.max(0L, rem - b * out), nextCost, cur);
                cur[idx] = 0L;
            }
        }
    }
}

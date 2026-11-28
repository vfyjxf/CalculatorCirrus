package dev.vfyjxf.cirrus.planning.core.select;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.RegimeSignature;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Strictly acyclic regime-conditioned Pareto frontier and weighted-Jaccard Top-K diversity selector.
 *
 * <p>Eliminates naive cross-regime epsilon-dominance 3-cycles by:
 * 1. Taking strict 6D Pareto frontier inside each prerequisite regime R(P) (while preserving the J-champion).
 * 2. Quantizing physical dimensions onto candidate-independent reference box coordinates
 *    b_i(P) = floor(C_i(P) / (epsilon_i * s_i)), adding directed cross-regime edge A -> B only when
 *    E(B) => E(A) and b(A) <= b(B) and at least one coordinate strictly smaller.
 * 3. Selecting Top-K via Max-Min weighted Jaccard diversity (group weights 0.30/0.25/0.20/0.15/0.10),
 *    using exact combination search for small pools (K <= 4, n <= 14) and deterministic O(K n L_s) greedy fallback.
 */
public final class ConditionedFrontierSelector {

    public static final double defaultBoxEpsilon = 0.05;
    public static final double defaultQualityBandAbs = 0.04;
    public static final double defaultQualityBandRel = 0.30;

    private static final double wMachine = 0.30;
    private static final double wCluster = 0.25;
    private static final double wTransform = 0.20;
    private static final double wFamily = 0.15;
    private static final double wAcquire = 0.10;

    public record SelectionOutput(
        List<ProductionPlan> selectedTopK,
        List<ProductionPlan> fullConditionedFrontier,
        int hiddenIndependentBranches
    ) {}

    private record Entry(
        ProductionPlan plan,
        String canonicalKey,
        long[] boxCoords,
        long boxSum,
        double normalizedSum,
        int pinnedUsed,
        int advisoryWeight,
        Map<String, Double> diversityVector
    ) {}

    private ConditionedFrontierSelector() {}

    public static SelectionOutput select(
        List<ProductionPlan> candidates,
        double[] referenceScalesS,
        double boxEpsilon,
        int maxReturnedK
    ) {
        return select(candidates, referenceScalesS, boxEpsilon, maxReturnedK, null, null);
    }

    public static SelectionOutput select(
        List<ProductionPlan> candidates,
        double[] referenceScalesS,
        double boxEpsilon,
        int maxReturnedK,
        @Nullable CompiledRecipeGraph graph,
        @Nullable HumanGuidanceView guidance
    ) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty() || maxReturnedK <= 0) {
            return new SelectionOutput(List.of(), List.of(), 0);
        }
        double[] scales = normalizeScales(referenceScalesS);
        double eps = Math.max(1e-6, boxEpsilon);

        // 0. Canonical deduplication (permutation-invariant).
        Map<String, Entry> deduped = new TreeMap<>();
        for (ProductionPlan p : candidates) {
            if (p == null) continue;
            String cKey = canonicalPlanKey(p);
            long[] boxes = boxCoords(p.totalCost(), scales, eps);
            long bSum = 0L;
            double nSum = 0.0;
            double[] arr = p.totalCost().asArray();
            for (int i = 0; i < 6; i++) {
                bSum += boxes[i];
                nSum += arr[i] / scales[i];
            }
            int pinnedUsed = countPinnedRecipes(p, guidance);
            int advWeight = advisoryWeight(p, graph, guidance);
            Entry entry = new Entry(p, cKey, boxes, bSum, nSum, pinnedUsed, advWeight, buildDiversityVector(p));
            Entry existing = deduped.get(cKey);
            if (existing == null || compareChampionOrder(entry, existing) < 0) {
                deduped.put(cKey, entry);
            }
        }
        if (deduped.isEmpty()) {
            return new SelectionOutput(List.of(), List.of(), 0);
        }

        List<Entry> pool = new ArrayList<>(deduped.values());
        int maxPinned = 0;
        boolean hasNonLastResort = false;
        for (Entry e : pool) {
            if (e.pinnedUsed() > maxPinned) {
                maxPinned = e.pinnedUsed();
            }
            if (e.advisoryWeight() < RecipeAdvisory.lastResort.weight()) {
                hasNonLastResort = true;
            }
        }
        if (maxPinned > 0) {
            final int bestPin = maxPinned;
            pool.removeIf(e -> e.pinnedUsed() < bestPin);
        }
        if (hasNonLastResort) {
            pool.removeIf(e -> e.advisoryWeight() >= RecipeAdvisory.lastResort.weight());
        }

        // 1. Within-regime strict 6D Pareto + retain J-champion per regime.
        Map<RegimeSignature, List<Entry>> byRegime = new TreeMap<>();
        for (Entry e : pool) {
            byRegime.computeIfAbsent(e.plan().regimeSignature(), k -> new ArrayList<>()).add(e);
        }

        List<Entry> stage1Survivors = new ArrayList<>();
        for (List<Entry> group : byRegime.values()) {
            group.sort(ConditionedFrontierSelector::compareChampionOrder);
            Entry jChampion = group.get(0);
            boolean champIncluded = false;
            for (int i = 0; i < group.size(); i++) {
                Entry cand = group.get(i);
                boolean dominated = false;
                for (int j = 0; j < group.size(); j++) {
                    if (i == j) continue;
                    if (group.get(j).plan().totalCost().dominates(cand.plan().totalCost())) {
                        dominated = true;
                        break;
                    }
                }
                if (!dominated) {
                    stage1Survivors.add(cand);
                    if (cand == jChampion) {
                        champIncluded = true;
                    }
                }
            }
            if (!champIncluded) {
                stage1Survivors.add(jChampion);
            }
        }

        // 2. Acyclic cross-regime box dominance: A -> B iff E(B) => E(A) AND b(A) <= b(B) AND strict in >= 1 dim.
        // Guaranteed acyclic because every edge strictly increases sum(b_i)!
        List<Entry> frontier = new ArrayList<>(stage1Survivors.size());
        for (int i = 0; i < stage1Survivors.size(); i++) {
            Entry b = stage1Survivors.get(i);
            boolean hasIncoming = false;
            for (int j = 0; j < stage1Survivors.size(); j++) {
                if (i == j) continue;
                Entry a = stage1Survivors.get(j);
                if (a.plan().regimeSignature().equals(b.plan().regimeSignature())) {
                    continue;
                }
                if (crossRegimeBoxDominates(a, b)) {
                    hasIncoming = true;
                    break;
                }
            }
            if (!hasIncoming) {
                frontier.add(b);
            }
        }
        frontier.sort(ConditionedFrontierSelector::compareChampionOrder);
        if (frontier.isEmpty()) {
            return new SelectionOutput(List.of(), List.of(), 0);
        }

        // 3. Quality band filter (preserving incompatible regime champions).
        double j0 = frontier.get(0).plan().scoreBreakdown().totalObjectiveJ();
        double maxBand = j0 + defaultQualityBandAbs + defaultQualityBandRel * Math.max(0.0, j0);
        List<Entry> eligible = new ArrayList<>(frontier.size());
        Set<RegimeSignature> keptRegimes = new TreeSet<>();
        for (Entry e : frontier) {
            boolean inBand = e.plan().scoreBreakdown().totalObjectiveJ() <= maxBand + 1e-12;
            boolean uniqueRegime = !keptRegimes.contains(e.plan().regimeSignature());
            if (inBand || uniqueRegime) {
                eligible.add(e);
                keptRegimes.add(e.plan().regimeSignature());
            }
        }

        // 4. Deterministic Max-Min Diversity Top-K (exact for K <= 4 && n <= 14, greedy O(K n L_s) otherwise).
        int k = Math.min(maxReturnedK, eligible.size());
        List<Entry> chosen;
        if (k <= 1) {
            chosen = List.of(eligible.get(0));
        } else if (k <= 4 && eligible.size() <= 14) {
            chosen = selectExactMaxMin(eligible, k);
        } else {
            chosen = selectGreedyMaxMin(eligible, k);
        }

        List<ProductionPlan> topKPlans = chosen.stream().map(Entry::plan).toList();
        List<ProductionPlan> fullPlans = frontier.stream().map(Entry::plan).toList();
        int hidden = Math.max(0, keptRegimes.size() - (int) chosen.stream().map(e -> e.plan().regimeSignature()).distinct().count());
        return new SelectionOutput(topKPlans, fullPlans, hidden);
    }

    /** Returns true iff A strictly box-dominates B across regimes (E(B) => E(A) and b(A) < b(B)). */
    public static boolean crossRegimeBoxDominates(Entry a, Entry b) {
        if (!b.plan().regimeSignature().entails(a.plan().regimeSignature())) {
            return false;
        }
        boolean anyStrict = false;
        for (int i = 0; i < 6; i++) {
            if (a.boxCoords()[i] > b.boxCoords()[i]) {
                return false;
            }
            if (a.boxCoords()[i] < b.boxCoords()[i]) {
                anyStrict = true;
            }
        }
        return anyStrict;
    }

    public static double weightedJaccardDistance(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        double num = 0.0;
        double den = 0.0;
        for (var e : a.entrySet()) {
            double va = e.getValue();
            double vb = b.getOrDefault(e.getKey(), 0.0);
            num += Math.min(va, vb);
            den += Math.max(va, vb);
        }
        for (var e : b.entrySet()) {
            if (!a.containsKey(e.getKey())) {
                den += e.getValue();
            }
        }
        return den <= 0.0 ? 0.0 : 1.0 - (num / den);
    }

    private static List<Entry> selectGreedyMaxMin(List<Entry> pool, int k) {
        int n = pool.size();
        List<Entry> selected = new ArrayList<>(k);
        boolean[] used = new boolean[n];
        double[] minDistToSelected = new double[n];
        selected.add(pool.get(0));
        used[0] = true;
        for (int i = 1; i < n; i++) {
            minDistToSelected[i] = weightedJaccardDistance(pool.get(i).diversityVector(), pool.get(0).diversityVector());
        }
        while (selected.size() < k) {
            int bestIdx = -1;
            double bestDist = -1.0;
            for (int i = 0; i < n; i++) {
                if (used[i]) continue;
                double d = minDistToSelected[i];
                if (bestIdx < 0
                        || d > bestDist + 1e-12
                        || (Math.abs(d - bestDist) <= 1e-12 && compareChampionOrder(pool.get(i), pool.get(bestIdx)) < 0)) {
                    bestDist = d;
                    bestIdx = i;
                }
            }
            if (bestIdx < 0) break;
            used[bestIdx] = true;
            Entry newlyAdded = pool.get(bestIdx);
            selected.add(newlyAdded);
            for (int i = 0; i < n; i++) {
                if (used[i]) continue;
                double d = weightedJaccardDistance(pool.get(i).diversityVector(), newlyAdded.diversityVector());
                if (d < minDistToSelected[i]) {
                    minDistToSelected[i] = d;
                }
            }
        }
        return selected;
    }

    private static List<Entry> selectExactMaxMin(List<Entry> pool, int k) {
        int n = pool.size();
        double[][] dist = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = weightedJaccardDistance(pool.get(i).diversityVector(), pool.get(j).diversityVector());
                dist[i][j] = d;
                dist[j][i] = d;
            }
        }
        int[] bestCombo = new int[k];
        bestCombo[0] = 0;
        for (int i = 1; i < k; i++) bestCombo[i] = i;
        double[] bestScore = {-1.0};
        int[] cur = new int[k];
        cur[0] = 0; // Always retain J-champion as index 0.
        searchCombos(pool, dist, k, 1, 1, cur, bestCombo, bestScore);
        List<Entry> out = new ArrayList<>(k);
        for (int idx : bestCombo) {
            out.add(pool.get(idx));
        }
        return out;
    }

    private static void searchCombos(
        List<Entry> pool,
        double[][] dist,
        int k,
        int depth,
        int startIdx,
        int[] cur,
        int[] bestCombo,
        double[] bestScore
    ) {
        if (depth == k) {
            double minPair = Double.POSITIVE_INFINITY;
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    if (dist[cur[i]][cur[j]] < minPair) {
                        minPair = dist[cur[i]][cur[j]];
                    }
                }
            }
            boolean better = minPair > bestScore[0] + 1e-12;
            if (!better && Math.abs(minPair - bestScore[0]) <= 1e-12) {
                // Canonical tie-break: lower sum of J, then lexicographical index order.
                double jCur = 0.0;
                double jBest = 0.0;
                for (int i = 0; i < k; i++) {
                    jCur += pool.get(cur[i]).plan().scoreBreakdown().totalObjectiveJ();
                    jBest += pool.get(bestCombo[i]).plan().scoreBreakdown().totalObjectiveJ();
                }
                better = jCur + 1e-12 < jBest;
            }
            if (better) {
                bestScore[0] = minPair;
                System.arraycopy(cur, 0, bestCombo, 0, k);
            }
            return;
        }
        int n = pool.size();
        for (int i = startIdx; i <= n - (k - depth); i++) {
            cur[depth] = i;
            searchCombos(pool, dist, k, depth + 1, i + 1, cur, bestCombo, bestScore);
        }
    }

    private static Map<String, Double> buildDiversityVector(ProductionPlan p) {
        Map<String, Double> raw = new TreeMap<>();
        List<String> mach = new ArrayList<>();
        List<String> clus = new ArrayList<>(p.regimeSignature().techClusters());
        List<String> trans = new ArrayList<>();
        List<String> acq = new ArrayList<>();

        for (PlanStep s : p.steps()) {
            switch (s) {
                case ProvisionMachineStep pm -> mach.add("M:" + pm.machine().canonicalKey());
                case CraftStep cs -> {
                    cs.chosenMachine().ifPresent(m -> mach.add("M:" + m.canonicalKey()));
                    trans.add("R:" + cs.recipeKey().shortString());
                }
                case ConvertStep cv -> trans.add("V:" + cv.conversionRecipeKey().shortString());
                case CycleLoopStep cl -> {
                    trans.add("Y:" + cl.macroKey().macroId());
                    for (CraftStep inner : cl.singleIterationTemplate()) {
                        inner.chosenMachine().ifPresent(m -> mach.add("M:" + m.canonicalKey()));
                        trans.add("R:" + inner.recipeKey().shortString());
                    }
                }
                case SetupLineStep sl -> {
                    mach.add("M:" + sl.primaryMachine().canonicalKey());
                    trans.add("S:" + sl.systemId() + ":" + sl.targetOutput().canonicalKey());
                }
                case LinePullStep lp -> acq.add("L:" + lp.lineId() + ":" + lp.resource().canonicalKey());
                case AcquireStep as -> acq.add("A:" + as.acquisitionKind().name() + ":" + as.resource().canonicalKey());
            }
        }
        addNormalizedGroup(raw, mach, wMachine);
        addNormalizedGroup(raw, clus, wCluster);
        addNormalizedGroup(raw, trans, wTransform);
        addNormalizedGroup(raw, List.copyOf(p.regimeSignature().premiseAtoms()), wFamily);
        addNormalizedGroup(raw, acq, wAcquire);
        return raw;
    }

    private static int countPinnedRecipes(ProductionPlan plan, @Nullable HumanGuidanceView guidance) {
        if (guidance == null || guidance.allRules().isEmpty()) {
            return 0;
        }
        int count = 0;
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs && guidance.isRecipePinned(cs.recipeKey())) {
                count++;
            } else if (step instanceof CycleLoopStep cl) {
                for (CraftStep inner : cl.singleIterationTemplate()) {
                    if (guidance.isRecipePinned(inner.recipeKey())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static int advisoryWeight(
        ProductionPlan plan,
        @Nullable CompiledRecipeGraph graph,
        @Nullable HumanGuidanceView guidance
    ) {
        if (graph == null) return 0;
        int sum = 0;
        for (PlanStep step : plan.steps()) {
            switch (step) {
                case CraftStep cs -> {
                    if (guidance == null || !guidance.isRecipePinned(cs.recipeKey())) {
                        sum += graph.recipeByKey(cs.recipeKey()).map(r -> r.advisory().weight()).orElse(0);
                    }
                }
                case ConvertStep cv ->
                    sum += graph.recipeByKey(cv.conversionRecipeKey()).map(r -> r.advisory().weight()).orElse(0);
                case CycleLoopStep cl -> {
                    for (CraftStep inner : cl.singleIterationTemplate()) {
                        if (guidance == null || !guidance.isRecipePinned(inner.recipeKey())) {
                            sum += graph.recipeByKey(inner.recipeKey()).map(r -> r.advisory().weight()).orElse(0);
                        }
                    }
                }
                default -> {}
            }
        }
        return sum;
    }

    private static void addNormalizedGroup(Map<String, Double> out, List<String> items, double groupWeight) {
        if (items.isEmpty()) return;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String it : items) {
            counts.merge(it, 1, Integer::sum);
        }
        double invTotal = groupWeight / items.size();
        for (var e : counts.entrySet()) {
            out.merge(e.getKey(), e.getValue() * invTotal, Double::sum);
        }
    }

    private static long[] boxCoords(CostVector c, double[] scales, double eps) {
        double[] arr = c.asArray();
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (long) Math.floor(arr[i] / (eps * scales[i]) + 1e-12);
        }
        return out;
    }

    private static double[] normalizeScales(double[] scales) {
        double[] out = {10.0, 10.0, 10.0, 10.0, 10.0, 1.0};
        if (scales != null) {
            for (int i = 0; i < Math.min(6, scales.length); i++) {
                if (Double.isFinite(scales[i]) && scales[i] > 0.0) {
                    out[i] = scales[i];
                }
            }
        }
        return out;
    }

    private static int compareChampionOrder(Entry a, Entry b) {
        int c = Integer.compare(b.pinnedUsed(), a.pinnedUsed());
        if (c != 0) return c;
        c = Integer.compare(a.advisoryWeight(), b.advisoryWeight());
        if (c != 0) return c;
        c = Long.compare(
            a.plan().scoreBreakdown().quantizedObjective(),
            b.plan().scoreBreakdown().quantizedObjective()
        );
        if (c != 0) return c;
        c = Double.compare(a.plan().totalCost().cognitiveFriction(), b.plan().totalCost().cognitiveFriction());
        if (c != 0) return c;
        return a.canonicalKey().compareTo(b.canonicalKey());
    }

    public static String canonicalPlanKey(ProductionPlan p) {
        return p.regimeSignature().canonicalKey()
                + "::" + DiverseParetoSelector.structuralKey(p)
                + "::" + p.totalCost().quantizedSignature();
    }
}

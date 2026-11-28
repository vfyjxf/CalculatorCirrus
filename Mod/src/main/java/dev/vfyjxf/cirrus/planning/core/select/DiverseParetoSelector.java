package dev.vfyjxf.cirrus.planning.core.select;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanVerifier;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.core.solve.ImprovementLoop;
import dev.vfyjxf.cirrus.planning.core.verify.DefaultPlanVerifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DiverseParetoSelector {

    private final PlanVerifier verifier;
    private final @Nullable CompiledRecipeGraph graph;

    public DiverseParetoSelector(PlanVerifier verifier) {
        this(verifier, null);
    }

    public DiverseParetoSelector(CompiledRecipeGraph graph) {
        this(new DefaultPlanVerifier(Objects.requireNonNull(graph, "graph")), graph);
    }

    public DiverseParetoSelector(PlanVerifier verifier, @Nullable CompiledRecipeGraph graph) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.graph = graph;
    }

    public List<ProductionPlan> select(List<ProductionPlan> candidates, PlanningRequest request) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(request, "request");

        // 1. Re-verify every candidate and stamp a fresh certificate.
        Map<String, CandidateEntry> deduped = new LinkedHashMap<>();
        CostWeights primaryWeights = request.profile().weights();
        HumanGuidanceView guidance = request.context().guidance();

        for (ProductionPlan raw : candidates) {
            if (raw == null) continue;
            VerificationCertificate cert = verifier.verify(raw, request);
            if (!cert.valid()) continue;
            ProductionPlan stamped = new ProductionPlan(
                raw.planId(),
                raw.strategyLabelKey(),
                raw.steps(),
                raw.preparation(),
                raw.surplusByproducts(),
                raw.totalCost(),
                cert
            );
            String structKey = structuralKey(stamped);
            int pinnedUsed = countPinnedRecipes(stamped, guidance);
            int advWeight = advisoryWeight(stamped, guidance);
            double scalar = ImprovementLoop.guidedScalar(stamped, primaryWeights, guidance);
            CandidateEntry entry = new CandidateEntry(
                stamped,
                structKey,
                featureSignature(stamped),
                pinnedUsed,
                advWeight,
                scalar
            );
            CandidateEntry existing = deduped.get(structKey);
            if (existing == null || entry.compareRank(existing) < 0) {
                deduped.put(structKey, entry);
            }
        }

        if (deduped.isEmpty()) {
            return List.of();
        }

        List<CandidateEntry> pool = new ArrayList<>(deduped.values());

        boolean hasNonLastResort = false;
        int maxPinnedUsed = 0;
        for (CandidateEntry c : pool) {
            if (c.advisoryWeight < RecipeAdvisory.lastResort.weight()) {
                hasNonLastResort = true;
            }
            if (c.pinnedUsed > maxPinnedUsed) {
                maxPinnedUsed = c.pinnedUsed;
            }
        }
        if (maxPinnedUsed > 0) {
            final int bestPin = maxPinnedUsed;
            pool.removeIf(c -> c.pinnedUsed < bestPin);
        }
        if (hasNonLastResort) {
            pool.removeIf(c -> c.advisoryWeight >= RecipeAdvisory.lastResort.weight());
        }

        // Prevent higher-advisory plans from dominating cleaner plans while preserving INV-04.
        List<CandidateEntry> cleanPool = new ArrayList<>(pool.size());
        for (int i = 0; i < pool.size(); i++) {
            CandidateEntry cand = pool.get(i);
            boolean dominatesCleaner = false;
            if (cand.advisoryWeight > 0) {
                for (int j = 0; j < pool.size(); j++) {
                    if (i == j) continue;
                    CandidateEntry other = pool.get(j);
                    if (other.advisoryWeight < cand.advisoryWeight
                            && cand.plan.totalCost().dominates(other.plan.totalCost())) {
                        dominatesCleaner = true;
                        break;
                    }
                }
            }
            if (!dominatesCleaner) {
                cleanPool.add(cand);
            }
        }

        // 2. Extract exact Pareto non-dominated frontier (INV-04).
        List<CandidateEntry> frontier = new ArrayList<>(cleanPool.size());
        for (int i = 0; i < cleanPool.size(); i++) {
            CandidateEntry cand = cleanPool.get(i);
            boolean dominated = false;
            for (int j = 0; j < cleanPool.size(); j++) {
                if (i == j) continue;
                if (cleanPool.get(j).plan.totalCost().dominates(cand.plan.totalCost())) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                frontier.add(cand);
            }
        }

        // 3. Deterministic ranking and Jaccard diversity Top-K selection.
        frontier.sort(CandidateEntry::compareRank);
        int limit = request.budget().maxReturnedPlans();
        double minDist = request.profile().diversityThreshold();

        List<CandidateEntry> selected = new ArrayList<>(Math.min(limit, frontier.size()));
        selected.add(frontier.get(0));
        for (int i = 1; i < frontier.size() && selected.size() < limit; i++) {
            CandidateEntry cand = frontier.get(i);
            double minJaccard = 1.0;
            for (CandidateEntry s : selected) {
                double d = jaccardDistance(cand.features, s.features);
                if (d < minJaccard) {
                    minJaccard = d;
                }
            }
            if (minJaccard + 1e-12 >= minDist) {
                selected.add(cand);
            }
        }

        List<ProductionPlan> result = new ArrayList<>(selected.size());
        for (CandidateEntry e : selected) {
            result.add(e.plan);
        }
        return List.copyOf(result);
    }

    public static String structuralKey(ProductionPlan plan) {
        List<String> parts = new ArrayList<>(plan.steps().size());
        for (PlanStep step : plan.steps()) {
            parts.add(switch (step) {
                case AcquireStep a -> "A:" + a.resource().canonicalKey() + "=" + a.amount();
                case LinePullStep lp -> "L:" + lp.lineId() + ":" + lp.resource().canonicalKey() + "=" + lp.amount();
                case ProvisionMachineStep pm ->
                    "P:" + pm.machine().canonicalKey() + "x" + pm.countToBuild() + ":" + pm.craftedByPredecessorSteps();
                case SetupLineStep sl ->
                    "S:" + sl.systemId() + ":" + sl.primaryMachine().canonicalKey() + ":" + sl.targetOutput().canonicalKey();
                case CraftStep cs ->
                    "C:" + cs.recipeKey().shortString() + "x" + cs.batches() + ":"
                            + cs.chosenMachine().map(MachineEntry::canonicalKey).orElse("")
                            + ":" + cs.executedByAutomation() + ":" + bindingsKey(cs.slotBindings());
                case ConvertStep cv -> "V:" + cv.conversionRecipeKey().shortString() + "x" + cv.batches() + ":"
                        + bindingsKey(cv.slotBindings());
                case CycleLoopStep cl -> "Y:" + cl.macroKey().macroId() + "x" + cl.iterations();
            });
        }
        parts.sort(Comparator.naturalOrder());
        return String.join("|", parts);
    }

    private static String bindingsKey(Map<Integer, SlotBinding> bindings) {
        if (bindings.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (var e : bindings.entrySet()) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue().chosenResource().canonicalKey());
        }
        return sb.toString();
    }

    private static Set<String> featureSignature(ProductionPlan plan) {
        Set<String> sig = new HashSet<>();
        for (PlanStep step : plan.steps()) {
            switch (step) {
                case AcquireStep a -> sig.add("acq:" + a.resource().canonicalKey());
                case LinePullStep lp -> sig.add("line:" + lp.lineId());
                case ProvisionMachineStep pm -> sig.add("prov:" + pm.machine().canonicalKey());
                case SetupLineStep sl -> sig.add("setup:" + sl.systemId() + ":" + sl.primaryMachine().canonicalKey());
                case CraftStep cs -> sig.add("craft:" + cs.recipeKey().shortString());
                case ConvertStep cv -> sig.add("conv:" + cv.conversionRecipeKey().shortString());
                case CycleLoopStep cl -> sig.add("loop:" + cl.macroKey().macroId());
            }
        }
        return sig;
    }

    private static int countPinnedRecipes(ProductionPlan plan, HumanGuidanceView guidance) {
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

    private int advisoryWeight(ProductionPlan plan, HumanGuidanceView guidance) {
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

    private static double jaccardDistance(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = a.size() <= b.size() ? b : a;
        for (String s : small) {
            if (large.contains(s)) {
                inter++;
            }
        }
        int union = a.size() + b.size() - inter;
        return 1.0 - ((double) inter / Math.max(1, union));
    }

    private record CandidateEntry(
        ProductionPlan plan,
        String structKey,
        Set<String> features,
        int pinnedUsed,
        int advisoryWeight,
        double primaryScalar
    ) {

        int compareRank(CandidateEntry o) {
            int c = Integer.compare(o.pinnedUsed, pinnedUsed);
            if (c != 0) return c;
            c = Integer.compare(advisoryWeight, o.advisoryWeight);
            if (c != 0) return c;
            c = Double.compare(primaryScalar, o.primaryScalar);
            if (c != 0) return c;
            c = Integer.compare(plan.steps().size(), o.plan.steps().size());
            if (c != 0) return c;
            return plan.planId().compareTo(o.plan.planId());
        }
    }
}

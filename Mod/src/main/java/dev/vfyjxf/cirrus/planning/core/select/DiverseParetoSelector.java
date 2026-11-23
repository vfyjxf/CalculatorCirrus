package dev.vfyjxf.cirrus.planning.core.select;

import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
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
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.verify.DefaultPlanVerifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class DiverseParetoSelector {

    private final PlanVerifier verifier;
    private final CompiledRecipeGraph graph;

    public DiverseParetoSelector(PlanVerifier verifier) {
        this(verifier, null);
    }

    public DiverseParetoSelector(CompiledRecipeGraph graph) {
        this(new DefaultPlanVerifier(Objects.requireNonNull(graph, "graph")), graph);
    }

    public DiverseParetoSelector(PlanVerifier verifier, CompiledRecipeGraph graph) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.graph = graph;
    }

    public List<ProductionPlan> select(List<ProductionPlan> candidates, PlanningRequest request) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(request, "request");

        // 1. Re-verify every candidate and stamp a fresh certificate.
        Map<String, CandidateEntry> deduped = new LinkedHashMap<>();
        CostWeights primaryWeights = request.profile().weights();

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
            int advWeight = advisoryWeight(stamped);
            double scalar = primaryWeights.scalarize(stamped.totalCost());
            CandidateEntry entry = new CandidateEntry(stamped, structKey, featureSignature(stamped), advWeight, scalar);
            CandidateEntry existing = deduped.get(structKey);
            if (existing == null || entry.compareRank(existing) < 0) {
                deduped.put(structKey, entry);
            }
        }

        if (deduped.isEmpty()) {
            return List.of();
        }

        // 2. Extract exact Pareto non-dominated frontier (INV-04).
        List<CandidateEntry> all = new ArrayList<>(deduped.values());
        List<CandidateEntry> frontier = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            CandidateEntry cand = all.get(i);
            boolean dominated = false;
            for (int j = 0; j < all.size(); j++) {
                if (i == j) continue;
                if (all.get(j).plan.totalCost().dominates(cand.plan.totalCost())) {
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
                case AcquireStep a -> "A:" + a.resource().shortString() + "=" + a.amount();
                case LinePullStep lp -> "L:" + lp.lineId() + ":" + lp.resource().shortString() + "=" + lp.amount();
                case ProvisionMachineStep pm -> "P:" + pm.machineId() + "x" + pm.countToBuild()
                        + ":" + pm.craftedByPredecessorSteps();
                case SetupLineStep sl -> "S:" + sl.systemId() + ":" + sl.primaryMachineId()
                        + ":" + sl.targetOutput().shortString();
                case CraftStep cs -> "C:" + cs.recipeKey().shortString() + "x" + cs.batches()
                        + ":" + cs.chosenMachineId().orElse("") + ":" + cs.executedByAutomation()
                        + ":" + bindingsKey(cs.slotBindings());
                case ConvertStep cv -> "V:" + cv.conversionRecipeKey().shortString() + "x" + cv.batches()
                        + ":" + bindingsKey(cv.slotBindings());
                case CycleLoopStep cl -> "Y:" + cl.macroKey().macroId() + "x" + cl.iterations();
            });
        }
        parts.sort(Comparator.naturalOrder());
        return String.join("|", parts);
    }

    private static String bindingsKey(Map<Integer, dev.vfyjxf.cirrus.api.planning.plan.SlotBinding> bindings) {
        if (bindings.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (var e : bindings.entrySet()) {
            if (!sb.isEmpty()) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue().chosenResource().shortString());
        }
        return sb.toString();
    }

    private static Set<String> featureSignature(ProductionPlan plan) {
        Set<String> sig = new HashSet<>();
        for (PlanStep step : plan.steps()) {
            switch (step) {
                case AcquireStep a -> sig.add("acq:" + a.resource().shortString());
                case LinePullStep lp -> sig.add("line:" + lp.lineId());
                case ProvisionMachineStep pm -> sig.add("prov:" + pm.machineId());
                case SetupLineStep sl -> sig.add("setup:" + sl.systemId() + ":" + sl.primaryMachineId());
                case CraftStep cs -> sig.add("craft:" + cs.recipeKey().shortString());
                case ConvertStep cv -> sig.add("conv:" + cv.conversionRecipeKey().shortString());
                case CycleLoopStep cl -> sig.add("loop:" + cl.macroKey().macroId());
            }
        }
        return sig;
    }

    private int advisoryWeight(ProductionPlan plan) {
        if (graph == null) return 0;
        int sum = 0;
        for (PlanStep step : plan.steps()) {
            switch (step) {
                case CraftStep cs -> sum += graph.recipeByKey(cs.recipeKey())
                        .map(r -> r.advisory().weight()).orElse(0);
                case ConvertStep cv -> sum += graph.recipeByKey(cv.conversionRecipeKey())
                        .map(r -> r.advisory().weight()).orElse(0);
                case CycleLoopStep cl -> {
                    for (CraftStep inner : cl.singleIterationTemplate()) {
                        sum += graph.recipeByKey(inner.recipeKey())
                                .map(r -> r.advisory().weight()).orElse(0);
                    }
                }
                default -> {
                }
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
            int advisoryWeight,
            double primaryScalar
    ) {

        int compareRank(CandidateEntry o) {
            int c = Integer.compare(advisoryWeight, o.advisoryWeight);
            if (c != 0) return c;
            c = Double.compare(primaryScalar, o.primaryScalar);
            if (c != 0) return c;
            c = Integer.compare(plan.steps().size(), o.plan.steps().size());
            if (c != 0) return c;
            return plan.planId().compareTo(o.plan.planId());
        }
    }
}

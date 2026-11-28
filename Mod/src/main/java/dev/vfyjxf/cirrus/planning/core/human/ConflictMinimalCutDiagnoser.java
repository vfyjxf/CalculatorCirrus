package dev.vfyjxf.cirrus.planning.core.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.ConflictDiagnosis;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cone.ConeExtractor;
import dev.vfyjxf.cirrus.planning.core.cone.ConeModel;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.lang.WorkBudget;
import dev.vfyjxf.cirrus.planning.core.solve.ChoiceTable;
import dev.vfyjxf.cirrus.planning.core.solve.Netting;
import dev.vfyjxf.cirrus.planning.core.solve.Potentials;
import dev.vfyjxf.cirrus.planning.core.solve.ProductionVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class ConflictMinimalCutDiagnoser {

    public static final int maxExactLocalizationRules = 16;
    public static final String keyRelaxSingle = "cirrus.conflict.relax_rule";
    public static final String keyRelaxSet = "cirrus.conflict.relax_rule_set";
    public static final String keyTooManyRules = "cirrus.conflict.too_many_rules_to_localize";

    private final CompiledRecipeGraph graph;
    private final ConeExtractor coneExtractor;
    private final CostMetadataProvider costMetadata;

    public ConflictMinimalCutDiagnoser(
        CompiledRecipeGraph graph,
        ConeExtractor coneExtractor,
        CostMetadataProvider costMetadata
    ) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.coneExtractor = Objects.requireNonNull(coneExtractor, "coneExtractor");
        this.costMetadata = Objects.requireNonNull(costMetadata, "costMetadata");
    }

    public Optional<ConflictDiagnosis> diagnose(
        PlanningRequest request,
        ConeModel failedCone,
        Map<ComponentStackKey, String> unmetResources,
        WorkBudget workBudget
    ) {
        HumanGuidanceView original = request.context().guidance();
        List<GuidanceRule> hardBans = original.hardBanRules();
        if (hardBans.isEmpty()) {
            return Optional.empty();
        }

        Set<String> allHardIds = new TreeSet<>();
        for (GuidanceRule rule : hardBans) {
            allHardIds.add(rule.ruleId());
        }

        if (!isFeasibleWithout(request, allHardIds, workBudget)) {
            return Optional.empty();
        }

        ComponentStackKey blocked = pickBlockedResource(request, failedCone, unmetResources);

        TreeSet<String> hitIds = new TreeSet<>();
        if (failedCone != null && !failedCone.blockingRuleIds.isEmpty()) {
            for (Set<String> ids : failedCone.blockingRuleIds.values()) {
                for (String id : ids) {
                    if (allHardIds.contains(id)) {
                        hitIds.add(id);
                    }
                }
            }
        }
        List<String> candidates;
        if (!hitIds.isEmpty() && isFeasibleWithout(request, hitIds, workBudget)) {
            candidates = new ArrayList<>(hitIds);
        } else {
            candidates = new ArrayList<>(allHardIds);
        }

        if (candidates.size() > maxExactLocalizationRules) {
            return Optional.of(
                new ConflictDiagnosis(
                    blocked,
                    List.copyOf(candidates.subList(0, maxExactLocalizationRules)),
                    keyTooManyRules
                )
            );
        }

        List<String> minimalCut = findMinimalCorrectionSet(request, candidates, workBudget);
        String suggestionKey = minimalCut.size() == 1 ? keyRelaxSingle : keyRelaxSet;
        return Optional.of(new ConflictDiagnosis(blocked, minimalCut, suggestionKey));
    }

    private List<String> findMinimalCorrectionSet(
        PlanningRequest request,
        List<String> candidates,
        WorkBudget workBudget
    ) {
        int m = candidates.size();
        if (m <= 1) {
            return List.copyOf(candidates);
        }

        // Exact size-1 check first so single-rule conflicts are found in O(m).
        for (int i = 0; i < m; i++) {
            if (isBudgetExhausted(workBudget)) break;
            String id = candidates.get(i);
            if (isFeasibleWithout(request, Set.of(id), workBudget)) {
                return List.of(id);
            }
        }

        // Exact size-2 check when candidate count is small.
        if (m <= 10) {
            for (int i = 0; i < m; i++) {
                for (int j = i + 1; j < m; j++) {
                    if (isBudgetExhausted(workBudget)) break;
                    Set<String> pair = Set.of(candidates.get(i), candidates.get(j));
                    if (isFeasibleWithout(request, pair, workBudget)) {
                        return List.of(candidates.get(i), candidates.get(j));
                    }
                }
            }
        }

        // Deletion-based contraction guarantees an inclusion-minimal correction set in <= m probes.
        List<String> activeDrop = new ArrayList<>(candidates);
        for (int i = activeDrop.size() - 1; i >= 0; i--) {
            if (activeDrop.size() <= 1 || isBudgetExhausted(workBudget)) {
                break;
            }
            String removed = activeDrop.remove(i);
            if (!isFeasibleWithout(request, new HashSet<>(activeDrop), workBudget)) {
                activeDrop.add(i, removed);
            }
        }
        Collections.sort(activeDrop);
        return List.copyOf(activeDrop);
    }

    private boolean isFeasibleWithout(PlanningRequest request, Set<String> droppedRuleIds, WorkBudget workBudget) {
        HumanGuidanceView filtered = filterGuidance(request.context().guidance(), droppedRuleIds);
        PlanningContext ctx = request.context();
        var oracle = ctx.acquisitionOracle() instanceof DefaultAcquisitionOracle dao
                ? dao.withGuidance(filtered)
                : ctx.acquisitionOracle();
        PlanningContext nextCtx = ctx.withOracleAndGuidance(oracle, filtered);
        PlanningRequest probe = new PlanningRequest(request.targets(), nextCtx, request.profile(), request.budget());
        ConeModel cone = coneExtractor.extract(probe, Set.of(), workBudget);
        for (TargetDemand td : probe.targets()) {
            int u = cone.localRes(td.resource());
            if (u < 0 || cone.tier[u] >= ConeModel.infTier) {
                return false;
            }
        }
        Potentials pot = Potentials.solve(cone, probe, probe.profile().weights(), costMetadata, Set.of(), Set.of());
        ChoiceTable choices = new ChoiceTable(pot);
        ProductionVector vec = Netting.settle(cone, probe, pot, choices, Map.of());
        return vec.isFeasible();
    }

    private static HumanGuidanceView filterGuidance(HumanGuidanceView original, Set<String> droppedRuleIds) {
        List<GuidanceRule> all = original.allRules();
        List<GuidanceRule> kept = new ArrayList<>(all.size());
        for (GuidanceRule r : all) {
            if (!droppedRuleIds.contains(r.ruleId())) {
                kept.add(r);
            }
        }
        return DefaultHumanGuidanceView.of(kept);
    }

    private static ComponentStackKey pickBlockedResource(
        PlanningRequest request,
        ConeModel failedCone,
        Map<ComponentStackKey, String> unmetResources
    ) {
        if (unmetResources != null && !unmetResources.isEmpty()) {
            for (TargetDemand td : request.targets()) {
                if (unmetResources.containsKey(td.resource())) {
                    return td.resource();
                }
            }
            return unmetResources.keySet().iterator().next();
        }
        if (failedCone != null) {
            for (TargetDemand td : request.targets()) {
                int u = failedCone.localRes(td.resource());
                if (u < 0 || failedCone.tier[u] >= ConeModel.infTier) {
                    return td.resource();
                }
            }
        }
        return request.targets().get(0).resource();
    }

    private static boolean isBudgetExhausted(WorkBudget workBudget) {
        return workBudget != null && workBudget.isHardTimeExceeded();
    }
}

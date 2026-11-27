package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.MachineReadiness;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M5 Iterative 3-Tier Machine Readiness Classifier (Pure JDK 21, ZERO wordlists):
 * classifies machines into owned, trivialToBuild (n <= 4 steps, k <= 45s_e, k / max(10, L_task) <= 0.25,
 * relying only on owned or shallower trivial facilities), and heavyInvestment.
 */
public final class MachineReadinessClassifier {

    public static final int defaultMaxTrivialSteps = 4;
    public static final double defaultMaxTrivialCostSeconds = 45.0;
    public static final double defaultMaxTrivialTaskRatio = 0.25;
    public static final double defaultTaskFloorSeconds = 10.0;

    private MachineReadinessClassifier() {}

    public static Map<String, MachineReadiness.Classification> classifyAll(
        CompiledRecipeGraph graph,
        FacilitySnapshot facilities,
        Map<ResourceKey, Double> baselineUnitPrices,
        double referenceTaskScaleSeconds,
        Set<String> candidateMachineIds
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(baselineUnitPrices, "baselineUnitPrices");
        Objects.requireNonNull(candidateMachineIds, "candidateMachineIds");

        Set<String> ownedIds = new HashSet<>();
        Set<MachineCapabilityId> ownedCaps = new HashSet<>();
        for (OwnedMachineGroup g : facilities.ownedMachines()) {
            if (g.count() > 0) {
                ownedIds.add(g.machineId());
                ownedCaps.add(g.capability());
            }
        }

        double maxAllowedByRatio = defaultMaxTrivialTaskRatio * Math.max(defaultTaskFloorSeconds, referenceTaskScaleSeconds);
        double maxTrivialCost = Math.min(defaultMaxTrivialCostSeconds, maxAllowedByRatio);

        Map<String, MachineReadiness.Classification> result = new HashMap<>(candidateMachineIds.size() * 2);
        Set<String> lightAvailableMachines = new HashSet<>(ownedIds);
        Set<MachineCapabilityId> lightAvailableCaps = new HashSet<>(ownedCaps);

        for (String mid : candidateMachineIds) {
            if (ownedIds.contains(mid)) {
                result.put(mid, new MachineReadiness.Classification(
                    mid,
                    MachineReadiness.owned,
                    0,
                    0.0,
                    MachineReadiness.UncertaintyTag.certifiedOwned
                ));
            }
        }

        // Iteratively promote machines whose verified build sub-plan uses only already-available light facilities.
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ < defaultMaxTrivialSteps) {
            changed = false;
            for (String mid : candidateMachineIds) {
                if (result.containsKey(mid)) {
                    continue;
                }
                BuildEstimate est = evaluateShallowBuild(
                    graph,
                    ResourceKey.item(mid),
                    baselineUnitPrices,
                    lightAvailableMachines,
                    lightAvailableCaps,
                    defaultMaxTrivialSteps,
                    new HashSet<>()
                );
                if (est != null && est.steps <= defaultMaxTrivialSteps && est.costSeconds <= maxTrivialCost + 1e-9) {
                    result.put(mid, new MachineReadiness.Classification(
                        mid,
                        MachineReadiness.trivialToBuild,
                        est.steps,
                        est.costSeconds,
                        MachineReadiness.UncertaintyTag.knownBuild
                    ));
                    lightAvailableMachines.add(mid);
                    changed = true;
                }
            }
        }

        // Classify remaining machines as heavyInvestment with honest uncertainty tag.
        for (String mid : candidateMachineIds) {
            if (result.containsKey(mid)) {
                continue;
            }
            ResourceKey mKey = ResourceKey.item(mid);
            Double basePrice = baselineUnitPrices.get(mKey);
            boolean hasRecipe = !graph.producersOf(mKey).isEmpty();
            MachineReadiness.UncertaintyTag tag = (basePrice != null && Double.isFinite(basePrice))
                    ? MachineReadiness.UncertaintyTag.knownBuild
                    : (hasRecipe ? MachineReadiness.UncertaintyTag.unknownOwnership : MachineReadiness.UncertaintyTag.noBuildCert);
            result.put(mid, new MachineReadiness.Classification(
                mid,
                MachineReadiness.heavyInvestment,
                hasRecipe ? defaultMaxTrivialSteps + 1 : Integer.MAX_VALUE,
                basePrice != null && Double.isFinite(basePrice) ? basePrice : Double.POSITIVE_INFINITY,
                tag
            ));
        }

        return Collections.unmodifiableMap(result);
    }

    private record BuildEstimate(int steps, double costSeconds) {}

    private static @Nullable BuildEstimate evaluateShallowBuild(
        CompiledRecipeGraph graph,
        ResourceKey target,
        Map<ResourceKey, Double> baselineUnitPrices,
        Set<String> lightMachines,
        Set<MachineCapabilityId> lightCaps,
        int depthBudget,
        Set<ResourceKey> visiting
    ) {
        if (depthBudget <= 0 || !visiting.add(target)) {
            return null;
        }
        try {
            BuildEstimate best = null;
            for (CompiledRecipe rec : graph.producersOf(target)) {
                if (rec.isSuspiciousJei()) continue;
                boolean machineOk = rec.machineRequirement().handCraftable();
                if (!machineOk) {
                    if (rec.machineRequirement().capabilityId().isPresent()
                            && lightCaps.contains(rec.machineRequirement().capabilityId().get())) {
                        machineOk = true;
                    } else {
                        for (String m : rec.machineRequirement().compatibleMachineIds()) {
                            if (lightMachines.contains(m)) {
                                machineOk = true;
                                break;
                            }
                        }
                    }
                }
                if (!machineOk) {
                    continue;
                }

                double cost = 3.0; // standard action time for one shallow build step
                int steps = 1;
                boolean ok = true;
                for (var slot : rec.inputs()) {
                    double minSlotPrice = Double.POSITIVE_INFINITY;
                    for (ResourceKey cand : slot.candidates()) {
                        Double p = baselineUnitPrices.get(cand);
                        if (p != null && Double.isFinite(p) && p < minSlotPrice) {
                            minSlotPrice = p;
                        }
                    }
                    if (!Double.isFinite(minSlotPrice)) {
                        // Try 1-level shallow sub-build if depth allows.
                        BuildEstimate sub = null;
                        for (ResourceKey cand : slot.candidates()) {
                            BuildEstimate candEst = evaluateShallowBuild(
                                graph,
                                cand,
                                baselineUnitPrices,
                                lightMachines,
                                lightCaps,
                                depthBudget - 1,
                                visiting
                            );
                            if (candEst != null && (sub == null || candEst.costSeconds < sub.costSeconds)) {
                                sub = candEst;
                            }
                        }
                        if (sub == null) {
                            ok = false;
                            break;
                        }
                        cost += sub.costSeconds * slot.amountPerBatch();
                        steps += sub.steps;
                    } else {
                        cost += minSlotPrice * slot.amountPerBatch();
                    }
                }
                if (!ok) continue;
                if (best == null || cost < best.costSeconds) {
                    best = new BuildEstimate(steps, cost);
                }
            }
            return best;
        } finally {
            visiting.remove(target);
        }
    }
}

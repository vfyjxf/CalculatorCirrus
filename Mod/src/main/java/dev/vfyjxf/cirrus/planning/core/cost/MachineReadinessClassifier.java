package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.MachineReadiness;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Iterative 3-tier machine readiness classifier (zero wordlists):
 * classifies machines into owned, trivialToBuild (<= 4 steps, <= 45s, <= 0.25 task scale,
 * relying only on owned or shallower trivial facilities), and heavyInvestment.
 */
public final class MachineReadinessClassifier {

    public static final int defaultMaxTrivialSteps = 4;
    public static final double defaultMaxTrivialCostSeconds = 45.0;
    public static final double defaultMaxTrivialTaskRatio = 0.25;
    public static final double defaultTaskFloorSeconds = 10.0;

    private MachineReadinessClassifier() {}

    public static Map<MachineEntry, MachineReadiness.Classification> classifyAll(
        CompiledRecipeGraph graph,
        FacilitySnapshot facilities,
        Map<ComponentStackKey, Double> baselineUnitPrices,
        double referenceTaskScaleSeconds,
        Set<MachineEntry> candidateMachines
    ) {
        return classifyAll(graph, facilities, baselineUnitPrices, referenceTaskScaleSeconds, candidateMachines, Map.of());
    }

    public static Map<MachineEntry, MachineReadiness.Classification> classifyAll(
        CompiledRecipeGraph graph,
        FacilitySnapshot facilities,
        Map<ComponentStackKey, Double> baselineUnitPrices,
        double referenceTaskScaleSeconds,
        Set<MachineEntry> candidateMachines,
        Map<MachineEntry, Set<MachineCapabilityId>> capabilitiesByMachine
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(baselineUnitPrices, "baselineUnitPrices");
        Objects.requireNonNull(candidateMachines, "candidateMachines");
        Objects.requireNonNull(capabilitiesByMachine, "capabilitiesByMachine");

        Set<MachineEntry> ownedMachines = new HashSet<>();
        Set<MachineCapabilityId> ownedCaps = new HashSet<>();
        for (OwnedMachineGroup g : facilities.ownedMachines()) {
            if (g.count() > 0) {
                ownedMachines.add(g.machine());
                ownedCaps.add(g.capability());
            }
        }

        double maxAllowedByRatio = defaultMaxTrivialTaskRatio * Math.max(defaultTaskFloorSeconds, referenceTaskScaleSeconds);
        double maxTrivialCost = Math.min(defaultMaxTrivialCostSeconds, maxAllowedByRatio);

        Map<MachineEntry, MachineReadiness.Classification> result = new HashMap<>(candidateMachines.size() * 2);
        Set<MachineEntry> lightAvailableMachines = new HashSet<>(ownedMachines);
        Set<MachineCapabilityId> lightAvailableCaps = new HashSet<>(ownedCaps);

        for (MachineEntry machine : candidateMachines) {
            if (ownedMachines.contains(machine) || facilities.ownsMachineFor(machine)) {
                result.put(
                    machine, new MachineReadiness.Classification(
                        machine,
                        MachineReadiness.owned,
                        0,
                        0.0,
                        MachineReadiness.UncertaintyTag.certifiedOwned
                    )
                );
                lightAvailableMachines.add(machine);
                Set<MachineCapabilityId> caps = capabilitiesByMachine.get(machine);
                if (caps != null) {
                    lightAvailableCaps.addAll(caps);
                }
            }
        }

        // Iteratively promote machines whose verified build sub-plan uses only already-available light facilities.
        boolean changed = true;
        int rounds = 0;
        while (changed && rounds++ < defaultMaxTrivialSteps) {
            changed = false;
            for (MachineEntry machine : candidateMachines) {
                if (result.containsKey(machine)) {
                    continue;
                }
                Optional<ComponentStackKey> mKeyOpt = machine.contentKey();
                if (mKeyOpt.isEmpty()) {
                    continue;
                }
                BuildEstimate est = evaluateShallowBuild(
                    graph,
                    mKeyOpt.get(),
                    baselineUnitPrices,
                    lightAvailableMachines,
                    lightAvailableCaps,
                    defaultMaxTrivialSteps,
                    new HashSet<>()
                );
                if (est != null && est.steps <= defaultMaxTrivialSteps && est.costSeconds <= maxTrivialCost + 1e-9) {
                    result.put(
                        machine, new MachineReadiness.Classification(
                            machine,
                            MachineReadiness.trivialToBuild,
                            est.steps,
                            est.costSeconds,
                            MachineReadiness.UncertaintyTag.knownBuild
                        )
                    );
                    lightAvailableMachines.add(machine);
                    Set<MachineCapabilityId> caps = capabilitiesByMachine.get(machine);
                    if (caps != null) {
                        lightAvailableCaps.addAll(caps);
                    }
                    changed = true;
                }
            }
        }

        // Classify remaining machines as heavyInvestment with honest uncertainty tag.
        for (MachineEntry machine : candidateMachines) {
            if (result.containsKey(machine)) {
                continue;
            }
            Optional<ComponentStackKey> mKeyOpt = machine.contentKey();
            Double basePrice = mKeyOpt.map(baselineUnitPrices::get).orElse(null);
            boolean hasRecipe = mKeyOpt.isPresent() && !graph.producersOf(mKeyOpt.get()).isEmpty();
            MachineReadiness.UncertaintyTag tag = (basePrice != null && Double.isFinite(basePrice))
                    ? MachineReadiness.UncertaintyTag.knownBuild
                    : (hasRecipe ? MachineReadiness.UncertaintyTag.unknownOwnership : MachineReadiness.UncertaintyTag.noBuildCert);
            result.put(
                machine, new MachineReadiness.Classification(
                    machine,
                    MachineReadiness.heavyInvestment,
                    hasRecipe ? defaultMaxTrivialSteps + 1 : Integer.MAX_VALUE,
                    basePrice != null && Double.isFinite(basePrice) ? basePrice : Double.POSITIVE_INFINITY,
                    tag
                )
            );
        }

        return Collections.unmodifiableMap(result);
    }

    private record BuildEstimate(int steps, double costSeconds) {}

    private static @Nullable BuildEstimate evaluateShallowBuild(
        CompiledRecipeGraph graph,
        ComponentStackKey target,
        Map<ComponentStackKey, Double> baselineUnitPrices,
        Set<MachineEntry> lightMachines,
        Set<MachineCapabilityId> lightCaps,
        int depthBudget,
        Set<ComponentStackKey> visiting
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
                        for (MachineEntry m : rec.machineRequirement().compatibleMachines()) {
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

                long outAmt = 0L;
                for (OutputSlot out : rec.outputs()) {
                    if (out.resource().equals(target)) {
                        outAmt += out.amountPerBatch();
                    }
                }
                if (outAmt <= 0L) {
                    continue;
                }

                double batchCost = 3.0; // standard action time for one shallow build step
                int steps = 1;
                boolean ok = true;
                for (var slot : rec.inputs()) {
                    double minSlotPrice = Double.POSITIVE_INFINITY;
                    for (ComponentStackKey cand : slot.candidates()) {
                        Double p = baselineUnitPrices.get(cand);
                        if (p != null && Double.isFinite(p) && p < minSlotPrice) {
                            minSlotPrice = p;
                        }
                    }
                    if (!Double.isFinite(minSlotPrice)) {
                        BuildEstimate sub = null;
                        for (ComponentStackKey cand : slot.candidates()) {
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
                        batchCost += sub.costSeconds * slot.amountPerBatch();
                        steps += sub.steps;
                    } else {
                        batchCost += minSlotPrice * slot.amountPerBatch();
                    }
                }
                if (!ok) continue;
                double unitCost = batchCost / outAmt;
                if (best == null || unitCost < best.costSeconds) {
                    best = new BuildEstimate(steps, unitCost);
                }
            }
            return best;
        } finally {
            visiting.remove(target);
        }
    }
}

package dev.vfyjxf.cirrus.planning.offline;

import com.google.gson.JsonObject;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ChainMetrics(
    int actions,
    int crafts,
    int distinctRecipes,
    int rawKinds,
    int machines,
    int depth,
    int advisoryInPath,
    int discouragedInPath,
    int lastResortInPath,
    int roundTrips,
    int poolAmplification,
    int rawNonLeaf,
    int dormantAcquires,
    int unmet
) {

    public static ChainMetrics compute(ProductionPlan plan, CompiledRecipeGraph graph, PlanningRequest request) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(request, "request");

        List<PlanStep> steps = plan.steps();
        int actions = steps.size();
        int crafts = 0;
        Set<CompiledRecipeKey> recipesSeen = new HashSet<>();
        Set<ComponentStackKey> rawResources = new HashSet<>();
        int machines = 0;
        int discouraged = 0;
        int lastResort = 0;
        int rawNonLeaf = 0;
        int dormantAcquires = 0;

        Map<String, Integer> depthByStep = new HashMap<>(steps.size() * 2 + 1);
        int maxDepth = 0;

        Set<SinglePair> forwardConversions = new HashSet<>();
        Map<ComponentStackKey, Long> convIn = new HashMap<>();
        Map<ComponentStackKey, Long> convOut = new HashMap<>();

        HumanGuidanceView guidance = request.context().guidance();
        FacilitySnapshot facilities = request.context().facilities();

        for (PlanStep step : steps) {
            int stepDepth = 1;
            for (String dep : step.dependsOnStepIds()) {
                stepDepth = Math.max(stepDepth, depthByStep.getOrDefault(dep, 0) + 1);
            }
            depthByStep.put(step.stepId(), stepDepth);
            if (stepDepth > maxDepth) {
                maxDepth = stepDepth;
            }

            switch (step) {
                case AcquireStep acq -> {
                    rawResources.add(acq.resource());
                    if (!guidance.isResourceTreatedAsBase(acq.resource())) {
                        List<CompiledRecipe> producers = graph.producersOf(acq.resource());
                        if (!producers.isEmpty()) {
                            boolean anyActive = false;
                            boolean anyOptInLocked = false;
                            for (CompiledRecipe producer : producers) {
                                Namespace sys = producer.key().systemId();
                                SystemAdmission adm = graph.admissionOf(sys);
                                boolean unlocked = facilities.unlockedSystems().contains(sys);
                                boolean sysEnabled = guidance.isSystemEnabled(sys, adm, unlocked);
                                boolean banned = guidance.isRecipeBanned(producer.key())
                                        || guidance.isRecipeBannedForTarget(acq.resource(), producer.key());
                                if (sysEnabled && !banned) {
                                    anyActive = true;
                                    break;
                                }
                                if (adm == SystemAdmission.optInOnly && !unlocked) {
                                    anyOptInLocked = true;
                                }
                            }
                            if (anyActive) {
                                rawNonLeaf++;
                            } else if (anyOptInLocked) {
                                dormantAcquires++;
                            }
                        }
                    }
                }
                case ProvisionMachineStep prov -> machines += prov.countToBuild();
                case SetupLineStep ignored -> machines += 1;
                case LinePullStep ignored -> {}
                case CraftStep craft -> {
                    crafts++;
                    recipesSeen.add(craft.recipeKey());
                    Optional<CompiledRecipe> recOpt = graph.recipeByKey(craft.recipeKey());
                    if (recOpt.isPresent()) {
                        CompiledRecipe rec = recOpt.get();
                        if (rec.advisory() == RecipeAdvisory.discouraged) {
                            discouraged++;
                        } else if (rec.advisory() == RecipeAdvisory.lastResort) {
                            lastResort++;
                        }
                        if (rec.inputs().size() == 1
                                && rec.outputs().size() == 1
                                && craft.slotBindings().containsKey(rec.inputs().get(0).slotIndex())) {
                            ComponentStackKey inKey = craft.slotBindings().get(rec.inputs().get(0).slotIndex())
                                    .chosenResource();
                            ComponentStackKey outKey = rec.outputs().get(0).resource();
                            if (!inKey.equals(outKey)) {
                                forwardConversions.add(new SinglePair(inKey, outKey));
                            }
                        }
                    }
                }
                case ConvertStep conv -> {
                    crafts++;
                    recipesSeen.add(conv.conversionRecipeKey());
                    Optional<CompiledRecipe> recOpt = graph.recipeByKey(conv.conversionRecipeKey());
                    if (recOpt.isPresent()) {
                        CompiledRecipe rec = recOpt.get();
                        if (rec.advisory() == RecipeAdvisory.discouraged) {
                            discouraged++;
                        } else if (rec.advisory() == RecipeAdvisory.lastResort) {
                            lastResort++;
                        }
                        if (rec.inputs().size() == 1
                                && rec.outputs().size() == 1
                                && conv.slotBindings().containsKey(rec.inputs().get(0).slotIndex())) {
                            ComponentStackKey inKey = conv.slotBindings().get(rec.inputs().get(0).slotIndex())
                                    .chosenResource();
                            ComponentStackKey outKey = rec.outputs().get(0).resource();
                            if (!inKey.equals(outKey)) {
                                forwardConversions.add(new SinglePair(inKey, outKey));
                            }
                            convIn.merge(inKey, rec.inputs().get(0).amountPerBatch() * conv.batches(), Long::sum);
                            convOut.merge(outKey, rec.outputs().get(0).amountPerBatch() * conv.batches(), Long::sum);
                        }
                    }
                }
                case CycleLoopStep loop -> {
                    for (CraftStep inner : loop.singleIterationTemplate()) {
                        crafts++;
                        recipesSeen.add(inner.recipeKey());
                        Optional<CompiledRecipe> recOpt = graph.recipeByKey(inner.recipeKey());
                        if (recOpt.isPresent()) {
                            RecipeAdvisory adv = recOpt.get().advisory();
                            if (adv == RecipeAdvisory.discouraged) {
                                discouraged++;
                            } else if (adv == RecipeAdvisory.lastResort) {
                                lastResort++;
                            }
                        }
                    }
                }
            }
        }

        int roundTrips = 0;
        Set<SinglePair> countedPairs = new HashSet<>();
        for (SinglePair pair : forwardConversions) {
            SinglePair reverse = new SinglePair(pair.to, pair.from);
            if (forwardConversions.contains(reverse) && !countedPairs.contains(reverse)) {
                countedPairs.add(pair);
                roundTrips++;
            }
        }

        int poolAmplification = 0;
        for (var e : convOut.entrySet()) {
            long inAmt = convIn.getOrDefault(e.getKey(), 0L);
            if (inAmt > 0L && e.getValue() > inAmt) {
                poolAmplification++;
            }
        }

        int unmet = plan.certificate().valid() ? 0 : 1;
        return new ChainMetrics(
            actions,
            crafts,
            recipesSeen.size(),
            rawResources.size(),
            machines,
            maxDepth,
            discouraged + lastResort,
            discouraged,
            lastResort,
            roundTrips,
            poolAmplification,
            rawNonLeaf,
            dormantAcquires,
            unmet
        );
    }

    public JsonObject toJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("actions", actions);
        obj.addProperty("advisory_in_path", advisoryInPath);
        obj.addProperty("crafts", crafts);
        obj.addProperty("depth", depth);
        obj.addProperty("discouraged_in_path", discouragedInPath);
        obj.addProperty("distinct_recipes", distinctRecipes);
        obj.addProperty("dormant_acquires", dormantAcquires);
        obj.addProperty("last_resort_in_path", lastResortInPath);
        obj.addProperty("machines", machines);
        obj.addProperty("pool_amplification", poolAmplification);
        obj.addProperty("raw_kinds", rawKinds);
        obj.addProperty("raw_nonleaf", rawNonLeaf);
        obj.addProperty("round_trips", roundTrips);
        obj.addProperty("unmet", unmet);
        return obj;
    }

    private record SinglePair(ComponentStackKey from, ComponentStackKey to) {}
}

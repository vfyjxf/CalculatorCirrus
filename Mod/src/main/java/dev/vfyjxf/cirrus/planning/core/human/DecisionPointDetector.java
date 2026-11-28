package dev.vfyjxf.cirrus.planning.core.human;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.MachineReadiness;
import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.QuerySubgraph;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.PromptSuppressionLedger;
import dev.vfyjxf.cirrus.api.planning.human.QuestionKey;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.human.VoIEstimate;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Value-of-Information gated human-in-the-loop decision prompt detector (zero wordlists).
 *
 * <p>Surfaces unobservable context gaps that materially change the optimal plan:
 * - machineAvailability: heavy machine ownership / build / skip-system (default worldSave)
 * - baseOrSupplyBoundary: high-share intermediate or bulk resource stop-here boundary (default worldSave)
 * - reusableCatalystCheck: non-consumable mold/template/seed ownership check (default worldSave)
 * - materialFamilyAnchor: multi-family high-fanout Tag anchor (default worldSave)
 * - suspiciousJeiVerify: low-fidelity / unverified JEI recipe confirmation + system ban (default modpackDefault)
 * - fieldTripConfirm: high-share manual field-trip gathering confirmation (default worldSave)
 */
public final class DecisionPointDetector {

    private static final double minLeverage = 0.25;

    private final CostMetadataProvider costMetadata;

    public DecisionPointDetector() {
        this(DefaultCostMetadataProvider.instance);
    }

    public DecisionPointDetector(CostMetadataProvider costMetadata) {
        this.costMetadata = Objects.requireNonNull(costMetadata, "costMetadata");
    }

    public List<DecisionPrompt> detect(
        PlanningRequest request,
        List<ProductionPlan> plans,
        QuerySubgraph subgraph,
        CompiledRecipeGraph graph
    ) {
        return detect(request, plans, subgraph, graph, Set.of(), new PromptSuppressionLedger(), 0L, Map.of());
    }

    public List<DecisionPrompt> detect(
        PlanningRequest request,
        List<ProductionPlan> plans,
        QuerySubgraph subgraph,
        CompiledRecipeGraph graph,
        Set<String> dismissedPromptIds
    ) {
        return detect(request, plans, subgraph, graph, dismissedPromptIds, new PromptSuppressionLedger(), 0L, Map.of());
    }

    public List<DecisionPrompt> detect(
        PlanningRequest request,
        List<ProductionPlan> plans,
        QuerySubgraph subgraph,
        CompiledRecipeGraph graph,
        Set<String> dismissedPromptIds,
        PromptSuppressionLedger suppressionLedger,
        long currentQuerySeq,
        Map<MachineEntry, MachineReadiness.Classification> machineClassifications
    ) {
        int maxPrompts = request.budget().maxDecisionPrompts();
        if (maxPrompts <= 0 || plans == null || plans.isEmpty()) {
            return List.of();
        }
        Set<String> dismissed = dismissedPromptIds == null ? Set.of() : dismissedPromptIds;
        HumanGuidanceView guidance = request.context().guidance();
        Map<String, DecisionPrompt> byId = new LinkedHashMap<>();
        double queryScaleA = Math.max(10.0, plans.get(0).scoreBreakdown().queryScaleA());

        detectMachineAvailability(plans, guidance, dismissed, machineClassifications, queryScaleA, byId);
        detectSupplyBoundary(plans.get(0), guidance, dismissed, queryScaleA, byId);
        detectReusableCatalyst(plans.get(0), graph, guidance, dismissed, queryScaleA, byId);
        detectTagDefault(request, plans.get(0), graph, guidance, dismissed, queryScaleA, byId);
        detectUnverifiedRecipe(plans.get(0), graph, guidance, dismissed, queryScaleA, byId);
        detectFieldTrip(plans.get(0), guidance, dismissed, queryScaleA, byId);

        List<DecisionPrompt> filtered = new ArrayList<>(byId.size());
        for (DecisionPrompt p : byId.values()) {
            if (p.leverageScore() < minLeverage
                    || p.voiEstimate().netEvsiSeconds() <= 0.0
                    || dismissed.contains(p.promptId())) {
                continue;
            }
            if (suppressionLedger != null && suppressionLedger.isSuppressed(p.questionKey(), currentQuerySeq)) {
                continue;
            }
            filtered.add(p);
        }
        filtered.sort(
            Comparator.comparingDouble((DecisionPrompt p) -> p.voiEstimate().netEvsiSeconds()).reversed()
                    .thenComparing(Comparator.comparingDouble(DecisionPrompt::leverageScore).reversed())
                    .thenComparingInt(p -> p.kind().ordinal())
                    .thenComparing(DecisionPrompt::promptId)
        );
        if (filtered.size() > maxPrompts) {
            return List.copyOf(filtered.subList(0, maxPrompts));
        }
        return List.copyOf(filtered);
    }

    /** Heavy machine availability confirmation (skips owned and trivialToBuild machines). */
    private void detectMachineAvailability(
        List<ProductionPlan> plans,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        Map<MachineEntry, MachineReadiness.Classification> readinessMap,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        ProductionPlan primary = plans.get(0);
        // Collect all newly provisioned machines across primary & alternative plans and map to their recipe system.
        Map<MachineEntry, Namespace> machineToSystem = new LinkedHashMap<>();
        for (ProductionPlan plan : plans) {
            for (CraftStep cs : allCraftSteps(plan)) {
                cs.chosenMachine().ifPresent(m -> machineToSystem.putIfAbsent(m, cs.recipeKey().systemId()));
            }
        }
        Map<MachineEntry, @Nullable Namespace> provisionedToSystem = new LinkedHashMap<>();
        for (ProductionPlan plan : plans) {
            for (PlanStep step : plan.steps()) {
                if (step instanceof ProvisionMachineStep pm) {
                    provisionedToSystem.putIfAbsent(pm.machine(), machineToSystem.get(pm.machine()));
                }
            }
        }
        for (var entry : provisionedToSystem.entrySet()) {
            MachineEntry machine = entry.getKey();
            @Nullable Namespace systemId = entry.getValue();
            if (isMachineAnswered(guidance, machine)) continue;
            MachineReadiness.Classification cls = readinessMap != null ? readinessMap.get(machine) : null;
            if (cls != null && cls.readiness() != MachineReadiness.heavyInvestment) {
                continue;
            }
            String machineKey = machine.canonicalKey();
            String promptId = "prompt:machine:" + machineKey;
            if (dismissed.contains(promptId)) continue;

            double setupCost = 60.0;
            for (PlanStep step : primary.steps()) {
                if (step instanceof ProvisionMachineStep pm && pm.machine().equals(machine)) {
                    setupCost = Math.max(setupCost, pm.stepCost().totalActiveSeconds() + pm.stepCost().machineSetupCapital());
                }
            }
            double altCost = setupCost * 0.65;
            VoIEstimate voi = VoICounterfactualEngine.evaluateExact(
                new double[]{0.5, 0.5},
                new double[][]{
                    {0.0, setupCost},
                    {altCost, altCost}
                },
                VoICounterfactualEngine.defaultAskCostSeconds,
                queryScaleA
            );
            double leverage = SafeMath.clamp01(Math.max(0.45, voi.grossEvsiSeconds() / Math.max(10.0, setupCost)));
            CostVector delta = new CostVector(setupCost * 0.5, 0.0, 0.0, setupCost * 0.5, 0.0, 1.0);

            List<DecisionOption> options = new ArrayList<>(4);
            options.add(new DecisionOption(
                "already_owned",
                "cirrus.prompt.machine_availability.option.already_owned",
                delta,
                List.of(),
                Optional.of(new DeclareOwnedMachineFeedback(machine, 1, true))
            ));
            options.add(new DecisionOption(
                "accept_build",
                "cirrus.prompt.machine_availability.option.accept_build",
                CostVector.zero,
                List.of(new MachineRule("rule:build:" + machineKey, GuidanceScope.session, RuleStrength.softPrefer, machine, true))
            ));
            options.add(new DecisionOption(
                "reject_machine",
                "cirrus.prompt.machine_availability.option.reject_machine",
                CostVector.zero,
                List.of(new MachineRule("rule:nobuild:" + machineKey, GuidanceScope.worldSave, RuleStrength.hardBan, machine, false))
            ));
            if (systemId != null) {
                options.add(new DecisionOption(
                    "skip_system",
                    "cirrus.prompt.machine_availability.option.skip_system",
                    CostVector.zero,
                    List.of(new SystemRule("rule:skip_sys:" + systemId, GuidanceScope.worldSave, RuleStrength.softPenalize, systemId, false))
                ));
            }
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.machineAvailability, machineKey, GuidanceScope.worldSave, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.machineAvailability,
                "cirrus.prompt.machine_availability.title",
                "cirrus.prompt.machine_availability.explanation",
                leverage,
                voi,
                GuidanceScope.worldSave,
                List.copyOf(options)
            ));
        }
    }

    /** Base stop-here boundary / automated supply confirmation for high-cost leaves or intermediates. */
    private void detectSupplyBoundary(
        ProductionPlan primary,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        double totalActive = Math.max(1e-6, primary.totalCost().totalActiveSeconds());
        double totalScarcity = Math.max(1e-6, primary.totalCost().materialScarcity());

        for (PlanStep step : primary.steps()) {
            if (!(step instanceof AcquireStep a)) continue;
            if (guidance.isResourceTreatedAsBase(a.resource()) || isResourceAnswered(guidance, a.resource())) {
                continue;
            }
            double share = Math.max(
                a.stepCost().totalActiveSeconds() / totalActive,
                a.stepCost().materialScarcity() / totalScarcity
            );
            boolean significant = a.stepCost().materialScarcity() >= 12.0 || a.stepCost().totalActiveSeconds() >= 18.0;
            if (share < 0.35 || (!significant && primary.steps().size() < 4)) {
                continue;
            }
            String resKey = a.resource().canonicalKey();
            String promptId = "prompt:supply:" + resKey;
            if (dismissed.contains(promptId)) continue;

            double stepSec = a.stepCost().totalActiveSeconds() + a.stepCost().materialScarcity();
            VoIEstimate voi = VoICounterfactualEngine.evaluateExact(
                new double[]{0.45, 0.55},
                new double[][]{
                    {1.0, stepSec},
                    {stepSec * 0.7, stepSec * 0.7}
                },
                VoICounterfactualEngine.defaultAskCostSeconds,
                queryScaleA
            );
            List<DecisionOption> options = List.of(
                new DecisionOption(
                    "set_world_base",
                    "cirrus.prompt.supply_boundary.option.set_base",
                    a.stepCost(),
                    List.of(new ResourceRule("rule:base:" + resKey, GuidanceScope.worldSave, RuleStrength.hardPin, a.resource(), true))
                ),
                new DecisionOption(
                    "avoid_resource",
                    "cirrus.prompt.supply_boundary.option.avoid_resource",
                    CostVector.zero,
                    List.of(new ResourceRule("rule:avoid:" + resKey, GuidanceScope.worldSave, RuleStrength.softPenalize, a.resource(), false))
                ),
                new DecisionOption(
                    "keep_current",
                    "cirrus.prompt.supply_boundary.option.keep_current",
                    CostVector.zero,
                    List.of()
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.baseOrSupplyBoundary, resKey, GuidanceScope.worldSave, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.baseOrSupplyBoundary,
                "cirrus.prompt.supply_boundary.title",
                "cirrus.prompt.supply_boundary.explanation",
                SafeMath.clamp01(share),
                voi,
                GuidanceScope.worldSave,
                options
            ));
        }
    }

    /** Non-consumable mold, template, tool, or catalytic cycle seed confirmation. */
    private void detectReusableCatalyst(
        ProductionPlan primary,
        CompiledRecipeGraph graph,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        for (CraftStep cs : allCraftSteps(primary)) {
            if (cs.catalystBindings().isEmpty()) continue;
            for (ComponentStackKey catRes : cs.catalystBindings().values()) {
                if (guidance.isResourceTreatedAsBase(catRes) || isResourceAnswered(guidance, catRes)) {
                    continue;
                }
                String resKey = catRes.canonicalKey();
                String promptId = "prompt:catalyst:" + resKey;
                if (dismissed.contains(promptId)) continue;
                VoIEstimate voi = VoICounterfactualEngine.evaluateExact(
                    new double[]{0.5, 0.5},
                    new double[][]{
                        {0.0, 30.0},
                        {18.0, 18.0}
                    },
                    VoICounterfactualEngine.defaultAskCostSeconds,
                    queryScaleA
                );
                List<DecisionOption> options = List.of(
                    new DecisionOption(
                        "already_own_catalyst",
                        "cirrus.prompt.reusable_catalyst.option.already_own",
                        new CostVector(15.0, 0.0, 0.0, 0.0, 0.0, 1.0),
                        List.of(new ResourceRule("rule:cat_own:" + resKey, GuidanceScope.worldSave, RuleStrength.hardPin, catRes, true))
                    ),
                    new DecisionOption(
                        "craft_with_plan",
                        "cirrus.prompt.reusable_catalyst.option.craft_with_plan",
                        CostVector.zero,
                        List.of()
                    )
                );
                QuestionKey qKey = new QuestionKey(DecisionPromptKind.reusableCatalystCheck, resKey, GuidanceScope.worldSave, 0L);
                mergeBest(out, new DecisionPrompt(
                    promptId,
                    qKey,
                    DecisionPromptKind.reusableCatalystCheck,
                    "cirrus.prompt.reusable_catalyst.title",
                    "cirrus.prompt.reusable_catalyst.explanation",
                    0.60,
                    voi,
                    GuidanceScope.worldSave,
                    options
                ));
            }
        }
    }

    /** Dominant material family / high-fanout Tag anchor confirmation (worldSave default). */
    private void detectTagDefault(
        PlanningRequest request,
        ProductionPlan primary,
        CompiledRecipeGraph graph,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        double totalScalar = Math.max(1e-6, request.profile().weights().scalarize(primary.totalCost()));
        for (CraftStep cs : allCraftSteps(primary)) {
            Optional<CompiledRecipe> recOpt = graph.recipeByKey(cs.recipeKey());
            if (recOpt.isEmpty()) continue;
            CompiledRecipe rec = recOpt.get();
            double stepShare = SafeMath.clamp01(request.profile().weights().scalarize(cs.stepCost()) / totalScalar);
            for (InputSlot slot : rec.inputs()) {
                if (slot.tagKey().isEmpty() || slot.candidates().size() < 6) continue;
                TagKey<?> tag = slot.tagKey().get();
                if (guidance.preferredTagMember(tag).isPresent()) continue;
                String tagStr = PlanningTagKeys.canonicalString(tag);
                String promptId = "prompt:tag:" + tagStr;
                if (dismissed.contains(promptId)) continue;

                double logTerm = Math.min(1.0, (StrictMath.log(slot.candidates().size()) / StrictMath.log(2.0)) / 6.0);
                double leverage = SafeMath.clamp01(Math.max(minLeverage, logTerm * (0.60 + 0.40 * stepShare)));
                LinkedHashSet<ComponentStackKey> picks = new LinkedHashSet<>();
                SlotBinding binding = cs.slotBindings().get(slot.slotIndex());
                if (binding != null) picks.add(binding.chosenResource());
                for (ComponentStackKey cand : slot.candidates()) {
                    if (picks.size() >= 3) break;
                    if (request.context().inventory().usableAmount(cand) > 0L) picks.add(cand);
                }
                for (ComponentStackKey cand : slot.candidates()) {
                    if (picks.size() >= 3) break;
                    picks.add(cand);
                }
                if (picks.size() < 2) continue;

                VoIEstimate voi = VoICounterfactualEngine.evaluateExact(
                    new double[]{0.5, 0.5},
                    new double[][]{
                        {0.0, 8.0},
                        {8.0, 0.0}
                    },
                    VoICounterfactualEngine.defaultAskCostSeconds,
                    queryScaleA
                );
                List<DecisionOption> options = new ArrayList<>(picks.size());
                int idx = 0;
                for (ComponentStackKey member : picks) {
                    options.add(new DecisionOption(
                        "pick_" + idx++ + ":" + member.canonicalKey(),
                        "cirrus.prompt.tag_default.option.anchor_world",
                        CostVector.zero,
                        List.of(new TagDefaultRule("rule:tag:" + tagStr, GuidanceScope.worldSave, RuleStrength.hardPin, tag, member))
                    ));
                }
                QuestionKey qKey = new QuestionKey(DecisionPromptKind.materialFamilyAnchor, tagStr, GuidanceScope.worldSave, 0L);
                mergeBest(out, new DecisionPrompt(
                    promptId,
                    qKey,
                    DecisionPromptKind.materialFamilyAnchor,
                    "cirrus.prompt.tag_default.title",
                    "cirrus.prompt.tag_default.explanation",
                    leverage,
                    voi,
                    GuidanceScope.worldSave,
                    options
                ));
            }
        }
    }

    /** Low-fidelity / unverified JEI recipe confirmation + one-click system ban (modpackDefault scope). */
    private void detectUnverifiedRecipe(
        ProductionPlan primary,
        CompiledRecipeGraph graph,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        for (CraftStep cs : allCraftSteps(primary)) {
            Optional<CompiledRecipe> recOpt = graph.recipeByKey(cs.recipeKey());
            if (recOpt.isEmpty()) continue;
            CompiledRecipe rec = recOpt.get();
            if (isRecipeAnswered(guidance, rec.key())) continue;
            if (!rec.isSuspiciousJei() && rec.advisory().weight() == 0) continue;

            String promptId = "prompt:unverified:" + rec.key().shortString();
            if (dismissed.contains(promptId)) continue;
            VoIEstimate voi = VoICounterfactualEngine.evaluateExact(
                new double[]{0.4, 0.6},
                new double[][]{
                    {0.0, 40.0},
                    {15.0, 15.0}
                },
                VoICounterfactualEngine.defaultAskCostSeconds,
                queryScaleA
            );
            Namespace sysId = rec.key().systemId();
            List<DecisionOption> options = List.of(
                new DecisionOption(
                    "confirm_trusted",
                    "cirrus.prompt.unverified_recipe.option.confirm_trusted",
                    CostVector.zero,
                    List.of(new RecipeRule("rule:trust_rec:" + rec.key().shortString(), GuidanceScope.modpackDefault, RuleStrength.softPrefer, rec.key()))
                ),
                new DecisionOption(
                    "ban_recipe_pack",
                    "cirrus.prompt.unverified_recipe.option.ban_recipe",
                    CostVector.zero,
                    List.of(new RecipeRule("rule:ban_rec:" + rec.key().shortString(), GuidanceScope.modpackDefault, RuleStrength.hardBan, rec.key()))
                ),
                new DecisionOption(
                    "ban_similar_system",
                    "cirrus.prompt.unverified_recipe.option.ban_system",
                    CostVector.zero,
                    List.of(new SystemRule("rule:ban_sys:" + sysId, GuidanceScope.modpackDefault, RuleStrength.hardBan, sysId, false))
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.suspiciousJeiVerify, rec.key().shortString(), GuidanceScope.modpackDefault, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.suspiciousJeiVerify,
                "cirrus.prompt.unverified_recipe.title",
                "cirrus.prompt.unverified_recipe.explanation",
                rec.isSuspiciousJei() ? 0.85 : 0.55,
                voi,
                GuidanceScope.modpackDefault,
                options
            ));
        }
    }

    /** High-share manual field-trip gathering confirmation. */
    private void detectFieldTrip(
        ProductionPlan primary,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        double totalActive = primary.totalCost().totalActiveSeconds();
        if (totalActive <= 1e-6 || primary.totalCost().tripActiveSeconds() / totalActive < 0.35) {
            return;
        }
        for (PlanStep step : primary.steps()) {
            if (!(step instanceof AcquireStep a) || a.acquisitionKind() != AcquisitionKind.manualGather) continue;
            if (isResourceAnswered(guidance, a.resource()) || guidance.isResourceTreatedAsBase(a.resource())) continue;
            double tripSec = a.stepCost().tripActiveSeconds();
            if (tripSec < 15.0) continue;
            String resKey = a.resource().canonicalKey();
            String promptId = "prompt:trip:" + resKey;
            if (dismissed.contains(promptId)) continue;

            VoIEstimate voi = VoICounterfactualEngine.evaluateExact(
                new double[]{0.5, 0.5},
                new double[][]{
                    {tripSec, tripSec * 3.0},
                    {tripSec * 1.6, tripSec * 1.6}
                },
                VoICounterfactualEngine.defaultAskCostSeconds,
                queryScaleA
            );
            List<DecisionOption> options = List.of(
                new DecisionOption(
                    "forbid_gather",
                    "cirrus.prompt.field_trip.option.stay_in_base",
                    CostVector.zero,
                    List.of(new ResourceRule("rule:no_trip:" + resKey, GuidanceScope.worldSave, RuleStrength.hardBan, a.resource(), false))
                ),
                new DecisionOption(
                    "set_base_supply",
                    "cirrus.prompt.field_trip.option.already_base",
                    a.stepCost(),
                    List.of(new ResourceRule("rule:base:" + resKey, GuidanceScope.worldSave, RuleStrength.hardPin, a.resource(), true))
                ),
                new DecisionOption(
                    "accept_trip",
                    "cirrus.prompt.field_trip.option.accept_trip",
                    CostVector.zero,
                    List.of()
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.fieldTripConfirm, resKey, GuidanceScope.worldSave, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.fieldTripConfirm,
                "cirrus.prompt.field_trip.title",
                "cirrus.prompt.field_trip.explanation",
                SafeMath.clamp01(tripSec / totalActive),
                voi,
                GuidanceScope.worldSave,
                options
            ));
        }
    }

    private static List<CraftStep> allCraftSteps(ProductionPlan plan) {
        List<CraftStep> out = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                out.add(cs);
            } else if (step instanceof CycleLoopStep cl) {
                out.addAll(cl.singleIterationTemplate());
            }
        }
        return out;
    }

    private static void mergeBest(Map<String, DecisionPrompt> out, DecisionPrompt prompt) {
        DecisionPrompt prev = out.get(prompt.promptId());
        if (prev == null || prompt.leverageScore() > prev.leverageScore()) {
            out.put(prompt.promptId(), prompt);
        }
    }

    private static boolean isMachineAnswered(HumanGuidanceView guidance, MachineEntry machine) {
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof MachineRule mr && mr.machine().equals(machine)) return true;
        }
        return false;
    }

    private static boolean isResourceAnswered(HumanGuidanceView guidance, ComponentStackKey key) {
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof ResourceRule rr && rr.resource().equals(key)) return true;
        }
        return false;
    }

    private static boolean isRecipeAnswered(HumanGuidanceView guidance, CompiledRecipeKey key) {
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof RecipeRule rr && rr.recipeKey().equals(key)) return true;
        }
        return false;
    }
}

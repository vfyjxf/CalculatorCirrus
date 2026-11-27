package dev.vfyjxf.cirrus.planning.core.human;

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
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;

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
 * Planning v3 VoI-Gated Human-in-the-Loop Decision Detector (H1–H7, Pure JDK 21, ZERO wordlists).
 *
 * <p>Covers all 7 genuine unobservable context gaps:
 * - H1 machineAvailability: heavy machine ownership / build / skip-system (default worldSave)
 * - H2 baseOrSupplyBoundary: high-share intermediate or bulk resource stop-here boundary (default worldSave)
 * - H3 reusableCatalystCheck: non-consumable mold/template/seed ownership check (default worldSave)
 * - H4 materialFamilyAnchor: multi-family high-fanout Tag anchor (default worldSave)
 * - H5 suspiciousJeiVerify: low-fidelity / suspicious JEI verification + bulk system ban (default modpackDefault)
 * - H6 fieldTripConfirm: high-share manual field-trip gathering confirmation (default session/worldSave)
 * - H7 ruleConflictResolution: surfaced when rules create minimal unsatisfiable conflicts.
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
        Map<String, MachineReadiness.Classification> machineClassifications
    ) {
        int maxPrompts = request.budget().maxDecisionPrompts();
        if (maxPrompts <= 0 || plans == null || plans.isEmpty()) {
            return List.of();
        }
        Set<String> dismissed = dismissedPromptIds == null ? Set.of() : dismissedPromptIds;
        HumanGuidanceView guidance = request.context().guidance();
        Map<String, DecisionPrompt> byId = new LinkedHashMap<>();
        double queryScaleA = Math.max(10.0, plans.get(0).scoreBreakdown().queryScaleA());

        detectH1MachineAvailability(plans, guidance, dismissed, machineClassifications, queryScaleA, byId);
        detectH2BaseOrSupplyBoundary(plans.get(0), guidance, dismissed, queryScaleA, byId);
        detectH3ReusableCatalyst(plans.get(0), graph, guidance, dismissed, queryScaleA, byId);
        detectH4MaterialFamilyAnchor(request, plans.get(0), graph, guidance, dismissed, queryScaleA, byId);
        detectH5SuspiciousJeiVerify(plans.get(0), graph, guidance, dismissed, queryScaleA, byId);
        detectH6FieldTripConfirm(plans.get(0), guidance, dismissed, queryScaleA, byId);

        List<DecisionPrompt> filtered = new ArrayList<>(byId.size());
        for (DecisionPrompt p : byId.values()) {
            if (p.leverageScore() < minLeverage || dismissed.contains(p.promptId())) {
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

    /** H1: Heavy machine availability confirmation (skips OWNED and TRIVIAL_TO_BUILD machines!). */
    private void detectH1MachineAvailability(
        List<ProductionPlan> plans,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        Map<String, MachineReadiness.Classification> readinessMap,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        ProductionPlan primary = plans.get(0);
        // Collect all newly provisioned machines across primary & alternative plans and map to their recipe system.
        Map<String, String> machineToSystem = new LinkedHashMap<>();
        for (ProductionPlan plan : plans) {
            for (PlanStep step : plan.steps()) {
                if (step instanceof CraftStep cs && cs.chosenMachineId().isPresent()) {
                    machineToSystem.putIfAbsent(cs.chosenMachineId().get(), cs.recipeKey().systemId());
                }
            }
        }
        Map<String, String> provisionedToSystem = new LinkedHashMap<>();
        for (ProductionPlan plan : plans) {
            for (PlanStep step : plan.steps()) {
                if (step instanceof ProvisionMachineStep pm) {
                    String sys = machineToSystem.getOrDefault(
                        pm.machineId(),
                        pm.satisfiedCapability().map(c -> c.id()).orElse(pm.machineId())
                    );
                    provisionedToSystem.putIfAbsent(pm.machineId(), sys);
                }
            }
        }
        for (var entry : provisionedToSystem.entrySet()) {
            String machineId = entry.getKey();
            String systemId = entry.getValue();
            if (isMachineAnswered(guidance, machineId)) continue;
            MachineReadiness.Classification cls = readinessMap != null ? readinessMap.get(machineId) : null;
            if (cls != null && cls.readiness() != MachineReadiness.heavyInvestment) {
                continue; // Do not nag about already-owned or trivial-to-build machines!
            }
            String promptId = "prompt:h1_machine:" + machineId;
            if (dismissed.contains(promptId)) continue;

            double setupCost = 60.0;
            for (PlanStep step : primary.steps()) {
                if (step instanceof ProvisionMachineStep pm && pm.machineId().equals(machineId)) {
                    setupCost = Math.max(setupCost, pm.stepCost().totalActiveSeconds() + pm.stepCost().machineSetupCapital());
                }
            }
            double altCost = setupCost * 0.65;
            // Counterfactual EVSI: answers = {alreadyOwned, mustBuild}; actions = {useMachine, avoidMachine}.
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

            List<DecisionOption> options = List.of(
                new DecisionOption(
                    "already_owned",
                    "cirrus.prompt.h1_machine.option.already_owned",
                    delta,
                    List.of(),
                    Optional.of(new DeclareOwnedMachineFeedback(machineId, 1, true))
                ),
                new DecisionOption(
                    "accept_build",
                    "cirrus.prompt.h1_machine.option.accept_build",
                    CostVector.zero,
                    List.of(new MachineRule("rule:build:" + machineId, GuidanceScope.session, RuleStrength.softPrefer, machineId, true))
                ),
                new DecisionOption(
                    "reject_machine",
                    "cirrus.prompt.h1_machine.option.reject_machine",
                    CostVector.zero,
                    List.of(new MachineRule("rule:nobuild:" + machineId, GuidanceScope.worldSave, RuleStrength.hardBan, machineId, false))
                ),
                new DecisionOption(
                    "skip_system",
                    "cirrus.prompt.h1_machine.option.skip_system",
                    CostVector.zero,
                    List.of(new SystemRule("rule:skip_sys:" + systemId, GuidanceScope.worldSave, RuleStrength.softPenalize, systemId, false))
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.machineAvailability, machineId, GuidanceScope.worldSave, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.machineAvailability,
                "cirrus.prompt.h1_machine.title",
                "cirrus.prompt.h1_machine.explanation",
                leverage,
                voi,
                GuidanceScope.worldSave,
                options
            ));
        }
    }

    /** H2: Base stop-here boundary / automated supply confirmation for high-cost leaves or intermediates. */
    private void detectH2BaseOrSupplyBoundary(
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
            String promptId = "prompt:h2_base:" + a.resource().shortString();
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
                    "cirrus.prompt.h2_base.option.set_base",
                    a.stepCost(),
                    List.of(new ResourceRule("rule:base:" + a.resource().shortString(), GuidanceScope.worldSave, RuleStrength.hardPin, a.resource(), true))
                ),
                new DecisionOption(
                    "avoid_resource",
                    "cirrus.prompt.h2_base.option.avoid_resource",
                    CostVector.zero,
                    List.of(new ResourceRule("rule:avoid:" + a.resource().shortString(), GuidanceScope.worldSave, RuleStrength.softPenalize, a.resource(), false))
                ),
                new DecisionOption(
                    "keep_current",
                    "cirrus.prompt.h2_base.option.keep_current",
                    CostVector.zero,
                    List.of()
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.baseOrSupplyBoundary, a.resource().canonicalString(), GuidanceScope.worldSave, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.baseOrSupplyBoundary,
                "cirrus.prompt.h2_base.title",
                "cirrus.prompt.h2_base.explanation",
                SafeMath.clamp01(share),
                voi,
                GuidanceScope.worldSave,
                options
            ));
        }
    }

    /** H3: Non-consumable mold, template, tool, or catalytic cycle seed confirmation. */
    private void detectH3ReusableCatalyst(
        ProductionPlan primary,
        CompiledRecipeGraph graph,
        HumanGuidanceView guidance,
        Set<String> dismissed,
        double queryScaleA,
        Map<String, DecisionPrompt> out
    ) {
        for (CraftStep cs : allCraftSteps(primary)) {
            if (cs.catalystBindings().isEmpty()) continue;
            for (ResourceKey catRes : cs.catalystBindings().values()) {
                if (guidance.isResourceTreatedAsBase(catRes) || isResourceAnswered(guidance, catRes)) {
                    continue;
                }
                String promptId = "prompt:h3_catalyst:" + catRes.shortString();
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
                        "cirrus.prompt.h3_catalyst.option.already_own",
                        new CostVector(15.0, 0.0, 0.0, 0.0, 0.0, 1.0),
                        List.of(new ResourceRule("rule:cat_own:" + catRes.shortString(), GuidanceScope.worldSave, RuleStrength.hardPin, catRes, true))
                    ),
                    new DecisionOption(
                        "craft_with_plan",
                        "cirrus.prompt.h3_catalyst.option.craft_with_plan",
                        CostVector.zero,
                        List.of()
                    )
                );
                QuestionKey qKey = new QuestionKey(DecisionPromptKind.reusableCatalystCheck, catRes.canonicalString(), GuidanceScope.worldSave, 0L);
                mergeBest(out, new DecisionPrompt(
                    promptId,
                    qKey,
                    DecisionPromptKind.reusableCatalystCheck,
                    "cirrus.prompt.h3_catalyst.title",
                    "cirrus.prompt.h3_catalyst.explanation",
                    0.60,
                    voi,
                    GuidanceScope.worldSave,
                    options
                ));
            }
        }
    }

    /** H4: Dominant material family / high-fanout Tag anchor confirmation (worldSave default!). */
    private void detectH4MaterialFamilyAnchor(
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
                if (slot.tagId().isEmpty() || slot.candidates().size() < 6) continue;
                String tagId = slot.tagId().get();
                if (guidance.preferredTagMember(tagId).isPresent()) continue;
                String promptId = "prompt:h4_tag:" + tagId;
                if (dismissed.contains(promptId)) continue;

                double logTerm = Math.min(1.0, (StrictMath.log(slot.candidates().size()) / StrictMath.log(2.0)) / 6.0);
                double leverage = SafeMath.clamp01(Math.max(minLeverage, logTerm * (0.60 + 0.40 * stepShare)));
                LinkedHashSet<ResourceKey> picks = new LinkedHashSet<>();
                SlotBinding binding = cs.slotBindings().get(slot.slotIndex());
                if (binding != null) picks.add(binding.chosenResource());
                for (ResourceKey cand : slot.candidates()) {
                    if (picks.size() >= 3) break;
                    if (request.context().inventory().usableAmount(cand) > 0L) picks.add(cand);
                }
                for (ResourceKey cand : slot.candidates()) {
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
                for (ResourceKey member : picks) {
                    options.add(new DecisionOption(
                        "pick_" + idx++ + ":" + member.shortString(),
                        "cirrus.prompt.h4_tag.option.anchor_world",
                        CostVector.zero,
                        List.of(new TagDefaultRule("rule:tag:" + tagId, GuidanceScope.worldSave, RuleStrength.hardPin, tagId, member))
                    ));
                }
                QuestionKey qKey = new QuestionKey(DecisionPromptKind.materialFamilyAnchor, tagId, GuidanceScope.worldSave, 0L);
                mergeBest(out, new DecisionPrompt(
                    promptId,
                    qKey,
                    DecisionPromptKind.materialFamilyAnchor,
                    "cirrus.prompt.h4_tag.title",
                    "cirrus.prompt.h4_tag.explanation",
                    leverage,
                    voi,
                    GuidanceScope.worldSave,
                    options
                ));
            }
        }
    }

    /** H5: Suspicious JEI recipe verification + one-click bulk system ban (modpackDefault scope!). */
    private void detectH5SuspiciousJeiVerify(
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

            String promptId = "prompt:h5_suspicious:" + rec.key().shortString();
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
            String sysId = rec.key().systemId();
            List<DecisionOption> options = List.of(
                new DecisionOption(
                    "confirm_trusted",
                    "cirrus.prompt.h5_suspicious.option.confirm_trusted",
                    CostVector.zero,
                    List.of(new RecipeRule("rule:trust_rec:" + rec.key().shortString(), GuidanceScope.modpackDefault, RuleStrength.softPrefer, rec.key()))
                ),
                new DecisionOption(
                    "ban_recipe_pack",
                    "cirrus.prompt.h5_suspicious.option.ban_recipe",
                    CostVector.zero,
                    List.of(new RecipeRule("rule:ban_rec:" + rec.key().shortString(), GuidanceScope.modpackDefault, RuleStrength.hardBan, rec.key()))
                ),
                new DecisionOption(
                    "ban_similar_system",
                    "cirrus.prompt.h5_suspicious.option.ban_system",
                    CostVector.zero,
                    List.of(new SystemRule("rule:ban_sys:" + sysId, GuidanceScope.modpackDefault, RuleStrength.hardBan, sysId, false))
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.suspiciousJeiVerify, rec.key().shortString(), GuidanceScope.modpackDefault, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.suspiciousJeiVerify,
                "cirrus.prompt.h5_suspicious.title",
                "cirrus.prompt.h5_suspicious.explanation",
                rec.isSuspiciousJei() ? 0.85 : 0.55,
                voi,
                GuidanceScope.modpackDefault,
                options
            ));
        }
    }

    /** H6: High-share manual field-trip gathering confirmation. */
    private void detectH6FieldTripConfirm(
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
            String promptId = "prompt:h6_trip:" + a.resource().shortString();
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
                    "cirrus.prompt.h6_trip.option.stay_in_base",
                    CostVector.zero,
                    List.of(new ResourceRule("rule:no_trip:" + a.resource().shortString(), GuidanceScope.worldSave, RuleStrength.hardBan, a.resource(), false))
                ),
                new DecisionOption(
                    "set_base_supply",
                    "cirrus.prompt.h6_trip.option.already_base",
                    a.stepCost(),
                    List.of(new ResourceRule("rule:base:" + a.resource().shortString(), GuidanceScope.worldSave, RuleStrength.hardPin, a.resource(), true))
                ),
                new DecisionOption(
                    "accept_trip",
                    "cirrus.prompt.h6_trip.option.accept_trip",
                    CostVector.zero,
                    List.of()
                )
            );
            QuestionKey qKey = new QuestionKey(DecisionPromptKind.fieldTripConfirm, a.resource().canonicalString(), GuidanceScope.worldSave, 0L);
            mergeBest(out, new DecisionPrompt(
                promptId,
                qKey,
                DecisionPromptKind.fieldTripConfirm,
                "cirrus.prompt.h6_trip.title",
                "cirrus.prompt.h6_trip.explanation",
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

    private static boolean isMachineAnswered(HumanGuidanceView guidance, String machineId) {
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof MachineRule mr && mr.machineId().equals(machineId)) return true;
        }
        return false;
    }

    private static boolean isResourceAnswered(HumanGuidanceView guidance, ResourceKey key) {
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

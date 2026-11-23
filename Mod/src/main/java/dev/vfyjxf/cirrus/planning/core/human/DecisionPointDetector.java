package dev.vfyjxf.cirrus.planning.core.human;

import dev.vfyjxf.cirrus.api.planning.cost.CostMetadataProvider;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.SystemCostTier;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.QuerySubgraph;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
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

public final class DecisionPointDetector {

    private static final double MIN_LEVERAGE = 0.35;

    private final CostMetadataProvider costMetadata;

    public DecisionPointDetector() {
        this(DefaultCostMetadataProvider.INSTANCE);
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
        return detect(request, plans, subgraph, graph, Set.of());
    }

    public List<DecisionPrompt> detect(
            PlanningRequest request,
            List<ProductionPlan> plans,
            QuerySubgraph subgraph,
            CompiledRecipeGraph graph,
            Set<String> dismissedPromptIds
    ) {
        int maxPrompts = request.budget().maxDecisionPrompts();
        if (maxPrompts <= 0 || plans == null || plans.isEmpty()) {
            return List.of();
        }
        Set<String> dismissed = dismissedPromptIds == null ? Set.of() : dismissedPromptIds;
        HumanGuidanceView guidance = request.context().guidance();
        Map<String, DecisionPrompt> byId = new LinkedHashMap<>();

        detectMachineInvestmentForks(plans, guidance, dismissed, byId);
        detectHighFanoutTags(request, plans.get(0), graph, guidance, dismissed, byId);
        detectExpensiveLeaves(plans.get(0), guidance, dismissed, byId);
        detectSuspiciousRecipes(plans.get(0), graph, guidance, dismissed, byId);

        List<DecisionPrompt> filtered = new ArrayList<>(byId.size());
        for (DecisionPrompt p : byId.values()) {
            if (p.leverageScore() >= MIN_LEVERAGE && !dismissed.contains(p.promptId())) {
                filtered.add(p);
            }
        }
        filtered.sort(Comparator
                .comparingDouble(DecisionPrompt::leverageScore).reversed()
                .thenComparingInt(p -> p.kind().ordinal())
                .thenComparing(DecisionPrompt::promptId));
        if (filtered.size() > maxPrompts) {
            return List.copyOf(filtered.subList(0, maxPrompts));
        }
        return List.copyOf(filtered);
    }

    private void detectMachineInvestmentForks(
            List<ProductionPlan> plans,
            HumanGuidanceView guidance,
            Set<String> dismissed,
            Map<String, DecisionPrompt> out
    ) {
        if (plans.size() < 2) {
            return;
        }
        for (int i = 0; i < plans.size(); i++) {
            ProductionPlan a = plans.get(i);
            Map<String, Integer> machA = a.preparation().machinesToProvision();
            if (machA.isEmpty()) continue;
            for (int j = 0; j < plans.size(); j++) {
                if (i == j) continue;
                ProductionPlan b = plans.get(j);
                Map<String, Integer> machB = b.preparation().machinesToProvision();
                double hSave = savingRatio(b.totalCost().humanActiveSeconds(), a.totalCost().humanActiveSeconds());
                double sSave = savingRatio(b.totalCost().materialScarcity(), a.totalCost().materialScarcity());
                if (hSave < 0.30 && sSave < 0.25) {
                    continue;
                }
                double leverage = SafeMath.clamp01(0.4 + 0.6 * Math.max(hSave, sSave));
                CostVector delta = new CostVector(
                        Math.max(0.0, b.totalCost().humanActiveSeconds() - a.totalCost().humanActiveSeconds()),
                        Math.max(0.0, b.totalCost().passiveWaitSeconds() - a.totalCost().passiveWaitSeconds()),
                        Math.max(0.0, a.totalCost().machineSetupCapital() - b.totalCost().machineSetupCapital()),
                        Math.max(0.0, b.totalCost().materialScarcity() - a.totalCost().materialScarcity()),
                        0.0
                );
                for (String machineId : machA.keySet()) {
                    if (machB.containsKey(machineId)) continue;
                    if (isMachineAnswered(guidance, machineId)) continue;
                    String promptId = "prompt:machine_fork:" + machineId;
                    if (dismissed.contains(promptId)) continue;
                    List<DecisionOption> options = List.of(
                            new DecisionOption(
                                    "accept_build",
                                    "cirrus.prompt.machine_fork.option.accept_build",
                                    delta,
                                    List.of(new MachineRule("rule:build:" + machineId,
                                            GuidanceScope.SESSION, RuleStrength.SOFT_PREFER, machineId, true))
                            ),
                            new DecisionOption(
                                    "reject_build",
                                    "cirrus.prompt.machine_fork.option.reject_build",
                                    CostVector.ZERO,
                                    List.of(new MachineRule("rule:nobuild:" + machineId,
                                            GuidanceScope.SESSION, RuleStrength.HARD_BAN, machineId, false))
                            ),
                            new DecisionOption(
                                    "already_owned",
                                    "cirrus.prompt.machine_fork.option.already_owned",
                                    CostVector.ZERO,
                                    List.of(),
                                    Optional.of(new DeclareOwnedMachineFeedback(machineId, 1, false))
                            )
                    );
                    DecisionPrompt prompt = new DecisionPrompt(
                            promptId,
                            DecisionPromptKind.MACHINE_INVESTMENT_FORK,
                            "cirrus.prompt.machine_fork.title",
                            "cirrus.prompt.machine_fork.explanation",
                            leverage,
                            options
                    );
                    mergeBest(out, prompt);
                }
            }
        }
    }

    private void detectHighFanoutTags(
            PlanningRequest request,
            ProductionPlan primary,
            CompiledRecipeGraph graph,
            HumanGuidanceView guidance,
            Set<String> dismissed,
            Map<String, DecisionPrompt> out
    ) {
        double totalScalar = Math.max(1e-6, request.profile().weights().scalarize(primary.totalCost()));
        for (CraftStep cs : allCraftSteps(primary)) {
            Optional<CompiledRecipe> recOpt = graph.recipeByKey(cs.recipeKey());
            if (recOpt.isEmpty()) continue;
            CompiledRecipe rec = recOpt.get();
            double stepShare = SafeMath.clamp01(request.profile().weights().scalarize(cs.stepCost()) / totalScalar);
            for (InputSlot slot : rec.inputs()) {
                if (slot.tagId().isEmpty() || slot.candidates().size() < 6) {
                    continue;
                }
                String tagId = slot.tagId().get();
                if (guidance.preferredTagMember(tagId).isPresent()) {
                    continue;
                }
                String promptId = "prompt:tag_choice:" + tagId;
                if (dismissed.contains(promptId)) continue;

                double logTerm = Math.min(1.0, (Math.log(slot.candidates().size()) / Math.log(2.0)) / 6.0);
                double leverage = SafeMath.clamp01(logTerm * (0.5 + 0.5 * stepShare));
                if (leverage < MIN_LEVERAGE) continue;

                LinkedHashSet<ResourceKey> picks = new LinkedHashSet<>();
                SlotBinding binding = cs.slotBindings().get(slot.slotIndex());
                if (binding != null) {
                    picks.add(binding.chosenResource());
                }
                for (ResourceKey cand : slot.candidates()) {
                    if (picks.size() >= 3) break;
                    if (request.context().inventory().usableAmount(cand) > 0L) {
                        picks.add(cand);
                    }
                }
                for (ResourceKey cand : slot.candidates()) {
                    if (picks.size() >= 3) break;
                    if (DefaultAcquisitionOracle.isKnownBaseNatural(cand)) {
                        picks.add(cand);
                    }
                }
                for (ResourceKey cand : slot.candidates()) {
                    if (picks.size() >= 3) break;
                    picks.add(cand);
                }
                if (picks.size() < 2) continue;

                List<DecisionOption> options = new ArrayList<>(picks.size());
                int idx = 0;
                for (ResourceKey member : picks) {
                    options.add(new DecisionOption(
                            "pick_" + idx++ + ":" + member.shortString(),
                            "cirrus.prompt.tag_choice.option.pick",
                            CostVector.ZERO,
                            List.of(new TagDefaultRule("rule:tag:" + tagId,
                                    GuidanceScope.SESSION, RuleStrength.HARD_PIN, tagId, member))
                    ));
                }
                mergeBest(out, new DecisionPrompt(
                        promptId,
                        DecisionPromptKind.HIGH_FANOUT_TAG_CHOICE,
                        "cirrus.prompt.tag_choice.title",
                        "cirrus.prompt.tag_choice.explanation",
                        leverage,
                        options
                ));
            }
        }
    }

    private void detectExpensiveLeaves(
            ProductionPlan primary,
            HumanGuidanceView guidance,
            Set<String> dismissed,
            Map<String, DecisionPrompt> out
    ) {
        double totalH = primary.totalCost().humanActiveSeconds();
        double totalS = primary.totalCost().materialScarcity();
        for (PlanStep step : primary.steps()) {
            if (!(step instanceof AcquireStep a)) continue;
            if (guidance.isResourceTreatedAsBase(a.resource()) || isResourceAnswered(guidance, a.resource())) {
                continue;
            }
            double hShare = totalH > 1e-6 ? a.stepCost().humanActiveSeconds() / totalH : 0.0;
            double sShare = totalS > 1e-6 ? a.stepCost().materialScarcity() / totalS : 0.0;
            double share = Math.max(hShare, sShare);
            if (share < 0.40) continue;

            String promptId = "prompt:expensive_leaf:" + a.resource().shortString();
            if (dismissed.contains(promptId)) continue;
            List<DecisionOption> options = List.of(
                    new DecisionOption(
                            "treat_free",
                            "cirrus.prompt.expensive_leaf.option.treat_free",
                            a.stepCost(),
                            List.of(new ResourceRule("rule:free:" + a.resource().shortString(),
                                    GuidanceScope.SESSION, RuleStrength.HARD_PIN, a.resource(), true))
                    ),
                    new DecisionOption(
                            "avoid_resource",
                            "cirrus.prompt.expensive_leaf.option.avoid_resource",
                            CostVector.ZERO,
                            List.of(new ResourceRule("rule:avoid:" + a.resource().shortString(),
                                    GuidanceScope.SESSION, RuleStrength.SOFT_PENALIZE, a.resource(), false))
                    ),
                    new DecisionOption(
                            "keep_gather",
                            "cirrus.prompt.expensive_leaf.option.keep_gather",
                            CostVector.ZERO,
                            List.of()
                    )
            );
            mergeBest(out, new DecisionPrompt(
                    promptId,
                    DecisionPromptKind.EXPENSIVE_LEAF_CONFIRM,
                    "cirrus.prompt.expensive_leaf.title",
                    "cirrus.prompt.expensive_leaf.explanation",
                    SafeMath.clamp01(share),
                    options
            ));
        }
    }

    private void detectSuspiciousRecipes(
            ProductionPlan primary,
            CompiledRecipeGraph graph,
            HumanGuidanceView guidance,
            Set<String> dismissed,
            Map<String, DecisionPrompt> out
    ) {
        for (CraftStep cs : allCraftSteps(primary)) {
            Optional<CompiledRecipe> recOpt = graph.recipeByKey(cs.recipeKey());
            if (recOpt.isEmpty()) continue;
            CompiledRecipe rec = recOpt.get();
            if (isRecipeAnswered(guidance, rec.key())) continue;

            String sysId = rec.key().systemId();
            SystemCostTier tier = costMetadata.tierOfSystem(sysId);
            boolean heavySmallBatch = (tier == SystemCostTier.T3_AUTOMATED_FACTORY_LINE
                    || tier == SystemCostTier.T4_HEAVY_SPECIALIZED) && cs.batches() <= 2L;

            boolean largeSccNonStandard = false;
            if (!"minecraft:crafting".equals(sysId) || rec.advisory() != RecipeAdvisory.NONE
                    || rec.role() != RecipeRole.STANDARD_PRODUCTION) {
                for (var o : rec.outputs()) {
                    int sccId = graph.sccTopology().sccIdOf(o.resource());
                    if (sccId >= 0 && graph.sccTopology().sccSize(sccId) >= 20
                            && (rec.role() != RecipeRole.STANDARD_PRODUCTION || rec.advisory() != RecipeAdvisory.NONE
                            || !sysId.startsWith("minecraft:"))) {
                        largeSccNonStandard = true;
                        break;
                    }
                }
            }
            if (!heavySmallBatch && !largeSccNonStandard) {
                continue;
            }
            double leverage = (heavySmallBatch && largeSccNonStandard) ? 0.80 : 0.55;
            String promptId = "prompt:suspicious:" + rec.key().shortString();
            if (dismissed.contains(promptId)) continue;

            List<DecisionOption> options = List.of(
                    new DecisionOption(
                            "ban_recipe",
                            "cirrus.prompt.suspicious.option.ban_recipe",
                            CostVector.ZERO,
                            List.of(new RecipeRule("rule:ban_rec:" + rec.key().shortString(),
                                    GuidanceScope.SESSION, RuleStrength.HARD_BAN, rec.key()))
                    ),
                    new DecisionOption(
                            "ban_system",
                            "cirrus.prompt.suspicious.option.ban_system",
                            CostVector.ZERO,
                            List.of(new SystemRule("rule:ban_sys:" + sysId,
                                    GuidanceScope.SESSION, RuleStrength.HARD_BAN, sysId, false))
                    ),
                    new DecisionOption(
                            "accept_recipe",
                            "cirrus.prompt.suspicious.option.accept_recipe",
                            CostVector.ZERO,
                            List.of(new RecipeRule("rule:accept_rec:" + rec.key().shortString(),
                                    GuidanceScope.SESSION, RuleStrength.SOFT_PREFER, rec.key()))
                    )
            );
            mergeBest(out, new DecisionPrompt(
                    promptId,
                    DecisionPromptKind.SUSPICIOUS_RECIPE_OR_CYCLE,
                    "cirrus.prompt.suspicious.title",
                    "cirrus.prompt.suspicious.explanation",
                    leverage,
                    options
            ));
        }
    }

    private static List<CraftStep> allCraftSteps(ProductionPlan plan) {
        List<CraftStep> out = new ArrayList<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep cs) {
                out.add(cs);
            } else if (step instanceof CycleLoopStep loop) {
                out.addAll(loop.singleIterationTemplate());
            }
        }
        return out;
    }

    private static double savingRatio(double baseline, double improved) {
        if (baseline <= 1e-6 || improved >= baseline) {
            return 0.0;
        }
        return (baseline - improved) / baseline;
    }

    private static void mergeBest(Map<String, DecisionPrompt> map, DecisionPrompt candidate) {
        DecisionPrompt existing = map.get(candidate.promptId());
        if (existing == null || candidate.leverageScore() > existing.leverageScore()) {
            map.put(candidate.promptId(), candidate);
        }
    }

    private static boolean isMachineAnswered(HumanGuidanceView guidance, String machineId) {
        if (guidance instanceof DefaultHumanGuidanceView view) {
            return view.hasMachineRule(machineId);
        }
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof MachineRule mr && mr.machineId().equals(machineId)) return true;
        }
        return false;
    }

    private static boolean isResourceAnswered(HumanGuidanceView guidance, ResourceKey key) {
        if (guidance instanceof DefaultHumanGuidanceView view) {
            return view.hasResourceRule(key);
        }
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof ResourceRule rr && rr.resource().equals(key)) return true;
        }
        return false;
    }

    private static boolean isRecipeAnswered(HumanGuidanceView guidance, dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey key) {
        if (guidance instanceof DefaultHumanGuidanceView view) {
            return view.hasRecipeRule(key);
        }
        for (GuidanceRule r : guidance.allRules()) {
            if (r instanceof RecipeRule rr && rr.recipeKey().equals(key)) return true;
        }
        return false;
    }
}

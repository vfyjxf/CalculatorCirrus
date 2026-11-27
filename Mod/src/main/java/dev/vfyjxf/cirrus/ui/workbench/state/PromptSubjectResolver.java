package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PromptSubjectResolver {

    public static final String promptMachineForkPrefix = "prompt:machine_fork:";
    public static final String promptTagChoicePrefix = "prompt:tag_choice:";
    public static final String promptExpensiveLeafPrefix = "prompt:expensive_leaf:";
    public static final String promptSuspiciousPrefix = "prompt:suspicious:";

    private PromptSubjectResolver() {}

    public static List<ResolvedPrompt> resolveAll(
        List<DecisionPrompt> prompts,
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph
    ) {
        Objects.requireNonNull(prompts, "prompts");
        Objects.requireNonNull(graph, "graph");
        List<ResolvedPrompt> resolved = new ArrayList<>(prompts.size());
        for (DecisionPrompt prompt : prompts) {
            if (prompt != null) {
                resolved.add(resolve(prompt, plan, graph));
            }
        }
        return List.copyOf(resolved);
    }

    private static ResolvedPrompt resolve(
        DecisionPrompt prompt,
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph
    ) {
        return switch (prompt.kind()) {
            case machineAvailability -> resolveMachineFork(prompt, plan);
            case materialFamilyAnchor -> resolveTagChoice(prompt, plan, graph);
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> resolveExpensiveLeaf(prompt, plan);
            case suspiciousJeiVerify, ruleConflictResolution -> resolveSuspicious(prompt, plan, graph);
        };
    }

    private static ResolvedPrompt resolveMachineFork(DecisionPrompt prompt, @Nullable ProductionPlan plan) {
        String machineId = suffix(prompt.promptId(), promptMachineForkPrefix);
        if (machineId == null || machineId.isEmpty()) {
            machineId = suffix(prompt.promptId(), "prompt:h1_machine:");
        }
        if (machineId == null || machineId.isEmpty()) {
            machineId = machineFromRules(prompt);
        }
        Set<String> anchors = machineId == null || plan == null ? Set.of() : anchoredByMachine(plan, machineId);
        return new ResolvedPrompt(prompt, null, machineId, null, null, null, anchors, resolveOptions(prompt, false));
    }

    private static ResolvedPrompt resolveTagChoice(
        DecisionPrompt prompt,
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph
    ) {
        String tagId = suffix(prompt.promptId(), promptTagChoicePrefix);
        if (tagId == null || tagId.isEmpty()) {
            tagId = suffix(prompt.promptId(), "prompt:h4_family:");
        }
        if (tagId == null || tagId.isEmpty()) {
            tagId = tagFromRules(prompt);
        }
        Set<String> anchors = tagId == null || plan == null ? Set.of() : anchoredByTag(plan, graph, tagId);
        return new ResolvedPrompt(prompt, null, null, tagId, null, null, anchors, resolveOptions(prompt, true));
    }

    private static ResolvedPrompt resolveExpensiveLeaf(DecisionPrompt prompt, @Nullable ProductionPlan plan) {
        String raw = suffix(prompt.promptId(), promptExpensiveLeafPrefix);
        if (raw == null || raw.isEmpty()) raw = suffix(prompt.promptId(), "prompt:h2_base:");
        if (raw == null || raw.isEmpty()) raw = suffix(prompt.promptId(), "prompt:h3_catalyst:");
        if (raw == null || raw.isEmpty()) raw = suffix(prompt.promptId(), "prompt:h6_trip:");
        ResourceKey resource = parseResourceSuffix(raw);
        if (resource == null) {
            resource = resourceFromRules(prompt);
        }
        Set<String> anchors = resource == null || plan == null ? Set.of() : anchoredByResource(plan, resource);
        return new ResolvedPrompt(prompt, resource, null, null, null, null, anchors, resolveOptions(prompt, false));
    }

    private static ResolvedPrompt resolveSuspicious(
        DecisionPrompt prompt,
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph
    ) {
        String raw = suffix(prompt.promptId(), promptSuspiciousPrefix);
        if (raw == null || raw.isEmpty()) raw = suffix(prompt.promptId(), "prompt:h5_suspicious:");
        CompiledRecipeKey recipeKey = parseRecipeSuffix(raw);
        String systemId = recipeKey == null ? null : recipeKey.systemId();
        if (recipeKey == null) {
            recipeKey = recipeFromRules(prompt);
            if (recipeKey != null) {
                systemId = recipeKey.systemId();
            }
        }
        if (systemId == null) {
            systemId = systemFromRules(prompt);
        }
        Set<String> anchors = recipeKey == null || plan == null ? Set.of() : anchoredByRecipe(plan, graph, recipeKey);
        return new ResolvedPrompt(
            prompt,
            null,
            null,
            null,
            recipeKey,
            systemId,
            anchors,
            resolveOptions(prompt, false)
        );
    }

    private static List<ResolvedPromptOption> resolveOptions(DecisionPrompt prompt, boolean tagChoice) {
        List<ResolvedPromptOption> options = new ArrayList<>(prompt.options().size());
        for (DecisionOption option : prompt.options()) {
            ResourceKey variant = null;
            String machineId = null;
            for (GuidanceRule rule : option.compiledRules()) {
                if (tagChoice && rule instanceof TagDefaultRule tagDefault) {
                    variant = tagDefault.preferredMember();
                }
                if (rule instanceof MachineRule machineRule) {
                    machineId = machineRule.machineId();
                }
            }
            options.add(
                new ResolvedPromptOption(
                    option,
                    variant,
                    machineId,
                    option.estimatedDelta(),
                    option.followUp().isPresent()
                )
            );
        }
        return List.copyOf(options);
    }

    private static Set<String> anchoredByMachine(ProductionPlan plan, String machineId) {
        Set<String> anchors = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof ProvisionMachineStep provision && provision.machineId().equals(machineId)) {
                anchors.add(step.stepId());
            } else
                if (step instanceof CraftStep craft && craft.chosenMachineId().map(machineId::equals).orElse(false)) {
                    anchors.add(step.stepId());
                } else if (step instanceof SetupLineStep setup && setup.primaryMachineId().equals(machineId)) {
                    anchors.add(step.stepId());
                }
        }
        return anchors;
    }

    private static Set<String> anchoredByTag(ProductionPlan plan, CompiledRecipeGraph graph, String tagId) {
        Set<String> anchors = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (!(step instanceof CraftStep craft)) {
                continue;
            }
            if (recipeUsesTag(graph, craft.recipeKey(), tagId)) {
                anchors.add(step.stepId());
                break;
            }
        }
        if (anchors.isEmpty()) {
            for (PlanStep step : plan.steps()) {
                if (step instanceof CycleLoopStep loop) {
                    for (CraftStep craft : loop.singleIterationTemplate()) {
                        if (recipeUsesTag(graph, craft.recipeKey(), tagId)) {
                            anchors.add(step.stepId());
                            break;
                        }
                    }
                    if (!anchors.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return anchors;
    }

    private static Set<String> anchoredByResource(ProductionPlan plan, ResourceKey resource) {
        Set<String> anchors = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof AcquireStep acquire && acquire.resource().equals(resource)) {
                anchors.add(step.stepId());
            } else if (step instanceof LinePullStep pull && pull.resource().equals(resource)) {
                anchors.add(step.stepId());
            }
        }
        return anchors;
    }

    private static Set<String> anchoredByRecipe(
        ProductionPlan plan,
        CompiledRecipeGraph graph,
        CompiledRecipeKey recipeKey
    ) {
        Set<String> anchors = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof CraftStep craft && craft.recipeKey().equals(recipeKey)) {
                anchors.add(step.stepId());
            } else if (step instanceof ConvertStep convert && convert.conversionRecipeKey().equals(recipeKey)) {
                anchors.add(step.stepId());
            } else if (step instanceof CycleLoopStep loop && loopContains(graph, loop, recipeKey)) {
                anchors.add(step.stepId());
            }
        }
        return anchors;
    }

    private static boolean loopContains(CompiledRecipeGraph graph, CycleLoopStep loop, CompiledRecipeKey recipeKey) {
        for (CraftStep craft : loop.singleIterationTemplate()) {
            if (craft.recipeKey().equals(recipeKey)) {
                return true;
            }
        }
        Optional<CompiledCycleMacro> macro = graph.cycleMacroByKey(loop.macroKey());
        if (macro.isPresent()) {
            for (CompiledRecipe recipe : macro.get().innerRecipes()) {
                if (recipe.key().equals(recipeKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean recipeUsesTag(CompiledRecipeGraph graph, CompiledRecipeKey recipeKey, String tagId) {
        Optional<CompiledRecipe> recipe = graph.recipeByKey(recipeKey);
        if (recipe.isEmpty()) {
            return false;
        }
        for (InputSlot slot : recipe.get().inputs()) {
            if (slot.tagId().map(tagId::equals).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String machineFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof MachineRule machineRule) {
                    return machineRule.machineId();
                }
            }
            if (option.followUp().isPresent()
                    && option.followUp().get() instanceof DeclareOwnedMachineFeedback declare) {
                return declare.machineId();
            }
        }
        return null;
    }

    private static @Nullable String tagFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof TagDefaultRule tagDefault) {
                    return tagDefault.tagId();
                }
            }
        }
        return null;
    }

    private static @Nullable ResourceKey resourceFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof ResourceRule resourceRule) {
                    return resourceRule.resource();
                }
            }
        }
        return null;
    }

    private static @Nullable CompiledRecipeKey recipeFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof RecipeRule recipeRule) {
                    return recipeRule.recipeKey();
                }
            }
        }
        return null;
    }

    private static @Nullable String systemFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof SystemRule systemRule) {
                    return systemRule.systemId();
                }
            }
        }
        return null;
    }

    private static @Nullable String suffix(String promptId, String prefix) {
        return promptId.startsWith(prefix) ? promptId.substring(prefix.length()) : null;
    }

    static @Nullable ResourceKey parseResourceSuffix(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String contentTypeId = "minecraft:item";
        String rest = raw;
        if (raw.charAt(0) == '[') {
            int close = raw.indexOf(']');
            if (close <= 1) {
                return null;
            }
            contentTypeId = raw.substring(1, close);
            rest = raw.substring(close + 1);
        }
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            rest = rest.substring(0, hash);
        }
        if (rest.isEmpty()) {
            return null;
        }
        return new ResourceKey(contentTypeId, rest, Optional.empty());
    }

    static @Nullable CompiledRecipeKey parseRecipeSuffix(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        int separator = raw.indexOf("::");
        if (separator <= 0) {
            return null;
        }
        String systemId = raw.substring(0, separator);
        String tail = raw.substring(separator + 2);
        if (tail.startsWith("#")) {
            try {
                long hash = Long.parseUnsignedLong(tail.substring(1), 16);
                return new CompiledRecipeKey(systemId, Optional.empty(), hash);
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        if (tail.isEmpty()) {
            return null;
        }
        return new CompiledRecipeKey(systemId, Optional.of(tail), 0L);
    }
}

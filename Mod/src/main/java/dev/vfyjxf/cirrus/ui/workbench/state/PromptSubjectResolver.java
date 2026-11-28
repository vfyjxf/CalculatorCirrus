package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
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
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PromptSubjectResolver {

    public static final String promptMachinePrefix = "prompt:machine:";
    public static final String promptSupplyPrefix = "prompt:supply:";
    public static final String promptCatalystPrefix = "prompt:catalyst:";
    public static final String promptTagPrefix = "prompt:tag:";
    public static final String promptUnverifiedPrefix = "prompt:unverified:";
    public static final String promptTripPrefix = "prompt:trip:";

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
            case baseOrSupplyBoundary, reusableCatalystCheck, fieldTripConfirm -> resolveResourcePrompt(prompt, plan);
            case suspiciousJeiVerify, ruleConflictResolution -> resolveRecipePrompt(prompt, plan, graph);
        };
    }

    private static ResolvedPrompt resolveMachineFork(DecisionPrompt prompt, @Nullable ProductionPlan plan) {
        MachineEntry machine = machineFromRules(prompt);
        if (machine == null) {
            String raw = suffix(prompt.promptId(), promptMachinePrefix);
            machine = parseMachineFallback(raw);
        }
        Set<String> anchors = machine == null || plan == null ? Set.of() : anchoredByMachine(plan, machine);
        return new ResolvedPrompt(prompt, null, machine, null, null, null, anchors, resolveOptions(prompt, false));
    }

    private static ResolvedPrompt resolveTagChoice(
        DecisionPrompt prompt,
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph
    ) {
        TagKey<?> tag = tagFromRules(prompt);
        if (tag == null) {
            String raw = suffix(prompt.promptId(), promptTagPrefix);
            if (raw != null && !raw.isEmpty()) {
                tag = PlanningTagKeys.parse(raw).orElse(null);
            }
        }
        Set<String> anchors = tag == null || plan == null ? Set.of() : anchoredByTag(plan, graph, tag);
        return new ResolvedPrompt(prompt, null, null, tag, null, null, anchors, resolveOptions(prompt, true));
    }

    private static ResolvedPrompt resolveResourcePrompt(DecisionPrompt prompt, @Nullable ProductionPlan plan) {
        ComponentStackKey resource = resourceFromRules(prompt);
        if (resource == null) {
            String raw = suffix(prompt.promptId(), promptSupplyPrefix);
            if (raw == null || raw.isEmpty()) raw = suffix(prompt.promptId(), promptCatalystPrefix);
            if (raw == null || raw.isEmpty()) raw = suffix(prompt.promptId(), promptTripPrefix);
            resource = parseResourceFallback(raw);
        }
        Set<String> anchors = resource == null || plan == null ? Set.of() : anchoredByResource(plan, resource);
        return new ResolvedPrompt(prompt, resource, null, null, null, null, anchors, resolveOptions(prompt, false));
    }

    private static ResolvedPrompt resolveRecipePrompt(
        DecisionPrompt prompt,
        @Nullable ProductionPlan plan,
        CompiledRecipeGraph graph
    ) {
        CompiledRecipeKey recipeKey = recipeFromRules(prompt);
        if (recipeKey == null) {
            recipeKey = parseRecipeSuffix(suffix(prompt.promptId(), promptUnverifiedPrefix));
        }
        Namespace systemId = recipeKey != null ? recipeKey.systemId() : systemFromRules(prompt);
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
            ComponentStackKey variant = null;
            MachineEntry machine = null;
            for (GuidanceRule rule : option.compiledRules()) {
                if (tagChoice && rule instanceof TagDefaultRule tagDefault) {
                    variant = tagDefault.preferredMember();
                }
                if (rule instanceof MachineRule machineRule) {
                    machine = machineRule.machine();
                }
            }
            if (machine == null
                    && option.followUp().isPresent()
                    && option.followUp().get() instanceof DeclareOwnedMachineFeedback declare) {
                machine = declare.machine();
            }
            options.add(
                new ResolvedPromptOption(
                    option,
                    variant,
                    machine,
                    option.estimatedDelta(),
                    option.followUp().isPresent()
                )
            );
        }
        return List.copyOf(options);
    }

    private static Set<String> anchoredByMachine(ProductionPlan plan, MachineEntry machine) {
        Set<String> anchors = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (step instanceof ProvisionMachineStep provision && provision.machine().equals(machine)) {
                anchors.add(step.stepId());
            } else if (step instanceof CraftStep craft && craft.chosenMachine().map(machine::equals).orElse(false)) {
                anchors.add(step.stepId());
            } else if (step instanceof SetupLineStep setup && setup.primaryMachine().equals(machine)) {
                anchors.add(step.stepId());
            }
        }
        return anchors;
    }

    private static Set<String> anchoredByTag(ProductionPlan plan, CompiledRecipeGraph graph, TagKey<?> tag) {
        Set<String> anchors = new LinkedHashSet<>();
        for (PlanStep step : plan.steps()) {
            if (!(step instanceof CraftStep craft)) {
                continue;
            }
            if (recipeUsesTag(graph, craft.recipeKey(), tag)) {
                anchors.add(step.stepId());
                break;
            }
        }
        if (anchors.isEmpty()) {
            for (PlanStep step : plan.steps()) {
                if (step instanceof CycleLoopStep loop) {
                    for (CraftStep craft : loop.singleIterationTemplate()) {
                        if (recipeUsesTag(graph, craft.recipeKey(), tag)) {
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

    private static Set<String> anchoredByResource(ProductionPlan plan, ComponentStackKey resource) {
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

    private static boolean recipeUsesTag(CompiledRecipeGraph graph, CompiledRecipeKey recipeKey, TagKey<?> tag) {
        Optional<CompiledRecipe> recipe = graph.recipeByKey(recipeKey);
        if (recipe.isEmpty()) {
            return false;
        }
        for (InputSlot slot : recipe.get().inputs()) {
            if (slot.tag().map(tag::equals).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable MachineEntry machineFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof MachineRule machineRule) {
                    return machineRule.machine();
                }
            }
            if (option.followUp().isPresent()
                    && option.followUp().get() instanceof DeclareOwnedMachineFeedback declare) {
                return declare.machine();
            }
        }
        return null;
    }

    private static @Nullable TagKey<?> tagFromRules(DecisionPrompt prompt) {
        for (DecisionOption option : prompt.options()) {
            for (GuidanceRule rule : option.compiledRules()) {
                if (rule instanceof TagDefaultRule tagDefault) {
                    return tagDefault.tag();
                }
            }
        }
        return null;
    }

    private static @Nullable ComponentStackKey resourceFromRules(DecisionPrompt prompt) {
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

    private static @Nullable Namespace systemFromRules(DecisionPrompt prompt) {
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

    private static @Nullable MachineEntry parseMachineFallback(@Nullable String raw) {
        ComponentStackKey key = parseResourceFallback(raw);
        return key == null ? null : MachineEntry.fromKey(key);
    }

    private static @Nullable ComponentStackKey parseResourceFallback(@Nullable String raw) {
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
        ResourceLocation loc = ResourceLocation.tryParse(rest);
        if (loc == null) {
            return null;
        }
        if ("minecraft:fluid".equals(contentTypeId)) {
            return BuiltInRegistries.FLUID.getOptional(loc).map(ComponentStackKey::ofFluid).orElse(null);
        }
        return BuiltInRegistries.ITEM.getOptional(loc).map(ComponentStackKey::ofItem).orElse(null);
    }

    static @Nullable CompiledRecipeKey parseRecipeSuffix(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        int separator = raw.indexOf("::");
        if (separator <= 0) {
            return null;
        }
        Namespace systemId;
        try {
            systemId = Namespace.parse(raw.substring(0, separator));
        } catch (RuntimeException malformed) {
            return null;
        }
        String tail = raw.substring(separator + 2);
        if (tail.startsWith("#")) {
            try {
                long hash = Long.parseUnsignedLong(tail.substring(1), 16);
                return new CompiledRecipeKey(systemId, Optional.empty(), hash);
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        ResourceLocation explicitId = ResourceLocation.tryParse(tail);
        if (explicitId == null) {
            return null;
        }
        return new CompiledRecipeKey(systemId, Optional.of(explicitId), 0L);
    }
}

package dev.vfyjxf.cirrus.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRecipeRule;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRecipeRules;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.ContextualRuleContext;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import dev.vfyjxf.cirrus.recipe.routing.topology.GraphTopology;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The built-in contextual screening rules.
 *
 * <p>Where general screening answers "is this recipe meaningful at all",
 * these rules answer "is this recipe meaningful for <em>this</em> request":
 *
 * <ul>
 *   <li>{@link TargetFamilyConversionRule} — when the request target belongs
 *   to a looping resource family (the sixteen concrete colors, etc.), any
 *   deeper-tree candidate that converts another family member is penalized.
 *   This backstops the demand-side intra-loop guard, which cannot fire when
 *   the demand is a tag/list slot without a concrete resource id.</li>
 *   <li>{@link MissingMachineRule} — a candidate whose machine the player
 *   does not have in stock is penalized, so paths through owned machines win
 *   (e.g. furnace recycling over arc-furnace recycling when no arc furnace
 *   is available). Never fires on empty stock, where ownership is unknown.</li>
 * </ul>
 */
public final class BuiltInContextualRules {

    /** Below the intra-loop guard's 100_000: a nudge, not a veto. */
    private static final double targetFamilyConversionPenalty = 5_000.0d;
    private static final double missingMachinePenalty = 2_000.0d;

    private static final AtomicBoolean registered = new AtomicBoolean();

    private BuiltInContextualRules() {
    }

    /** Registers the built-in rules into {@link ContextualRecipeRules}. Idempotent. */
    public static void register() {
        if (!registered.compareAndSet(false, true)) {
            return;
        }
        ContextualRecipeRules.register(new TargetFamilyConversionRule());
        ContextualRecipeRules.register(new MissingMachineRule());
    }

    /**
     * Penalizes deeper-tree candidates that consume a member of the target's
     * looping resource family. Depth 0 is left to the demand-side intra-loop
     * penalty to avoid double-charging the root choice.
     */
    private static final class TargetFamilyConversionRule implements ContextualRecipeRule {

        @Override
        public String ruleId() {
            return "cirrus:contextual/target_family_conversion";
        }

        @Override
        public double penalty(ContextualRuleContext context) {
            String target = context.targetResourceId();
            if (target == null || context.depth() == 0) {
                return 0.0d;
            }
            GraphTopology topology = context.service().topology();
            if (!topology.isLoopingResource(target)) {
                return 0.0d;
            }
            for (int inputIndex = 0; inputIndex < context.candidate().inputCount(); inputIndex++) {
                InputContent input = context.candidate().inputAt(inputIndex);
                if (input.isEmpty() || input.notConsumed()) {
                    continue;
                }
                String inputResource = ClassifyViewFactory.resourceIdOf(input);
                if (inputResource != null && topology.sharesLoopingScc(inputResource, target)) {
                    return targetFamilyConversionPenalty;
                }
            }
            return 0.0d;
        }
    }

    /**
     * Penalizes candidates whose machine is absent from the request stock.
     * Empty stock means "no ownership data", so the rule stays silent there.
     */
    private static final class MissingMachineRule implements ContextualRecipeRule {

        @Override
        public String ruleId() {
            return "cirrus:contextual/missing_machine";
        }

        @Override
        public double penalty(ContextualRuleContext context) {
            Stock stock = context.request().stock();
            if (stock.isEmpty()) {
                return 0.0d;
            }
            MachineEntry machine = context.candidate().machineEntry();
            if (machine == null || machine.isEmpty()) {
                return 0.0d;
            }
            ItemStack machineStack = machine.toItemStack();
            if (machineStack.isEmpty()) {
                return 0.0d;
            }
            ContentStack<?, ?> machineContent = ContentStack.fromStack(machineStack);
            if (machineContent.isEmpty()) {
                return 0.0d;
            }
            return stock.contains(machineContent) ? 0.0d : missingMachinePenalty;
        }
    }
}

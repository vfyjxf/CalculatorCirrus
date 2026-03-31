package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlanDebugText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Formats translated interaction plans into debug-readable text.
 *
 * <p>This mirrors {@link RoutingPlanDebugText} but reports runtime-facing
 * interaction steps, making it easier to compare routing output with translated execution.
 */
public final class InteractionExecutionPlanDebugText {

    private InteractionExecutionPlanDebugText() {
    }

    //region plan

    public static String describe(@Nullable InteractionExecutionPlan plan) {
        if (plan == null) {
            return "<null interaction plan>";
        }

        MutableList<String> lines = Lists.mutable.empty();
        lines.add("interaction-plan target=" + describeRecipe(plan.targetRecipe()) + " x" + plan.targetTimes());
        lines.add("steps=" + plan.steps().size() + " capabilities=" + plan.requiredCapabilities().makeString("[", ", ", "]"));
        for (int index = 0; index < plan.steps().size(); index++) {
            lines.addAllIterable(describeLines(index, plan.steps().get(index)));
        }
        return lines.makeString(System.lineSeparator());
    }

    public static ImmutableList<String> describeLines(int index, InteractionStep step) {
        MutableList<String> lines = Lists.mutable.empty();
        String prefix = "[" + index + "] ";
        String suffix = sideCapabilitySuffix(step);
        switch (step) {
            case InteractionStep.EnsureAccessibleStep ensure ->
                lines.add(prefix + "ensure-accessible " + describeAccessTarget(ensure.target())
                    + " x" + ensure.requestedAmount() + suffix);
            case InteractionStep.WithdrawResourceStep withdraw ->
                lines.add(prefix + "withdraw " + describeWithdrawTarget(withdraw.target())
                    + " x" + withdraw.requestedAmount() + suffix);
            case InteractionStep.ReserveCatalystStep reserve ->
                lines.add(prefix + "reserve-catalyst " + RoutingPlanDebugText.describeInput(reserve.input())
                    + " x" + reserve.amount() + suffix);
            case InteractionStep.EnsureCapacityStep capacity ->
                lines.add(prefix + "ensure-capacity " + RoutingPlanDebugText.describeResource(capacity.resource())
                    + " x" + capacity.amount() + suffix);
            case InteractionStep.StoreOutputsStep store ->
                lines.add(prefix + "store-output " + RoutingPlanDebugText.describeResource(store.resource())
                    + " x" + store.amount() + suffix);
            case InteractionStep.OpenInteractionStep open ->
                lines.add(prefix + "open " + describeRecipe(open.recipe()) + " x" + open.times()
                    + " interaction=" + open.interactionId() + suffix);
            case InteractionStep.PrepareInputsStep prepare -> {
                lines.add(prefix + "prepare " + describeRecipe(prepare.recipe()) + suffix);
                lines.add("  inputs: " + RoutingPlanDebugText.describeInputs(prepare.inputs()));
            }
            case InteractionStep.ExecuteRecipeStep execute -> {
                lines.add(prefix + "execute " + describeRecipe(execute.recipe()) + " x" + execute.times() + suffix);
                lines.add("  inputs:  " + RoutingPlanDebugText.describeInputs(execute.inputs()));
                lines.add("  outputs: " + RoutingPlanDebugText.describeOutputs(execute.expectedOutputs()));
            }
            case InteractionStep.CollectOutputsStep collect -> {
                lines.add(prefix + "collect " + describeRecipe(collect.recipe()) + suffix);
                lines.add("  outputs: " + RoutingPlanDebugText.describeOutputs(collect.expectedOutputs()));
            }
            case InteractionStep.ManualCheckpointStep checkpoint ->
                lines.add(prefix + "manual-checkpoint " + describeGuidance(checkpoint.guidance()) + suffix);
        }
        return lines.toImmutable();
    }

    //endregion

    //region targets

    private static String describeAccessTarget(InteractionStep.AccessTarget target) {
        return switch (target) {
            case InteractionStep.AccessTarget.Resource resource -> RoutingPlanDebugText.describeResource(resource.resource());
            case InteractionStep.AccessTarget.Input input -> RoutingPlanDebugText.describeInput(input.input());
        };
    }

    private static String describeWithdrawTarget(InteractionStep.WithdrawTarget target) {
        return switch (target) {
            case InteractionStep.WithdrawTarget.Resource resource -> RoutingPlanDebugText.describeResource(resource.resource());
            case InteractionStep.WithdrawTarget.Input input -> RoutingPlanDebugText.describeInput(input.input());
        };
    }

    //endregion

    //region helpers

    private static String describeRecipe(RecipeEntry<?> recipe) {
        ResourceLocation id = recipe.id();
        return id == null ? recipe.systemId().toString() : id.toString();
    }

    private static String describeGuidance(ExecutionGuidance guidance) {
        Component summary = guidance.summary();
        return summary == null ? "<no guidance>" : quote(summary.getString());
    }

    private static String sideCapabilitySuffix(InteractionStep step) {
        return " side=" + step.requiredSide() + " capability=" + step.requiredCapability();
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }

    //endregion
}

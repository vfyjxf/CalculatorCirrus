package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Immutable execution step model consumed by runtime and progress visualization.
 */
public sealed interface InteractionStep permits
    InteractionStep.EnsureAccessibleStep,
    InteractionStep.WithdrawResourceStep,
    InteractionStep.ReserveCatalystStep,
    InteractionStep.EnsureCapacityStep,
    InteractionStep.OpenInteractionStep,
    InteractionStep.PrepareInputsStep,
    InteractionStep.ExecuteRecipeStep,
    InteractionStep.CollectOutputsStep,
    InteractionStep.StoreOutputsStep,
    InteractionStep.ManualCheckpointStep {

    ExecutionSideRequirement requiredSide();

    ExecutionCapability requiredCapability();

    //region access target

    sealed interface AccessTarget permits AccessTarget.Resource, AccessTarget.Input {

        record Resource(ContentResource resource) implements AccessTarget {}

        record Input(InputContent input) implements AccessTarget {}
    }

    //endregion

    //region storage

    record EnsureAccessibleStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        AccessTarget target,
        long requestedAmount
    ) implements InteractionStep {
        public EnsureAccessibleStep {
            Checks.checkArgument(requestedAmount > 0, "requestedAmount must be positive");
        }
    }

    record WithdrawResourceStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        WithdrawTarget target,
        long requestedAmount
    ) implements InteractionStep {
        public WithdrawResourceStep {
            Checks.checkArgument(requestedAmount > 0, "requestedAmount must be positive");
        }
    }

    sealed interface WithdrawTarget permits WithdrawTarget.Resource, WithdrawTarget.Input {

        record Resource(ContentResource resource) implements WithdrawTarget {}

        record Input(InputContent input) implements WithdrawTarget {}
    }

    record ReserveCatalystStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        InputContent input,
        long amount
    ) implements InteractionStep {
        public ReserveCatalystStep {
            Checks.checkArgument(amount > 0, "amount must be positive");
        }
    }

    record EnsureCapacityStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        ContentResource resource,
        long amount
    ) implements InteractionStep {
        public EnsureCapacityStep {
            Checks.checkArgument(amount > 0, "amount must be positive");
        }
    }

    record StoreOutputsStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        ContentResource resource,
        long amount
    ) implements InteractionStep {
        public StoreOutputsStep {
            Checks.checkArgument(amount > 0, "amount must be positive");
        }
    }

    //endregion

    //region interaction

    record OpenInteractionStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        RecipeEntry<?> recipe,
        int times,
        Namespace interactionId
    ) implements InteractionStep {
        public OpenInteractionStep {
            Checks.checkArgument(times > 0, "times must be positive");
        }
    }

    record PrepareInputsStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        RecipeEntry<?> recipe,
        ImmutableList<PlanAction.InputBinding> inputs
    ) implements InteractionStep {}

    record ExecuteRecipeStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        RecipeEntry<?> recipe,
        int times,
        ImmutableList<PlanAction.InputBinding> inputs,
        ImmutableList<PlanAction.OutputBinding> expectedOutputs
    ) implements InteractionStep {
        public ExecuteRecipeStep {
            Checks.checkArgument(times > 0, "times must be positive");
        }
    }

    record CollectOutputsStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        RecipeEntry<?> recipe,
        ImmutableList<PlanAction.OutputBinding> expectedOutputs
    ) implements InteractionStep {}

    record ManualCheckpointStep(
        ExecutionSideRequirement requiredSide,
        ExecutionCapability requiredCapability,
        ExecutionGuidance guidance
    ) implements InteractionStep {}

    //endregion
}

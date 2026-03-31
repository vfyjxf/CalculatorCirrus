package dev.vfyjxf.cirrus.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotAccess;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.slot.SlotLayout;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayViewModel;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayTargetProgress;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.jetbrains.annotations.Nullable;

/**
 * Slot interaction flow with validation, step execution, and tracking.
 */
public interface SlotFlow {
    //region contract

    Validation validate(Context context);

    StepResult open(Context context, InteractionStep.OpenInteractionStep step);

    StepResult prepare(Context context, InteractionStep.PrepareInputsStep step);

    InteractionPerformResult execute(Context context, InteractionStep.ExecuteRecipeStep step);

    StepResult collect(Context context, InteractionStep.CollectOutputsStep step);

    PauseReason pauseReason(Context context, Failure failure);

    Tracking track(Context context, ExecutionSession session);
    //endregion

    //region state

    record Context(
        SlotLayout layout,
        SlotAccess access,
        @Nullable SlotAccess.Interaction interaction,
        @Nullable PlacementResolver placementResolver
    ) {}

    record Validation(boolean valid, @Nullable PauseReason pauseReason) {
        public static Validation ok() {
            return new Validation(true, null);
        }

        public static Validation invalid(PauseReason pauseReason) {
            return new Validation(false, pauseReason);
        }
    }

    record StepResult(boolean completed, @Nullable PauseReason pauseReason) {
        public static StepResult success() {
            return new StepResult(true, null);
        }

        public static StepResult blocked() {
            return new StepResult(false, null);
        }

        public static StepResult paused(PauseReason pauseReason) {
            return new StepResult(false, pauseReason);
        }
    }


    record Failure(InteractionStep step, FailureKind kind, @Nullable Throwable error) {
        public enum FailureKind {
            validation,
            openBlocked,
            prepareBlocked,
            collectBlocked,
            executeFailed
        }
    }

    record Tracking(
        @Nullable ExecutionOverlayViewModel viewModel,
        ImmutableList<OverlayMaterialRequirement> materials,
        @Nullable OverlayTargetProgress targetProgress,
        @Nullable ExecutionOverlayTraceSnapshot trace
    ) {
        public static Tracking empty() {
            return new Tracking(null, Lists.immutable.empty(), null, null);
        }
    }
    //endregion

    //region placement

    record Placement(
        SlotLayout.Reference slot,
        ItemStack template,
        long amount,
        @Nullable InputContent input
    ) {
        public Placement(SlotLayout.Reference slot, ItemStack template, long amount) {
            this(slot, template, amount, null);
        }

        public Placement {
            template = template.copy();
            amount = Math.max(0L, amount);
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (input != null && input.notEmpty()) {
                return input.satisfy(ContentStack.fromStack(stack));
            }
            return !template.isEmpty() && ItemStack.isSameItemSameComponents(stack, template);
        }
    }

    @FunctionalInterface
    interface PlacementResolver {
        ImmutableList<Placement> resolve(
            Context context,
            @Nullable RecipeEntry<?> recipe,
            ImmutableList<PlanAction.InputBinding> inputs
        );
    }
    //endregion
}


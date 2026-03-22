package dev.vfyjxf.cirrus.api.recipe.routing.plan;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * A single executable action in a {@link RoutingPlan}.
 *
 * <p>Actions are the atomic steps that compose an execution plan. They are produced
 * by planners during graph traversal and stored in a strictly ordered sequence.
 * Action instances are immutable so a generated plan can be replayed reliably on
 * the client side.
 *
 * <p>The sealed hierarchy defines three action types:
 * <ul>
 *     <li>{@link ConsumeStockAction} — consume a resource directly from the
 *         player's available inventory.</li>
 *     <li>{@link ReserveCatalystAction} — reserve a non-consumed (catalyst) input
 *         that must be present but is not used up by the recipe.</li>
 *     <li>{@link ExecuteRecipeAction} — execute a recipe a given number of times
 *         with fully bound inputs and expected outputs.</li>
 * </ul>
 *
 * @see RoutingPlan
 * @see PlanBuilder
 */
public sealed interface PlanAction permits
    PlanAction.ConsumeStockAction,
    PlanAction.ReserveCatalystAction,
    PlanAction.ExecuteRecipeAction {

    /** Globally unique action identifier within one plan. */
    int actionId();

    /** Zero-based insertion order index within the plan's action list. */
    int actionIndex();

    /** Whether this action blocks subsequent execution until completed. */
    boolean blocking();

    /**
     * Consume a resource directly from the player's available stock.
     *
     * @param actionId    unique action identifier
     * @param actionIndex insertion order index
     * @param blocking    whether this action blocks subsequent steps
     * @param resource    the resource to consume
     * @param amount      the amount to consume (must be positive)
     */
    record ConsumeStockAction(
        int actionId,
        int actionIndex,
        boolean blocking,
        ContentResource resource,
        long amount
    ) implements PlanAction {
        public ConsumeStockAction {
            Checks.checkNotNull(resource, "resource");
            Checks.checkArgument(amount > 0, "amount must be positive");
            Checks.checkArgument(actionId >= 0, "actionId must be non-negative");
            Checks.checkArgument(actionIndex >= 0, "actionIndex must be non-negative");
        }
    }

    /**
     * Reserve a catalyst (non-consumed) input that must be present for a recipe
     * but is not consumed during execution.
     *
     * @param actionId    unique action identifier
     * @param actionIndex insertion order index
     * @param blocking    whether this action blocks subsequent steps
     * @param input       the catalyst input content
     * @param amount      the amount to reserve (must be positive)
     * @param fromRecipe  the recipe that requires this catalyst
     */
    record ReserveCatalystAction(
        int actionId,
        int actionIndex,
        boolean blocking,
        InputContent input,
        long amount,
        RecipeEntry<?> fromRecipe
    ) implements PlanAction {
        public ReserveCatalystAction {
            Checks.checkNotNull(input, "input");
            Checks.checkNotNull(fromRecipe, "fromRecipe");
            Checks.checkArgument(amount > 0, "amount must be positive");
            Checks.checkArgument(actionId >= 0, "actionId must be non-negative");
            Checks.checkArgument(actionIndex >= 0, "actionIndex must be non-negative");
        }
    }

    /**
     * Execute a recipe a specified number of times with fully resolved input
     * and output bindings.
     *
     * @param actionId        unique action identifier
     * @param actionIndex     insertion order index
     * @param blocking        whether this action blocks subsequent steps
     * @param recipe          the recipe to execute
     * @param times           how many times to execute (must be positive)
     * @param boundInputs     resolved input bindings with amounts
     * @param expectedOutputs expected output bindings with amounts
     */
    record ExecuteRecipeAction(
        int actionId,
        int actionIndex,
        boolean blocking,
        RecipeEntry<?> recipe,
        int times,
        ImmutableList<InputBinding> boundInputs,
        ImmutableList<OutputBinding> expectedOutputs
    ) implements PlanAction {
        public ExecuteRecipeAction {
            Checks.checkNotNull(recipe, "recipe");
            Checks.checkNotNull(boundInputs, "boundInputs");
            Checks.checkNotNull(expectedOutputs, "expectedOutputs");
            Checks.checkArgument(times > 0, "times must be positive");
            Checks.checkArgument(actionId >= 0, "actionId must be non-negative");
            Checks.checkArgument(actionIndex >= 0, "actionIndex must be non-negative");
        }
    }

    /**
     * Resolved binding of an input content to a specific amount.
     *
     * @param input  the input content
     * @param amount the required amount (must be positive)
     */
    record InputBinding(InputContent input, long amount) {
        public InputBinding {
            Checks.checkNotNull(input, "input");
            Checks.checkArgument(amount > 0, "amount must be positive");
        }
    }

    /**
     * Expected output binding indicating the resource and amount
     * produced by executing a recipe.
     *
     * @param resource the produced resource
     * @param amount   the expected amount (must be positive)
     */
    record OutputBinding(ContentResource resource, long amount) {
        public OutputBinding {
            Checks.checkNotNull(resource, "resource");
            Checks.checkArgument(amount > 0, "amount must be positive");
        }
    }
}


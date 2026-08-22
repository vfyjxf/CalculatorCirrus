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
 * <p>The sealed hierarchy defines four action types:
 * <ul>
 *     <li>{@link ConsumeStock} — consume a resource directly from the
 *         player's available inventory.</li>
 *     <li>{@link PendingInput} — preserve the original {@link InputContent} semantics as a
 *         pending external requirement when planning cannot resolve a source.</li>
 *     <li>{@link ReserveCatalyst} — reserve a non-consumed (catalyst) input
 *         that must be present but is not used up by the recipe.</li>
 *     <li>{@link ExecuteRecipe} — execute a recipe a given number of times
 *         with fully bound inputs and expected outputs.</li>
 * </ul>
 *
 * @see RoutingPlan
 * @see PlanBuilder
 */
public sealed interface PlanAction permits
    PlanAction.ConsumeStock,
    PlanAction.PendingInput,
    PlanAction.ReserveCatalyst,
    PlanAction.ExecuteRecipe {

    /**
     * Globally unique action identifier within one plan.
     */
    int actionId();

    /**
     * Zero-based insertion order index within the plan's action list.
     */
    int actionIndex();

    /**
     * Whether this action blocks subsequent execution until completed.
     */
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
    record ConsumeStock(
        int actionId,
        int actionIndex,
        boolean blocking,
        InputContent input,
        ContentResource resource,
        long amount
    ) implements PlanAction {
        public ConsumeStock {
            Checks.checkNotNull(input, "input");
            Checks.checkNotNull(resource, "resource");
            Checks.checkArgument(amount > 0, "amount must be positive");
            Checks.checkArgument(actionId >= 0, "actionId must be non-negative");
            Checks.checkArgument(actionIndex >= 0, "actionIndex must be non-negative");
        }
    }

    /**
     * Mark an unresolved demand as a pending external input while preserving the
     * original input semantics.
     *
     * <p>This is used when the planner cannot resolve a source and should stop
     * expanding the chain instead of forcing the request down to base materials.
     *
     * @param candidateCount how many producer candidates were known for this input
     *                       ({@code 0} when resolution never reached candidate evaluation);
     *                       used by continuation runs to decide whether a deeper wave
     *                       could still resolve this input
     * @param attemptedCount how many of those candidates were actually tried before
     *                       the input went pending
     * @param truncated      whether the input went pending only because a budget or
     *                       ranking window cut resolution short — such pendings can
     *                       still be resolved by a deeper/continuation wave, unlike
     *                       {@link PendingReason#TRUE_NO_SOURCE} or
     *                       {@link PendingReason#CASCADE_EXHAUSTED}
     * @param reason         why the input went pending; never {@code null}
     */
    record PendingInput(
        int actionId,
        int actionIndex,
        boolean blocking,
        InputContent input,
        long amount,
        int candidateCount,
        int attemptedCount,
        boolean truncated,
        PendingReason reason
    ) implements PlanAction {
        public PendingInput {
            Checks.checkNotNull(input, "input");
            Checks.checkArgument(amount > 0, "amount must be positive");
            Checks.checkArgument(actionId >= 0, "actionId must be non-negative");
            Checks.checkArgument(actionIndex >= 0, "actionIndex must be non-negative");
            Checks.checkArgument(candidateCount >= 0, "candidateCount must be non-negative");
            Checks.checkArgument(attemptedCount >= 0, "attemptedCount must be non-negative");
            Checks.checkNotNull(reason, "reason");
        }

        /** Compatibility constructor used when no truncation analysis is available. */
        public PendingInput(
            int actionId,
            int actionIndex,
            boolean blocking,
            InputContent input,
            long amount,
            int candidateCount,
            int attemptedCount
        ) {
            this(actionId, actionIndex, blocking, input, amount, candidateCount, attemptedCount, false, PendingReason.TRUE_NO_SOURCE);
        }
    }

    /**
     * Why an input went pending. Distinguishes genuinely unreachable demands from
     * "no source found" verdicts that are merely budget/ranking artifacts — the
     * latter are resolvable by continuation waves with a larger budget.
     */
    enum PendingReason {
        /** No producer exists in the (policy-filtered) index; not resolvable by deepening. */
        TRUE_NO_SOURCE,
        /** Producer enumeration was cut by a budget gate before this input was reached. */
        BUDGET_TRUNCATED,
        /** Real producers existed but were dropped by the top-K candidate ranking window. */
        RANK_TRUNCATED,
        /** All candidates were genuinely tried and failed; not resolvable by deepening. */
        CASCADE_EXHAUSTED
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
    record ReserveCatalyst(
        int actionId,
        int actionIndex,
        boolean blocking,
        InputContent input,
        long amount,
        RecipeEntry<?> fromRecipe
    ) implements PlanAction {
        public ReserveCatalyst {
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
    record ExecuteRecipe(
        int actionId,
        int actionIndex,
        boolean blocking,
        RecipeEntry<?> recipe,
        int times,
        ImmutableList<InputBinding> boundInputs,
        ImmutableList<OutputBinding> expectedOutputs
    ) implements PlanAction {
        public ExecuteRecipe {
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


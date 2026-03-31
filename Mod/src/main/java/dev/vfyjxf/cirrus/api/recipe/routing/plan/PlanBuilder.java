package dev.vfyjxf.cirrus.api.recipe.routing.plan;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

/**
 * Mutable builder used during planning to assemble an immutable {@link RoutingPlan}.
 *
 * <p>Planners append actions sequentially as they resolve the dependency graph.
 * Each action is assigned an auto-incrementing {@code actionId} and an
 * {@code actionIndex} equal to its insertion position. When planning completes,
 * call {@link #build()} to freeze the action list into an immutable plan.
 *
 * <p>A builder is intended for single-use within one planner invocation —
 * it is <b>not</b> thread-safe.
 *
 * @see PlanAction
 * @see RoutingPlan
 */
public final class PlanBuilder {

    private final long planVersion;
    private final long tagSnapshotVersion;
    private final RecipeEntry<?> targetRecipe;
    private final int targetTimes;
    private final MutableList<PlanAction> actions = Lists.mutable.empty();
    private int nextActionId;

    /**
     * Creates a new plan builder.
     *
     * @param planVersion        monotonic version stamp for this plan
     * @param tagSnapshotVersion the tag snapshot version used for source-policy filtering
     * @param targetRecipe       the top-level target recipe being routed
     * @param targetTimes        how many times the target recipe should be executed
     */
    public PlanBuilder(long planVersion, long tagSnapshotVersion, RecipeEntry<?> targetRecipe, int targetTimes) {
        Checks.checkArgument(planVersion >= 0, "planVersion must be non-negative");
        Checks.checkArgument(tagSnapshotVersion >= 0, "tagSnapshotVersion must be non-negative");
        Checks.checkNotNull(targetRecipe, "targetRecipe");
        Checks.checkArgument(targetTimes > 0, "targetTimes must be positive");
        this.planVersion = planVersion;
        this.tagSnapshotVersion = tagSnapshotVersion;
        this.targetRecipe = targetRecipe;
        this.targetTimes = targetTimes;
    }

    /**
     * Appends a stock-consumption action.
     *
     * @param input    the original input semantics that this stock consume satisfies
     * @param resource the resource to consume from stock
     * @param amount   the amount to consume
     * @return the created action
     */
    public PlanAction.ConsumeStock addConsumeStock(InputContent input, ContentResource resource, long amount) {
        int index = actions.size();
        var action = new PlanAction.ConsumeStock(nextActionId++, index, false, input, resource, amount);
        actions.add(action);
        return action;
    }

    /**
     * Appends a pending-input action that keeps the original input semantics when the
     * planner cannot resolve a source.
     */
    public PlanAction.PendingInput addPendingInput(InputContent input, long amount) {
        int index = actions.size();
        var action = new PlanAction.PendingInput(nextActionId++, index, false, input, amount);
        actions.add(action);
        return action;
    }

    /**
     * Appends a catalyst-reservation action.
     *
     * @param input      the non-consumed input to reserve
     * @param amount     the amount to reserve
     * @param fromRecipe the recipe requiring this catalyst
     * @return the created action
     */
    public PlanAction.ReserveCatalyst addReserveCatalyst(InputContent input, long amount, RecipeEntry<?> fromRecipe) {
        int index = actions.size();
        var action = new PlanAction.ReserveCatalyst(nextActionId++, index, false, input, amount, fromRecipe);
        actions.add(action);
        return action;
    }

    /**
     * Appends a recipe-execution action.
     *
     * @param recipe          the recipe to execute
     * @param times           how many times to execute
     * @param boundInputs     the resolved input bindings
     * @param expectedOutputs the expected output bindings
     * @return the created action
     */
    public PlanAction.ExecuteRecipe addExecuteRecipe(
        RecipeEntry<?> recipe,
        int times,
        ImmutableList<PlanAction.InputBinding> boundInputs,
        ImmutableList<PlanAction.OutputBinding> expectedOutputs
    ) {
        int index = actions.size();
        var action = new PlanAction.ExecuteRecipe(
            nextActionId++,
            index,
            false,
            recipe,
            times,
            boundInputs,
            expectedOutputs
        );
        actions.add(action);
        return action;
    }

    /** Returns an immutable snapshot of the actions accumulated so far. */
    public ImmutableList<PlanAction> actionsView() {
        return actions.toImmutable();
    }

    public Snapshot snapshot() {
        return new Snapshot(actions.size(), nextActionId);
    }

    public void restore(Snapshot snapshot) {
        Checks.checkNotNull(snapshot, "snapshot");
        while (actions.size() > snapshot.actionCount()) {
            actions.remove(actions.size() - 1);
        }
        nextActionId = snapshot.nextActionId();
    }

    /** Returns {@code true} if at least one action has been added. */
    public boolean hasActions() {
        return actions.notEmpty();
    }

    /**
     * Freezes the accumulated actions into an immutable {@link RoutingPlan}.
     *
     * @return the completed plan
     */
    public RoutingPlan build() {
        return new RoutingPlan(planVersion, tagSnapshotVersion, targetRecipe, targetTimes, actions.toImmutable());
    }

    public record Snapshot(int actionCount, int nextActionId) {
        public Snapshot {
            Checks.checkArgument(actionCount >= 0, "actionCount must be non-negative");
            Checks.checkArgument(nextActionId >= 0, "nextActionId must be non-negative");
        }
    }
}


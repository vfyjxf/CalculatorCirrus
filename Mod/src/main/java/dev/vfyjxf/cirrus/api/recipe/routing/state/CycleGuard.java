package dev.vfyjxf.cirrus.api.recipe.routing.state;

import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.Nullable;

/**
 * Guards against uncontrolled cyclic expansion during planning.
 *
 * <p>When the recipe graph contains cycles (recipes whose inputs are produced by
 * their own outputs, directly or transitively), the planner may re-enter the same
 * recipe while it is still on the active path. {@code CycleGuard} tracks that
 * active path and rejects expansion when:
 * <ul>
 *     <li>The {@link LoopPolicy} forbids re-entry entirely
 *         ({@link LoopPolicy#forbidAmplification}).</li>
 *     <li>The amplification risk exceeds the hard threshold.</li>
 *     <li>The same recipe appears too many times on the active path.</li>
 *     <li>The global loop expansion count exceeds the budget.</li>
 * </ul>
 *
 * <p>The guard uses a stack-based approach: recipes are
 * {@linkplain #enter(int, boolean, double, double) entered} before expansion and
 * {@linkplain #leave(int) left} afterwards, maintaining correct nesting.
 *
 * @see LoopPolicy
 * @see LoopBudget
 */
public final class CycleGuard {

    private final LoopPolicy loopPolicy;
    private final LoopBudget loopBudget;
    private final IntArrayList stack = new IntArrayList();
    private final Int2IntOpenHashMap activeRecipes = new Int2IntOpenHashMap();
    private int globalLoopExpansions;

    /**
     * Creates a new cycle guard with the given policy and budget.
     *
     * @param loopPolicy the policy governing cycle behavior
     * @param loopBudget the budget limits for cyclic expansion
     */
    public CycleGuard(LoopPolicy loopPolicy, LoopBudget loopBudget) {
        this.loopPolicy = Checks.checkNotNull(loopPolicy, "loopPolicy");
        this.loopBudget = Checks.checkNotNull(loopBudget, "loopBudget");
    }

    /**
     * Attempts to enter (expand) a recipe node.
     *
     * <p>Returns a {@link Decision} indicating whether expansion is allowed.
     * If allowed, the recipe is pushed onto the active path and its repeat
     * count is incremented. The caller <b>must</b> call {@link #leave(int)}
     * when expansion completes.
     *
     * @param recipeIndex       the integer index of the recipe
     * @param cycleReentry      whether this recipe is already active on the path
     * @param amplificationRisk estimated risk of infinite amplification in [0, 1]
     * @param hardThreshold     the amplification risk threshold from the rule profile
     * @return a decision indicating whether expansion is allowed
     */
    public Decision enter(int recipeIndex, boolean cycleReentry, double amplificationRisk, double hardThreshold) {
        if (cycleReentry) {
            if (loopPolicy == LoopPolicy.forbidAmplification) {
                return Decision.reject(RoutingFailureCode.cycleAmplificationBlocked, "cycle_reentry_blocked");
            }
            if (amplificationRisk >= hardThreshold && loopPolicy != LoopPolicy.allowUnbounded) {
                return Decision.reject(RoutingFailureCode.cycleAmplificationBlocked, "amplification_threshold");
            }
        }

        int nextGlobalLoopExpansions = globalLoopExpansions + (cycleReentry ? 1 : 0);
        if (nextGlobalLoopExpansions > loopBudget.maxLoopExpansionsGlobal() && loopPolicy != LoopPolicy.allowUnbounded) {
            return Decision.reject(RoutingFailureCode.cycleBudgetExhausted, "global_loop_budget");
        }

        int nextActiveRepeats = activeRecipes.get(recipeIndex) + 1;
        if (nextActiveRepeats > loopBudget.maxActiveRepeatsPerRecipe() && loopPolicy != LoopPolicy.allowUnbounded) {
            return Decision.reject(RoutingFailureCode.cycleBudgetExhausted, "recipe_loop_budget");
        }

        globalLoopExpansions = nextGlobalLoopExpansions;
        stack.push(recipeIndex);
        activeRecipes.put(recipeIndex, nextActiveRepeats);
        return Decision.accept();
    }

    /**
     * Leaves (pops) a previously entered recipe from the active path.
     *
     * @param recipeIndex the recipe index to leave
     */
    public void leave(int recipeIndex) {
        if (!stack.isEmpty() && stack.topInt() == recipeIndex) {
            stack.popInt();
        }
        decrement(activeRecipes, recipeIndex);
    }

    private static void decrement(Int2IntOpenHashMap map, int key) {
        int value = map.get(key);
        if (value <= 1) {
            map.remove(key);
        } else {
            map.put(key, value - 1);
        }
    }

    /**
     * Result of a cycle-guard entry check.
     *
     * @param allowed     whether the expansion was permitted
     * @param failureCode the failure code if rejected; {@code null} if allowed
     * @param reason      human-readable reason for rejection; {@code null} if allowed
     */
    public record Decision(
        boolean allowed,
        @Nullable RoutingFailureCode failureCode,
        @Nullable String reason
    ) {
        public static Decision accept() {
            return new Decision(true, null, null);
        }

        public static Decision reject(RoutingFailureCode code, String reason) {
            return new Decision(false, code, reason);
        }
    }
}

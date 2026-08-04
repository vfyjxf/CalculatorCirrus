package dev.vfyjxf.cirrus.api.recipe.routing.state;

import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.LoopPolicy;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingFailureCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CycleGuardTest {

    @Test
    void forbidAmplificationRejectsExactActiveRecipeReentry() {
        CycleGuard guard = new CycleGuard(LoopPolicy.forbidAmplification, new LoopBudget(2, 10));

        assertTrue(guard.enter(7, false, 0.05, 0.85).allowed());

        CycleGuard.Decision decision = guard.enter(7, true, 0.50, 0.85);
        assertFalse(decision.allowed());
        assertEquals(RoutingFailureCode.cycleAmplificationBlocked, decision.failureCode());
        assertEquals("cycle_reentry_blocked", decision.reason());

        guard.leave(7);
    }

    @Test
    void boundedAmplificationLimitsActiveRepeatsPerRecipe() {
        CycleGuard guard = new CycleGuard(LoopPolicy.allowBoundedAmplification, new LoopBudget(2, 10));

        assertTrue(guard.enter(3, false, 0.05, 1.0).allowed());
        assertTrue(guard.enter(3, true, 0.50, 1.0).allowed());

        CycleGuard.Decision decision = guard.enter(3, true, 0.50, 1.0);
        assertFalse(decision.allowed());
        assertEquals(RoutingFailureCode.cycleBudgetExhausted, decision.failureCode());
        assertEquals("recipe_loop_budget", decision.reason());

        guard.leave(3);
        guard.leave(3);
    }

    @Test
    void boundedAmplificationLimitsGlobalLoopExpansions() {
        CycleGuard guard = new CycleGuard(LoopPolicy.allowBoundedAmplification, new LoopBudget(10, 1));

        assertTrue(guard.enter(1, false, 0.05, 1.0).allowed());
        assertTrue(guard.enter(1, true, 0.50, 1.0).allowed());
        assertTrue(guard.enter(2, false, 0.05, 1.0).allowed());

        CycleGuard.Decision decision = guard.enter(2, true, 0.50, 1.0);
        assertFalse(decision.allowed());
        assertEquals(RoutingFailureCode.cycleBudgetExhausted, decision.failureCode());
        assertEquals("global_loop_budget", decision.reason());

        guard.leave(2);
        guard.leave(1);
        guard.leave(1);
    }

    @Test
    void boundedAmplificationStillHonorsHardRiskThreshold() {
        CycleGuard guard = new CycleGuard(LoopPolicy.allowBoundedAmplification, new LoopBudget(10, 10));

        assertTrue(guard.enter(5, false, 0.05, 0.85).allowed());

        CycleGuard.Decision decision = guard.enter(5, true, 0.90, 0.85);
        assertFalse(decision.allowed());
        assertEquals(RoutingFailureCode.cycleAmplificationBlocked, decision.failureCode());
        assertEquals("amplification_threshold", decision.reason());

        guard.leave(5);
    }

    @Test
    void nonReentryExpansionsDoNotConsumeGlobalLoopBudget() {
        CycleGuard guard = new CycleGuard(LoopPolicy.allowBoundedAmplification, new LoopBudget(1, 1));

        assertTrue(guard.enter(1, false, 0.05, 0.85).allowed());
        assertTrue(guard.enter(2, false, 0.07, 0.85).allowed());
        assertTrue(guard.enter(3, false, 0.09, 0.85).allowed());

        guard.leave(3);
        guard.leave(2);
        guard.leave(1);
    }

    @Test
    void recipeRepeatBudgetUsesCurrentActivePathNotTotalRunCount() {
        CycleGuard guard = new CycleGuard(LoopPolicy.allowBoundedAmplification, new LoopBudget(1, 1));

        assertTrue(guard.enter(9, false, 0.05, 0.85).allowed());
        guard.leave(9);

        assertTrue(guard.enter(9, false, 0.05, 0.85).allowed());
        guard.leave(9);
    }
}

package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.Nullable;

/**
 * Structured payload attached to a {@link RoutingIssue}.
 *
 * <p>Payloads own the conversion from typed routing semantics into translatable
 * argument arrays and machine-readable detail maps. Callers should prefer
 * payload access over interpreting raw string arguments.
 */
public sealed interface RoutingIssuePayload permits
    RoutingIssuePayload.Empty,
    RoutingIssuePayload.InputShortage,
    RoutingIssuePayload.CapabilityGate,
    RoutingIssuePayload.BudgetExhaustion {

    ImmutableMap<String, String> messageArgs();

    Object[] translationArgs();

    ImmutableMap<String, String> details();

    record Empty() implements RoutingIssuePayload {
        @Override
        public ImmutableMap<String, String> messageArgs() {
            return Maps.immutable.empty();
        }

        @Override
        public Object[] translationArgs() {
            return new Object[0];
        }

        @Override
        public ImmutableMap<String, String> details() {
            return Maps.immutable.empty();
        }
    }

    record InputShortage(
        String input,
        String visibleRecipe,
        long amount,
        @Nullable String actualRecipe
    ) implements RoutingIssuePayload {
        public InputShortage {
            Checks.checkNotNull(input, "input");
            Checks.checkNotNull(visibleRecipe, "visibleRecipe");
            Checks.checkArgument(amount > 0, "amount must be positive");
        }

        public InputShortage withVisibleRecipe(String recipe) {
            return new InputShortage(input, recipe, amount, actualRecipe);
        }

        public InputShortage withActualRecipe(@Nullable String recipe) {
            return new InputShortage(input, visibleRecipe, amount, recipe);
        }

        @Override
        public ImmutableMap<String, String> messageArgs() {
            return Maps.immutable.of(
                "arg0_input", input,
                "arg1_recipe", visibleRecipe,
                "arg2_amount", Long.toString(amount)
            );
        }

        @Override
        public Object[] translationArgs() {
            return new Object[]{input, visibleRecipe, amount};
        }

        @Override
        public ImmutableMap<String, String> details() {
            var details = Maps.mutable.<String, String>ofInitialCapacity(actualRecipe == null ? 3 : 4);
            details.put("context.input", input);
            details.put("context.recipe", visibleRecipe);
            details.put("context.amount", Long.toString(amount));
            if (actualRecipe != null) {
                details.put("context.actual_recipe", actualRecipe);
            }
            return details.toImmutable();
        }
    }

    record CapabilityGate(String missingCapabilities) implements RoutingIssuePayload {
        public CapabilityGate {
            Checks.checkNotNull(missingCapabilities, "missingCapabilities");
        }

        @Override
        public ImmutableMap<String, String> messageArgs() {
            return Maps.immutable.of("missing", missingCapabilities);
        }

        @Override
        public Object[] translationArgs() {
            return new Object[]{missingCapabilities};
        }

        @Override
        public ImmutableMap<String, String> details() {
            return Maps.immutable.of("context.missing_capabilities", missingCapabilities);
        }
    }

    record BudgetExhaustion(
        String stopReason,
        String recipe,
        String input,
        int depth,
        long remaining,
        boolean activeCycleCandidate
    ) implements RoutingIssuePayload {
        public BudgetExhaustion {
            Checks.checkNotNull(stopReason, "stopReason");
            Checks.checkNotNull(recipe, "recipe");
            Checks.checkNotNull(input, "input");
        }

        @Override
        public ImmutableMap<String, String> messageArgs() {
            return Maps.immutable.of(
                "reason", stopReason,
                "recipe", recipe,
                "input", input
            );
        }

        @Override
        public Object[] translationArgs() {
            return new Object[]{stopReason, recipe, input};
        }

        @Override
        public ImmutableMap<String, String> details() {
            var details = Maps.mutable.<String, String>ofInitialCapacity(6);
            details.put("context.budget_stop", stopReason);
            details.put("context.recipe", recipe);
            details.put("context.input", input);
            details.put("context.depth", Integer.toString(depth));
            details.put("context.remaining", Long.toString(remaining));
            details.put("context.active_cycle_candidate", Boolean.toString(activeCycleCandidate));
            return details.toImmutable();
        }
    }
}

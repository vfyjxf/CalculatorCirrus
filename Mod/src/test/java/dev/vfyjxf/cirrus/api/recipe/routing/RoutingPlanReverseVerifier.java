package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.factory.Maps;

/**
 * Reverse+forward validator for generated routing plans.
 *
 * <p>Forward replay validates executability under the same matching semantics used by the planner.
 * Reverse closure validates that execution steps form a closed dependency chain from target outputs.
 */
final class RoutingPlanReverseVerifier {

    private RoutingPlanReverseVerifier() {
    }

    static VerificationReport verify(RoutingRequest request, RoutingOutcome outcome, RecipeGraph graph) {
        MutableList<String> errors = Lists.mutable.empty();
        MutableList<String> warnings = Lists.mutable.empty();

        RoutingPlan plan = outcome.plan();
        if (plan == null) {
            errors.add("outcome has no plan");
            return new VerificationReport(errors.toImmutable(), warnings.toImmutable());
        }

        MutableMap<RecipeEntry<?>, RecipeGraph.Node> recipeNodeMap = Maps.mutable.empty();
        for (RecipeGraph.Node node : graph.nodes()) {
            recipeNodeMap.put(node.recipe(), node);
        }

        validateActionOrder(plan, errors);
        validateForwardReplay(request, plan, recipeNodeMap, errors);
        validateReverseClosure(request, plan, recipeNodeMap, errors, warnings);
        return new VerificationReport(errors.toImmutable(), warnings.toImmutable());
    }

    private static void validateActionOrder(RoutingPlan plan, MutableList<String> errors) {
        for (int i = 0; i < plan.actions().size(); i++) {
            PlanAction action = plan.actions().get(i);
            if (action.actionId() != i) {
                errors.add("actionId is not contiguous at index " + i + ": " + action.actionId());
            }
            if (action.actionIndex() != i) {
                errors.add("actionIndex is not contiguous at index " + i + ": " + action.actionIndex());
            }
        }
    }

    private static void validateForwardReplay(
        RoutingRequest request,
        RoutingPlan plan,
        MutableMap<RecipeEntry<?>, RecipeGraph.Node> recipeNodeMap,
        MutableList<String> errors
    ) {
        Inventory inventory = Inventory.fromStock(request.stock());
        long targetRecipeExecTimes = 0;

        for (PlanAction action : plan.actions()) {
            switch (action) {
                case PlanAction.ConsumeStockAction consume -> {
                    long consumed = inventory.consumeExact(consume.resource(), consume.amount());
                    if (consumed < consume.amount()) {
                        errors.add("consume action underflow: need " + consume.amount() + ", got " + consumed);
                    }
                }
                case PlanAction.ReserveCatalystAction reserve -> {
                    long available = inventory.countByInput(reserve.input());
                    if (available < reserve.amount()) {
                        errors.add("reserve catalyst unavailable: need " + reserve.amount() + ", got " + available);
                    }
                }
                case PlanAction.ExecuteRecipeAction exec -> {
                    RecipeGraph.Node node = recipeNodeMap.get(exec.recipe());
                    if (node == null) {
                        errors.add("execute references recipe not in graph: " + exec.recipe());
                        continue;
                    }
                    if (exec.recipe().equals(plan.targetRecipe())) {
                        targetRecipeExecTimes += exec.times();
                    }

                    validateExecuteBindings(exec, node, errors);
                    for (PlanAction.InputBinding inputBinding : exec.boundInputs()) {
                        if (inputBinding.input().notConsumed()) {
                            long available = inventory.countByInput(inputBinding.input());
                            if (available < inputBinding.amount()) {
                                errors.add("catalyst input unavailable in execute: need " + inputBinding.amount() + ", got " + available);
                            }
                            continue;
                        }
                        long consumed = inventory.consumeByInput(inputBinding.input(), inputBinding.amount());
                        if (consumed < inputBinding.amount()) {
                            errors.add("execute input underflow: need " + inputBinding.amount() + ", got " + consumed);
                        }
                    }

                    for (PlanAction.OutputBinding outputBinding : exec.expectedOutputs()) {
                        inventory.add(outputBinding.resource(), outputBinding.amount());
                    }
                }
            }
        }

        if (targetRecipeExecTimes < plan.targetTimes()) {
            errors.add("target recipe executed " + targetRecipeExecTimes + " times, expected at least " + plan.targetTimes());
        }
    }

    private static void validateExecuteBindings(
        PlanAction.ExecuteRecipeAction exec,
        RecipeGraph.Node node,
        MutableList<String> errors
    ) {
        for (PlanAction.InputBinding inputBinding : exec.boundInputs()) {
            boolean matched = false;
            for (InputContent recipeInput : node.mergedInputs()) {
                if (recipeInput.mergeable(inputBinding.input()) && inputBinding.amount() == recipeInput.amount() * exec.times()) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                errors.add("execute input binding does not match recipe declaration for " + exec.recipe());
            }
        }

        for (PlanAction.OutputBinding outputBinding : exec.expectedOutputs()) {
            boolean matched = false;
            for (OutputContent recipeOutput : node.mergedOutputs()) {
                long expectedAmount = recipeOutput.amount() * exec.times();
                if (outputBinding.amount() == expectedAmount &&
                    recipeOutput.asResource().matches(outputBinding.resource(), MatchStrategy.identityAndComponent)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                errors.add("execute output binding does not match recipe declaration for " + exec.recipe());
            }
        }
    }

    private static void validateReverseClosure(
        RoutingRequest request,
        RoutingPlan plan,
        MutableMap<RecipeEntry<?>, RecipeGraph.Node> recipeNodeMap,
        MutableList<String> errors,
        MutableList<String> warnings
    ) {
        MutableList<Demand> demands = Lists.mutable.empty();
        RecipeGraph.Node targetNode = recipeNodeMap.get(plan.targetRecipe());
        if (targetNode == null) {
            errors.add("target recipe not found in graph during reverse closure");
            return;
        }

        for (OutputContent output : targetNode.mergedOutputs()) {
            long amount = output.amount() * plan.targetTimes();
            if (amount > 0) {
                demands.add(Demand.exact(output.asResource(), amount, "targetOutput"));
            }
        }

        for (int i = plan.actions().size() - 1; i >= 0; i--) {
            PlanAction action = plan.actions().get(i);
            switch (action) {
                case PlanAction.ConsumeStockAction consume -> consumeDemand(demands, consume.resource(), consume.amount());
                case PlanAction.ReserveCatalystAction ignored -> {
                    // Reservation is validated in forward replay; reverse closure tracks consumed flows only.
                }
                case PlanAction.ExecuteRecipeAction exec -> {
                    long usefulProduced = 0;
                    for (PlanAction.OutputBinding outputBinding : exec.expectedOutputs()) {
                        usefulProduced += consumeDemand(demands, outputBinding.resource(), outputBinding.amount());
                    }
                    if (usefulProduced == 0 && !exec.recipe().equals(plan.targetRecipe())) {
                        warnings.add("orphan execute candidate detected: " + exec.recipe());
                    }
                    for (PlanAction.InputBinding inputBinding : exec.boundInputs()) {
                        if (inputBinding.input().consumed()) {
                            demands.add(Demand.byInput(inputBinding.input(), inputBinding.amount(), "executeInput"));
                        }
                    }
                }
            }
        }

        Inventory initialInventory = Inventory.fromStock(request.stock());
        long unresolved = 0;
        for (Demand demand : demands) {
            if (demand.remaining <= 0) {
                continue;
            }
            long consumed = initialInventory.consumeDemand(demand.matcher, demand.remaining);
            demand.remaining -= consumed;
            if (demand.remaining > 0) {
                unresolved += demand.remaining;
            }
        }

        if (unresolved > 0) {
            errors.add("reverse closure has unresolved demand: " + unresolved);
        }
    }

    private static long consumeDemand(MutableList<Demand> demands, ContentResource resource, long amount) {
        long remaining = amount;
        for (Demand demand : demands) {
            if (remaining <= 0) {
                break;
            }
            if (!demand.matcher.matches(resource) || demand.remaining <= 0) {
                continue;
            }
            long consumed = Math.min(demand.remaining, remaining);
            demand.remaining -= consumed;
            remaining -= consumed;
        }
        return amount - remaining;
    }

    static final class VerificationReport {
        private final ImmutableList<String> errors;
        private final ImmutableList<String> warnings;

        private VerificationReport(ImmutableList<String> errors, ImmutableList<String> warnings) {
            this.errors = errors;
            this.warnings = warnings;
        }

        boolean valid() {
            return errors.isEmpty();
        }

        ImmutableList<String> errors() {
            return errors;
        }

        ImmutableList<String> warnings() {
            return warnings;
        }
    }

    private static final class Demand {
        private final Matcher matcher;
        private long remaining;
        @SuppressWarnings("unused")
        private final String source;

        private Demand(Matcher matcher, long remaining, String source) {
            this.matcher = matcher;
            this.remaining = remaining;
            this.source = source;
        }

        static Demand exact(ContentResource resource, long amount, String source) {
            return new Demand(new ExactMatcher(resource), amount, source);
        }

        static Demand byInput(InputContent input, long amount, String source) {
            return new Demand(new InputMatcher(input), amount, source);
        }
    }

    private interface Matcher {
        boolean matches(ContentResource resource);
    }

    private static final class ExactMatcher implements Matcher {
        private final ContentResource expected;

        private ExactMatcher(ContentResource expected) {
            this.expected = normalize(expected);
        }

        @Override
        public boolean matches(ContentResource resource) {
            ContentResource normalized = normalize(resource);
            return expected.matches(normalized, MatchStrategy.identityAndComponent) ||
                   normalized.matches(expected, MatchStrategy.identityAndComponent);
        }
    }

    private static final class InputMatcher implements Matcher {
        private final InputContent input;

        private InputMatcher(InputContent input) {
            this.input = input;
        }

        @Override
        public boolean matches(ContentResource resource) {
            return input.satisfy(resource);
        }
    }

    private static final class Inventory {
        private final MutableList<ResourceBucket> buckets = Lists.mutable.empty();

        static Inventory fromStock(Stock stock) {
            Inventory inventory = new Inventory();
            for (ContentStack<?, ?> stack : stock.getStacks()) {
                long amount = stock.countMatching(stack);
                inventory.add(stack, amount);
            }
            return inventory;
        }

        void add(ContentResource resource, long amount) {
            if (amount <= 0) {
                return;
            }
            ContentResource normalized = normalize(resource);
            for (ResourceBucket bucket : buckets) {
                if (bucket.resource.matches(normalized, MatchStrategy.identityAndComponent)) {
                    bucket.amount += amount;
                    return;
                }
            }
            buckets.add(new ResourceBucket(normalized, amount));
        }

        long consumeExact(ContentResource resource, long amount) {
            if (amount <= 0) {
                return 0;
            }
            ContentResource normalized = normalize(resource);
            for (ResourceBucket bucket : buckets) {
                if (!bucket.resource.matches(normalized, MatchStrategy.identityAndComponent)) {
                    continue;
                }
                long consumed = Math.min(bucket.amount, amount);
                bucket.amount -= consumed;
                return consumed;
            }
            return 0;
        }

        long consumeByInput(InputContent input, long amount) {
            if (amount <= 0) {
                return 0;
            }
            long remaining = amount;
            for (ResourceBucket bucket : buckets) {
                if (remaining <= 0) {
                    break;
                }
                if (!input.satisfy(bucket.resource)) {
                    continue;
                }
                long consumed = Math.min(bucket.amount, remaining);
                bucket.amount -= consumed;
                remaining -= consumed;
            }
            return amount - remaining;
        }

        long countByInput(InputContent input) {
            long total = 0;
            for (ResourceBucket bucket : buckets) {
                if (input.satisfy(bucket.resource)) {
                    total += bucket.amount;
                }
            }
            return total;
        }

        long consumeDemand(Matcher matcher, long amount) {
            if (amount <= 0) {
                return 0;
            }
            long remaining = amount;
            for (ResourceBucket bucket : buckets) {
                if (remaining <= 0) {
                    break;
                }
                if (!matcher.matches(bucket.resource)) {
                    continue;
                }
                long consumed = Math.min(bucket.amount, remaining);
                bucket.amount -= consumed;
                remaining -= consumed;
            }
            return amount - remaining;
        }
    }

    private static ContentResource normalize(ContentResource resource) {
        if (resource instanceof ContentStack<?, ?> stack) {
            return stack.normalize();
        }
        return resource.normalize();
    }

    private static final class ResourceBucket {
        private final ContentResource resource;
        private long amount;

        private ResourceBucket(ContentResource resource, long amount) {
            this.resource = resource;
            this.amount = amount;
        }
    }
}


package dev.vfyjxf.cirrus.api.recipe.routing.evaluator;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.RoutingContext;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Collection;

public final class CompositeEvaluator {

    public static <T extends RoutingEvaluator> WightedEvaluator<T> wighted(double weight, T evaluator) {
        return new WightedEvaluator<>(evaluator, weight);
    }

    public record WightedEvaluator<T extends RoutingEvaluator>(T evaluator, double weight) {}

    public static ContentEvaluator content(ImmutableList<WightedEvaluator<ContentEvaluator>> evaluators) {
        return new Content(evaluators);
    }

    @SafeVarargs
    public static ContentEvaluator content(WightedEvaluator<ContentEvaluator>... evaluators) {
        return content(Lists.immutable.of(evaluators));
    }

    public static ContentEvaluator content(Collection<? extends WightedEvaluator<ContentEvaluator>> evaluators) {
        return content(Lists.immutable.ofAll(evaluators));
    }

    public static RecipeEvaluator recipe(ImmutableList<WightedEvaluator<RecipeEvaluator>> evaluators) {
        return new Recipe(evaluators);
    }

    @SafeVarargs
    public static RecipeEvaluator recipe(WightedEvaluator<RecipeEvaluator>... evaluators) {
        return recipe(Lists.immutable.of(evaluators));
    }

    public static RecipeEvaluator recipe(Collection<? extends WightedEvaluator<RecipeEvaluator>> evaluators) {
        return recipe(Lists.immutable.ofAll(evaluators));
    }

    private record Content(
        ImmutableList<WightedEvaluator<ContentEvaluator>> evaluators
    ) implements ContentEvaluator {

        public Content(ImmutableList<WightedEvaluator<ContentEvaluator>> evaluators) {
            this.evaluators = evaluators.reject(e -> e.weight == 0);
        }

        @Override
        public double getCost(ContentStack<?, ?> stack, RoutingContext context) {
            double result = noContribution;
            for (var evaluator : evaluators) {
                double cost = evaluator.evaluator.getCost(stack, context);
                if (cost == impossible) return impossible;
                result += evaluator.weight * cost;
            }
            return result;
        }
    }

    private record Recipe(
        ImmutableList<WightedEvaluator<RecipeEvaluator>> evaluators
    ) implements RecipeEvaluator {

        public Recipe(ImmutableList<WightedEvaluator<RecipeEvaluator>> evaluators) {
            this.evaluators = evaluators.reject(e -> e.weight == 0);
        }

        @Override
        public double getCost(RecipeGraph.Node recipe, int times, RoutingContext context) {
            double result = noContribution;
            for (var evaluator : evaluators) {
                double cost = evaluator.evaluator.getCost(recipe, times, context);
                if (cost == impossible) return impossible;
                result += evaluator.weight * cost;
            }
            return result;
        }
    }


    private CompositeEvaluator() {}
}

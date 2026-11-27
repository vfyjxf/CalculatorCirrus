package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cloudlib.util.Checks;

import java.util.function.Predicate;

/**
 * Extensible query condition for {@link RecipeLookup}.
 *
 * <p>Modders and custom {@link RecipeSystem}s can define reusable typed conditions (e.g., by machine tier,
 * energy budget, cooking time, tag, or custom recipe property) and compose them via {@link RecipeLookup#withCondition}.
 */
@FunctionalInterface
public interface RecipeLookupCondition<R> extends Predicate<R> {

    @Override
    boolean test(R recipe);

    default RecipeLookupCondition<R> and(RecipeLookupCondition<? super R> other) {
        Checks.checkNotNull(other, "other");
        return recipe -> test(recipe) && other.test(recipe);
    }

    default RecipeLookupCondition<R> or(RecipeLookupCondition<? super R> other) {
        Checks.checkNotNull(other, "other");
        return recipe -> test(recipe) || other.test(recipe);
    }

    @Override
    default RecipeLookupCondition<R> negate() {
        return recipe -> !test(recipe);
    }

    static <R> RecipeLookupCondition<R> always() {
        return ignored -> true;
    }

    static <R> RecipeLookupCondition<R> never() {
        return ignored -> false;
    }
}

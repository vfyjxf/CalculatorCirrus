package dev.vfyjxf.calculatorcirrus.api.recipe.ingredient;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;
import org.jetbrains.annotations.Range;

public sealed interface RecipeContent permits InputContent, OutputContent {

    //region constants

    EmptyRecipeContent empty = new EmptyRecipeContent();

    float MAX_CHANCE = 1f;
    float MIN_CHANCE = 0f;

    static EmptyRecipeContent empty() {
        return empty;
    }

    //endregion

    //region basic info

    boolean isEmpty();

    default boolean notEmpty() {
        return !isEmpty();
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    long amount();

    @Range(from = 0, to = 1)
    float chance();

    //endregion

    //region copy

    RecipeContent copy();

    RecipeContent copyWithAmount(long amount);

    RecipeContent copyWithChance(float chance);

    RecipeContent copyWithAmountAndChance(long amount, float chance);

    //endregion

    //region match

    boolean satisfy(ContentValue<?> value);

    boolean satisfy(ContentStack<?, ?> stack, MatchStrategy strategy);

    /**
     * @param other the content to match
     * @return 该RecipeContent与给定的RecipeContent是否在合成上等价，用于统计物料总量的筛选匹配
     */
    boolean equivalent(RecipeContent other);

    //endregion

}

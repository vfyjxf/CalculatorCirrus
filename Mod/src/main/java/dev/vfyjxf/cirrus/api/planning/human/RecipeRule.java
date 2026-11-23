package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;

import java.util.Objects;

public record RecipeRule(
        String ruleId,
        GuidanceScope scope,
        RuleStrength strength,
        CompiledRecipeKey recipeKey
) implements GuidanceRule {

    public RecipeRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(recipeKey, "recipeKey");
    }
}

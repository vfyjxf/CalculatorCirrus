package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record CycleBreakRule(
        String ruleId,
        GuidanceScope scope,
        RuleStrength strength,
        ResourceKey targetResource,
        CompiledRecipeKey bannedSourceRecipe
) implements GuidanceRule {

    public CycleBreakRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(targetResource, "targetResource");
        Objects.requireNonNull(bannedSourceRecipe, "bannedSourceRecipe");
    }
}

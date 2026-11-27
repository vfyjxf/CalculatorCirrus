package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.Objects;

public record CostOverrideRule(
    String ruleId,
    GuidanceScope scope,
    RuleStrength strength,
    ResourceKey resource,
    CostVector overrideUnitCost
) implements GuidanceRule {

    public CostOverrideRule {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(strength, "strength");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(overrideUnitCost, "overrideUnitCost");
    }
}

package dev.vfyjxf.cirrus.api.planning.human;

import java.util.List;
import java.util.Objects;

public record ApplyRulesFeedback(
        List<GuidanceRule> rules,
        boolean persistToWorld
) implements HumanFeedback {

    public ApplyRulesFeedback {
        Objects.requireNonNull(rules, "rules");
        rules = List.copyOf(rules);
    }
}

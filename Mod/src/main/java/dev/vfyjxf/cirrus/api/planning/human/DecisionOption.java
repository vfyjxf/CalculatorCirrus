package dev.vfyjxf.cirrus.api.planning.human;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DecisionOption(
        String optionId,
        String labelKey,
        CostVector estimatedDelta,
        List<GuidanceRule> compiledRules,
        Optional<HumanFeedback> followUp
) {

    public DecisionOption(
            String optionId,
            String labelKey,
            CostVector estimatedDelta,
            List<GuidanceRule> compiledRules
    ) {
        this(optionId, labelKey, estimatedDelta, compiledRules, Optional.empty());
    }

    public DecisionOption {
        Objects.requireNonNull(optionId, "optionId");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(estimatedDelta, "estimatedDelta");
        Objects.requireNonNull(compiledRules, "compiledRules");
        Objects.requireNonNull(followUp, "followUp");
        compiledRules = List.copyOf(compiledRules);
    }
}

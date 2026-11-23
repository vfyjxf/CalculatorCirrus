package dev.vfyjxf.cirrus.api.planning.human;

import java.util.List;
import java.util.Objects;

public record DecisionPrompt(
        String promptId,
        DecisionPromptKind kind,
        String titleKey,
        String explanationKey,
        double leverageScore,
        List<DecisionOption> options
) {

    public DecisionPrompt {
        Objects.requireNonNull(promptId, "promptId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(explanationKey, "explanationKey");
        Objects.requireNonNull(options, "options");
        if (!Double.isFinite(leverageScore) || leverageScore < 0.0 || leverageScore > 1.0) {
            throw new IllegalArgumentException("leverageScore must be in [0, 1], got " + leverageScore);
        }
        options = List.copyOf(options);
        if (options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("DecisionPrompt must have 2..4 options, got " + options.size());
        }
    }
}

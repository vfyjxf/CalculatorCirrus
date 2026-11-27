package dev.vfyjxf.cirrus.api.planning.human;

import java.util.List;
import java.util.Objects;

public record DecisionPrompt(
    String promptId,
    QuestionKey questionKey,
    DecisionPromptKind kind,
    String titleKey,
    String explanationKey,
    double leverageScore,
    VoIEstimate voiEstimate,
    GuidanceScope defaultScope,
    List<DecisionOption> options
) {

    public DecisionPrompt(
        String promptId,
        DecisionPromptKind kind,
        String titleKey,
        String explanationKey,
        double leverageScore,
        List<DecisionOption> options
    ) {
        this(
            promptId,
            new QuestionKey(kind, promptId, defaultScopeFor(kind), 0L),
            kind,
            titleKey,
            explanationKey,
            leverageScore,
            VoIEstimate.zero,
            defaultScopeFor(kind),
            options
        );
    }

    public DecisionPrompt {
        Objects.requireNonNull(promptId, "promptId");
        Objects.requireNonNull(questionKey, "questionKey");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(titleKey, "titleKey");
        Objects.requireNonNull(explanationKey, "explanationKey");
        Objects.requireNonNull(voiEstimate, "voiEstimate");
        Objects.requireNonNull(defaultScope, "defaultScope");
        Objects.requireNonNull(options, "options");
        if (!Double.isFinite(leverageScore) || leverageScore < 0.0 || leverageScore > 1.0) {
            throw new IllegalArgumentException("leverageScore must be in [0, 1], got " + leverageScore);
        }
        options = List.copyOf(options);
        if (options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("DecisionPrompt must have 2..4 options, got " + options.size());
        }
    }

    public static GuidanceScope defaultScopeFor(DecisionPromptKind kind) {
        return switch (kind) {
            case machineAvailability, baseOrSupplyBoundary, reusableCatalystCheck, materialFamilyAnchor ->
                GuidanceScope.worldSave;
            case suspiciousJeiVerify -> GuidanceScope.modpackDefault;
            case fieldTripConfirm, ruleConflictResolution -> GuidanceScope.session;
        };
    }
}

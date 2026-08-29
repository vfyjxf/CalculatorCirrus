package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.recipe.rule.ScoreModel;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleEngine;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleSpec;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleStage;
import dev.vfyjxf.cirrus.api.recipe.rule.RuleAction;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import dev.vfyjxf.cirrus.recipe.routing.rule.CandidateRuleClauses;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record RuleProfileSpec(
    Namespace profileId,
    RuleProfilePreset preset,
    Optional<ScoreModel> scoreModel,
    UnresolvedInputMode unresolvedInputMode,
    int shortlistK,
    int rollbackWidth,
    double nearTieEpsilon,
    double amplificationSoftThreshold,
    double amplificationHardThreshold,
    boolean liftMultiSemanticChildShortages
) {
    public static final Codec<RuleProfileSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("profile_id").forGetter(RuleProfileSpec::profileId),
        RuleProfilePreset.CODEC.fieldOf("preset").forGetter(RuleProfileSpec::preset),
        ScoreModel.CODEC.optionalFieldOf("score_model").forGetter(RuleProfileSpec::scoreModel),
        UnresolvedInputMode.CODEC.fieldOf("unresolved_input_mode").forGetter(RuleProfileSpec::unresolvedInputMode),
        Codec.INT.fieldOf("shortlist_k").forGetter(RuleProfileSpec::shortlistK),
        Codec.INT.fieldOf("rollback_width").forGetter(RuleProfileSpec::rollbackWidth),
        Codec.DOUBLE.fieldOf("near_tie_epsilon").forGetter(RuleProfileSpec::nearTieEpsilon),
        Codec.DOUBLE.fieldOf("amplification_soft_threshold").forGetter(RuleProfileSpec::amplificationSoftThreshold),
        Codec.DOUBLE.fieldOf("amplification_hard_threshold").forGetter(RuleProfileSpec::amplificationHardThreshold),
        Codec.BOOL.fieldOf("lift_multi_semantic_child_shortages").forGetter(RuleProfileSpec::liftMultiSemanticChildShortages)
    ).apply(instance, RuleProfileSpec::new));

    public RuleProfileSpec {
        Checks.checkNotNull(profileId, "profileId");
        Checks.checkNotNull(preset, "preset");
        scoreModel = scoreModel == null ? Optional.empty() : scoreModel;
        Checks.checkNotNull(unresolvedInputMode, "unresolvedInputMode");
        Checks.checkArgument(shortlistK > 0, "shortlistK must be positive");
        Checks.checkArgument(rollbackWidth >= 0, "rollbackWidth must be non-negative");
        Checks.checkArgument(nearTieEpsilon >= 0, "nearTieEpsilon must be non-negative");
        Checks.checkArgument(amplificationSoftThreshold >= 0, "amplificationSoftThreshold must be non-negative");
        Checks.checkArgument(amplificationHardThreshold >= 0, "amplificationHardThreshold must be non-negative");
        scoreModel.ifPresent(model -> {
            List<String> problems = model.validate();
            if (!problems.isEmpty()) {
                throw new IllegalArgumentException("invalid score model for " + profileId + ": " + String.join("; ", problems));
            }
        });
    }

    /** Convenience constructor without a score-model override. */
    public RuleProfileSpec(
        Namespace profileId,
        RuleProfilePreset preset,
        UnresolvedInputMode unresolvedInputMode,
        int shortlistK,
        int rollbackWidth,
        double nearTieEpsilon,
        double amplificationSoftThreshold,
        double amplificationHardThreshold,
        boolean liftMultiSemanticChildShortages
    ) {
        this(profileId, preset, Optional.empty(), unresolvedInputMode, shortlistK, rollbackWidth,
            nearTieEpsilon, amplificationSoftThreshold, amplificationHardThreshold, liftMultiSemanticChildShortages);
    }

    public static RuleProfileSpec fromPreset(Namespace profileId, RuleProfilePreset preset, UnresolvedInputMode unresolvedInputMode) {
        RuleProfile base = preset.createProfile();
        return new RuleProfileSpec(
            profileId,
            preset,
            Optional.empty(),
            unresolvedInputMode,
            base.shortlistK(),
            base.rollbackWidth(),
            base.nearTieEpsilon(),
            base.amplificationSoftThreshold(),
            base.amplificationHardThreshold(),
            base.liftMultiSemanticChildShortages()
        );
    }

    public RuleProfile toProfile() {
        return toProfile(Lists.immutable.empty());
    }

    public RuleProfile toProfile(ImmutableList<RuleSpec> clauses) {
        Checks.checkNotNull(clauses, "clauses");
        RuleProfile base = preset.createProfile();
        ScoreModel model = scoreModel.orElseGet(base::model);
        List<String> problems = new ArrayList<>(model.validate());
        for (RuleSpec clause : clauses) {
            if (clause.stage() != RuleStage.candidate) {
                problems.add("clause " + clause.id() + " is not a candidate-stage rule");
            }
            problems.addAll(clause.validate().stream().map(problem -> "clause " + clause.id() + ": " + problem).toList());
        }
        RuleEngine engine = null;
        if (!clauses.isEmpty()) {
            try {
                engine = RuleEngine.compile(clauses.toList(), model.componentNames());
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("invalid profile " + profileId + ": " + String.join("; ", problems));
        }
        var hardRules = Lists.mutable.<HardRule>empty();
        hardRules.addAllIterable(base.hardRules());
        var scoreRules = Lists.mutable.<ScoreRule>empty();
        if (engine != null) {
            if (clauses.anySatisfy(clause -> clause.actions().stream().anyMatch(RuleAction.Reject.class::isInstance))) {
                hardRules.add(CandidateRuleClauses.hardRule(engine));
            }
            if (clauses.anySatisfy(clause -> clause.actions().stream().anyMatch(RuleAction.ScoreAdjust.class::isInstance))) {
                scoreRules.add(CandidateRuleClauses.scoreRule(engine));
            }
        }
        return new RuleProfile(
            profileId,
            model,
            hardRules.toImmutable(),
            scoreRules.toImmutable(),
            base.rankPolicy(),
            base.tieBreakPolicy(),
            base.explorePolicy(),
            unresolvedInputMode.toRule(),
            base.capabilities(),
            shortlistK,
            rollbackWidth,
            nearTieEpsilon,
            amplificationSoftThreshold,
            amplificationHardThreshold,
            liftMultiSemanticChildShortages,
            clauses.isEmpty()
        );
    }
}

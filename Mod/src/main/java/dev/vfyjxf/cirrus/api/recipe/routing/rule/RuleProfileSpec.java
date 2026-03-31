package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

public record RuleProfileSpec(
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
    public static final Codec<RuleProfileSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("profile_id").forGetter(RuleProfileSpec::profileId),
        RuleProfilePreset.CODEC.fieldOf("preset").forGetter(RuleProfileSpec::preset),
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
        Checks.checkNotNull(unresolvedInputMode, "unresolvedInputMode");
        Checks.checkArgument(shortlistK > 0, "shortlistK must be positive");
        Checks.checkArgument(rollbackWidth >= 0, "rollbackWidth must be non-negative");
        Checks.checkArgument(nearTieEpsilon >= 0, "nearTieEpsilon must be non-negative");
        Checks.checkArgument(amplificationSoftThreshold >= 0, "amplificationSoftThreshold must be non-negative");
        Checks.checkArgument(amplificationHardThreshold >= 0, "amplificationHardThreshold must be non-negative");
    }

    public static RuleProfileSpec fromPreset(Namespace profileId, RuleProfilePreset preset, UnresolvedInputMode unresolvedInputMode) {
        RuleProfile base = preset.createProfile();
        return new RuleProfileSpec(
            profileId,
            preset,
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

    public RuleProfile toProfile(ImmutableList<RuleClauseSpec> clauses) {
        Checks.checkNotNull(clauses, "clauses");
        RuleProfile base = preset.createProfile();
        var hardRules = Lists.mutable.<HardRule>empty();
        hardRules.addAllIterable(base.hardRules());
        var scoreRules = Lists.mutable.<ScoreRule>empty();
        scoreRules.addAllIterable(base.scoreRules());
        for (RuleClauseSpec clause : clauses) {
            if (clause.hardReject()) {
                hardRules.add(clause::evaluate);
            } else {
                scoreRules.add(clause::contribute);
            }
        }
        return new RuleProfile(
            profileId,
            base.scoreKeys(),
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
            liftMultiSemanticChildShortages
        );
    }
}

package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;

public enum RuleProfilePreset {
    standardDefault,
    standardExecution,
    assuredBoundedDefault,
    assuredProvenCompat;

    public static final Codec<RuleProfilePreset> CODEC = dev.vfyjxf.cloudlib.api.util.Codecs.lowerCaseEnum(RuleProfilePreset.class);

    public RuleProfile createProfile() {
        return switch (this) {
            case standardDefault -> DefaultRuleProfiles.standardDefault();
            case standardExecution -> DefaultRuleProfiles.standardExecution();
            case assuredBoundedDefault -> DefaultRuleProfiles.assuredBoundedDefault();
            case assuredProvenCompat -> DefaultRuleProfiles.assuredProvenCompat();
        };
    }
}

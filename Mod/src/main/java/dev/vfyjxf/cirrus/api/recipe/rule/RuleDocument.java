package dev.vfyjxf.cirrus.api.recipe.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;
import java.util.Optional;

/**
 * The v3 unified rule document: one versioned file carrying both the catalog
 * rules (classification/exclusion/hints) and, optionally, the tiered score
 * model. Replaces the v2 classification-only {@code builtin_rules.json} and
 * the separately-encoded routing clause schema.
 *
 * @param version   document schema version; must be {@link #CURRENT_VERSION}
 * @param scoreModel optional score model; empty = use the profile default
 * @param rules     rules in document (evaluation) order
 */
public record RuleDocument(int version, Optional<ScoreModel> scoreModel, List<RuleSpec> rules) {

    public static final int currentVersion = 3;

    public static final Codec<RuleDocument> codec = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.INT.fieldOf("version")
                    .validate(
                        version -> version == currentVersion
                                ? DataResult.success(version)
                                : DataResult.error(
                                    () -> "unsupported rule document version: " + version + " (expected "
                                            + currentVersion + ")"
                                )
                    ).forGetter(RuleDocument::version),
            ScoreModel.codec.optionalFieldOf("score_model").forGetter(RuleDocument::scoreModel),
            Codec.list(RuleSpec.codec).fieldOf("rules").forGetter(RuleDocument::rules)
        ).apply(instance, RuleDocument::new)
    );

    public RuleDocument {
        Checks.checkArgument(version == currentVersion, "unsupported rule document version: " + version);
        scoreModel = scoreModel == null ? Optional.empty() : scoreModel;
        rules = List.copyOf(rules == null ? List.of() : rules);
    }

    /** Convenience constructor without a score model. */
    public static RuleDocument of(List<RuleSpec> rules) {
        return new RuleDocument(currentVersion, Optional.empty(), rules);
    }
}

package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * One ordered rule specification within a {@link SourcePolicy}.
 *
 * <p>Each spec declares:
 * <ul>
 *     <li>A {@link #ruleId()} for diagnostics and identification.</li>
 *     <li>An {@link #enabled()} flag to skip rules without removing them.</li>
 *     <li>A {@link #when()} tag expression that selects matching recipes.</li>
 *     <li>An {@link #action()} determining how matched recipes affect the
 *         allowed set.</li>
 *     <li>An {@link #override()} flag that enables additive behavior for
 *         {@link SourcePolicyAction#includeMatch}.</li>
 * </ul>
 *
 * @param ruleId   unique identifier for this rule (used in diagnostics)
 * @param enabled  whether this rule is active
 * @param when     the tag expression to match recipes against
 * @param action   the action to apply to matched recipes
 * @param override whether to force-add matched recipes (for {@code includeMatch})
 */
public record SourcePolicyRuleSpec(
    Namespace ruleId,
    boolean enabled,
    TagExpr when,
    SourcePolicyAction action,
    boolean override
) {
    public static final Codec<SourcePolicyRuleSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("rule_id").forGetter(SourcePolicyRuleSpec::ruleId),
        Codec.BOOL.fieldOf("enabled").forGetter(SourcePolicyRuleSpec::enabled),
        TagExpr.CODEC.fieldOf("when").forGetter(SourcePolicyRuleSpec::when),
        SourcePolicyAction.CODEC.fieldOf("action").forGetter(SourcePolicyRuleSpec::action),
        Codec.BOOL.fieldOf("override").forGetter(SourcePolicyRuleSpec::override)
    ).apply(instance, SourcePolicyRuleSpec::new));

    public SourcePolicyRuleSpec {
        Checks.checkNotNull(ruleId, "ruleId");
        Checks.checkNotNull(when, "when");
        Checks.checkNotNull(action, "action");
    }
}

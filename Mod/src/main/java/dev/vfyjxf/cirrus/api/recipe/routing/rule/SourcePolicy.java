package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Ordered list of source-policy rules applied before candidate generation.
 *
 * <p>A {@code SourcePolicy} defines which recipes are allowed to participate
 * in routing by filtering them through an ordered sequence of
 * {@link SourcePolicyRuleSpec} entries. Each rule matches recipes using a
 * {@link TagExpr} and applies a {@link SourcePolicyAction}.
 *
 * <p>The default policy excludes recipes tagged {@code cirrus:quality/junk}
 * or {@code cirrus:intent/debug}.
 *
 * @param policyId unique identifier for this policy
 * @param version  monotonic version for staleness detection
 * @param rules    ordered list of rule specifications to evaluate
 * @see SourcePolicyRuleSpec
 * @see TagSourcePolicyRule
 */
public record SourcePolicy(
    Namespace policyId,
    long version,
    ImmutableList<SourcePolicyRuleSpec> rules
) {
    public static final Namespace defaultPolicyId = Namespace.of("cirrus", "routing/source_policy/default");
    public static final Codec<SourcePolicy> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Namespace.codec.fieldOf("policy_id").forGetter(SourcePolicy::policyId),
        Codec.LONG.fieldOf("version").forGetter(SourcePolicy::version),
        dev.vfyjxf.cloudlib.api.util.Codecs.immutableList(SourcePolicyRuleSpec.CODEC).fieldOf("rules").forGetter(SourcePolicy::rules)
    ).apply(instance, SourcePolicy::new));

    public SourcePolicy {
        Checks.checkNotNull(policyId, "policyId");
        Checks.checkArgument(version >= 0, "version must be non-negative");
        Checks.checkNotNull(rules, "rules");
    }

    /**
     * Returns the default source policy that excludes junk and debug recipes.
     *
     * @return the default policy instance
     */
    public static SourcePolicy defaultPolicy() {
        return new SourcePolicy(
            defaultPolicyId,
            1,
            Lists.immutable.of(
                new SourcePolicyRuleSpec(
                    Namespace.of("cirrus", "routing/source_policy_rule/exclude_junk"),
                    true,
                    TagExpr.has("cirrus:quality/junk"),
                    SourcePolicyAction.excludeMatch,
                    false
                ),
                new SourcePolicyRuleSpec(
                    Namespace.of("cirrus", "routing/source_policy_rule/exclude_debug"),
                    true,
                    TagExpr.has("cirrus:intent/debug"),
                    SourcePolicyAction.excludeMatch,
                    false
                )
            )
        );
    }
}

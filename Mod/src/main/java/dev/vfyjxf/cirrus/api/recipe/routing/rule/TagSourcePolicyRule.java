package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

import java.util.BitSet;

/**
 * Default implementation of {@link SourcePolicyRule} that evaluates
 * source-policy rules using tag-expression bitset operations.
 *
 * <p>Processes the ordered {@link SourcePolicyRuleSpec} entries in a
 * {@link SourcePolicy}, applying each rule's {@link SourcePolicyAction}
 * to the running allowed-recipe bitset. Produces a
 * {@link SourcePolicyDecision} with both the final allowed set and a
 * {@link PolicyExplain} trace.
 */
public final class TagSourcePolicyRule implements SourcePolicyRule {

    @Override
    public SourcePolicyDecision apply(TagSnapshot snapshot, SourcePolicy sourcePolicy) {
        BitSet allowed = new BitSet(snapshot.recipeCount());
        allowed.set(0, snapshot.recipeCount());

        MutableList<PolicyExplain.RuleTrace> traces = Lists.mutable.empty();
        int initial = allowed.cardinality();

        for (SourcePolicyRuleSpec rule : sourcePolicy.rules()) {
            if (!rule.enabled()) {
                continue;
            }
            BitSet matched = rule.when().evaluate(snapshot);
            int before = allowed.cardinality();
            switch (rule.action()) {
                case excludeMatch -> allowed.andNot(matched);
                case includeMatch -> {
                    if (rule.override()) {
                        allowed.or(matched);
                    }
                }
                case allowOnlyMatch -> allowed.and(matched);
            }
            int after = allowed.cardinality();
            traces.add(new PolicyExplain.RuleTrace(
                rule.ruleId(),
                rule.action(),
                matched.cardinality(),
                after - before,
                after
            ));
        }

        PolicyExplain explain = new PolicyExplain(snapshot.version(), initial, allowed.cardinality(), traces.toImmutable());
        return new SourcePolicyDecision(allowed, explain);
    }
}


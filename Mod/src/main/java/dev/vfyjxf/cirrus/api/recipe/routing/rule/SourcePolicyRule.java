package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.tag.TagSnapshot;

/**
 * Contract for evaluating a {@link SourcePolicy} against a tag snapshot.
 *
 * <p>Implementations iterate the ordered rule specs in the policy, evaluate
 * each spec's {@link TagExpr} against the snapshot, and apply the
 * corresponding {@link SourcePolicyAction} to produce a final
 * {@link SourcePolicyDecision} containing the allowed-recipe bitset.
 *
 * @see TagSourcePolicyRule
 * @see SourcePolicyDecision
 */
public interface SourcePolicyRule {

    /**
     * Evaluates the source policy against the given tag snapshot.
     *
     * @param snapshot     the current tag assignments
     * @param sourcePolicy the ordered list of source-policy rule specifications
     * @return the decision containing the allowed-recipe bitset and
     *         diagnostic {@link PolicyExplain}
     */
    SourcePolicyDecision apply(TagSnapshot snapshot, SourcePolicy sourcePolicy);
}


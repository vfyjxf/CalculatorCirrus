package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cloudlib.util.Checks;

import java.util.BitSet;

/**
 * Result of evaluating a {@link SourcePolicy} against a tag snapshot.
 *
 * <p>Contains the final bitset of allowed recipe indices and a diagnostic
 * {@link PolicyExplain} recording the effect of each rule.
 *
 * @param allowedRecipes bitset of recipe indices permitted after policy evaluation
 * @param explain        diagnostic trace of each rule's effect
 */
public record SourcePolicyDecision(
    BitSet allowedRecipes,
    PolicyExplain explain
) {
    public SourcePolicyDecision {
        Checks.checkNotNull(allowedRecipes, "allowedRecipes");
        Checks.checkNotNull(explain, "explain");
    }
}


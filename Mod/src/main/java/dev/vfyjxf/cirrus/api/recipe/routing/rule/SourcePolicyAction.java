package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Action applied by a {@link SourcePolicyRuleSpec} when its tag expression matches.
 *
 * <p>Source policy rules process recipes in a bitset-based pipeline:
 * <ul>
 *     <li>{@link #excludeMatch} — removes matched recipes from the allowed set.</li>
 *     <li>{@link #includeMatch} — adds matched recipes back (only if
 *         {@link SourcePolicyRuleSpec#override()} is set).</li>
 *     <li>{@link #allowOnlyMatch} — restricts the allowed set to only the
 *         matched recipes (intersection).</li>
 * </ul>
 */
public enum SourcePolicyAction {
    /** Removes all matched recipes from the allowed set (AND-NOT). */
    excludeMatch,
    /** Adds matched recipes to the allowed set (OR), if override is set. */
    includeMatch,
    /** Restricts the allowed set to only matched recipes (AND). */
    allowOnlyMatch
}


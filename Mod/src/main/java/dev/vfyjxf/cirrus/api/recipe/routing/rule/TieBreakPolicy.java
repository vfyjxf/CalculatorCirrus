package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Deterministic tiebreaker for candidates with equal score vectors.
 *
 * <p>When the {@link RankPolicy} reports two candidates as equal, the
 * tiebreak policy provides a stable secondary ordering. The default
 * implementation compares by recipe system ID and hash code to ensure
 * fully deterministic, reproducible planning results.
 *
 * @see RuleProfile#tieBreakPolicy()
 */
public interface TieBreakPolicy {

    /**
     * Compares two candidates that have equal scores.
     *
     * @param left  the first candidate's context
     * @param right the second candidate's context
     * @return negative if {@code left} should come first, positive if
     *         {@code right} should come first, zero if truly equal
     */
    int compare(CandidateContext left, CandidateContext right);
}


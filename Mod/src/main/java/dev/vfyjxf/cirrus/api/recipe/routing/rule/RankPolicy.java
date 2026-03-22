package dev.vfyjxf.cirrus.api.recipe.routing.rule;

/**
 * Comparator over score vectors produced by {@link ScoreRule} evaluation.
 *
 * <p>The {@code RankPolicy} defines the total (or partial) ordering of
 * candidate recipes based on their score vectors. Lower scores are
 * preferred ("cost" semantics). The built-in implementation uses
 * lexicographic comparison, but custom implementations may use
 * weighted sums, Pareto dominance, etc.
 *
 * @see RuleProfile#rankPolicy()
 */
public interface RankPolicy {

    /**
     * Compares two score vectors.
     *
     * @param left  the score vector of the first candidate
     * @param right the score vector of the second candidate
     * @return negative if {@code left} is better, positive if {@code right}
     *         is better, zero if equal
     */
    int compare(double[] left, double[] right);
}


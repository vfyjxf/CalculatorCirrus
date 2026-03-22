package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Read-only snapshot of a candidate recipe under evaluation.
 *
 * <p>Passed to {@link HardRule#evaluate(CandidateContext)} and
 * {@link ScoreRule#contribute(CandidateContext, ScoreAccumulator)} during
 * the candidate ranking stage. Contains all information a rule needs to
 * decide whether to accept/reject or how to score a candidate.
 *
 * @param recipe            the graph node representing the candidate recipe
 * @param demandedInput     the specific input that this candidate would supply
 * @param requiredAmount    the amount of the demanded input still needed
 * @param craftTimes        the number of craft iterations required to produce
 *                          the required amount
 * @param depth             the current search depth (0 = root demand)
 * @param cycleCandidate    whether this candidate would create or extend a
 *                          cycle in the dependency graph
 * @param amplificationRisk estimated risk of infinite amplification in
 *                          {@code [0, 1]}; higher values indicate more
 *                          dangerous cycles
 */
public record CandidateContext(
    RecipeGraph.Node recipe,
    InputContent demandedInput,
    long requiredAmount,
    int craftTimes,
    int depth,
    boolean cycleCandidate,
    double amplificationRisk
) {
    public CandidateContext {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(demandedInput, "demandedInput");
        Checks.checkArgument(requiredAmount > 0, "requiredAmount must be positive");
        Checks.checkArgument(craftTimes > 0, "craftTimes must be positive");
        Checks.checkArgument(depth >= 0, "depth must be non-negative");
        Checks.checkArgument(amplificationRisk >= 0, "amplificationRisk must be non-negative");
    }
}


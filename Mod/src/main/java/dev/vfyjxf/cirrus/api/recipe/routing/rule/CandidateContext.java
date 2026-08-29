package dev.vfyjxf.cirrus.api.recipe.routing.rule;

import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Read-only snapshot of a candidate recipe under evaluation.
 *
 * <p>Passed to {@link HardRule#evaluate(CandidateContext)} and
 * {@link ScoreRule#contribute(CandidateContext, ScoreAccumulator)} during
 * the candidate ranking stage. Contains all information a rule needs to
 * decide whether to accept/reject or how to score a candidate, including the
 * loop topology flags and routing hints that feed the score model's features.
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
 * @param availableCoverage how much of this candidate's own inputs can be
 *                          satisfied immediately from current stock/byproducts
 * @param estimatedSubtreeCost estimated total crafting cost of this candidate's
 *                          input subtree from the unit-cost oracle
 *                          ({@code craftTimes × (1 + Σ input × h)}); large
 *                          values mark chains that dead-end into rare or
 *                          unsourced resources
 * @param indirectActiveDemandCycle whether the candidate closes an indirect
 *                          active-demand cycle (planner guard)
 * @param intraLoopConversion whether the candidate is an intra-loop no-op
 *                          conversion within the demand's SCC
 * @param roundTripWithActive whether the candidate closes an equal-amount
 *                          round trip with a recipe on the active path
 * @param routingHints      the node's routing hints (policy + penalty)
 * @param contextualPenalty request-scoped contextual penalty total
 */
public record CandidateContext(
    RecipeGraph.Node recipe,
    InputContent demandedInput,
    long requiredAmount,
    int craftTimes,
    int depth,
    boolean cycleCandidate,
    double amplificationRisk,
    double availableCoverage,
    double estimatedSubtreeCost,
    boolean indirectActiveDemandCycle,
    boolean intraLoopConversion,
    boolean roundTripWithActive,
    RecipeRoutingHints routingHints,
    double contextualPenalty
) {
    public CandidateContext {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(demandedInput, "demandedInput");
        routingHints = RecipeRoutingHints.canonical(routingHints);
        Checks.checkArgument(requiredAmount > 0, "requiredAmount must be positive");
        Checks.checkArgument(craftTimes > 0, "craftTimes must be positive");
        Checks.checkArgument(depth >= 0, "depth must be non-negative");
        Checks.checkArgument(amplificationRisk >= 0, "amplificationRisk must be non-negative");
        Checks.checkArgument(availableCoverage >= 0, "availableCoverage must be non-negative");
        Checks.checkArgument(estimatedSubtreeCost >= 0, "estimatedSubtreeCost must be non-negative");
        Checks.checkArgument(contextualPenalty >= 0, "contextualPenalty must be non-negative");
    }

    /** Constructor without loop flags/hints/contextual data (all neutral). */
    public CandidateContext(
        RecipeGraph.Node recipe,
        InputContent demandedInput,
        long requiredAmount,
        int craftTimes,
        int depth,
        boolean cycleCandidate,
        double amplificationRisk,
        double availableCoverage,
        double estimatedSubtreeCost
    ) {
        this(recipe, demandedInput, requiredAmount, craftTimes, depth, cycleCandidate,
            amplificationRisk, availableCoverage, estimatedSubtreeCost,
            false, false, false, RecipeRoutingHints.empty(), 0.0d);
    }
}

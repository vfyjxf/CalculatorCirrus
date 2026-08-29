package dev.vfyjxf.cirrus.recipe.routing.planner;

import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.recipe.routing.cost.UnitCostOracle;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Request-scoped memoization for the candidate pipeline.
 *
 * <p>Evaluating producer candidates for the same demanded input repeatedly is
 * the dominant planning cost on large packs (a {@code #minecraft:logs} input
 * alone has thousands of producers and typically occurs several times in one
 * plan). The expensive, request-static part of the scan — walking the producer
 * index, checking source-policy/heuristic gates and resolving the matched
 * variant — depends only on the demand key, so it is computed once per request
 * and cached here; per-occurrence scoring (craft times, stock coverage, cycle
 * state) is recomputed from the cached refs on every use.
 *
 * <p>The cache lives on {@link PlannerContext} and dies with the request; the
 * graph and catalogs are immutable during the run, and the dynamic parts of
 * scoring deliberately stay outside the cache, so no invalidation is needed.
 */
public final class PlannerRequestCaches {

    /**
     * A producer that passed the request-static filters (allowed recipe,
     * non-empty matched output, heuristic policy, variant resolution). Dynamic
     * gates (hard rules with candidate context, cycle/loop penalties, coverage)
     * are re-evaluated per occurrence.
     */
    public record CachedProducer(
        int recipeIndex,
        RecipeGraph.Node node,
        OutputContent matchedOutput,
        int matchedVariantIndex
    ) {
    }

    private final Map<String, List<CachedProducer>> filteredProducersByDemand = new HashMap<>();
    // producer cache keys are rebuilt per lookup otherwise (registry id
    // resolution + concat, plus a tag suffix); InputContent instances are
    // stable per graph node, so identity-memo the built key per request
    private final Map<InputContent, String> producerCacheKeys = new IdentityHashMap<>();
    private @Nullable UnitCostOracle unitCostOracle;
    private @Nullable UnitCostOracle baseUnitCostOracle;

    /** Identity-memoized producer cache key; {@code null} keys (opaque inputs) are not memoized. */
    public @Nullable String producerCacheKey(InputContent input, Function<InputContent, @Nullable String> builder) {
        String cached = producerCacheKeys.get(input);
        if (cached != null) {
            return cached;
        }
        String built = builder.apply(input);
        if (built != null) {
            producerCacheKeys.put(input, built);
        }
        return built;
    }

    public @Nullable List<CachedProducer> filteredProducers(String demandKey) {
        return filteredProducersByDemand.get(demandKey);
    }

    public void putFilteredProducers(String demandKey, List<CachedProducer> producers) {
        filteredProducersByDemand.put(demandKey, producers);
    }

    /**
     * The per-request unit-cost oracle, created lazily on first scoring use.
     * The supplier captures the planner's producer-filter pipeline, so the
     * oracle always sees the same scan-capped, pre-filtered producer lists.
     */
    public UnitCostOracle unitCostOracle(Supplier<UnitCostOracle> factory) {
        UnitCostOracle current = unitCostOracle;
        if (current == null) {
            current = factory.get();
            unitCostOracle = current;
        }
        return current;
    }

    /**
     * The per-request base unit-cost oracle (no advisory multipliers); feeds
     * the score model's base-cost feature so the advisory excess stays a
     * separate additive component.
     */
    public UnitCostOracle baseUnitCostOracle(Supplier<UnitCostOracle> factory) {
        UnitCostOracle current = baseUnitCostOracle;
        if (current == null) {
            current = factory.get();
            baseUnitCostOracle = current;
        }
        return current;
    }

}

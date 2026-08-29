package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeHeuristicContext;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristicRule;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeUsePolicy;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges recipe classification into graph-build-time node hints.
 *
 * <p>{@link RecipeRoutingHeuristics} rules are evaluated once per recipe node
 * when a {@code RecipeGraph} is built. The hint policies themselves are
 * declared by the catalog-stage rules in the unified rule document ({@code
 * hint} actions); this bridge only transports the classifier's verdict into
 * the hint pipeline, with zero planner changes:
 *
 * <ul>
 *   <li>{@code hint_discouraged} / {@code hint_last_resort} — the hint policy
 *   declared by matching unified rules (recolor/exchange → discouraged,
 *   recycling/storage ladders → per-rule declaration).</li>
 *   <li>{@code recycling_role} — attaches the {@code recycling} role to
 *   recipes tagged as recycling (role flag, no policy change).</li>
 * </ul>
 *
 * <p>The registered rules share one classification computation per recipe: a
 * thread-local last-context cache dedupes the rules evaluated for a single
 * node, and a per-recipe memo ({@link #outcomeByRecipe}) dedupes across the
 * fresh per-node contexts graph building allocates (graph building is
 * parallel, so both must stay thread-safe).
 */
public final class ClassificationHeuristics {

    private static final ThreadLocal<CacheEntry> lastClassification = new ThreadLocal<>();

    /**
     * Per-recipe memo of classification outcomes, keyed by {@code systemId +
     * '\n' + recipeId} — the same key space and semantics as the classifier's
     * {@code localOutcomeCache} (see {@link RecipeClassifier#classifyLocal}).
     * Graph building allocates a fresh {@link RecipeHeuristicContext} per node,
     * so the thread-local above only dedupes the rules evaluated for one node;
     * this map dedupes across nodes and skips the view materialization in
     * {@link ClassifyViewFactory#fromHeuristicContext} entirely. Entries with a
     * null recipe id are never cached (distinct recipes would share the key).
     * Cleared from {@link RecipeClassificationService#clearRuntimeCaches()} on
     * client unload, same lifecycle as the classifier memo.
     */
    private static final ConcurrentHashMap<String, RecipeClassifier.LocalOutcome> outcomeByRecipe = new ConcurrentHashMap<>();

    private ClassificationHeuristics() {
    }

    /**
     * Registers the classification heuristic rules into the global heuristic
     * registry. Idempotent by rule id: rules already present are skipped, so
     * a {@code RecipeRoutingHeuristics.clear()} (tests) followed by a fresh
     * {@code register()} restores them. The match conditions check the
     * screening config switch at evaluation time, so disabling screening
     * turns the hints off without needing an unregister hook.
     *
     * <p>Two hint sources flow together (strictest wins in the hint
     * pipeline): explicit {@code hint} actions declared by unified rules
     * (e.g. storage ladders), and the classic advisory tags emitted by the
     * structural rules (recolor/exchange/recycling).
     */
    public static synchronized void register() {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (RecipeRoutingHeuristicRule rule : RecipeRoutingHeuristics.snapshot()) {
            existing.add(rule.ruleId().toString());
        }
        if (existing.add("cirrus:classification/hint_discouraged")) {
            RecipeRoutingHeuristics.add("cirrus:classification/hint_discouraged")
                .match(context -> screeningEnabled() && isDiscouraged(context))
                .discouraged()
                .reasonKey("cirrus.routing.classification.hint")
                .register();
        }
        if (existing.add("cirrus:classification/hint_last_resort")) {
            RecipeRoutingHeuristics.add("cirrus:classification/hint_last_resort")
                .match(context -> screeningEnabled() && isLastResort(context))
                .lastResort()
                .reasonKey("cirrus.routing.classification.hint")
                .register();
        }
        if (existing.add("cirrus:classification/recycling_role")) {
            RecipeRoutingHeuristics.add("cirrus:classification/recycling_role")
                .match(context -> screeningEnabled() && tagsOf(context).contains(ClassificationTags.RECYCLING))
                .recycling()
                .reasonKey("cirrus.routing.classification.recycling")
                .register();
        }
    }

    private static boolean isDiscouraged(RecipeHeuristicContext context) {
        RecipeClassifier.LocalOutcome outcome = outcomeOf(context);
        return outcome.hint() == RecipeUsePolicy.discouraged
            || outcome.tags().contains(ClassificationTags.RECOLOR)
            || outcome.tags().contains(ClassificationTags.EXCHANGE);
    }

    private static boolean isLastResort(RecipeHeuristicContext context) {
        RecipeClassifier.LocalOutcome outcome = outcomeOf(context);
        return outcome.hint() == RecipeUsePolicy.lastResort
            || outcome.tags().contains(ClassificationTags.RECYCLING);
    }

    private static boolean screeningEnabled() {
        return dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig.calculatorRecipeScreeningEnabled();
    }

    // package-private for ClassificationHeuristicsTest
    static Set<String> tagsOf(RecipeHeuristicContext context) {
        return outcomeOf(context).tags();
    }

    private static RecipeClassifier.LocalOutcome outcomeOf(RecipeHeuristicContext context) {
        CacheEntry cached = lastClassification.get();
        if (cached != null && cached.context == context) {
            return cached.outcome;
        }
        ResourceLocation recipeId = context.recipeId();
        RecipeClassifier.LocalOutcome outcome;
        if (recipeId != null) {
            outcome = outcomeByRecipe.computeIfAbsent(
                context.systemId() + "\n" + recipeId,
                key -> classify(context)
            );
        } else {
            outcome = classify(context);
        }
        lastClassification.set(new CacheEntry(context, outcome));
        return outcome;
    }

    private static RecipeClassifier.LocalOutcome classify(RecipeHeuristicContext context) {
        RecipeClassifyView view = ClassifyViewFactory.fromHeuristicContext(context);
        // immutable copy: the returned set is shared across threads and rules
        return RecipeClassificationService.classifier().classifyLocalOutcome(view);
    }

    /** Drops the per-recipe outcome memo (world unload / datapack reload). */
    static void clearTagsCache() {
        outcomeByRecipe.clear();
    }

    // package-private for ClassificationHeuristicsTest
    static int tagsByRecipeSize() {
        return outcomeByRecipe.size();
    }

    private record CacheEntry(RecipeHeuristicContext context, RecipeClassifier.LocalOutcome outcome) {
    }
}

package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeHeuristicContext;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristicRule;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges recipe classification into graph-build-time node hints.
 *
 * <p>{@link RecipeRoutingHeuristics} rules are evaluated once per recipe node
 * when a {@code RecipeGraph} is built; registering classification-derived
 * rules there lets advisory tags ({@code recolor}/{@code exchange}/{@code
 * recycling}) translate into scoring penalties through the existing hint
 * pipeline, with zero changes to the planner.
 *
 * <p>The three registered rules share one classification computation per
 * recipe: a thread-local last-context cache dedupes the rules evaluated for a
 * single node, and a per-recipe memo ({@link #tagsByRecipe}) dedupes across
 * the fresh per-node contexts graph building allocates (graph building is
 * parallel, so both must stay thread-safe).
 */
public final class ClassificationHeuristics {

    private static final ThreadLocal<CacheEntry> lastClassification = new ThreadLocal<>();

    /**
     * Per-recipe memo of classification tags, keyed by {@code systemId + '\n' +
     * recipeId} — the same key space and semantics as the classifier's
     * {@code localTagsCache} (see {@link RecipeClassifier#classifyLocal}).
     * Graph building allocates a fresh {@link RecipeHeuristicContext} per node,
     * so the thread-local above only dedupes the three rules evaluated for one
     * node; this map dedupes across nodes and skips the view materialization in
     * {@link ClassifyViewFactory#fromHeuristicContext} entirely. Entries with a
     * null recipe id are never cached (distinct recipes would share the key).
     * Cleared from {@link RecipeClassificationService#clearRuntimeCaches()} on
     * client unload, same lifecycle as the classifier memo.
     */
    private static final ConcurrentHashMap<String, Set<String>> tagsByRecipe = new ConcurrentHashMap<>();

    private ClassificationHeuristics() {
    }

    /**
     * Registers the classification heuristic rules into the global heuristic
     * registry. Idempotent by rule id: rules already present are skipped, so
     * a {@code RecipeRoutingHeuristics.clear()} (tests) followed by a fresh
     * {@code register()} restores them. The match conditions check the
     * screening config switch at evaluation time, so disabling screening
     * turns the penalties off without needing an unregister hook.
     */
    public static synchronized void register() {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (RecipeRoutingHeuristicRule rule : RecipeRoutingHeuristics.snapshot()) {
            existing.add(rule.ruleId().toString());
        }
        if (existing.add("cirrus:classification/recolor")) {
            RecipeRoutingHeuristics.add("cirrus:classification/recolor")
                .match(context -> screeningEnabled() && tagsOf(context).contains(ClassificationTags.RECOLOR))
                .discouraged()
                .reasonKey("cirrus.routing.classification.recolor")
                .register();
        }
        if (existing.add("cirrus:classification/exchange")) {
            RecipeRoutingHeuristics.add("cirrus:classification/exchange")
                .match(context -> screeningEnabled() && tagsOf(context).contains(ClassificationTags.EXCHANGE))
                .discouraged()
                .reasonKey("cirrus.routing.classification.exchange")
                .register();
        }
        if (existing.add("cirrus:classification/recycling")) {
            RecipeRoutingHeuristics.add("cirrus:classification/recycling")
                .match(context -> screeningEnabled() && tagsOf(context).contains(ClassificationTags.RECYCLING))
                .lastResort()
                .recycling()
                .reasonKey("cirrus.routing.classification.recycling")
                .register();
        }
    }

    private static boolean screeningEnabled() {
        return dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig.calculatorRecipeScreeningEnabled();
    }

    // package-private for ClassificationHeuristicsTest
    static Set<String> tagsOf(RecipeHeuristicContext context) {
        CacheEntry cached = lastClassification.get();
        if (cached != null && cached.context == context) {
            return cached.tags;
        }
        ResourceLocation recipeId = context.recipeId();
        Set<String> tags;
        if (recipeId != null) {
            tags = tagsByRecipe.computeIfAbsent(
                context.systemId() + "\n" + recipeId,
                key -> classify(context)
            );
        } else {
            tags = classify(context);
        }
        lastClassification.set(new CacheEntry(context, tags));
        return tags;
    }

    private static Set<String> classify(RecipeHeuristicContext context) {
        RecipeClassifyView view = ClassifyViewFactory.fromHeuristicContext(context);
        // immutable copy: the returned set is shared across threads and rules
        return Set.copyOf(RecipeClassificationService.classifier().classifyLocal(view));
    }

    /** Drops the per-recipe tag memo (world unload / datapack reload). */
    static void clearTagsCache() {
        tagsByRecipe.clear();
    }

    // package-private for ClassificationHeuristicsTest
    static int tagsByRecipeSize() {
        return tagsByRecipe.size();
    }

    private record CacheEntry(RecipeHeuristicContext context, Set<String> tags) {
    }
}

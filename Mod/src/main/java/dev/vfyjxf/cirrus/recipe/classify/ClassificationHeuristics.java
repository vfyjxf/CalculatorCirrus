package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationTags;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeHeuristicContext;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristicRule;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;

import java.util.Set;

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
 * context through a thread-local last-context cache (graph building is
 * parallel, so this must stay thread-safe).
 */
public final class ClassificationHeuristics {

    private static final ThreadLocal<CacheEntry> lastClassification = new ThreadLocal<>();

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

    private static Set<String> tagsOf(RecipeHeuristicContext context) {
        CacheEntry cached = lastClassification.get();
        if (cached != null && cached.context == context) {
            return cached.tags;
        }
        RecipeClassifyView view = ClassifyViewFactory.fromHeuristicContext(context);
        Set<String> tags = RecipeClassificationService.classifier().classifyLocal(view);
        lastClassification.set(new CacheEntry(context, tags));
        return tags;
    }

    private record CacheEntry(RecipeHeuristicContext context, Set<String> tags) {
    }
}

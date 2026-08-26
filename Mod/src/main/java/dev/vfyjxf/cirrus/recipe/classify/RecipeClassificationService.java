package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassificationResult;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared access to the default {@link RecipeClassifier} and graph-level
 * classification.
 *
 * <p>The default classifier is assembled lazily from the built-in pattern
 * rules resource plus the built-in structural rules, and shared process-wide
 * (the classifier itself is stateless).
 */
public final class RecipeClassificationService {

    private static volatile RecipeClassifier defaultClassifier;

    private RecipeClassificationService() {
    }

    /** Returns the shared default classifier. */
    public static RecipeClassifier classifier() {
        RecipeClassifier current = defaultClassifier;
        if (current == null) {
            synchronized (RecipeClassificationService.class) {
                current = defaultClassifier;
                if (current == null) {
                    current = RecipeClassifier.createDefault();
                    defaultClassifier = current;
                }
            }
        }
        return current;
    }

    /**
     * Drops classification memos that outlive a world session: the shared
     * classifier's per-recipe local tags, the per-recipe tag memo held by
     * {@link ClassificationHeuristics}, and the tag member sets held by
     * {@link ClassifyViewFactory}. Called on client unload.
     */
    public static void clearRuntimeCaches() {
        RecipeClassifier current = defaultClassifier;
        if (current != null) {
            current.clearLocalTagsCache();
        }
        ClassificationHeuristics.clearTagsCache();
        ClassifyViewFactory.clearTagMemberCache();
    }

    /**
     * Classifies every node of a built recipe graph. Node order matches the
     * graph's stable catalog order, so result bitsets align with the routing
     * catalog indices used by the routing {@code TagIndex}.
     */
    public static ClassificationResult classify(RecipeGraph graph) {
        List<RecipeClassifyView> views = new ArrayList<>(graph.stableNodes().size());
        int index = 0;
        for (RecipeGraph.Node node : graph.stableNodes()) {
            views.add(ClassifyViewFactory.fromNode(node, index++));
        }
        return classifier().classifyAll(views);
    }
}

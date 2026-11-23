package dev.vfyjxf.cirrus.recipe.classify;

import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifier;

/**
 * Shared access to the default {@link RecipeClassifier}.
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

}

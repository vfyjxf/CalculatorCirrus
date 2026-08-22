package dev.vfyjxf.cirrus.api.recipe.classify;

/**
 * Receives classification tags for the recipe currently being classified.
 */
@FunctionalInterface
public interface ClassificationSink {

    /** Attaches the given classification tag to the current recipe. */
    void tag(String tag);
}

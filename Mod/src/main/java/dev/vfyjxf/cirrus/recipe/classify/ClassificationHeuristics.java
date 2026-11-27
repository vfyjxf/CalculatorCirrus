package dev.vfyjxf.cirrus.recipe.classify;

public final class ClassificationHeuristics {

    private ClassificationHeuristics() {}

    public static synchronized void register() {
        // Classification verdicts are bridged during graph compilation via ClassifierVerdictBridge.
    }

    static void clearTagsCache() {}
}

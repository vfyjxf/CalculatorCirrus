package dev.vfyjxf.cirrus.recipe.classify;

/**
 * Bridges recipe classification into graph-build-time node hints.
 *
 * <p>The hint policies themselves are declared by the catalog-stage rules in the unified rule
 * document ({@code hint} actions) and remain on the classifier outcome; this bridge only
 * transported the classifier's verdict into the old graph's hint pipeline.
 */
public final class ClassificationHeuristics {

    private ClassificationHeuristics() {
    }

    /**
     * Registers the classification heuristic rules into the graph-build-time hint registry.
     */
    public static synchronized void register() {
        // TODO(planning-bridge): 分类 hint 由新规划器图构建期接入（旧路由已删除，待 bridge 接线）
    }

    /** Drops the per-recipe outcome memo (world unload / datapack reload). */
    static void clearTagsCache() {
        // TODO(planning-bridge): 分类 outcome memo 随旧图构建删除（旧路由已删除，待 bridge 接线）
    }
}

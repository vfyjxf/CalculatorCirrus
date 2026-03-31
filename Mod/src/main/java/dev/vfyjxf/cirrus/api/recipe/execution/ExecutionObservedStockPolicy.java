package dev.vfyjxf.cirrus.api.recipe.execution;

/**
 * Controls whether rebuild planning may consume newly observed live stock that
 * was not part of the original session baseline.
 */
public enum ExecutionObservedStockPolicy {
    /**
     * Default behavior: rebuild against the currently observed live stock.
     */
    consumeObservedStock,
    /**
     * Conservative behavior: only consume stock explainable by the original
     * planning baseline plus plan-completed execution progress.
     */
    preservePlannedStock
}

package dev.vfyjxf.cirrus.api.recipe.execution.interaction;

import dev.vfyjxf.cirrus.api.content.stock.Stock;

/**
 * Frozen planning-time resource view supplied by an interaction/driver.
 *
 * <p>This keeps planning-time stock discovery out of generic execution state code.
 * Concrete drivers can expose whatever slots or buffers are visible to the current
 * interaction without leaking machine-specific details into shared planning code.
 */
public record ExecutionPlanningSnapshot(
    Stock stock,
    long fingerprint
) {

    private static final ExecutionPlanningSnapshot emptySnapshot = new ExecutionPlanningSnapshot(Stock.empty(), 0L);

    public ExecutionPlanningSnapshot {
        stock = stock.copy();
    }

    public static ExecutionPlanningSnapshot empty() {
        return emptySnapshot;
    }
}

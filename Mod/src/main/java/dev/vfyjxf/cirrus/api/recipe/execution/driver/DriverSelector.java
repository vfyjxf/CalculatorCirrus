package dev.vfyjxf.cirrus.api.recipe.execution.driver;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.DriverContext;

/**
 * Matches a single {@link DriverContext} element with three-state result.
 *
 */
@FunctionalInterface
public interface DriverSelector {

    /**
     * Tests whether this selector accepts the given context element.
     */
    MatchResult match(DriverContext context);

    enum MatchResult {
        /**
         * Context satisfies a condition of this selector.
         */
        accept,
        /**
         * Context type is relevant but condition not met — excludes this driver.
         */
        reject,
        /**
         * Context type is irrelevant to this selector — skip.
         */
        pass
    }
}

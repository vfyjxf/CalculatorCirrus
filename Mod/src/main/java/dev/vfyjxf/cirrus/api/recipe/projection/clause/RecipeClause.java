package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cloudlib.api.util.Namespace;

/**
 * A typed marker with data that can participate in projection and condition checks.
 */
public interface RecipeClause {

    /**
     * The unique id of this clause.
     *
     * @return the unique id of this clause
     */
    Namespace id();
}

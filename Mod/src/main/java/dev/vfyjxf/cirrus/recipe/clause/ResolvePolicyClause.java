package dev.vfyjxf.cirrus.recipe.clause;

import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.resolve.ResolvePolicy;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public record ResolvePolicyClause(
    ResolvePolicy policy
) implements RecipeClause {

    public static final Namespace id = CirrusNamespaces.ofMod("resolve/policy");
    public static final ClauseType type = new ClauseType(id, (recipe, content) -> true);

    public ResolvePolicyClause {
        Checks.checkNotNull(policy, "policy");
    }

    @Override
    public Namespace id() {
        return id;
    }
}

package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionTransformer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeClauseRegister {

    void registerClauseType(ClauseType type);

    <T extends RecipeContent, F extends RecipeClause> void registerContentTransformer(ClauseType type, ContentTransformer<T, F> transformer);

    <F extends RecipeClause> void registerProjectionTransformer(ClauseType type, ProjectionTransformer<F> transformer);

}

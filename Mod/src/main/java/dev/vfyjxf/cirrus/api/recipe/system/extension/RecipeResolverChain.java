package dev.vfyjxf.cirrus.api.recipe.system.extension;

import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;

public interface RecipeResolverChain<R> {

    ResolveResult next(ResolveContext context, R recipe);
}

package dev.vfyjxf.cirrus.api.recipe.system.extension;

import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;

public interface RecipeSubsystem<R> extends RecipeSystemExtension {

    ResolveResult resolve(ResolveContext context, R recipe, RecipeResolverChain<R> chain);
}

package dev.vfyjxf.cirrus.recipe.system.jei;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeResolverChain;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeSubsystem;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.RecipeType;

import java.util.List;

public final class JeiRecipeLayoutSubsystem<R> implements RecipeSubsystem<R> {
    public static final Namespace id = CirrusNamespaces.ofMod("recipe/subsystem/jei_layout");

    private final RecipeSystem<R> system;
    private final List<RecipeType<?>> recipeTypes;

    public JeiRecipeLayoutSubsystem(RecipeSystem<R> system, RecipeType<?> recipeType) {
        this(system, List.of(recipeType));
    }

    public JeiRecipeLayoutSubsystem(RecipeSystem<R> system, List<? extends RecipeType<?>> recipeTypes) {
        this.system = system;
        this.recipeTypes = List.copyOf(recipeTypes);
    }

    @Override
    public ResolveResult resolve(ResolveContext context, R recipe, RecipeResolverChain<R> chain) {
        ResolveResult fallback = chain.next(context, recipe);
        if (fallback.resolved()) {
            return fallback;
        }
        try {
            for (RecipeType<?> recipeType : recipeTypes) {
                ResolveResult result = tryResolve(recipe, recipeType);
                if (result.resolved()) {
                    return result;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private <T> ResolveResult tryResolve(R recipe, RecipeType<T> recipeType) {
        if (!recipeType.getRecipeClass().isInstance(recipe)) {
            return ResolveResult.unsupported();
        }
        RecipeType<R> typedType = (RecipeType<R>) recipeType;
        return JeiRecipeLayoutResolver.resolve(system, recipe, typedType);
    }
}

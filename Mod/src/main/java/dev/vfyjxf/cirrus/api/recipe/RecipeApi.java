package dev.vfyjxf.cirrus.api.recipe;


import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.RecipeType;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface RecipeApi {

    static RecipeApi instance() {
        RecipeApi instance = RecipeApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("RecipeApi not initialized");
        }
        return instance;
    }

    //region system

    boolean hasSystem(Namespace systemId);

    RecipeSystem<?> getSystem(Namespace systemId);

    @Nullable
    RecipeSystem<?> findSystem(Namespace systemId);

    RichIterable<? extends RecipeSystem<?>> getTaggedSystems(Namespace tag);

    /**
     * @return an unmodifiable view of all registered recipe systems
     */
    @UnmodifiableView
    RichIterable<? extends RecipeSystem<?>> allSystems();

    //endregion

    //region facet

    boolean hasFacet(Namespace facetId);

    RecipeFacetType getFacetType(Namespace facetId) throws IllegalArgumentException;

    <T extends RecipeContent, F extends RecipeFacet> @Nullable FacetTransformer<T, F> findFacetTransformer(RecipeFacetType type);

    <F extends RecipeFacet> @Nullable ViewTransformer<F> findViewTransformer(RecipeFacetType type);

    //endregion

    //region handler

    /**
     * @param systemId        the id of the system
     * @param withJeiFallback whether to include handlers from JEI's {@link mezz.jei.api.recipe.IRecipeManager#createRecipeCatalystLookup(RecipeType)} when {@link RecipeSystem} bind to a {@link RecipeType}
     * @return an unmodifiable view of all handlers for the given system
     * @see RecipeApi#getJeiRecipeTypeForSystem(Namespace) (RecipeSystem)
     */
    RichIterable<MachineEntry> getMachines(Namespace systemId, boolean withJeiFallback);

    //endregion

    //region ingredient

    <T> @UnmodifiableView RichIterable<IngredientResolver<T>> getIngredientResolver(IngredientResolverType<T> type);

    <T> @Nullable IngredientResolver<T> findIngredientResolver(IngredientResolverType<T> type, T ingredient);

    //endregion

    //region filter

    <R> RichIterable<? extends RecipeFilter<R>> getFilter(Namespace id, RecipeSystem<R> system);

    //endregion

    //region jei bridge

    RichIterable<RecipeType<?>> getJeiRecipeTypeForSystem(Namespace systemId);

    //endregion

}

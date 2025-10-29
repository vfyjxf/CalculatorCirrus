package dev.vfyjxf.calculatorcirrus.api.recipe;


import dev.vfyjxf.calculatorcirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiImpl;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
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

    boolean hasSystem(ResourceLocation systemId);

    RecipeSystem<?> getSystem(ResourceLocation systemId);

    @Nullable
    RecipeSystem<?> getSystemOrNull(ResourceLocation systemId);

    /**
     * @return an unmodifiable view of all registered recipe systems
     */
    @UnmodifiableView
    RichIterable<? extends RecipeSystem<?>> allSystems();

    //endregion

    //region facet

    boolean hasFacet(ResourceLocation facetId);

    RecipeFacetType getFacetType(ResourceLocation facetId) throws IllegalArgumentException;

    <T extends RecipeContent, F extends RecipeFacet> @Nullable FacetTransformer<T, F> getFacetTransformer(RecipeFacetType type);

    <F extends RecipeFacet> @Nullable ViewTransformer<F> getViewTransformer(RecipeFacetType type);

    //endregion

    //region handler

    /**
     * @param systemId        the id of the system
     * @param withJeiFallback whether to include handlers from JEI's {@link mezz.jei.api.recipe.IRecipeManager#createRecipeCatalystLookup(RecipeType)} when {@link RecipeSystem} bind to a {@link RecipeType}
     * @return an unmodifiable view of all handlers for the given system
     * @see RecipeApi#getJeiRecipeTypeForSystem(ResourceLocation) (RecipeSystem)
     */
    RichIterable<MachineEntry> getMachines(ResourceLocation systemId, boolean withJeiFallback);


    //endregion

    //region ingredient

    @UnmodifiableView
    <T> RichIterable<IngredientResolver<T>> getIngredientResolver(IngredientResolverType<T> type);

    <T> @Nullable IngredientResolver<T> findIngredientResolver(IngredientResolverType<T> type, T ingredient);

    //endregion

    //region jei bridge

    RichIterable<RecipeType<?>> getJeiRecipeTypeForSystem(ResourceLocation systemId);

    //endregion

}

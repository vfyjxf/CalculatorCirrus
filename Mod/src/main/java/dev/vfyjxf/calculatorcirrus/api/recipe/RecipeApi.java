package dev.vfyjxf.calculatorcirrus.api.recipe;


import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface RecipeApi {

    static RecipeApi instance() {
        return Singletons.get(RecipeApi.class);
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

}

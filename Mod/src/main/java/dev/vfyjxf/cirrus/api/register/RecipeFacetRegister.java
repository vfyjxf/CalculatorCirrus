package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeFacetRegister {

    void registerFacetType(RecipeFacetType type);

    <T extends RecipeContent, F extends RecipeFacet> void registerFacetTransformer(RecipeFacetType type, FacetTransformer<T, F> transformer);

    <F extends RecipeFacet> void registerViewTransformer(RecipeFacetType type, ViewTransformer<F> transformer);

}

package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.content.RecipeContent;

public record FacetedContent<T extends RecipeContent>(
    T content,
    FacetContainer facets
) {
    public static <T extends RecipeContent> FacetedContent<T> from(T content) {
        return new FacetedContent<>(content, new FacetContainer());
    }
}

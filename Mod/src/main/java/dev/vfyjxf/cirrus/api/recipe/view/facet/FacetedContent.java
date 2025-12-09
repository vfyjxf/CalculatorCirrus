package dev.vfyjxf.cirrus.api.recipe.view.facet;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Range;

public record FacetedContent<T extends RecipeContent>(
    T content,
    FacetMap facets
) {
    public static <T extends RecipeContent> FacetedContent<T> from(T content) {
        return new FacetedContent<>(content, FacetMap.create());
    }

    public static <T extends RecipeContent> FacetedContent<T> from(T content, FacetMap facets) {
        return new FacetedContent<>(content, facets);
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public boolean notEmpty() {
        return content.notEmpty();
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long amount() {
        return content.amount();
    }

    @Range(from = 0, to = 1)
    public float chance() {
        return content.chance();
    }

    public boolean constantly() {
        return content.constantly();
    }

    public boolean mergeable(RecipeContent other) {
        return content.mergeable(other);
    }

    public boolean satisfy(ContentVariant actual) {
        return content.satisfy(actual);
    }

    public RichIterable<RecipeFacet> allFacets() {
        return facets.allFacets();
    }

    public RecipeFacet getFacet(RecipeFacetType facetType) {
        return facets.getFacet(facetType);
    }

    public RecipeFacet getFacet(Namespace facetId) {
        return facets.getFacet(facetId);
    }

    public boolean hasFacet(RecipeFacet facet) {
        return facets.hasFacet(facet);
    }

    public boolean hasFacet(RecipeFacetType facetType) {
        return facets.hasFacet(facetType);
    }

    public boolean hasFacet(Namespace facetId) {
        return facets.hasFacet(facetId);
    }

    public FacetMap removeFacet(Namespace facetId) {
        return facets.removeFacet(facetId);
    }

    public FacetMap removeFacet(RecipeFacetType facetType) {
        return facets.removeFacet(facetType);
    }

    public FacetMap addFacet(RecipeFacet facet) {
        return facets.addFacet(facet);
    }

    public void reset() {
        facets.reset();
    }

    @Override
    public String toString() {
        return '[' +
               "content=" + content +
               ", facets=" + facets +
               ']';
    }
}

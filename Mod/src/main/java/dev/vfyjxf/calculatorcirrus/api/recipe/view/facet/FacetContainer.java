package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.UnmodifiableView;

public final class FacetContainer {

    public static FacetContainer create() {
        return new FacetContainer();
    }

    private final MutableMap<ResourceLocation, RecipeFacet> facets = Maps.mutable.empty();

    public FacetContainer addFacet(RecipeFacet facet) {
        this.facets.put(facet.id(), facet);
        return this;
    }

    public boolean hasFacet(RecipeFacetType facetType) {
        return facets.containsKey(facetType.id());
    }

    public boolean hasFacet(ResourceLocation facetId) {
        return facets.containsKey(facetId);
    }

    public boolean hasFacet(RecipeFacet facet) {
        return facets.containsValue(facet);
    }

    public @UnmodifiableView RichIterable<RecipeFacet> allFacets() {
        return facets.valuesView();
    }

    public RecipeFacet getFacet(ResourceLocation facetId) {
        RecipeFacet ret = facets.get(facetId);
        if (ret == null)
            throw new IllegalArgumentException("No recipe facet found with id: " + facetId);
        return ret;
    }

    public RecipeFacet getFacet(RecipeFacetType facetType) {
        RecipeFacet ret = facets.get(facetType.id());
        if (ret == null)
            throw new IllegalArgumentException("No recipe facet found with type: " + facetType);
        return ret;
    }

}

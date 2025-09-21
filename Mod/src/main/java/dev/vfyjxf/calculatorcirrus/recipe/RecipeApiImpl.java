package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

public class RecipeApiImpl implements RecipeApi {

    private final ImmutableMap<ResourceLocation, RecipeSystem<?>> systems;
    private final ImmutableMap<ResourceLocation, RecipeFacetType> facets;
    private final ImmutableMap<RecipeFacetType, FacetTransformer<?, ?>> facetTransformers;
    private final ImmutableMap<RecipeFacetType, ViewTransformer<?>> viewTransformers;

    public RecipeApiImpl(RecipeApiRegistryBuilder builder) {
        this.systems = Maps.immutable.ofMap(builder.recipeSystems);
        this.facets = Maps.immutable.ofMap(builder.facets);
        this.facetTransformers = Maps.immutable.ofMap(builder.facetTransformers);
        this.viewTransformers = Maps.immutable.ofMap(builder.viewTransformers);
    }

    @Override
    public boolean hasSystem(ResourceLocation systemId) {
        return systems.containsKey(systemId);
    }

    @Override
    public RecipeSystem<?> getSystem(ResourceLocation systemId) {
        var ret = systems.get(systemId);
        if (ret == null) {
            throw new IllegalArgumentException("No recipe system found with id: " + systemId);
        }
        return ret;
    }

    @Override
    public @Nullable RecipeSystem<?> getSystemOrNull(ResourceLocation systemId) {
        return systems.get(systemId);
    }

    @Override
    public @UnmodifiableView RichIterable<? extends RecipeSystem<?>> allSystems() {
        return systems.valuesView();
    }

    @Override
    public boolean hasFacet(ResourceLocation facetId) {
        return facets.containsKey(facetId);
    }

    @Override
    public RecipeFacetType getFacetType(ResourceLocation facetId) throws IllegalArgumentException {
        var ret = facets.get(facetId);
        if (ret == null) {
            throw new IllegalArgumentException("No facet found with id: " + facetId);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends RecipeContent, F extends RecipeFacet> FacetTransformer<T, F> getFacetTransformer(RecipeFacetType type) {
        return (FacetTransformer<T, F>) facetTransformers.get(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <F extends RecipeFacet> ViewTransformer<F> getViewTransformer(RecipeFacetType type) {
        return (ViewTransformer<F>) viewTransformers.get(type);
    }
}

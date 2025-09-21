package dev.vfyjxf.calculatorcirrus.recipe;


import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class RecipeApiRegistryBuilder {

    final ConcurrentHashMap<ResourceLocation, RecipeSystem<?>> recipeSystems = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ResourceLocation, RecipeFacetType> facets = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RecipeFacetType, FacetTransformer<?, ?>> facetTransformers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RecipeFacetType, ViewTransformer<?>> viewTransformers = new ConcurrentHashMap<>();

    public void registerSystem(RecipeSystem<?> system) {
        if (recipeSystems.containsKey(system.systemId())) {
            throw new IllegalArgumentException("Recipe system with id: " + system.systemId() + " already registered");
        }
        recipeSystems.put(system.systemId(), system);
    }

    public <T extends RecipeContent, F extends RecipeFacet> void registerFacet(RecipeFacetType type, FacetTransformer<T, F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        if (facets.containsKey(type.id())) {
            throw new IllegalArgumentException("Recipe facet with id: " + type.id() + " already registered");
        }
        facets.put(type.id(), type);
        facetTransformers.put(type, transformer);
    }

    public void registerFacet(RecipeFacetType type) {
        Checks.checkNotNull(type, "type");
        if (facets.containsKey(type.id())) {
            throw new IllegalArgumentException("Recipe facet with id: " + type.id() + " already registered");
        }
        facets.put(type.id(), type);
    }


    public <F extends RecipeFacet> void registerViewTransformer(RecipeFacetType type, ViewTransformer<F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        if (viewTransformers.containsKey(type)) {
            throw new IllegalArgumentException("View transformer for facet with id: " + type.id() + " already registered");
        }
        viewTransformers.put(type, transformer);
    }
}

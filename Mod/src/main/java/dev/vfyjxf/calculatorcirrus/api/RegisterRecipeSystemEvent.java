package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.calculatorcirrus.recipe.RecipeApiRegistryBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterRecipeSystemEvent extends Event implements IModBusEvent {

    private final RecipeApiRegistryBuilder builder;

    public RegisterRecipeSystemEvent(RecipeApiRegistryBuilder builder) {this.builder = builder;}

    public void registerSystem(RecipeSystem<?> system) {
        builder.registerSystem(system);
    }

    public void registerFacet(RecipeFacetType type) {
        builder.registerFacet(type);
    }

    public <T extends RecipeContent, F extends RecipeFacet> void registerFacetTransformer(RecipeFacetType type, FacetTransformer<T, F> transformer) {
        builder.registerFacet(type, transformer);
    }

    public <F extends RecipeFacet> void registerViewTransformer(RecipeFacetType type, ViewTransformer<F> transformer) {
        builder.registerViewTransformer(type, transformer);
    }
}

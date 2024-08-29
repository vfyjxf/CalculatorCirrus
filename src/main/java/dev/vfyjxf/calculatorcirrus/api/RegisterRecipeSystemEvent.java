package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.recipes.RecipeRegistryImpl;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterRecipeSystemEvent extends Event implements IModBusEvent {

    private final RecipeRegistryImpl recipeRegistry;

    public RegisterRecipeSystemEvent(RecipeRegistryImpl recipeRegistry) {
        this.recipeRegistry = recipeRegistry;
    }

    public void registerSystem(RecipeSystem<?> system) {
        recipeRegistry.registerSystem(system);
    }

}

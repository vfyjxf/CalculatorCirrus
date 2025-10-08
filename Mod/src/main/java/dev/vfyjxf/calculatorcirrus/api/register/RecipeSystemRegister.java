package dev.vfyjxf.calculatorcirrus.api.register;

import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystemExtension;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeSystemRegister {

    void registerSystem(RecipeSystem<?> system);

    void registerExtension(ResourceLocation systemId, RecipeSystemExtension extension);

}

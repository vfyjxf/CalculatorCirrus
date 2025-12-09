package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeSystemExtension;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeSystemRegister {

    void registerSystem(RecipeSystem<?> system);

    void registerExtension(Namespace systemId, RecipeSystemExtension extension);

}

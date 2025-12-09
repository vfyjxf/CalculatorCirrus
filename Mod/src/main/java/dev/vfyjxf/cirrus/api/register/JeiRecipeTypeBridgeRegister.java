package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.RecipeType;

public interface JeiRecipeTypeBridgeRegister {

    void registerRecipeTypeForSystem(Namespace systemId, RecipeType<?> recipeType);

}

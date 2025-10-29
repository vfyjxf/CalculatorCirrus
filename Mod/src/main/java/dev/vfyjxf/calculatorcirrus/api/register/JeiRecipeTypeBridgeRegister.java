package dev.vfyjxf.calculatorcirrus.api.register;

import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;

public interface JeiRecipeTypeBridgeRegister {

    void registerRecipeTypeForSystem(ResourceLocation systemId, RecipeType<?> recipeType);

}

package dev.vfyjxf.calculatorcirrus.api.recipe.transform;

import net.minecraft.resources.ResourceLocation;

/**
 * Level Related Context Transformer for Recipe
 */
public interface RecipeModifier {

    ResourceLocation id();

    interface Applier {

    }

}

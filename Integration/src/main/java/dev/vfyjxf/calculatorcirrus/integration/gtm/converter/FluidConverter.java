package dev.vfyjxf.calculatorcirrus.integration.gtm.converter;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.integration.gtm.ContentConverter;

public class FluidConverter implements ContentConverter {
    @Override
    public IngredientStack convert(Content content) {
        FluidRecipeCapability capability = FluidRecipeCapability.CAP;
        float chance = content.chance == content.maxChance ? IngredientStack.MAX_CHANCE : ((float) content.chance) / content.maxChance;
        return IngredientStack.create(capability.of(content), chance);
    }
}

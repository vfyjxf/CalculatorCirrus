package dev.vfyjxf.cirrus.integration.gtm.converter;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import dev.vfyjxf.cirrus.api.recipe.content.IngredientStack;
import dev.vfyjxf.cirrus.integration.gtm.ContentConverter;

public class FluidConverter implements ContentConverter {
    @Override
    public IngredientStack convert(Content content) {
        FluidRecipeCapability capability = FluidRecipeCapability.CAP;
        float chance = content.chance == content.maxChance ? IngredientStack.MAX_CHANCE : ((float) content.chance) / content.maxChance;
        return IngredientStack.create(capability.of(content), chance);
    }
}

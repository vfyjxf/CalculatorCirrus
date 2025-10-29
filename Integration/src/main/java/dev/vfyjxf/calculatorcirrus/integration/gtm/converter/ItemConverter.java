package dev.vfyjxf.calculatorcirrus.integration.gtm.converter;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.IngredientStack;
import dev.vfyjxf.calculatorcirrus.integration.gtm.ContentConverter;

public class ItemConverter implements ContentConverter {
    @Override
    public IngredientStack convert(Content content) {
        ItemRecipeCapability capability = ItemRecipeCapability.CAP;
        float chance = content.chance == content.maxChance ? IngredientStack.MAX_CHANCE : ((float) content.chance) / content.maxChance;
        return IngredientStack.create(capability.of(content.content), chance);
    }
}

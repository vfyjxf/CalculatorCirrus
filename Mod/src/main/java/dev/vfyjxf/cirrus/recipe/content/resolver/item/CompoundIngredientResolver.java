package dev.vfyjxf.cirrus.recipe.content.resolver.item;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.CompoundContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;

public class CompoundIngredientResolver implements IngredientResolver<ICustomIngredient> {

    public static final CompoundIngredientResolver instance = new CompoundIngredientResolver();

    private CompoundIngredientResolver() {}

    @Override
    public boolean select(ICustomIngredient ingredient) {
        return ingredient instanceof CompoundIngredient;
    }

    @Override
    public InputContent resolve(ICustomIngredient ingredient, long amount, float chance) {
        return CompoundContent.fromIngredient((CompoundIngredient) ingredient, amount, chance);
    }
}

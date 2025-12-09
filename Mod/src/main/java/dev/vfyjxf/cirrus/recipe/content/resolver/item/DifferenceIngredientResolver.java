package dev.vfyjxf.cirrus.recipe.content.resolver.item;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DifferenceComponent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import net.neoforged.neoforge.common.crafting.DifferenceIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;

public final class DifferenceIngredientResolver implements IngredientResolver<ICustomIngredient> {

    public static final DifferenceIngredientResolver instance = new DifferenceIngredientResolver();

    private DifferenceIngredientResolver() {}

    @Override
    public boolean select(ICustomIngredient ingredient) {
        return ingredient instanceof DifferenceIngredient;
    }

    @Override
    public InputContent resolve(ICustomIngredient ingredient, long amount, float chance) {
        var actual = (DifferenceIngredient) ingredient;
        InputContent required = InputContent.fromItemIngredient(actual.base());
        InputContent rejected = InputContent.fromItemIngredient(actual.subtracted());
        return DifferenceComponent.from(required, rejected, amount, chance);
    }
}

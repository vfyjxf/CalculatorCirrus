package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

public final class StonecuttingRecipeSystem extends VanillaIngredientRecipeSystem<StonecutterRecipe> {

    public static final Namespace systemId = Namespace.ofMc("stonecutting");

    public StonecuttingRecipeSystem() {
        super(systemId, RecipeType.STONECUTTING);
    }

    @Override
    public String toString() {
        return "StonecuttingRecipeSystem";
    }
}

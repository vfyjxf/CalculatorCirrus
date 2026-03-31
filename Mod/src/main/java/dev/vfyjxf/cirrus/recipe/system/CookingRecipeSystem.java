package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;

public final class CookingRecipeSystem<T extends AbstractCookingRecipe> extends VanillaIngredientRecipeSystem<T> {

    public static final Namespace smeltingSystemId = Namespace.ofMc("smelting");
    public static final Namespace blastingSystemId = Namespace.ofMc("blasting");
    public static final Namespace smokingSystemId = Namespace.ofMc("smoking");
    public static final Namespace campfireCookingSystemId = Namespace.ofMc("campfire_cooking");

    public static CookingRecipeSystem<SmeltingRecipe> smelting() {
        return new CookingRecipeSystem<>(smeltingSystemId, RecipeType.SMELTING);
    }

    public static CookingRecipeSystem<BlastingRecipe> blasting() {
        return new CookingRecipeSystem<>(blastingSystemId, RecipeType.BLASTING);
    }

    public static CookingRecipeSystem<SmokingRecipe> smoking() {
        return new CookingRecipeSystem<>(smokingSystemId, RecipeType.SMOKING);
    }

    public static CookingRecipeSystem<CampfireCookingRecipe> campfireCooking() {
        return new CookingRecipeSystem<>(campfireCookingSystemId, RecipeType.CAMPFIRE_COOKING);
    }

    private CookingRecipeSystem(Namespace systemId, RecipeType<T> recipeType) {
        super(systemId, recipeType);
    }

    @Override
    public String toString() {
        return "CookingRecipeSystem{" + systemId() + '}';
    }
}

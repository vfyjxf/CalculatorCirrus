package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

public final class StonecuttingRecipeSystem extends VanillaIngredientRecipeSystem<StonecutterRecipe> {

    public static final Namespace systemId = Namespace.ofMc("stonecutting");

    public StonecuttingRecipeSystem() {
        super(systemId, RecipeType.STONECUTTING);
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.STONECUTTER));
    }

    @Override
    public String toString() {
        return "StonecuttingRecipeSystem";
    }
}

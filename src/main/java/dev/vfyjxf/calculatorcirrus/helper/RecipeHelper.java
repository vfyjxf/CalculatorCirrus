package dev.vfyjxf.calculatorcirrus.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;

public final class RecipeHelper {

    @SuppressWarnings("ConstantConditions")
    public static ItemStack getOutput(@NotNull Recipe<?> recipe) {
        Minecraft minecraft = Minecraft.getInstance();
        return recipe.getResultItem(minecraft.level.registryAccess());
    }

    @SuppressWarnings("ConstantConditions")
    public static RecipeManager getRecipeManager() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level.getRecipeManager();
    }

    private RecipeHelper() {
    }
}

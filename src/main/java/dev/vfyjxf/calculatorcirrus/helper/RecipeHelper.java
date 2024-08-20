package dev.vfyjxf.calculatorcirrus.helper;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

public final class RecipeHelper {

    @SuppressWarnings("ConstantConditions")
    public static ItemStack getOutput(@NotNull Recipe<?> recipe) {
        Minecraft minecraft = Minecraft.getInstance();
        return recipe.getResultItem(minecraft.level.registryAccess());
    }

    private RecipeHelper() {
    }
}

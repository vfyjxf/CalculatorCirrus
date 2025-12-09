package dev.vfyjxf.cirrus.helper;

import dev.vfyjxf.cirrus.util.GameInstances;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

public final class RecipeHelper {

    @SuppressWarnings("ConstantConditions")
    public static ItemStack getOutput(@NotNull Recipe<?> recipe) {
        return recipe.getResultItem(GameInstances.getRegistryAccess());
    }

    private RecipeHelper() {
    }
}

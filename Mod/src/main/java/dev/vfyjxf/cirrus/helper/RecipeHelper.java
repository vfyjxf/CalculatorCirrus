package dev.vfyjxf.cirrus.helper;

import dev.vfyjxf.cloudlib.api.util.Games;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

public final class RecipeHelper {

    @SuppressWarnings("ConstantConditions")
    public static ItemStack getOutput(@NotNull Recipe<?> recipe) {
        return recipe.getResultItem(Games.registryAccess());
    }

    private RecipeHelper() {throw new AssertionError("This class should not be instantiated!");}
}

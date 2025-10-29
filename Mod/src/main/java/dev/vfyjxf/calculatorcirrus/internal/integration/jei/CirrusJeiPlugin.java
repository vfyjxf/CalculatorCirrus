package dev.vfyjxf.calculatorcirrus.internal.integration.jei;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CirrusJeiPlugin implements IModPlugin {

    private static IRecipeManager recipeManager;

    public static IRecipeManager recipeManager() {
        Checks.checkNotNull(recipeManager, "recipeManager");
        return recipeManager;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.MOD_ID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        CirrusJeiPlugin.recipeManager = jeiRuntime.getRecipeManager();
    }
}

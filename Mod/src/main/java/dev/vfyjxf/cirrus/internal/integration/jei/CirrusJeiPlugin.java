package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.ui.execution.workbench.WorkbenchExecutionOverlayManager;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class CirrusJeiPlugin implements IModPlugin {

    private static IRecipeManager recipeManager;

    public static IFocusFactory focusFactory;

    public static IRecipeManager recipeManager() {
        Checks.checkNotNull(recipeManager, "recipeManager");
        return recipeManager;
    }

    public static IFocusFactory focusFactory() {
        Checks.checkNotNull(focusFactory, "focusFactory");
        return focusFactory;

    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public java.util.Collection<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas() {
                return WorkbenchExecutionOverlayManager.jeiExtraAreas();
            }
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        CirrusJeiPlugin.recipeManager = jeiRuntime.getRecipeManager();
        CirrusJeiPlugin.focusFactory = jeiRuntime.getJeiHelpers().getFocusFactory();
    }
}

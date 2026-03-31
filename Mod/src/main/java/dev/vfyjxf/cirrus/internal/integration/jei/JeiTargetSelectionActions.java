package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class JeiTargetSelectionActions {

    private JeiTargetSelectionActions() {
    }

    public static void closeScreenIfNeeded(Screen recipesGui, JeiTargetSelectionResult result) {
        if (!result.success()) {
            return;
        }
        if (ExecutionOverlayClientConfig.jeiTargetButtonAutoClose()) {
            recipesGui.onClose();
        }
    }

    public static void closeCurrentRecipeScreenIfNeeded(JeiTargetSelectionResult result) {
        if (Minecraft.getInstance().screen instanceof RecipesGui recipesGui) {
            closeScreenIfNeeded(recipesGui, result);
        }
    }
}

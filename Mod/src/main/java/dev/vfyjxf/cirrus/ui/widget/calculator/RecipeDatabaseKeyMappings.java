package dev.vfyjxf.cirrus.ui.widget.calculator;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class RecipeDatabaseKeyMappings {
    private static final String category = "key.categories." + CirrusConstants.modId;

    public static final KeyMapping openRecipeInCalculator = new KeyMapping(
        "key." + CirrusConstants.modId + ".database.open_recipe_in_calculator",
        KeyConflictContext.GUI,
        KeyModifier.SHIFT,
        InputConstants.Type.MOUSE,
        GLFW.GLFW_MOUSE_BUTTON_LEFT,
        category
    );

    private RecipeDatabaseKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(openRecipeInCalculator);
    }

    public static boolean openRecipeInCalculatorDown() {
        return openRecipeInCalculator.isDown();
    }

    public static boolean openRecipeInCalculatorPressed(InputConstants.Key key) {
        return openRecipeInCalculator.isActiveAndMatches(key);
    }

    public static Component keyName(KeyMapping keyMapping) {
        return keyMapping.getTranslatedKeyMessage();
    }
}

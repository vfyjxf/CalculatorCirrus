package dev.vfyjxf.cirrus.ui.widget.calculator;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class CalculatorInputKeyMappings {
    private static final String category = "key.categories." + CirrusConstants.modId;

    public static final KeyMapping openAmountCalculatorPopup = new KeyMapping(
        "key." + CirrusConstants.modId + ".calculator.open_amount_popup",
        KeyConflictContext.GUI,
        KeyModifier.NONE,
        InputConstants.Type.MOUSE,
        GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
        category
    );

    public static final KeyMapping openWorkbench = new KeyMapping(
        "key." + CirrusConstants.modId + ".calculator.open_workbench",
        KeyConflictContext.GUI,
        KeyModifier.NONE,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        category
    );

    private CalculatorInputKeyMappings() {}

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(openAmountCalculatorPopup);
        event.register(openWorkbench);
    }

    public static boolean openAmountCalculatorPopupPressed(InputConstants.Key key) {
        return openAmountCalculatorPopup.isActiveAndMatches(key);
    }

    public static boolean isOpenWorkbenchPressed(InputConstants.Key key) {
        return openWorkbench.isActiveAndMatches(key);
    }

    public static MutableComponent openAmountCalculatorPopupUsage(Component usage) {
        return keyUsage(openAmountCalculatorPopup, usage);
    }

    public static MutableComponent openWorkbenchUsage(Component usage) {
        return keyUsage(openWorkbench, usage);
    }

    public static MutableComponent keyUsage(@Nullable KeyMapping keyMapping, Component usage) {
        Component key = keyMapping == null
                ? InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT).getDisplayName()
                : keyMapping.getTranslatedKeyMessage();
        return Component.empty().append(Component.literal("[")).append(key.copy()).append(Component.literal("] "))
                .append(usage.copy());
    }
}

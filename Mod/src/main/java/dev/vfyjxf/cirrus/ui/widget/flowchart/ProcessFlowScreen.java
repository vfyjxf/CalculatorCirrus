package dev.vfyjxf.cirrus.ui.widget.flowchart;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
import dev.vfyjxf.cloudlib.api.ui.base.SceneLayer;
import dev.vfyjxf.cloudlib.api.ui.debug.Inspector;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;
import static dev.vfyjxf.taffy.style.LengthPercentageAuto.auto;
import static dev.vfyjxf.taffy.style.LengthPercentageAuto.length;

/**
 * Full-screen process flow chart viewer.
 * <p>
 * Opens with <b>Ctrl+F</b> in debug mode.
 * Displays a complex industrial process graph laid out using the Sugiyama
 * layered graph drawing algorithm.
 */
@EventBusSubscriber(modid = CirrusConstants.MOD_ID, value = Dist.CLIENT)
public class ProcessFlowScreen extends BasicScreen {

    // ───────── Keybinding ─────────

    private static final KeyMapping OPEN_FLOW_SCREEN = new KeyMapping(
            "Open Process Flow Chart",
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "cirrus"
    );

    @SubscribeEvent
    private static void onInputKey(InputEvent.Key event) {
        if (FMLEnvironment.production) return;
        InputContext inputContext = InputContext.fromEvent(event);
        if (inputContext.released(OPEN_FLOW_SCREEN)) {
            Minecraft.getInstance().setScreen(new ProcessFlowScreen());
        }
    }

    // ───────── Screen construction ─────────

    public ProcessFlowScreen() {
        // Create the recipe steps widget with complex test data
        var stepsWidget = new RecipeStepsWidget();
        stepsWidget.setGraph(TestDataGenerator.createComplexGraph())
                   .setCraftCount(64);
        stepsWidget.useStyle(
                sizeOf(TaffyDimension.percent(1), TaffyDimension.percent(1))
        );

        mainGroup.addWidget(stepsWidget);
        mainGroup.useStyle(
                sizeOf(TaffyDimension.percent(1), TaffyDimension.percent(1))
        );

//        // Debug inspector overlay
//        mainGroup.addWidget(
//                Inspector.create()
//                         .setTrackMouse(true)
//                         .useStyle(
//                                 positionAbsolute(),
//                                 inset(auto(), length(0), length(0), auto()),
//                                 sizeOf(280, 180)
//                         )
//                         .setSceneLayer(SceneLayer.debug)
//        );
    }
}

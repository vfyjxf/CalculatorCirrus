package dev.vfyjxf.cirrus.ui.widget.execution;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.workbench.WorkbenchExecutionDemoController;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
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

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsStretch;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Standalone sandbox for execution-overlay styling.
 * Opens with /cirrus exec_overlay or Ctrl+O in development mode.
 */
@EventBusSubscriber(modid = CirrusConstants.MOD_ID, value = Dist.CLIENT)
public final class ExecutionOverlaySandboxScreen extends BasicScreen {
    private static final int debugOverlayWidth = ExecutionOverlayWidget.jeiBookmarkAlignedPanelWidth;

    //region keybinds
    private static final KeyMapping openSandboxScreen = new KeyMapping(
        "Open Execution Overlay Sandbox",
        KeyConflictContext.GUI,
        KeyModifier.CONTROL,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        "cirrus"
    );

    @SubscribeEvent
    private static void onInputKey(InputEvent.Key event) {
        if (FMLEnvironment.production) return;
        InputContext inputContext = InputContext.fromEvent(event);
        if (inputContext.released(openSandboxScreen)) {
            maximizeWindowedForSandbox();
            Minecraft.getInstance().setScreen(new ExecutionOverlaySandboxScreen());
        }
    }
    //endregion

    //region State
    private final WorkbenchExecutionDemoController controller = new WorkbenchExecutionDemoController();
    private final ExecutionOverlayWidget overlayWidget = new ExecutionOverlayWidget();
    //endregion

    public ExecutionOverlaySandboxScreen() {
        maximizeWindowedForSandbox();
        mainGroup.useStyle(sizeFull(), padding(6), background(CirrusTextures.Background.dark));

        var root = new WidgetGroup<>();
        root.useStyle(flexRow(), sizeFull(), gap(6), alignItemsStretch());

        root.addWidget(createOverlayPane());
        var spacer = new WidgetGroup<>();
        spacer.useStyle(flexGrow(1), minWidth(0), minHeight(0));
        root.addWidget(spacer);
        mainGroup.addWidget(root);

        refreshOverlay();
    }

    //region Screen lifecycle
    @Override
    public void tick() {
        super.tick();
        controller.tick();
        refreshOverlay();
    }
    //endregion

    //region Debug API
    public static int debugPreviewComponentCount() {
        return 0;
    }

    public static boolean debugUsesSharedOverlayController() {
        return true;
    }
    //endregion

    //region Layout
    private Widget createOverlayPane() {
        var leftPane = new WidgetGroup<>();
        leftPane.useStyle(
            flexColumn(),
            widthOf(debugOverlayWidth),
            minWidth(debugOverlayWidth),
            minHeight(0),
            padding(2),
            background(CirrusTextures.Background.flat)
        );

        overlayWidget.useStyle(flexGrow(1), minHeight(0));
        leftPane.addWidget(overlayWidget);
        return leftPane;
    }
    //endregion

    //region Helpers
    private void refreshOverlay() {
        overlayWidget.refreshFromHost(controller);
    }

    private static void maximizeWindowedForSandbox() {
        if (FMLEnvironment.production) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return;
        }
        var window = minecraft.getWindow();
        if (window.isFullscreen()) {
            return;
        }
        long handle = window.getWindow();
        if (handle == 0L) {
            return;
        }
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_FALSE) {
            GLFW.glfwMaximizeWindow(handle);
        }
    }
    //endregion

}

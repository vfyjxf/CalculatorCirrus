package dev.vfyjxf.cirrus.ui.execution.workbench;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.event.EventListeners;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.widget.execution.ExecutionOverlayWidget;
import dev.vfyjxf.cloudlib.api.ui.base.FocusScopeNode;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.base.host.ScreenSceneHost;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.columnGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.inset;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.rowGap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;
import static dev.vfyjxf.taffy.style.LengthPercentageAuto.auto;
import static dev.vfyjxf.taffy.style.LengthPercentageAuto.length;

/**
 * Mounts a standalone CloudLib scene on top of the vanilla crafting table screen.
 */
public final class WorkbenchExecutionOverlayManager {

    //region singleton

    private static final WorkbenchExecutionOverlayManager instance = new WorkbenchExecutionOverlayManager();

    public static void register() {
        instance.registerListeners();
    }

    public static Collection<Rect2i> jeiExtraAreas() {
        return instance.currentJeiExtraAreas();
    }

    //endregion

    //region state

    private boolean registered;
    private OverlayState currentState;

    //endregion

    private WorkbenchExecutionOverlayManager() {
    }

    //region listener registration

    private void registerListeners() {
        if (registered) {
            return;
        }
        registered = true;

        EventListeners.register(ScreenEvent.Init.Post.class, this::onScreenInit);
        EventListeners.register(ScreenEvent.Render.Post.class, this::onScreenRender);
        EventListeners.register(ScreenEvent.MouseButtonPressed.Pre.class, this::onMousePressed);
        EventListeners.register(ScreenEvent.MouseButtonReleased.Pre.class, this::onMouseReleased);
        EventListeners.register(ScreenEvent.MouseDragged.Pre.class, this::onMouseDragged);
        EventListeners.register(ScreenEvent.MouseScrolled.Pre.class, this::onMouseScrolled);
        EventListeners.register(ScreenEvent.KeyPressed.Pre.class, this::onKeyPressed);
        EventListeners.register(ScreenEvent.CharacterTyped.Pre.class, this::onCharTyped);
        EventListeners.register(ClientTickEvent.Post.class, this::onClientTick);
    }

    //endregion

    //region jei

    private Collection<Rect2i> currentJeiExtraAreas() {
        Screen screen = Minecraft.getInstance().screen;
        if (currentState == null || !currentState.matches(screen) || !currentState.visible()) {
            return List.of();
        }
        return List.of(currentState.overlayBounds());
    }

    //endregion

    //region events

    private void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CraftingScreen craftingScreen) {
            if (currentState != null && currentState.matches(craftingScreen)) {
                currentState.rebuildScene();
            } else {
                replaceState(new OverlayState(craftingScreen));
            }
            return;
        }
        clearStateIfDifferent(event.getScreen());
    }

    private void onScreenRender(ScreenEvent.Render.Post event) {
        if (currentState == null || !currentState.matches(event.getScreen()) || !currentState.visible()) {
            return;
        }
        currentState.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
    }

    private void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (currentState != null && currentState.matches(event.getScreen()) && currentState.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    private void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (currentState != null && currentState.matches(event.getScreen()) && currentState.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    private void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (currentState != null
            && currentState.matches(event.getScreen())
            && currentState.mouseDragged(event.getMouseX(), event.getMouseY(), event.getMouseButton(), event.getDragX(), event.getDragY())) {
            event.setCanceled(true);
        }
    }

    private void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (currentState != null
            && currentState.matches(event.getScreen())
            && currentState.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (currentState == null || !currentState.matches(event.getScreen())) {
            return;
        }
        if (isToggleOverlay(event.getKeyCode(), event.getModifiers())) {
            currentState.toggleVisible();
            event.setCanceled(true);
            return;
        }
        if (currentState.handleHotkey(event.getKeyCode())) {
            event.setCanceled(true);
            return;
        }
        if (currentState.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (currentState != null && currentState.matches(event.getScreen()) && currentState.charTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (currentState == null) {
            return;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (!currentState.matches(screen)) {
            clearState();
            return;
        }
        currentState.tick();
    }

    //endregion

    //region helpers

    private boolean isToggleOverlay(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_U && (modifiers & InputConstants.MOD_CONTROL) != 0;
    }

    private void replaceState(OverlayState nextState) {
        clearState();
        currentState = nextState;
    }

    private void clearStateIfDifferent(Screen screen) {
        if (currentState != null && !currentState.matches(screen)) {
            clearState();
        }
    }

    private void clearState() {
        if (currentState != null) {
            currentState.destroy();
            currentState = null;
        }
    }

    //endregion

    //region overlay state

    private static final class OverlayState {

        private final CraftingScreen screen;
        private final WorkbenchExecutionDemoController controller;

        private Scene scene;
        private WidgetGroup<Widget> root;
        private WidgetGroup<Widget> panel;
        private ExecutionOverlayWidget overlayWidget;
        private boolean visible = true;

        private OverlayState(CraftingScreen screen) {
            this.screen = screen;
            controller = new WorkbenchExecutionDemoController();
            rebuildScene();
        }

        //region lifecycle

        private void rebuildScene() {
            if (scene != null) {
                scene.destroy();
            }

            root = new WidgetGroup<>();
            root.setFocusNode(new FocusScopeNode());
            root.useStyle(UIStyle.of(sizeOf(screen.width, screen.height)));

            panel = root.addWidget(new WidgetGroup<>());
            panel.useStyle(UIStyle.of(
                flexColumn(),
                positionAbsolute(),
                inset(length(WorkbenchExecutionOverlayLayout.panelMarginTop), auto(), auto(), length(WorkbenchExecutionOverlayLayout.panelMarginLeft))
            ));

            overlayWidget = panel.addWidget(new ExecutionOverlayWidget());
            overlayWidget.useStyle(UIStyle.of(
                flexGrow(1),
                minHeight(0)
            ));

            scene = new Scene(root);
            scene.init();
            scene.mount(SceneContext.create(new ScreenSceneHost(screen)));
            layout();
            applyVisibility();
            refresh();
        }

        private void destroy() {
            if (scene != null) {
                scene.destroy();
                scene = null;
            }
        }

        //endregion

        //region state

        private boolean matches(Screen candidate) {
            return screen == candidate;
        }

        private boolean visible() {
            return visible;
        }

        private Rect2i overlayBounds() {
            return WorkbenchExecutionOverlayLayout.overlayBounds(screen.width, screen.height);
        }

        private void toggleVisible() {
            visible = !visible;
            applyVisibility();
            refresh();
        }

        private void applyVisibility() {
            if (panel != null) {
                panel.setVisible(visible);
            }
        }

        private void refresh() {
            if (overlayWidget == null) {
                return;
            }
            overlayWidget.refreshFromHost(controller);
        }

        //endregion

        //region interaction

        private boolean handleHotkey(int keyCode) {
            if (!visible) {
                return false;
            }
            return switch (keyCode) {
                case GLFW.GLFW_KEY_A -> {
                    controller.overlayAdvanceSteps(1);
                    refresh();
                    yield true;
                }
                case GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_S -> {
                    controller.overlayAdvanceSteps(3);
                    refresh();
                    yield true;
                }
                case GLFW.GLFW_KEY_T -> {
                    controller.overlayToggleAutoAdvance();
                    refresh();
                    yield true;
                }
                case GLFW.GLFW_KEY_R -> {
                    controller.overlayReset();
                    refresh();
                    yield true;
                }
                default -> false;
            };
        }

        private boolean mouseClicked(double mouseX, double mouseY, int button) {
            return visible && scene != null && scene.mouseClicked(mouseX, mouseY, button);
        }

        private boolean mouseReleased(double mouseX, double mouseY, int button) {
            return visible && scene != null && scene.mouseReleased(mouseX, mouseY, button);
        }

        private boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            return visible && scene != null && scene.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        private boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return visible && scene != null && scene.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return visible && scene != null && scene.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean charTyped(char codePoint, int modifiers) {
            return visible && scene != null && scene.charTyped(codePoint, modifiers);
        }

        //endregion

        //region frame

        private void tick() {
            if (!visible || scene == null) {
                return;
            }
            controller.tick();
            refresh();
            scene.tick();
        }

        private void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (scene == null) {
                return;
            }
            layout();
            scene.mouseMoved(mouseX, mouseY);
            scene.render(graphics, mouseX, mouseY, partialTick);
        }

        private void layout() {
            var bounds = overlayBounds();
            root.useStyle(UIStyle.of(sizeOf(screen.width, screen.height)));
            panel.useStyle(UIStyle.of(
                flexColumn(),
                rowGap(WorkbenchExecutionOverlayLayout.controlsGap),
                positionAbsolute(),
                inset(length(bounds.getY()), auto(), auto(), length(bounds.getX())),
                sizeOf(bounds.getWidth(), bounds.getHeight())
            ));
            overlayWidget.useStyle(UIStyle.of(
                widthOf(bounds.getWidth()),
                heightOf(WorkbenchExecutionOverlayLayout.contentHeight(screen.height))
            ));
            scene.setLayoutArea(screen.width, screen.height);
            scene.layout();
            root.applyLayout();
        }

        //endregion
    }

    //endregion
}

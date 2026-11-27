package dev.vfyjxf.cirrus.ui.widget.calculator;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchIntegrationProvider;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchSurfaces;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.BasicScreen;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetPath;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetZero;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

@EventBusSubscriber(modid = CirrusConstants.modId, value = Dist.CLIENT)
public class CalculatorScreen extends BasicScreen {

    private final WorkbenchShellWidget shell;
    private final WorkbenchSurfaces surfaces;
    private final SessionTabManager tabManager;
    private final ExecutorService planningExecutor;
    private boolean pendingTargetOpened;
    private boolean released;

    public CalculatorScreen() {
        this(WorkbenchMode.planner);
    }

    public CalculatorScreen(WorkbenchMode mode) {
        planningExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CalculatorCirrus planning");
            thread.setDaemon(true);
            return thread;
        });
        tabManager = SessionTabManager
                .forRuntime(CirrusPlanningRuntime.sharedOrCreate(), Minecraft.getInstance().player, planningExecutor);
        WorkbenchIntegrationProvider provider = new WorkbenchIntegrationProvider();
        shell = new WorkbenchShellWidget(provider, tabManager, mode);
        surfaces = WorkbenchSurfaces.install(shell, provider);
        scene().setRespectExclusions(false);
        mainGroup.useStyle(sizeFull());
        shell.useStyle(positionAbsolute(), insetZero(), sizeFull());
        mainGroup.addWidget(shell);
    }

    /**
     * The workbench hotkey opens the full-screen workbench from any GUI except this screen and text input.
     */
    @SubscribeEvent
    private static void onInputKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        if (current instanceof CalculatorScreen) {
            return;
        }
        if (current != null && current.getFocused() instanceof EditBox) {
            return;
        }
        InputContext input = InputContext.fromEvent(event);
        if (!input.pressed(CalculatorInputKeyMappings.openWorkbench)) {
            return;
        }
        minecraft.setScreen(new CalculatorScreen());
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean released = super.mouseReleased(mouseX, mouseY, button);
        shell.releaseAllDrags(mouseX, mouseY);
        return released;
    }

    @Override
    protected void init() {
        super.init();
        shell.applyViewport(width, height);
        scene().layout();
        mainGroup().applyLayout();
        if (!pendingTargetOpened) {
            pendingTargetOpened = true;
            openPendingTarget();
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        shell.applyViewport(width, height);
        scene().layout();
        mainGroup().applyLayout();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (shell.commandPaletteOpen()) {
                shell.closeCommandPalette();
                return true;
            }
            if (shell.closePopovers()) {
                return true;
            }
            if (shell.zenMode()) {
                shell.setZenMode(false);
                return true;
            }
        }
        if (!textInputFocused()) {
            if (keyCode == GLFW.GLFW_KEY_TAB && shell.mode() == WorkbenchMode.planner) {
                shell.setZenMode(!shell.zenMode());
                return true;
            }
            if (CalculatorInputKeyMappings.isOpenWorkbenchPressed(InputConstants.getKey(keyCode, scanCode))) {
                shell.openCommandPalette();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        releaseScreenState();
        super.onClose();
    }

    @Override
    public void removed() {
        releaseScreenState();
        super.removed();
    }

    public WorkbenchShellWidget shell() {
        return shell;
    }

    public WorkbenchSurfaces surfaces() {
        return surfaces;
    }

    private void openPendingTarget() {
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.consumePending();
        List<TargetDemand> demands = selection == null ? List.of() : selection.demands();
        RecipeEntry<?> recipe = selection == null ? null : selection.recipe();
        shell.openTab(demands, recipe);
    }

    private boolean textInputFocused() {
        Widget focused = scene().focusingWidget();
        if (focused == null) {
            return false;
        }
        WidgetPath path = focused.path();
        for (int index = 0; index < path.size(); index++) {
            if (isTextInput(path.get(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTextInput(@Nullable Widget widget) {
        return widget instanceof TextFieldWidget
                || widget instanceof CirrusTextInputWidget
                || widget instanceof SearchBoxWidget
                || widget instanceof NumberInputWidget;
    }

    private void releaseScreenState() {
        if (released) {
            return;
        }
        released = true;
        shell.shutdown();
        planningExecutor.shutdownNow();
    }
}

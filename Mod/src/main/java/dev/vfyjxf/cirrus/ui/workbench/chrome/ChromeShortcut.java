package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Pure implementation of the 04 §3 shortcut table and the focus-isolation rules.
 */
public final class ChromeShortcut {

    public enum Command {
        togglePalette, fitView, centerTarget, autoLayout, cycleLayout, declareOwned, openProducers, banStepRecipe, viewSpatialDag, viewLedger, viewChecklist, undo, redo, newTab, closeTab, sendExecution, toggleZen, modePlanner, modeFacilities, modeDatabase, modeSettings
    }

    public enum Scope {
        global, planner, dagCanvas, dagSelection
    }

    public enum EscapeLayer {
        drag, palette, popover, zenOrMultiselect, screen
    }

    public record Modifiers(boolean ctrl, boolean shift, boolean alt) {

        public static final Modifiers none = new Modifiers(false, false, false);

        public static Modifiers of(boolean ctrl, boolean shift, boolean alt) {
            return new Modifiers(ctrl, shift, alt);
        }

        public static Modifiers fromGlfw(int glfwModifiers) {
            return new Modifiers(
                (glfwModifiers & GLFW.GLFW_MOD_CONTROL) != 0,
                (glfwModifiers & GLFW.GLFW_MOD_SHIFT) != 0,
                (glfwModifiers & GLFW.GLFW_MOD_ALT) != 0
            );
        }

        public boolean plain() {
            return !ctrl && !shift && !alt;
        }
    }

    public record Binding(int keyCode, Modifiers modifiers, Scope scope, Command command) {

        public boolean matches(int pressedKey, Modifiers pressedModifiers) {
            return keyCode == pressedKey && modifiers.equals(pressedModifiers);
        }
    }

    private static final Modifiers ctrl = new Modifiers(true, false, false);
    private static final Modifiers ctrlShift = new Modifiers(true, true, false);
    private static final Modifiers alt = new Modifiers(false, false, true);

    private static final List<Binding> bindings = List.of(
        binding(GLFW.GLFW_KEY_K, ctrl, Scope.global, Command.togglePalette),
        binding(GLFW.GLFW_KEY_SLASH, Modifiers.none, Scope.global, Command.togglePalette),
        binding(GLFW.GLFW_KEY_TAB, Modifiers.none, Scope.planner, Command.toggleZen),
        binding(GLFW.GLFW_KEY_Z, ctrl, Scope.planner, Command.undo),
        binding(GLFW.GLFW_KEY_Z, ctrlShift, Scope.planner, Command.redo),
        binding(GLFW.GLFW_KEY_Y, ctrl, Scope.planner, Command.redo),
        binding(GLFW.GLFW_KEY_T, ctrl, Scope.global, Command.newTab),
        binding(GLFW.GLFW_KEY_W, ctrl, Scope.global, Command.closeTab),
        binding(GLFW.GLFW_KEY_ENTER, ctrl, Scope.planner, Command.sendExecution),
        binding(GLFW.GLFW_KEY_1, alt, Scope.global, Command.modePlanner),
        binding(GLFW.GLFW_KEY_2, alt, Scope.global, Command.modeFacilities),
        binding(GLFW.GLFW_KEY_3, alt, Scope.global, Command.modeDatabase),
        binding(GLFW.GLFW_KEY_4, alt, Scope.global, Command.modeSettings),
        binding(GLFW.GLFW_KEY_1, Modifiers.none, Scope.planner, Command.viewSpatialDag),
        binding(GLFW.GLFW_KEY_2, Modifiers.none, Scope.planner, Command.viewLedger),
        binding(GLFW.GLFW_KEY_3, Modifiers.none, Scope.planner, Command.viewChecklist),
        binding(GLFW.GLFW_KEY_F, Modifiers.none, Scope.dagCanvas, Command.fitView),
        binding(GLFW.GLFW_KEY_HOME, Modifiers.none, Scope.dagCanvas, Command.centerTarget),
        binding(GLFW.GLFW_KEY_L, Modifiers.none, Scope.dagCanvas, Command.autoLayout),
        binding(GLFW.GLFW_KEY_R, Modifiers.none, Scope.dagCanvas, Command.cycleLayout),
        binding(GLFW.GLFW_KEY_M, Modifiers.none, Scope.dagSelection, Command.declareOwned),
        binding(GLFW.GLFW_KEY_P, Modifiers.none, Scope.dagSelection, Command.openProducers),
        binding(GLFW.GLFW_KEY_DELETE, Modifiers.none, Scope.dagSelection, Command.banStepRecipe),
        binding(GLFW.GLFW_KEY_BACKSPACE, Modifiers.none, Scope.dagSelection, Command.banStepRecipe)
    );

    private static final List<Command> textFocusAllowed = List.of(Command.togglePalette, Command.undo, Command.redo);

    private ChromeShortcut() {}

    private static Binding binding(int keyCode, Modifiers modifiers, Scope scope, Command command) {
        return new Binding(keyCode, modifiers, scope, command);
    }

    public static List<Binding> bindings() {
        return bindings;
    }

    public static List<Command> textFocusAllowed() {
        return textFocusAllowed;
    }

    /**
     * Under text focus only Esc/Enter/Ctrl+K/Ctrl+Z/Ctrl+Y pass; any other single key returns null, meaning unconsumed.
     */
    public static @Nullable Command resolve(int keyCode, Modifiers modifiers, boolean textInputFocused) {
        Modifiers pressed = modifiers == null ? Modifiers.none : modifiers;
        for (Binding binding : bindings) {
            if (!binding.matches(keyCode, pressed)) {
                continue;
            }
            if (textInputFocused && !textFocusAllowed.contains(binding.command())) {
                return null;
            }
            return binding.command();
        }
        return null;
    }

    public static boolean consumedFor(int keyCode, Modifiers modifiers, boolean textInputFocused) {
        return resolve(keyCode, modifiers, textInputFocused) != null;
    }

    public static EscapeLayer escapeLayer(
        boolean activeDrag,
        boolean paletteOpen,
        boolean popoverOpen,
        boolean zenModeOrMultiSelect
    ) {
        if (activeDrag) {
            return EscapeLayer.drag;
        }
        if (paletteOpen) {
            return EscapeLayer.palette;
        }
        if (popoverOpen) {
            return EscapeLayer.popover;
        }
        if (zenModeOrMultiSelect) {
            return EscapeLayer.zenOrMultiselect;
        }
        return EscapeLayer.screen;
    }

    public static boolean allowsWhileTextFocused(int keyCode, Modifiers modifiers) {
        Command command = resolve(keyCode, modifiers, true);
        return command != null;
    }

    public static boolean isTextInput(@Nullable Widget widget) {
        return widget instanceof TextFieldWidget
                || widget instanceof CirrusTextInputWidget
                || widget instanceof SearchBoxWidget
                || widget instanceof NumberInputWidget;
    }

    public static int bindingCount(Scope scope) {
        int count = 0;
        for (Binding binding : bindings) {
            if (binding.scope() == scope) {
                count++;
            }
        }
        return count;
    }
}

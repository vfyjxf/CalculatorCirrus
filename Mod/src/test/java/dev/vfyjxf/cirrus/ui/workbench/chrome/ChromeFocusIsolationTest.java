package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.demand;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.ironIngot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-19: pure-function and routing assertions for text focus isolation and the five-level Esc stack unwind.
 */
class ChromeFocusIsolationTest {

    private static final ChromeShortcut.Modifiers ctrl = ChromeShortcut.Modifiers.of(true, false, false);
    private static final ChromeShortcut.Modifiers ctrlShift = ChromeShortcut.Modifiers.of(true, true, false);
    private static final ChromeShortcut.Modifiers alt = ChromeShortcut.Modifiers.of(false, false, true);
    private static final ChromeShortcut.Modifiers none = ChromeShortcut.Modifiers.none;

    private record KeyCase(int key, ChromeShortcut.Command command) {}

    @Test
    void textFocusBlocksEverySingleKeyShortcut() {
        List<KeyCase> cases = List.of(
            new KeyCase(GLFW.GLFW_KEY_F, ChromeShortcut.Command.fitView),
            new KeyCase(GLFW.GLFW_KEY_L, ChromeShortcut.Command.autoLayout),
            new KeyCase(GLFW.GLFW_KEY_R, ChromeShortcut.Command.cycleLayout),
            new KeyCase(GLFW.GLFW_KEY_HOME, ChromeShortcut.Command.centerTarget),
            new KeyCase(GLFW.GLFW_KEY_M, ChromeShortcut.Command.declareOwned),
            new KeyCase(GLFW.GLFW_KEY_P, ChromeShortcut.Command.openProducers),
            new KeyCase(GLFW.GLFW_KEY_1, ChromeShortcut.Command.viewSpatialDag),
            new KeyCase(GLFW.GLFW_KEY_2, ChromeShortcut.Command.viewLedger),
            new KeyCase(GLFW.GLFW_KEY_3, ChromeShortcut.Command.viewChecklist),
            new KeyCase(GLFW.GLFW_KEY_DELETE, ChromeShortcut.Command.banStepRecipe),
            new KeyCase(GLFW.GLFW_KEY_BACKSPACE, ChromeShortcut.Command.banStepRecipe),
            new KeyCase(GLFW.GLFW_KEY_TAB, ChromeShortcut.Command.toggleZen)
        );

        for (KeyCase testCase : cases) {
            assertNull(
                ChromeShortcut.resolve(testCase.key(), none, true),
                "text focus must release key " + testCase.key()
            );
            assertFalse(ChromeShortcut.consumedFor(testCase.key(), none, true));
            assertEquals(
                testCase.command(),
                ChromeShortcut.resolve(testCase.key(), none, false),
                "without text focus key " + testCase.key() + " must dispatch"
            );
            assertTrue(ChromeShortcut.consumedFor(testCase.key(), none, false));
        }

        assertEquals(
            List.of(ChromeShortcut.Command.togglePalette, ChromeShortcut.Command.undo, ChromeShortcut.Command.redo),
            ChromeShortcut.textFocusAllowed()
        );
    }

    @Test
    void ctrlKUndoAndRedoStayAvailableInsideTextFields() {
        assertTrue(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_K, ctrl));
        assertTrue(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_Z, ctrl));
        assertTrue(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_Z, ctrlShift));
        assertTrue(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_Y, ctrl));
        assertEquals(ChromeShortcut.Command.togglePalette, ChromeShortcut.resolve(GLFW.GLFW_KEY_K, ctrl, true));
        assertEquals(ChromeShortcut.Command.undo, ChromeShortcut.resolve(GLFW.GLFW_KEY_Z, ctrl, true));
        assertEquals(ChromeShortcut.Command.redo, ChromeShortcut.resolve(GLFW.GLFW_KEY_Z, ctrlShift, true));
        assertEquals(ChromeShortcut.Command.redo, ChromeShortcut.resolve(GLFW.GLFW_KEY_Y, ctrl, true));

        assertFalse(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_T, ctrl));
        assertFalse(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_W, ctrl));
        assertFalse(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_ENTER, ctrl));
        assertFalse(ChromeShortcut.allowsWhileTextFocused(GLFW.GLFW_KEY_1, alt));
        assertNull(ChromeShortcut.resolve(GLFW.GLFW_KEY_T, ctrl, true));
        assertNull(ChromeShortcut.resolve(GLFW.GLFW_KEY_ENTER, ctrl, true));
    }

    @Test
    void routerDropsBlockedCommandsBeforeAnySideEffect() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        ctx.publishGraph();
        ctx.controller().refine(
            new ApplyRulesFeedback(
                List.of(
                    new MachineRule(
                        "rule-focus",
                        GuidanceScope.session,
                        RuleStrength.softPrefer,
                        OfflineContentTypes.machine("minecraft:furnace"),
                        true
                    )
                ),
                false
            ),
            null
        );
        ChromeTestContext.RecordingSink sink = new ChromeTestContext.RecordingSink();
        ChromeKeyRouter router = new ChromeKeyRouter(ctx).sink(sink);

        assertFalse(router.dispatch(GLFW.GLFW_KEY_F, none, true));
        assertFalse(router.dispatch(GLFW.GLFW_KEY_L, none, true));
        assertFalse(router.dispatch(GLFW.GLFW_KEY_M, none, true));
        assertFalse(router.dispatch(GLFW.GLFW_KEY_P, none, true));
        assertFalse(router.dispatch(GLFW.GLFW_KEY_DELETE, none, true));
        assertFalse(router.dispatch(GLFW.GLFW_KEY_1, none, true));
        assertFalse(router.dispatch(GLFW.GLFW_KEY_T, ctrl, true));
        assertTrue(sink.canvasCommands.isEmpty(), "no canvas command may fire while text is focused");
        assertEquals(0, ctx.openTabCalls.size());
        assertEquals(FlowViewMode.spatialDag, ctx.controller().state().flowViewMode());

        assertTrue(router.dispatch(GLFW.GLFW_KEY_F, none, false));
        assertTrue(router.dispatch(GLFW.GLFW_KEY_HOME, none, false));
        assertTrue(router.dispatch(GLFW.GLFW_KEY_M, none, false));
        assertTrue(router.dispatch(GLFW.GLFW_KEY_P, none, false));
        assertTrue(router.dispatch(GLFW.GLFW_KEY_DELETE, none, false));
        assertEquals(
            List.of(
                ChromeShortcut.Command.fitView,
                ChromeShortcut.Command.centerTarget,
                ChromeShortcut.Command.declareOwned,
                ChromeShortcut.Command.openProducers,
                ChromeShortcut.Command.banStepRecipe
            ),
            sink.canvasCommands
        );

        assertTrue(router.dispatch(GLFW.GLFW_KEY_2, none, false));
        assertEquals(FlowViewMode.preparationLedger, ctx.controller().state().flowViewMode());
        assertFalse(router.dispatch(GLFW.GLFW_KEY_1, none, true));
        assertEquals(
            FlowViewMode.preparationLedger,
            ctx.controller().state().flowViewMode(),
            "the blocked view key must not change the active flow view"
        );
        assertTrue(router.dispatch(GLFW.GLFW_KEY_1, none, false));
        assertEquals(FlowViewMode.spatialDag, ctx.controller().state().flowViewMode());

        assertEquals(DagLayoutMode.horizontalLr, ctx.controller().state().dagLayoutMode());
        assertTrue(router.dispatch(GLFW.GLFW_KEY_R, none, false));
        assertEquals(DagLayoutMode.verticalTb, ctx.controller().state().dagLayoutMode());

        assertEquals(0, ctx.backend.undoCallCount());
        assertTrue(router.dispatch(GLFW.GLFW_KEY_Z, ctrl, true), "Ctrl+Z stays available inside a text field");
        assertEquals(1, ctx.backend.undoCallCount());

        assertTrue(router.dispatch(GLFW.GLFW_KEY_TAB, none, false));
        assertEquals(List.of(true), ctx.zenCalls);
    }

    @Test
    void escapeLayerStackPopsInTheDocumentedOrder() {
        boolean drag = true;
        boolean palette = true;
        boolean popover = true;
        boolean zenOrMultiSelect = true;

        assertEquals(
            ChromeShortcut.EscapeLayer.drag,
            ChromeShortcut.escapeLayer(drag, palette, popover, zenOrMultiSelect)
        );
        drag = false;
        assertEquals(
            ChromeShortcut.EscapeLayer.palette,
            ChromeShortcut.escapeLayer(drag, palette, popover, zenOrMultiSelect)
        );
        palette = false;
        assertEquals(
            ChromeShortcut.EscapeLayer.popover,
            ChromeShortcut.escapeLayer(drag, palette, popover, zenOrMultiSelect)
        );
        popover = false;
        assertEquals(
            ChromeShortcut.EscapeLayer.zenOrMultiselect,
            ChromeShortcut.escapeLayer(drag, palette, popover, zenOrMultiSelect)
        );
        zenOrMultiSelect = false;
        assertEquals(
            ChromeShortcut.EscapeLayer.screen,
            ChromeShortcut.escapeLayer(drag, palette, popover, zenOrMultiSelect)
        );

        assertNull(ChromeShortcut.resolve(GLFW.GLFW_KEY_ESCAPE, none, false));
        assertNull(ChromeShortcut.resolve(GLFW.GLFW_KEY_ESCAPE, none, true));
    }

    @Test
    void shortcutTableCoversEveryDocumentedScope() {
        assertEquals(24, ChromeShortcut.bindings().size());
        assertEquals(8, ChromeShortcut.bindingCount(ChromeShortcut.Scope.global));
        assertEquals(8, ChromeShortcut.bindingCount(ChromeShortcut.Scope.planner));
        assertEquals(4, ChromeShortcut.bindingCount(ChromeShortcut.Scope.dagCanvas));
        assertEquals(4, ChromeShortcut.bindingCount(ChromeShortcut.Scope.dagSelection));
        assertEquals(
            24,
            ChromeShortcut.bindingCount(ChromeShortcut.Scope.global)
                    + ChromeShortcut.bindingCount(ChromeShortcut.Scope.planner)
                    + ChromeShortcut.bindingCount(ChromeShortcut.Scope.dagCanvas)
                    + ChromeShortcut.bindingCount(ChromeShortcut.Scope.dagSelection)
        );
    }

    @Test
    void textInputRecognitionCoversTheFourFieldTypes() {
        assertTrue(ChromeShortcut.isTextInput(TextFieldWidget.create()));
        assertTrue(ChromeShortcut.isTextInput(NumberInputWidget.create()));
        assertTrue(ChromeShortcut.isTextInput(new CirrusTextInputWidget()));
        assertTrue(ChromeShortcut.isTextInput(new SearchBoxWidget()));
        assertFalse(ChromeShortcut.isTextInput(new Widget()));
        assertFalse(ChromeShortcut.isTextInput(null));
    }
}

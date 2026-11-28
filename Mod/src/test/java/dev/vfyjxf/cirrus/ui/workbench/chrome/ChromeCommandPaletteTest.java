package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.state.CommandPaletteEngine;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteAction;
import dev.vfyjxf.cirrus.ui.workbench.state.PaletteItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.coal;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.demand;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.ironIngot;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromeCommandPaletteTest {

    @AfterEach
    void resetSink() {
        ChromeSink.install(null);
    }

    @Test
    void openCloseAndEscapeRestoreThePreviousFocus() {
        ChromeTestContext ctx = new ChromeTestContext();
        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(ctx);
        NumberInputWidget previous = NumberInputWidget.create(3L);
        previous.useStyle(sizeOf(40f, 12f));

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(palette, previous).layout()) {
            mounted.scene.requestFocus(previous);
            assertSame(previous, mounted.scene.focusingWidget());

            palette.open();
            assertTrue(palette.isOpen());
            assertTrue(palette.visible());
            assertSame(palette, mounted.scene.focusingWidget());

            assertTrue(palette.handleKeyPressed(GLFW.GLFW_KEY_ESCAPE, false, false));
            assertFalse(palette.isOpen());
            assertFalse(palette.visible());
            assertSame(previous, mounted.scene.focusingWidget(), "Esc must restore the prior focus");

            palette.open();
            assertSame(palette, mounted.scene.focusingWidget());
            palette.close();
            assertSame(previous, mounted.scene.focusingWidget());
        }
    }

    @Test
    void arrowKeysWrapAndRowsStartWithACategoryHeader() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 5L), demand(coal, 2L)));
        ctx.publishGraph();
        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(ctx);

        try (ChromeTestContext.Mounted mounted = ChromeTestContext.mount(palette).layout()) {
            assertEquals(13, palette.itemCapacity());
            palette.open();
            palette.setQueryText(">");
            assertEquals(CommandPaletteOverlayWidget.queryLimit, palette.results().size());
            assertEquals(0, palette.selectedIndex());

            int last = palette.results().size() - 1;
            palette.moveSelection(-1);
            assertEquals(last, palette.selectedIndex());
            palette.moveSelection(1);
            assertEquals(0, palette.selectedIndex());
            palette.select(9999);
            assertEquals(last, palette.selectedIndex());
            palette.select(-5);
            assertEquals(0, palette.selectedIndex());

            List<CommandPaletteOverlayWidget.Row> rows = palette.rows();
            assertEquals(13, rows.size());
            assertNull(rows.get(0).item());
            assertEquals(CommandPaletteEngine.categoryCommand, rows.get(0).categoryKey());
            assertEquals(0, rows.get(1).itemIndex());
            assertEquals(
                0,
                palette.itemIndexAt(
                    10.0,
                    CommandPaletteOverlayWidget.headerHeight + CommandPaletteOverlayWidget.categoryRowHeight + 2.0
                )
            );
            assertEquals(-1, palette.itemIndexAt(-1.0, 0.0));
        }
    }

    @Test
    void enterSetsTheTargetWhileShiftEnterAppendsToTheBasket() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 5L), demand(coal, 2L)));
        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(ctx);
        palette.open();

        PaletteItem first = palette.results().get(0);
        assertEquals(CommandPaletteEngine.resourceItemPrefix + ironIngot.canonicalKey(), first.id());
        assertTrue(first.primaryAction() instanceof PaletteAction.SetTarget);
        assertTrue(first.shiftAction() instanceof PaletteAction.AddToBasket);
        assertSame(first, palette.highlightedItem());

        assertTrue(palette.activateSelected(false));
        assertFalse(palette.isOpen());
        assertEquals(List.of(demand(ironIngot, 5L)), lastTargets(ctx));

        palette.open();
        assertTrue(palette.activateSelected(true));
        assertFalse(palette.isOpen());
        assertEquals(
            List.of(demand(ironIngot, 6L)),
            lastTargets(ctx),
            "Shift+Enter must add one to the existing basket entry"
        );

        palette.open();
        palette.setQueryText("zzzz-no-command");
        assertTrue(palette.results().isEmpty());
        assertFalse(palette.activateSelected(false));
        assertFalse(palette.isOpen());
    }

    @Test
    void everyBuiltinCommandDispatchesToItsOwnEntryPoint() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        ctx.publishGraph();
        ctx.controller().refine(
            new ApplyRulesFeedback(
                List.of(
                    new MachineRule(
                        "rule-history",
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
        assertTrue(ctx.controller().snapshot().canUndo());

        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(ctx);
        ChromeTestContext.RecordingSink sink = new ChromeTestContext.RecordingSink();
        ChromeSink.install(sink);
        Set<String> executed = new HashSet<>();

        List<String> ids = CommandPaletteEngine.builtinCommandIds();
        assertEquals(22, ids.size());

        assertTrue(ctx.controller().snapshot().canUndo());
        run(palette, executed, "history.undo");
        assertEquals(1, ctx.backend.undoCallCount());
        assertTrue(ctx.controller().snapshot().canRedo());
        run(palette, executed, "history.redo");
        assertEquals(1, ctx.backend.redoCallCount());

        run(palette, executed, "view.dag");
        assertEquals(FlowViewMode.spatialDag, ctx.controller().state().flowViewMode());
        run(palette, executed, "view.ledger");
        assertEquals(FlowViewMode.preparationLedger, ctx.controller().state().flowViewMode());
        run(palette, executed, "view.checklist");
        assertEquals(FlowViewMode.executionChecklist, ctx.controller().state().flowViewMode());

        run(palette, executed, "layout.lr");
        assertEquals(DagLayoutMode.horizontalLr, ctx.controller().state().dagLayoutMode());
        run(palette, executed, "layout.tb");
        assertEquals(DagLayoutMode.verticalTb, ctx.controller().state().dagLayoutMode());
        run(palette, executed, "layout.swimlane");
        assertEquals(DagLayoutMode.systemSwimlane, ctx.controller().state().dagLayoutMode());

        run(palette, executed, "preset.balanced");
        assertEquals(ObjectivePreset.autoSmart, ctx.controller().state().profile().preset());
        assertEquals("preset_autosmart", ctx.controller().state().profile().profileId());
        run(palette, executed, "preset.min_human_time");
        assertEquals(ObjectivePreset.quickHandcraft, ctx.controller().state().profile().preset());
        run(palette, executed, "preset.no_new_machines");
        assertEquals(ObjectivePreset.stayAtBase, ctx.controller().state().profile().preset());
        run(palette, executed, "preset.invest_automation");
        assertEquals(ObjectivePreset.factoryScale, ctx.controller().state().profile().preset());
        run(palette, executed, "preset.min_raw_materials");
        assertEquals(ObjectivePreset.frugalMaterials, ctx.controller().state().profile().preset());
        assertEquals(
            ObjectivePreset.frugalMaterials,
            ctx.backend.requests.get(ctx.backend.requests.size() - 1).profile().preset()
        );

        run(palette, executed, "theme.toggle");
        assertEquals(List.of(WorkbenchThemeMode.atelierStone), ctx.themeCalls);
        run(palette, executed, "zen.toggle");
        assertEquals(List.of(true), ctx.zenCalls);

        PlanningBudget before = ctx.controller().state().budget();
        run(palette, executed, "action.deepen");
        assertEquals(ChromeProfiles.deepened(before), ctx.controller().state().budget());

        run(palette, executed, "action.export_json");
        assertEquals(1, sink.exportJsonCalls);
        assertTrue(ctx.toasts.contains(CirrusLangs.Workbench.Settings.exportStats.key()));

        run(palette, executed, "action.recompile");
        assertEquals(1, ctx.requestReplanCalls);

        assertTrue(ctx.mode() == WorkbenchMode.planner);
        run(palette, executed, "workspace.facilities");
        run(palette, executed, "workspace.database");
        run(palette, executed, "workspace.settings");
        assertEquals(WorkbenchMode.settings, ctx.mode());
        run(palette, executed, "workspace.planner");
        assertEquals(WorkbenchMode.planner, ctx.mode());
        assertEquals(
            List.of(WorkbenchMode.facilities, WorkbenchMode.database, WorkbenchMode.settings, WorkbenchMode.planner),
            ctx.modeCalls
        );

        assertEquals(new HashSet<>(ids), executed, "all 22 builtin command ids must dispatch");
        assertEquals(22, executed.size());

        assertFalse(palette.executeCommand(null));
        assertFalse(palette.executeCommand("  "));
        assertFalse(palette.executeCommand("bogus.command"));
        assertEquals("bogus.command", palette.lastDispatchedCommandId());
    }

    @Test
    void copilotAndModPrefixesRouteThroughTheirDedicatedHandlers() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 2L)));
        ctx.publishGraph();
        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(ctx);
        ChromeTestContext.RecordingSink sink = new ChromeTestContext.RecordingSink();
        ChromeSink.install(sink);

        ctx.setMode(WorkbenchMode.facilities);
        assertTrue(palette.executeCommand("copilot.focus:prompt:machine:minecraft:furnace"));
        assertEquals("prompt:machine:minecraft:furnace", palette.pendingPromptId());
        assertEquals(List.of("prompt:machine:minecraft:furnace"), sink.focusedPrompts);
        assertEquals(WorkbenchMode.planner, ctx.mode());
        assertTrue(ctx.toasts.contains(CirrusLangs.Workbench.Palette.categoryPrompt.key()));

        assertTrue(palette.executeCommand("mod.prefer:ae2"));
        assertTrue(palette.executeCommand("mod.penalize:ae2"));
        assertTrue(palette.executeCommand("mod.ban:ae2"));
        List<GuidanceRule> rules = ctx.controller().state().sessionRules();
        assertEquals(3, rules.size());
        assertRule(rules.get(0), "palette.mod.prefer:ae2", RuleStrength.softPrefer, "ae2");
        assertRule(rules.get(1), "palette.mod.penalize:ae2", RuleStrength.softPenalize, "ae2");
        assertRule(rules.get(2), "palette.mod.ban:ae2", RuleStrength.hardBan, "ae2");
        assertEquals(3, ctx.backend.refineCalls());
        assertFalse(palette.executeCommand("mod.ban:"));

        assertTrue(palette.dispatch(new PaletteAction.FocusStep("s004")));
        assertEquals("s004", palette.pendingFocusStepId());
        assertEquals(List.of("s004"), sink.focusedSteps);
        assertTrue(ctx.toasts.contains(CirrusLangs.Workbench.Palette.categoryCurrentNode.key()));

        assertFalse(palette.dispatch(null));
    }

    @Test
    void emptyInputShowsRecentTargetsPromptsAndFrequentCommands() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 5L), demand(coal, 2L)));
        ctx.publishGraph(
            request -> ChromeTestContext.result(
                PlanningStatus.feasible,
                List.of(ChromeTestContext.planFor("plan-x", request.targets().get(0))),
                List.of(
                    ChromeTestContext.prompt("prompt:a", DecisionPromptKind.machineAvailability, 0.8),
                    ChromeTestContext.prompt("prompt:b", DecisionPromptKind.baseOrSupplyBoundary, 0.4)
                )
            )
        );

        CommandPaletteOverlayWidget palette = new CommandPaletteOverlayWidget(ctx);
        palette.open();
        List<PaletteItem> items = palette.results();
        assertEquals(8, items.size(), "2 recent targets + 2 prompts + 4 frequent commands");

        assertEquals(
            List.of(
                CommandPaletteEngine.categoryRecent,
                CommandPaletteEngine.categoryPrompt,
                CommandPaletteEngine.categoryCommand
            ),
            items.stream().map(PaletteItem::categoryKey).distinct().toList()
        );
        assertEquals(CommandPaletteEngine.resourceItemPrefix + ironIngot.canonicalKey(), items.get(0).id());
        assertEquals(CommandPaletteEngine.resourceItemPrefix + coal.canonicalKey(), items.get(1).id());
        assertEquals("cmd:" + CommandPaletteEngine.promptFocusPrefix + "prompt:a", items.get(2).id());
        assertEquals("cmd:" + CommandPaletteEngine.promptFocusPrefix + "prompt:b", items.get(3).id());
        assertEquals(
            List.of("cmd:view.dag", "cmd:action.deepen", "cmd:theme.toggle", "cmd:zen.toggle"),
            items.subList(4, 8).stream().map(PaletteItem::id).toList()
        );
        assertNotNull(palette.highlightedItem());
    }

    private static void run(CommandPaletteOverlayWidget palette, Set<String> executed, String id) {
        assertTrue(palette.executeCommand(id), "command did not dispatch: " + id);
        assertEquals(id, palette.lastDispatchedCommandId());
        executed.add(id);
    }

    private static void assertRule(GuidanceRule rule, String ruleId, RuleStrength strength, String namespace) {
        assertTrue(rule instanceof ModNamespaceRule, rule.getClass().getSimpleName());
        ModNamespaceRule modRule = (ModNamespaceRule) rule;
        assertEquals(ruleId, modRule.ruleId());
        assertEquals(strength, modRule.strength());
        assertEquals(GuidanceScope.session, modRule.scope());
        assertEquals(namespace, modRule.modNamespace());
    }

    private static List<TargetDemand> lastTargets(ChromeTestContext ctx) {
        return ctx.targetCalls.get(ctx.targetCalls.size() - 1).targets();
    }
}

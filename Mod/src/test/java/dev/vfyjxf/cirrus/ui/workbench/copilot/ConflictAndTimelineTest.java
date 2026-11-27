package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.human.ConflictDiagnosis;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.ui.workbench.state.HistoryEntry;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.acquire;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.cost;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.historyEntry;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.plan;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotTestFixtures.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UI-TC-29: human rule conflict diagnosis, relaxation recovery and decision timeline jumps. */
class ConflictAndTimelineTest {

    private static final String banRuleId = "r-ban-charger";

    private static ProductionPlan feasiblePlan() {
        return plan(
            "plan-ok",
            "cirrus.strategy.balanced",
            List.of(acquire("s-acq", ironIngot, 8L)),
            cost(10.0, 2.0, 0.0, 1.0, 0.0)
        );
    }

    private static PlanningResult feasibleResult() {
        return result(PlanningStatus.feasible, List.of(feasiblePlan()), List.of(), Optional.empty());
    }

    private static PlanningResult conflictResult() {
        return result(
            PlanningStatus.infeasibleByHumanRules,
            List.of(),
            List.of(),
            Optional.of(new ConflictDiagnosis(ironIngot, List.of(banRuleId), "cirrus.conflict.relax_rule"))
        );
    }

    private static GuidanceRule banRule() {
        return new SystemRule(banRuleId, GuidanceScope.session, RuleStrength.hardBan, "ae2:charger", false);
    }

    private static WorkbenchUiSnapshot conflictSnapshot(List<HistoryEntry> undoHistory) {
        return snapshot(List.of(), conflictResult(), feasibleResult(), undoHistory, List.of(banRule()));
    }

    @Test
    void infeasibleByRulesKeepsLastFeasibleGhostAndRelaxRestores() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(conflictSnapshot(List.of()));
        ConflictDiagnosisOverlayWidget overlay = new ConflictDiagnosisOverlayWidget(host);

        assertTrue(overlay.visible());
        assertNotNull(host.snapshot().lastFeasibleResult());
        assertEquals(PlanningStatus.feasible, host.snapshot().lastFeasibleResult().status());
        assertEquals(ironIngot, overlay.blockedResource());
        assertEquals(List.of(banRuleId), overlay.minimalConflictRuleIds());
        assertEquals(1, overlay.conflictingRules().size());
        assertEquals(RuleStrength.hardBan, overlay.conflictingRules().get(0).strength());
        assertEquals(GuidanceScope.session, overlay.conflictingRules().get(0).scope());

        assertTrue(overlay.relaxOne(0));
        assertEquals(List.of(List.of(banRuleId)), host.relaxedRules);

        host.snapshot = snapshot(List.of(), feasibleResult(), feasibleResult(), List.of(), List.of());
        assertFalse(overlay.visible());
        assertNull(overlay.diagnosis());
        assertEquals(1, host.relaxedRules.size());
    }

    @Test
    void relaxAllAndUndoTimelineNodeDispatchExactlyOnce() {
        List<HistoryEntry> history = List.of(historyEntry("h0", 0L), historyEntry("h1", 1L));
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(conflictSnapshot(history));
        ConflictDiagnosisOverlayWidget overlay = new ConflictDiagnosisOverlayWidget(host);

        assertTrue(overlay.relaxAll());
        assertEquals(List.of(List.of(banRuleId)), host.relaxedRules);
        assertEquals(0, host.historyJumps.size());

        assertTrue(overlay.undoLastStep());
        assertEquals(List.of(1), host.historyJumps);
        assertEquals(1, host.relaxedRules.size());

        CoPilotTestFixtures.FakeHost firstNodeHost = new CoPilotTestFixtures.FakeHost(
            conflictSnapshot(List.of(historyEntry("h0", 0L)))
        );
        ConflictDiagnosisOverlayWidget firstNode = new ConflictDiagnosisOverlayWidget(firstNodeHost);
        assertTrue(firstNode.undoLastStep());
        assertEquals(List.of(0), firstNodeHost.historyJumps);
    }

    @Test
    void tooManyRulesOffersSessionReset() {
        List<GuidanceRule> rules = new ArrayList<>();
        for (int index = 0; index < ConflictDiagnosisOverlayWidget.tooManyRulesThreshold + 1; index++) {
            rules.add(
                new SystemRule("r" + index, GuidanceScope.session, RuleStrength.softPenalize, "system:" + index, false)
            );
        }
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(
            snapshot(List.of(), conflictResult(), feasibleResult(), List.of(), rules)
        );
        ConflictDiagnosisOverlayWidget overlay = new ConflictDiagnosisOverlayWidget(host);

        assertTrue(overlay.visible());
        assertTrue(overlay.tooManyRules());
        assertTrue(overlay.clearSessionRules());
        assertEquals(1, host.relaxedRules.size());
        assertEquals(rules.size(), host.relaxedRules.get(0).size());

        assertFalse(overlay.openFacilitiesWorkspace());
        assertEquals(1, host.toasts.size());
        final boolean[] opened = {false};
        overlay.openFacilities(() -> opened[0] = true);
        assertTrue(overlay.openFacilitiesWorkspace());
        assertTrue(opened[0]);
    }

    @Test
    void promptQueueSortsByLeverageAndConflictCardStaysHiddenWhenFeasible() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(snapshot(feasibleResult()));
        ConflictDiagnosisOverlayWidget overlay = new ConflictDiagnosisOverlayWidget(host);
        assertFalse(overlay.visible());
        assertFalse(overlay.relaxAll());
        assertFalse(overlay.undoLastStep());
        assertEquals(0, host.dispatchCount());

        RightCoPilotDockWidget dock = new RightCoPilotDockWidget(host, subject -> {});
        assertTrue(dock.isEmptyQueue());
        assertEquals(0, dock.promptCount());
        assertEquals(RightCoPilotDockWidget.Tab.prompts, dock.tab());
        dock.setTab(RightCoPilotDockWidget.Tab.quickDrop);
        assertEquals(RightCoPilotDockWidget.Tab.quickDrop, dock.tab());
    }

    @Test
    void contextMenuBuildsEveryScopeRowAndMenuTree() {
        CoPilotTestFixtures.FakeHost host = new CoPilotTestFixtures.FakeHost(snapshot(feasibleResult()));
        for (NodeContextMenuWidget.MenuKind kind : NodeContextMenuWidget.MenuKind.values()) {
            NodeContextMenuWidget menu = new NodeContextMenuWidget(
                host,
                kind,
                "s-acq",
                null,
                ironIngot,
                null,
                "ae2:charger"
            );
            assertFalse(menu.items().isEmpty(), kind.name());
            assertTrue(menu.preferredHeight() > NodeContextMenuWidget.scopeRowHeight, kind.name());
            assertNotNull(menu.scopeRect(true));
            assertFalse(menu.scopeRect(true).contains(menu.scopeRect(false).x(), menu.scopeRect(false).y()));
        }

        NodeContextMenuWidget menu = new NodeContextMenuWidget(
            host,
            NodeContextMenuWidget.MenuKind.acquireStep,
            "s-acq",
            null,
            ironIngot,
            null,
            null
        );
        assertFalse(menu.persistToWorld());
        menu.setPersistToWorld(true);
        GuidanceRule freeRule = menu.ruleFor(NodeContextMenuWidget.Action.treatFree);
        assertNotNull(freeRule);
        assertEquals(GuidanceScope.worldSave, freeRule.scope());
        GuidanceRule banRule = menu.ruleFor(NodeContextMenuWidget.Action.banResource);
        assertNotNull(banRule);
        assertEquals(RuleStrength.hardBan, banRule.strength());

        assertTrue(menu.trigger(NodeContextMenuWidget.Action.banResource));
        assertEquals(1, host.feedbacks.size());
        assertTrue(menu.trigger(NodeContextMenuWidget.Action.selectPlan));
    }
}

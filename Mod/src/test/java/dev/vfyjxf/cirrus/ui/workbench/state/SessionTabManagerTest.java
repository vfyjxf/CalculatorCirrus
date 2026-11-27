package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.coal;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.demand;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.ironIngot;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.requestSource;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.result;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.singleStepPlan;
import static dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchStateFixtures.sync;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTabManagerTest {

    private WorkbenchStateFixtures.FakeBackend backend;
    private SessionTabManager manager;

    @BeforeEach
    void setUp() {
        backend = new WorkbenchStateFixtures.FakeBackend(
            request -> result(
                PlanningStatus.feasible,
                List.of(singleStepPlan("plan-" + request.targets().get(0).amount()))
            )
        );
        manager = new SessionTabManager(
            state -> new PlannerWorkbenchController(state, backend, requestSource(), sync())
        );
    }

    @Test
    void singleTargetTabPlansOnceGraphIsPublished() {
        PlannerWorkbenchController tab = manager.openOrFocusTab(List.of(demand(ironIngot, 1L)), null);

        assertEquals(1, manager.tabs().size());
        assertEquals(tab.tabId(), manager.activeTabId());
        assertSame(tab, manager.activeTab());
        assertEquals(0, backend.openCount(), "no planning before the recipe graph is published");

        manager.onRuntimeGraphVersionChanged(1L);

        assertEquals(1, backend.openCount());
        assertEquals(WorkbenchPhase.readyPareto, tab.snapshot().phase());
        assertEquals("plan-1", tab.snapshot().selectedPlanId());
    }

    @Test
    void multiTargetBasketLivesInOneTabAndIsNormalized() {
        PlannerWorkbenchController tab = manager.openOrFocusTab(List.of(demand(ironIngot, 4L), demand(coal, 2L)), null);
        manager.onRuntimeGraphVersionChanged(1L);

        assertEquals(2, tab.state().targets().size());
        assertEquals(
            List.of(coal, ironIngot),
            backend.requests().get(0).targets().stream().map(TargetDemand::resource).toList()
        );
        assertEquals(1, manager.tabs().size());

        PlannerWorkbenchController again = manager
                .openOrFocusTab(List.of(demand(ironIngot, 4L), demand(coal, 2L)), null);

        assertSame(tab, again, "an equal basket must focus the existing tab");
        assertEquals(1, manager.tabs().size());
        assertEquals(1, backend.openCount());
    }

    @Test
    void forkedTabKeepsRuleSetsIsolated() {
        PlannerWorkbenchController source = manager.openOrFocusTab(List.of(demand(ironIngot, 1L)), null);
        manager.onRuntimeGraphVersionChanged(1L);
        source.setSessionRules(List.of(machineRule("rule-a")));

        PlannerWorkbenchController fork = manager.forkActiveTab();

        assertEquals(2, manager.tabs().size());
        assertEquals(fork.tabId(), manager.activeTabId());
        assertNotEquals(source.tabId(), fork.tabId());
        assertNotSame(source.state(), fork.state());
        assertEquals(List.of("rule-a"), ruleIds(source));
        assertEquals(List.of("rule-a"), ruleIds(fork));

        source.refine(new ApplyRulesFeedback(List.of(machineRule("rule-b")), false), null);
        assertEquals(List.of("rule-a", "rule-b"), ruleIds(source));
        assertEquals(List.of("rule-a"), ruleIds(fork), "source refine must not pollute the fork");

        fork.refine(new ApplyRulesFeedback(List.of(machineRule("rule-c")), false), null);
        assertEquals(List.of("rule-a", "rule-c"), ruleIds(fork));
        assertEquals(List.of("rule-a", "rule-b"), ruleIds(source), "fork refine must not pollute the source");

        manager.closeTab(fork.tabId());
        assertEquals(1, manager.tabs().size());
        assertEquals(WorkbenchPhase.closed, fork.snapshot().phase());
        assertEquals(source.tabId(), manager.activeTabId());
    }

    @Test
    void ninthTabIsRejectedWithIllegalState() {
        List<PlannerWorkbenchController> tabs = new ArrayList<>();
        for (int i = 1; i <= SessionTabManager.maxTabs; i++) {
            tabs.add(manager.openOrFocusTab(List.of(demand(ironIngot, i)), null));
        }
        assertEquals(SessionTabManager.maxTabs, manager.tabs().size());

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> manager.openOrFocusTab(List.of(demand(ironIngot, 99L)), null)
        );

        assertEquals(SessionTabManager.maxTabs, manager.tabs().size());
        assertThrows(IllegalStateException.class, manager::forkActiveTab);
        assertEquals(SessionTabManager.maxTabs, manager.tabs().size());
        assertEquals(SessionTabManager.maxTabs, tabs.size());
        assertTrue(tabs.stream().allMatch(tab -> tab.state().targets().size() == 1));
    }

    @Test
    void closeAllReleasesEveryTabSession() {
        List<PlannerWorkbenchController> tabs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            tabs.add(manager.openOrFocusTab(List.of(demand(ironIngot, i)), null));
        }
        manager.onRuntimeGraphVersionChanged(1L);
        manager.onRuntimeGraphVersionChanged(2L);
        for (PlannerWorkbenchController tab : tabs) {
            manager.activate(tab.tabId());
        }
        for (PlannerWorkbenchController tab : tabs) {
            assertTrue(tab.openSessionCount() >= 1, "tab " + tab.tabId() + " never planned");
        }

        manager.closeAll();

        assertTrue(manager.tabs().isEmpty());
        assertNull(manager.activeTab());
        assertNull(manager.activeTabId());
        for (PlannerWorkbenchController tab : tabs) {
            assertEquals(WorkbenchPhase.closed, tab.snapshot().phase());
            assertEquals(
                tab.openSessionCount(),
                tab.closedSessionCount(),
                "tab " + tab.tabId() + " leaked a planning session"
            );
        }

        manager.closeAll();
        manager.close();
    }

    @Test
    void closingActiveTabFallsBackToLastRemainingTab() {
        PlannerWorkbenchController first = manager.openOrFocusTab(List.of(demand(ironIngot, 1L)), null);
        PlannerWorkbenchController second = manager.openOrFocusTab(List.of(demand(ironIngot, 2L)), null);
        PlannerWorkbenchController third = manager.openOrFocusTab(List.of(demand(ironIngot, 3L)), null);
        manager.activate(first.tabId());

        manager.closeTab(second.tabId());
        assertEquals(first.tabId(), manager.activeTabId(), "closing an inactive tab keeps the focus");

        manager.closeTab(first.tabId());
        assertEquals(third.tabId(), manager.activeTabId(), "closing the active tab falls back to the last tab");
        assertEquals(WorkbenchPhase.closed, first.snapshot().phase());

        manager.closeTab("tab-missing");
        assertEquals(1, manager.tabs().size());
    }

    private static MachineRule machineRule(String ruleId) {
        return new MachineRule(ruleId, GuidanceScope.session, RuleStrength.softPrefer, "minecraft:furnace", true);
    }

    private static List<String> ruleIds(PlannerWorkbenchController controller) {
        List<GuidanceRule> rules = controller.snapshot().activeSessionRules();
        return rules.stream().map(GuidanceRule::ruleId).toList();
    }
}

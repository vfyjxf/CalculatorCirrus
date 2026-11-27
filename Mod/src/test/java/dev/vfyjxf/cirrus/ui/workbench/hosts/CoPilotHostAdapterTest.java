package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotDnd;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Co-Pilot host adapter: each of the 13 methods maps to the planning controller and the shell.
 */
class CoPilotHostAdapterTest {

    @Test
    void snapshotAndPaletteComeFromTheShellContext() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellCoPilotHost host = new ShellCoPilotHost(context);

        assertEquals(context.activeSnapshot(), host.snapshot());
        assertSame(context.palette(), host.palette());
        assertFalse(host.isReducedMotion());
        assertTrue(new ShellCoPilotHost(context, () -> true).isReducedMotion());
    }

    @Test
    void refineForwardsTheFeedbackInstanceAndHistoryEntry() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        ShellCoPilotHost host = new ShellCoPilotHost(fixture.context());
        ApplyRulesFeedback feedback = new ApplyRulesFeedback(List.of(), false);

        host.refine(feedback, null);

        assertSame(feedback, fixture.backend().lastSession().refined().get(0));
        assertSame(feedback, host.lastRefined());
    }

    @Test
    void jumpToHistoryForwardsTheIndex() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        PlannerWorkbenchController controller = fixture.controller();
        ShellCoPilotHost host = new ShellCoPilotHost(fixture.context());
        host.refine(new RejectStepFeedback(HostsTestFixtures.planId, "s001", false, false), null);

        host.jumpToHistory(0);
        assertEquals(1, fixture.backend().lastSession().undoCount());
        assertEquals(0, fixture.backend().lastSession().redoCount());

        host.jumpToHistory(1);
        assertEquals(1, fixture.backend().lastSession().redoCount());
        assertEquals(1, controller.snapshot().undoHistory().size());
    }

    @Test
    void relaxConflictRulesForwardsTheRuleIdListVerbatim() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        PlannerWorkbenchController controller = fixture.controller();
        var relax = CoPilotDnd.banRule(HostsTestFixtures.ironOre, false);
        var keep = CoPilotDnd.banRule(HostsTestFixtures.ironIngot, false);
        controller.setSessionRules(List.of(relax, keep));
        assertEquals(2, controller.snapshot().activeSessionRules().size());
        ShellCoPilotHost host = new ShellCoPilotHost(fixture.context());

        host.relaxConflictRules(List.of(relax.ruleId()));

        List<String> remaining = controller.snapshot().activeSessionRules().stream().map(rule -> rule.ruleId())
                .toList();
        assertEquals(List.of(keep.ruleId()), remaining);
    }

    @Test
    void promoteSessionRulesToWorldReportsTheMissingWorldGate() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        ShellCoPilotHost host = new ShellCoPilotHost(fixture.context());

        host.promoteSessionRulesToWorld(null);

        assertEquals(
            PlannerWorkbenchController.errorWorldPersistUnavailable,
            fixture.controller().snapshot().lastErrorMessage()
        );
    }

    @Test
    void planSelectionAndComparisonReachTheActiveController() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        PlannerWorkbenchController controller = fixture.controller();
        ShellCoPilotHost host = new ShellCoPilotHost(fixture.context());

        host.selectPlan("plan-beta");
        assertEquals("plan-beta", controller.snapshot().selectedPlanId());

        host.setComparePlan(HostsTestFixtures.planId);
        assertEquals(HostsTestFixtures.planId, controller.snapshot().comparePlanId());
    }

    @Test
    void selectedStepsReachTheActiveController() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        ShellCoPilotHost host = new ShellCoPilotHost(fixture.context());

        host.setSelectedSteps(Set.of("s002", "s003"));

        assertEquals(Set.of("s002", "s003"), fixture.controller().snapshot().selectedStepIds());
    }

    @Test
    void focusStepInDagIsRecordedAndToasted() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellCoPilotHost host = new ShellCoPilotHost(context);

        host.focusStepInDag("s004");

        assertEquals(List.of("s004"), host.focusRequests());
        assertEquals(List.of(CirrusLangs.Workbench.Action.locateDag), context.langToasts());
    }

    @Test
    void openAlternativesForRecordsThePendingResource() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellCoPilotHost host = new ShellCoPilotHost(context);

        host.openAlternativesFor(HostsTestFixtures.ironOre);

        assertEquals(HostsTestFixtures.ironOre, host.pendingAlternatives());
        assertEquals(HostsTestFixtures.ironOre, host.consumePendingAlternatives());
        assertNull(host.pendingAlternatives());
    }

    @Test
    void toastGoesStraightToTheShell() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellCoPilotHost host = new ShellCoPilotHost(context);

        host.toast(CirrusLangs.Workbench.Copilot.tabPrompts);

        assertEquals(List.of(CirrusLangs.Workbench.Copilot.tabPrompts), context.langToasts());
    }

    @Test
    void hostsWithoutAnActiveControllerStaySilent() {
        HostsTestFixtures.FakeShellContext context = new HostsTestFixtures.FakeShellContext(null);
        ShellCoPilotHost host = new ShellCoPilotHost(context);
        HumanFeedback feedback = new ApplyRulesFeedback(List.of(), false);

        assertDoesNotThrow(() -> {
            host.refine(feedback, null);
            host.jumpToHistory(0);
            host.relaxConflictRules(List.of("rule"));
            host.promoteSessionRulesToWorld("rule");
            host.selectPlan("plan");
            host.setComparePlan("plan");
            host.setSelectedSteps(Set.of("s001"));
        });
        assertSame(feedback, host.lastRefined());
        assertNull(host.pendingAlternatives());
    }
}

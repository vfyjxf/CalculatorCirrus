package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Host adapter of the three views: dispatch keeps the same feedback instance, focus requests and toasts go through the shell.
 */
class FlowViewHostAdapterTest {

    @Test
    void dispatchRefinesWithTheSameFeedbackInstance() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        ShellFlowViewHost host = new ShellFlowViewHost(fixture.context());
        RejectStepFeedback feedback = new RejectStepFeedback(HostsTestFixtures.planId, "s001", false, false);

        host.dispatch(feedback);

        assertSame(feedback, fixture.backend().lastSession().refined().get(0));
        assertSame(feedback, host.lastDispatched());
    }

    @Test
    void focusStepIsRecordedAndToasted() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        HostsTestFixtures.FakeShellContext context = fixture.context();
        ShellFlowViewHost host = new ShellFlowViewHost(context);

        host.onFocusStep("s001");
        host.onFocusStep("s002");

        assertEquals(List.of("s001", "s002"), host.focusRequests());
        assertEquals("s002", host.lastFocusedStepId());
        assertEquals(
            List.of(CirrusLangs.Workbench.Action.locateDag, CirrusLangs.Workbench.Action.locateDag),
            context.langToasts()
        );

        host.clearFocusRequests();

        assertTrue(host.focusRequests().isEmpty());
        assertNull(host.lastFocusedStepId());
    }

    @Test
    void toastGoesStraightToTheShell() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellFlowViewHost host = new ShellFlowViewHost(context);

        host.toast(CirrusLangs.Workbench.Topbar.sentToast);

        assertEquals(List.of(CirrusLangs.Workbench.Topbar.sentToast), context.langToasts());
    }

    @Test
    void readsAndReducedMotionComeFromTheShell() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        HostsTestFixtures.FakeShellContext context = fixture.context();

        ShellFlowViewHost plain = new ShellFlowViewHost(context);
        ShellFlowViewHost reduced = new ShellFlowViewHost(context, () -> true);

        assertEquals(context.activeSnapshot(), plain.snapshot());
        assertSame(CirrusPalette.obsidianBlueprint, plain.palette());
        assertFalse(plain.isReducedMotion());
        assertTrue(reduced.isReducedMotion());
    }
}

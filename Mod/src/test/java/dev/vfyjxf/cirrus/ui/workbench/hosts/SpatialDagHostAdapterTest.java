package dev.vfyjxf.cirrus.ui.workbench.hosts;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spatial DAG host adapter: each of the 13 methods forwards to the right shell or controller entry point.
 */
class SpatialDagHostAdapterTest {

    @Test
    void snapshotAndPaletteComeFromTheShellContext() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        HostsTestFixtures.FakeShellContext context = fixture.context();
        ShellSpatialDagHost host = new ShellSpatialDagHost(context);

        assertEquals(context.activeSnapshot(), host.snapshot());
        assertSame(CirrusPalette.obsidianBlueprint, host.palette());
        assertSame(context.palette(), host.palette());
        assertFalse(host.isReducedMotion());
    }

    @Test
    void reducedMotionFollowsTheInjectedSupplier() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();

        assertTrue(new ShellSpatialDagHost(context, () -> true).isReducedMotion());
        assertFalse(new ShellSpatialDagHost(context, null).isReducedMotion());
    }

    @Test
    void selectStepsReachesTheActiveController() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        ShellSpatialDagHost host = new ShellSpatialDagHost(fixture.context());

        host.onSelectSteps(Set.of("s001", "s002"));

        assertEquals(Set.of("s001", "s002"), fixture.controller().snapshot().selectedStepIds());
    }

    @Test
    void nodeMoveAndClearReachTheActiveController() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        PlannerWorkbenchController controller = fixture.controller();
        ShellSpatialDagHost host = new ShellSpatialDagHost(fixture.context());
        FloatPos offset = HostsTestFixtures.offset(12.5, -8.25);

        host.onNodeMoved("s001", offset);

        assertEquals(offset, controller.snapshot().manualNodeOffsets().get("s001"));

        host.onClearManualOffsets();

        assertTrue(controller.snapshot().manualNodeOffsets().isEmpty());
    }

    @Test
    void hoversReachTheActiveController() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        PlannerWorkbenchController controller = fixture.controller();
        ShellSpatialDagHost host = new ShellSpatialDagHost(fixture.context());

        host.onHoverStep("s002");

        assertEquals("s002", controller.snapshot().hoveredStepId());

        host.onHoverEdge("s001", "s002", HostsTestFixtures.ironOre);

        assertEquals(HostsTestFixtures.ironOre, controller.snapshot().hoveredResource());
        assertEquals(
            new ShellSpatialDagHost.HoveredEdge("s001", "s002", HostsTestFixtures.ironOre),
            host.hoveredEdge()
        );

        host.onHoverEdge(null, null, null);

        assertNull(controller.snapshot().hoveredResource());
        assertNull(host.hoveredEdge());
    }

    @Test
    void inlineBeaconClickIsRecordedAndToasted() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellSpatialDagHost host = new ShellSpatialDagHost(context);

        host.onInlineBeaconClick("prompt:tag_choice:minecraft:logs", "s003");

        assertEquals(
            new ShellSpatialDagHost.InlineBeaconClick("prompt:tag_choice:minecraft:logs", "s003"),
            host.pendingBeacon()
        );
        assertEquals(List.of(CirrusLangs.Workbench.Copilot.tabPrompts.get()), context.componentToasts());

        assertEquals(
            new ShellSpatialDagHost.InlineBeaconClick("prompt:tag_choice:minecraft:logs", "s003"),
            host.consumePendingBeacon()
        );
        assertNull(host.pendingBeacon());
    }

    @Test
    void contextMenusRecordTheirScreenCoordinates() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        ShellSpatialDagHost host = new ShellSpatialDagHost(context);

        host.onNodeContextMenu("s004", 300.5, 220.25, true);

        assertEquals(
            new ShellSpatialDagHost.ContextMenuRequest("s004", 300.5, 220.25, true, false),
            host.pendingContextMenu()
        );

        host.onCanvasContextMenu(11.75, 42.5);

        assertEquals(
            new ShellSpatialDagHost.ContextMenuRequest(null, 11.75, 42.5, false, true),
            host.pendingContextMenu()
        );

        assertEquals(
            new ShellSpatialDagHost.ContextMenuRequest(null, 11.75, 42.5, false, true),
            host.consumePendingContextMenu()
        );
        assertNull(host.pendingContextMenu());
    }

    @Test
    void dropForwardsThePayloadAndTargetVerbatim() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        List<DragPayload> payloads = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        ShellSpatialDagHost host = new ShellSpatialDagHost(context, () -> false, (payload, targetStepId) -> {
            payloads.add(payload);
            targets.add(targetStepId);
        }, null);
        DragPayload payload = new DragPayload.ResourceDrag(HostsTestFixtures.ironOre, 16L, "unit-test");

        host.onDrop(payload, "s005");

        assertSame(payload, payloads.get(0));
        assertEquals("s005", targets.get(0));
        assertSame(payload, host.lastDropPayload());
        assertEquals("s005", host.lastDropTargetStepId());

        host.onDrop(payload, null);

        assertSame(payload, payloads.get(1));
        assertNull(targets.get(1));
    }

    @Test
    void dropSinkCanBeReboundAfterConstruction() {
        HostsTestFixtures.FakeShellContext context = HostsTestFixtures.fixture().context();
        List<String> targets = new ArrayList<>();
        ShellSpatialDagHost host = new ShellSpatialDagHost(context);
        DragPayload payload = new DragPayload.MachineDrag("minecraft:furnace", 2);

        host.dropSink((dropped, targetStepId) -> targets.add(targetStepId));
        host.onDrop(payload, "s006");

        assertEquals(List.of("s006"), targets);
    }

    @Test
    void layoutModeReachesTheActiveController() {
        HostsTestFixtures.Fixture fixture = HostsTestFixtures.fixture();
        ShellSpatialDagHost host = new ShellSpatialDagHost(fixture.context());

        host.onSetLayoutMode(DagLayoutMode.systemSwimlane);

        assertEquals(DagLayoutMode.systemSwimlane, fixture.controller().snapshot().dagLayoutMode());
    }

    @Test
    void hostsWithoutAnActiveControllerStaySilent() {
        HostsTestFixtures.FakeShellContext context = new HostsTestFixtures.FakeShellContext(null);
        ShellSpatialDagHost host = new ShellSpatialDagHost(context);
        ResourceKey resource = HostsTestFixtures.ironOre;

        assertDoesNotThrow(() -> {
            host.onSelectSteps(Set.of("s001"));
            host.onNodeMoved("s001", HostsTestFixtures.offset(1.0, 2.0));
            host.onClearManualOffsets();
            host.onHoverStep("s001");
            host.onHoverEdge("s001", "s002", resource);
            host.onSetLayoutMode(DagLayoutMode.verticalTb);
        });
        assertEquals(WorkbenchUiSnapshot.empty(), host.snapshot());
    }
}

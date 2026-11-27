package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import dev.vfyjxf.cirrus.ui.workbench.state.FlowViewMode;
import dev.vfyjxf.cirrus.ui.workbench.state.PlannerWorkbenchController;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchPhase;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkbenchDatabaseHostingTest {

    private static final int width = 960;
    private static final int height = 540;

    @Test
    void databaseWorkspaceFillsTheAreaBelowTheTabBarWithoutClosingSessions() {
        HostingFixture fixture = HostingFixture.mount(width, height);
        try {
            PlannerWorkbenchController controller = fixture.openTab();
            controller.setSelectedSteps(Set.of("step-a", "step-b"));
            controller.setFlowViewMode(FlowViewMode.preparationLedger);
            controller.setDagLayoutMode(DagLayoutMode.verticalTb);
            int tabsBefore = fixture.shell.tabManager().tabs().size();
            String activeBefore = fixture.shell.tabManager().activeTabId();
            WorkbenchPhase phaseBefore = fixture.shell.activeSnapshot().phase();
            Set<String> selectedBefore = controller.state().selectedStepIds();
            Widget plannerPane = fixture.plannerPane();

            fixture.shell.setMode(WorkbenchMode.database);
            fixture.relayout();

            Widget databasePane = fixture.databasePane();
            assertNotNull(databasePane, "DATABASE mode must host a database workspace");
            Rect expectedStage = new Rect(
                (int) WorkbenchBreakpoint.wide.leftRailWidth(),
                (int) WorkbenchBreakpoint.wide.sessionTabBarHeight(),
                width - (int) WorkbenchBreakpoint.wide.leftRailWidth(),
                height - (int) WorkbenchBreakpoint.wide.sessionTabBarHeight()
            );
            assertEquals(expectedStage, databasePane.absoluteBounds());
            assertTrue(databasePane.visible());
            assertTrue(plannerPane.lifecycle().unmounted(), "planner workspace stays docked, not destroyed");

            assertEquals(tabsBefore, fixture.shell.tabManager().tabs().size());
            assertEquals(activeBefore, fixture.shell.tabManager().activeTabId());
            assertEquals(selectedBefore, controller.state().selectedStepIds());
            assertEquals(FlowViewMode.preparationLedger, controller.state().flowViewMode());
            assertEquals(DagLayoutMode.verticalTb, controller.state().dagLayoutMode());

            fixture.shell.setMode(WorkbenchMode.planner);
            fixture.relayout();

            WorkbenchShellLayout plannerLayout = WorkbenchShellLayout.compute(width, height);
            assertEquals(
                new Rect(
                    (int) plannerLayout.stage().x(),
                    (int) plannerLayout.stage().y(),
                    (int) plannerLayout.stage().width(),
                    (int) plannerLayout.stage().height()
                ),
                plannerPane.absoluteBounds()
            );
            assertTrue(plannerPane.visible());
            assertEquals(selectedBefore, controller.state().selectedStepIds());
            assertEquals(Set.of("step-a", "step-b"), fixture.shell.activeSnapshot().selectedStepIds());
            assertEquals(FlowViewMode.preparationLedger, fixture.shell.activeSnapshot().flowViewMode());
            assertEquals(phaseBefore, fixture.shell.activeSnapshot().phase());
            assertEquals(tabsBefore, fixture.shell.tabManager().tabs().size());
            assertEquals(activeBefore, fixture.shell.tabManager().activeTabId());
        } finally {
            fixture.close();
        }
    }

    @Test
    void defaultWorkspaceProviderSuppliesTheRealDatabasePage() {
        DatabasePage databasePage = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));
        DefaultWorkspaceProvider provider = new DefaultWorkspaceProvider(() -> databasePage);

        assertSame(databasePage, provider.databaseWorkspace(null));
    }

    private static final class HostingFixture {
        private final WorkbenchShellWidget shell;
        private final Scene scene;
        private final SessionTabManager tabManager;
        private final WorkspaceProvider provider;

        private HostingFixture(
            WorkbenchShellWidget shell,
            Scene scene,
            SessionTabManager tabManager,
            WorkspaceProvider provider
        ) {
            this.shell = shell;
            this.scene = scene;
            this.tabManager = tabManager;
            this.provider = provider;
        }

        private static HostingFixture mount(int width, int height) {
            return mount(width, height, new PaneProvider());
        }

        private static HostingFixture mount(int width, int height, WorkspaceProvider provider) {
            SessionTabManager tabManager = new SessionTabManager(
                state -> new PlannerWorkbenchController(state, request -> {
                    throw new IllegalStateException("headless test has no planning backend");
                }, spec -> {
                    throw new IllegalStateException("headless test has no planning request source");
                }, Runnable::run)
            );
            WorkbenchShellWidget shell = new WorkbenchShellWidget(provider, tabManager, WorkbenchMode.planner);
            WidgetGroup<Widget> root = new WidgetGroup<>();
            root.useStyle(sizeFull());
            root.addWidget(shell);
            Scene scene = new Scene(root);
            scene.init();
            scene.mount(SceneContext.create(host(width, height)));
            scene.setLayoutArea(width, height);
            shell.applyViewport((float) width, (float) height);
            relayout(scene);
            return new HostingFixture(shell, scene, tabManager, provider);
        }

        private Widget plannerPane() {
            if (provider instanceof PaneProvider panes) {
                return panes.planner;
            }
            for (Widget child : stageChildren()) {
                if (!(child instanceof DatabasePage)) {
                    return child;
                }
            }
            throw new AssertionError("planner workspace is not hosted");
        }

        private @Nullable Widget databasePane() {
            if (provider instanceof PaneProvider panes) {
                return panes.database;
            }
            return stageChildren().stream().filter(DatabasePage.class::isInstance).findFirst().orElse(null);
        }

        private List<Widget> stageChildren() {
            List<Widget> found = new ArrayList<>();
            for (Widget child : shell.children()) {
                if (child instanceof WidgetGroup<?> group) {
                    for (Widget inner : group.children()) {
                        found.add(inner);
                    }
                }
            }
            return found;
        }

        private PlannerWorkbenchController openTab() {
            PlannerWorkbenchController controller = tabManager.openOrFocusTab(List.of(), null);
            relayout();
            return controller;
        }

        private void relayout() {
            relayout(scene);
        }

        private static void relayout(Scene scene) {
            scene.layout();
            scene.stabilize();
            scene.layout();
            scene.stabilize();
        }

        private void close() {
            shell.shutdown();
            scene.destroy();
        }
    }

    private static final class PaneProvider implements WorkspaceProvider {
        private Widget planner = pane();
        private Widget database = pane();

        @Override
        public Widget plannerWorkspace(WorkbenchShellContext ctx) {
            planner = pane();
            return planner;
        }

        @Override
        public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
            return pane();
        }

        @Override
        public Widget settingsWorkspace(WorkbenchShellContext ctx) {
            return pane();
        }

        @Override
        public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
            database = pane();
            return database;
        }

        private static Widget pane() {
            WidgetGroup<Widget> group = new WidgetGroup<>();
            group.useStyle(sizeFull());
            return group;
        }
    }

    private static SceneHost host(int width, int height) {
        return new SceneHost() {
            @Override
            // Headless host: no Minecraft Font exists in unit tests, and the paths under test never read it.
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }
}

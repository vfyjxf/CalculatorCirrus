package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsHost;
import dev.vfyjxf.cloudlib.api.ui.base.Lifecycle;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-TC-32 (workspace level): two workspaces mount and unmount repeatedly in a headless Scene without lifecycle exceptions.
 */
class WorkspaceLifecycleTest {

    private static final int rounds = 10;

    @Test
    void facilitiesWorkspaceSurvivesTenMountCycles() {
        FacilitiesHost host = new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached());
        FacilitiesAndRulesWorkspace workspace = new FacilitiesAndRulesWorkspace(
            host,
            WorkbenchThemeMode.obsidianBlueprint
        );
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(workspace);
        Scene scene = new Scene(root);
        SceneContext context = SceneContext.create(testSceneHost());

        scene.init();
        scene.mount(context);
        scene.stabilize();

        for (int round = 0; round < rounds; round++) {
            if (round > 0) {
                root.addWidget(workspace);
                scene.init();
                scene.stabilize();
            }
            assertEquals(Lifecycle.mounted, workspace.lifecycle());

            for (FacilitiesAndRulesWorkspace.Section section : FacilitiesAndRulesWorkspace.Section.values()) {
                assertDoesNotThrow(() -> workspace.selectSection(section), "select " + section + " in round " + round);
                scene.stabilize();
                assertEquals(section, workspace.section());
                assertSame(workspace.panel(section), workspace.activePanel());
                assertTrue(
                    workspace.activePanel().debugEntryCount() > 0,
                    "panel " + section + " must render content in round " + round
                );
            }
            workspace.selectSection(FacilitiesAndRulesWorkspace.Section.machines);
            scene.stabilize();
            int entryCount = workspace.activePanel().debugEntryCount();

            assertDoesNotThrow(scene::tick, "tick in round " + round);
            assertEquals(
                entryCount,
                workspace.activePanel().debugEntryCount(),
                "tick must not duplicate panel entries in round " + round
            );

            root.remove(workspace);
            assertEquals(Lifecycle.unmounted, workspace.lifecycle());
            assertDoesNotThrow(() -> workspace.activePanel().refresh(), "refresh while unmounted in round " + round);
            assertDoesNotThrow(() -> workspace.search("probe"), "search while unmounted in round " + round);
            assertDoesNotThrow(scene::tick, "tick after unmount in round " + round);
        }
    }

    @Test
    void facilitiesSectionSwitchReusesPanelsAcrossImmediateReversals() {
        FacilitiesHost host = new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached());
        FacilitiesAndRulesWorkspace workspace = new FacilitiesAndRulesWorkspace(
            host,
            WorkbenchThemeMode.obsidianBlueprint
        );
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(workspace);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.stabilize();

        workspace.selectSection(FacilitiesAndRulesWorkspace.Section.lines);
        scene.stabilize();
        // Rapid reversal within the same frame before remount flushes must not throw
        workspace.selectSection(FacilitiesAndRulesWorkspace.Section.machines);
        workspace.selectSection(FacilitiesAndRulesWorkspace.Section.lines);
        scene.stabilize();
        assertEquals(FacilitiesAndRulesWorkspace.Section.lines, workspace.section());
        assertEquals(Lifecycle.mounted, workspace.activePanel().lifecycle());
        workspace.selectSection(FacilitiesAndRulesWorkspace.Section.machines);
        scene.stabilize();
        assertEquals(Lifecycle.mounted, workspace.activePanel().lifecycle());
    }

    @Test
    void settingsWorkspaceSurvivesTenMountCycles() {
        SettingsAndDiagnosticsWorkspace workspace = new SettingsAndDiagnosticsWorkspace(
            new SettingsHost.Detached(),
            WorkbenchThemeMode.atelierStone
        );
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(workspace);
        Scene scene = new Scene(root);
        SceneContext context = SceneContext.create(testSceneHost());
        scene.init();
        scene.mount(context);
        scene.stabilize();

        for (int round = 0; round < rounds; round++) {
            if (round > 0) {
                root.addWidget(workspace);
                scene.init();
                scene.stabilize();
            }
            assertEquals(Lifecycle.mounted, workspace.lifecycle());
            assertDoesNotThrow(scene::tick, "tick in round " + round);
            assertTrue(workspace.children().size() >= 2, "settings workspace keeps both columns");
            root.remove(workspace);
            assertEquals(Lifecycle.unmounted, workspace.lifecycle());
        }
    }

    @Test
    void workspacesDoNotRegisterSceneTimersThatOutliveUnmount() {
        List<String> ticks = new ArrayList<>();
        FacilitiesHost host = new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached());
        FacilitiesAndRulesWorkspace workspace = new FacilitiesAndRulesWorkspace(
            host,
            WorkbenchThemeMode.obsidianBlueprint
        );
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(workspace);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.stabilize();

        int before = workspace.activePanel().debugEntryCount();
        for (int index = 0; index < 5; index++) {
            scene.tick();
            ticks.add(Integer.toString(index));
        }
        assertEquals(5, ticks.size());
        assertEquals(before, workspace.activePanel().debugEntryCount());

        root.remove(workspace);
        int afterUnmount = workspace.activePanel().debugEntryCount();
        for (int index = 0; index < 5; index++) {
            scene.tick();
        }
        assertEquals(
            afterUnmount,
            workspace.activePanel().debugEntryCount(),
            "unmounted panels must not keep rebuilding content on scene ticks"
        );
    }

    private static SceneHost testSceneHost() {
        return new SceneHost() {
            @Override
            // Headless host: no Minecraft Font exists in unit tests, and the paths under test never read it.
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 1280;
            }

            @Override
            public int height() {
                return 720;
            }
        };
    }
}

package dev.vfyjxf.cirrus.ui.workbench;

import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UI-TC-32: repeated mount, mode switching and teardown stay free of lifecycle errors.
 */
class WorkbenchShellLifecycleTest {

    private static final int rounds = 10;

    @Test
    void shellMountsSwitchesModesAndUnmountsTenTimes() {
        for (int round = 0; round < rounds; round++) {
            int index = round;
            assertDoesNotThrow(() -> runRound(index), "round " + index + " threw");
        }
    }

    private static void runRound(int round) {
        SessionTabManager tabs = headlessTabs();
        PaneProvider provider = new PaneProvider();
        WorkbenchShellWidget shell = new WorkbenchShellWidget(provider, tabs, WorkbenchMode.planner);
        Scene scene = mount(shell);
        try {
            int tabsBefore = tabs.tabs().size();
            switch (round % 4) {
                case 0 -> shell.setMode(WorkbenchMode.planner);
                case 1 -> shell.setMode(WorkbenchMode.facilities);
                case 2 -> shell.setMode(WorkbenchMode.settings);
                default -> shell.setMode(WorkbenchMode.planner);
            }
            scene.layout();
            scene.stabilize();
            for (int tick = 0; tick < 4; tick++) {
                scene.tick();
            }
            shell.setZenMode(round % 2 == 0);
            scene.layout();
            scene.stabilize();
            assertEquals(tabsBefore, tabs.tabs().size(), "tab count must not change while switching modes");
        } finally {
            shell.shutdown();
            scene.destroy();
        }
    }

    private static Scene mount(WorkbenchShellWidget shell) {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull());
        root.addWidget(shell);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(host(1280, 720)));
        scene.setLayoutArea(1280, 720);
        shell.applyViewport(1280f, 720f);
        scene.layout();
        scene.stabilize();
        return scene;
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

    private static SessionTabManager headlessTabs() {
        return new SessionTabManager(state -> {
            throw new IllegalStateException("no planning backend in lifecycle test");
        });
    }

    private static final class PaneProvider implements WorkspaceProvider {
        @Override
        public Widget plannerWorkspace(WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }

        @Override
        public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }

        @Override
        public Widget settingsWorkspace(WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }

        @Override
        public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
            return new WidgetGroup<>();
        }
    }
}

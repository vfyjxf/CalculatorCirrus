package dev.vfyjxf.cirrus.ui.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionOverlayToggleContractTest {

    @Test
    void closedOverlayHasNoInternalToggleButtonOrExclusionAndStopsPanelProcessing() throws Exception {
        String root = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/overlay/ExecutionOverlayRoot.java"));
        String state = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/overlay/ExecutionOverlayOpenState.java"));
        String overlay = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/ExecutionOverlay.java"));
        String panel = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/MainPanel.java"));

        assertTrue(root.contains("ExecutionOverlayOpenState.addListener(openStateListener);"));
        assertTrue(root.contains("ExecutionOverlayOpenState.removeListener(openStateListener)"));
        assertTrue(root.contains("boolean open = ExecutionOverlayOpenState.open();"));
        assertTrue(root.contains("panel.setVisible(open);"));
        assertTrue(root.contains("panel.setActive(open);"));
        assertTrue(root.contains("panel.setTickable(open);"));
        assertTrue(root.contains("traceLayer.setVisible(open);"));
        assertTrue(root.contains("if (!ExecutionOverlayOpenState.open() || !panel.traceEnabled())"));
        assertTrue(root.contains("if (!ExecutionOverlayOpenState.open())"));
        assertTrue(root.contains("super.tick();"));
        assertTrue(root.contains("return ExecutionOverlayOpenState.open() ? List.of(panelBounds) : List.of();"));
        assertTrue(state.contains("public static boolean toggle()"));
        assertTrue(state.contains("notifyListeners();"));
        assertTrue(overlay.contains("widget.exclusionBounds()"));
        assertFalse(root.contains("ToggleButtonWidget"));
        assertFalse(root.contains("toggleButton"));
        assertFalse(root.contains("collapsedBounds"));
        assertFalse(root.contains("toggleGlobalOpen"));
        assertFalse(root.contains("CirrusControlChrome"));
        assertFalse(panel.contains("\t\tensureDriver();"));
    }

    private static Path resolvePath(String relative) {
        String moduleRelative = "Mod/" + relative;
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path local = cursor.resolve(relative);
            if (Files.exists(local)) {
                return local;
            }
            Path module = cursor.resolve(moduleRelative);
            if (Files.exists(module)) {
                return module;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relative + " from " + Path.of("").toAbsolutePath());
    }
}

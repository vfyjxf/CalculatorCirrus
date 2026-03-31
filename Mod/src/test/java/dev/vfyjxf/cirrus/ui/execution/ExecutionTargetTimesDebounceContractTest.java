package dev.vfyjxf.cirrus.ui.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTargetTimesDebounceContractTest {

    @Test
    void targetTimesChangesDoNotRebuildExecutionSelectionImmediately() throws Exception {
        String panel = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/MainPanel.java"));
        String page = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/ui/execution/page/RecipePage.java"));

        assertTrue(panel.contains("targetTimesDebouncer.request(selection.recipe(), normalized, selection.times())"));
        assertTrue(panel.contains("tickPendingTargetTimes();"));
        assertTrue(panel.contains("targetTimesDebouncer.tick(selection.recipe(), selection.times())"));
        assertTrue(panel.contains("ExecutionTargetRecipeState.set(commit.target(), commit.value())"));
        assertTrue(page.contains("state.flushTargetTimes();"));

        String setTargetTimes = panel.substring(panel.indexOf("public void setTargetTimes(int times)"));
        setTargetTimes = setTargetTimes.substring(0, setTargetTimes.indexOf("@Override", 1));
        assertFalse(setTargetTimes.contains("ExecutionTargetRecipeState.set("));
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

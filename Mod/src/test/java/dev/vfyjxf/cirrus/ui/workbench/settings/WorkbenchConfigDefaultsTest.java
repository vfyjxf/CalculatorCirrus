package dev.vfyjxf.cirrus.ui.workbench.settings;

import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLayoutMode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-UI-6 config extension: default values and ranges of the nine workbench_* options.
 */
class WorkbenchConfigDefaultsTest {

    @Test
    void workbenchConfigDefaultsMatchTheSpecTable() {
        assertEquals(WorkbenchThemeMode.obsidianBlueprint, ExecutionOverlayClientConfig.workbenchThemeDefault);
        assertEquals(WorkbenchThemeMode.obsidianBlueprint, ExecutionOverlayClientConfig.workbenchTheme());

        assertFalse(ExecutionOverlayClientConfig.workbenchReducedMotionDefault);
        assertFalse(ExecutionOverlayClientConfig.workbenchReducedMotion());

        assertEquals(DagLayoutMode.horizontalLr, ExecutionOverlayClientConfig.workbenchDefaultDagLayoutDefault);
        assertEquals(DagLayoutMode.horizontalLr, ExecutionOverlayClientConfig.workbenchDefaultDagLayout());

        assertEquals(ObjectivePreset.autoSmart, ExecutionOverlayClientConfig.workbenchDefaultPresetDefault);
        assertEquals(ObjectivePreset.autoSmart, ExecutionOverlayClientConfig.workbenchDefaultPreset());

        assertEquals(80, ExecutionOverlayClientConfig.workbenchSoftBudgetMsDefault);
        assertEquals(80, ExecutionOverlayClientConfig.workbenchSoftBudgetMs());
        assertEquals(250, ExecutionOverlayClientConfig.workbenchHardBudgetMsDefault);
        assertEquals(250, ExecutionOverlayClientConfig.workbenchHardBudgetMs());

        assertFalse(ExecutionOverlayClientConfig.workbenchDefaultPersistWorldDefault);
        assertFalse(ExecutionOverlayClientConfig.workbenchDefaultPersistWorld());

        assertEquals(ProgressionPreset.overworld, ExecutionOverlayClientConfig.workbenchProgressionPresetDefault);
        assertEquals(ProgressionPreset.overworld, ExecutionOverlayClientConfig.workbenchProgressionPreset());

        assertTrue(ExecutionOverlayClientConfig.workbenchGridSnapDefault);
        assertTrue(ExecutionOverlayClientConfig.workbenchGridSnap());
    }

    @Test
    void workbenchBudgetBoundsMatchTheSpecRanges() {
        assertEquals(20, ExecutionOverlayClientConfig.workbenchSoftBudgetMsMin);
        assertEquals(2000, ExecutionOverlayClientConfig.workbenchSoftBudgetMsMax);
        assertEquals(80, ExecutionOverlayClientConfig.workbenchHardBudgetMsMin);
        assertEquals(5000, ExecutionOverlayClientConfig.workbenchHardBudgetMsMax);

        assertTrue(
            ExecutionOverlayClientConfig.workbenchSoftBudgetMs()
                    >= ExecutionOverlayClientConfig.workbenchSoftBudgetMsMin
        );
        assertTrue(
            ExecutionOverlayClientConfig.workbenchSoftBudgetMs()
                    <= ExecutionOverlayClientConfig.workbenchSoftBudgetMsMax
        );
        assertTrue(
            ExecutionOverlayClientConfig.workbenchHardBudgetMs()
                    >= ExecutionOverlayClientConfig.workbenchHardBudgetMsMin
        );
        assertTrue(
            ExecutionOverlayClientConfig.workbenchHardBudgetMs()
                    <= ExecutionOverlayClientConfig.workbenchHardBudgetMsMax
        );
    }

    @Test
    void progressionPresetsBuildRealProgressionStates() {
        assertEquals(ProgressionState.overworldDefault, ProgressionPreset.overworld.toProgressionState());

        ProgressionState nether = ProgressionPreset.nether.toProgressionState();
        assertEquals(Set.of("minecraft:overworld", "minecraft:the_nether"), nether.accessibleDimensions());
        assertEquals(Set.of("minecraft:nether"), nether.unlockedStages());

        ProgressionState end = ProgressionPreset.theEnd.toProgressionState();
        assertEquals(
            Set.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
            end.accessibleDimensions()
        );
        assertEquals(Set.of("minecraft:nether", "minecraft:the_end"), end.unlockedStages());

        assertTrue(
            nether.accessibleDimensions()
                    .containsAll(ProgressionPreset.overworld.toProgressionState().accessibleDimensions())
        );
        assertTrue(end.accessibleDimensions().containsAll(nether.accessibleDimensions()));
        assertEquals(ProgressionPreset.overworld, ProgressionPreset.defaultPreset());
    }

}

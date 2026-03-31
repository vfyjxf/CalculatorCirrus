package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutionOverlayTexturesTest {
    @Test
    void exposesExecutionOverlayTabIcons() {
        assertNotNull(CirrusTextures.Icon.Execution.Category.recipe);
        assertNotNull(CirrusTextures.Icon.Execution.Category.recipeSelected);
        assertNotNull(CirrusTextures.Icon.Execution.Category.timeline);
        assertNotNull(CirrusTextures.Icon.Execution.Category.timelineSelected);
        assertNotNull(CirrusTextures.Icon.Execution.Category.ingredients);
        assertNotNull(CirrusTextures.Icon.Execution.Category.ingredientsSelected);
    }

    @Test
    void exposesRockAndSlideTexturesWithResourceBackedSizes() {
        assertEquals(9, CirrusTextures.Toggle.Switch.Rock.leftOn.width());
        assertEquals(9, CirrusTextures.Toggle.Switch.Rock.leftOn.height());
        assertEquals(11, CirrusTextures.Toggle.Switch.Rock.Bg.onLeft.width());
        assertEquals(13, CirrusTextures.Toggle.Switch.Rock.Bg.onLeft.height());
        assertEquals(20, CirrusTextures.Toggle.Switch.Slide.on.width());
        assertEquals(13, CirrusTextures.Toggle.Switch.Slide.on.height());
        assertEquals(20, CirrusTextures.Toggle.Switch.Slide.onHover.width());
        assertEquals(12, CirrusTextures.Toggle.Switch.Slide.onHover.height());
    }
}

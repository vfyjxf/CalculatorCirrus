package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class CirrusCalculatorTextureContractTest {

    @Test
    void workbenchThemedTexturesAreRegisteredForBothThemes() {
        for (WorkbenchThemeMode mode : WorkbenchThemeMode.values()) {
            List<VisualTexture> textures = CirrusTextures.Workbench.all(mode);
            assertEquals(23, textures.size(), mode + " must expose every themed workbench texture");
            for (int index = 0; index < textures.size(); index++) {
                assertNotNull(textures.get(index), mode + " texture #" + index + " must not be null");
            }
        }
    }

    @Test
    void workbenchThemedTexturesAreLazilyCached() {
        assertSame(
            CirrusTextures.Workbench.dockIsland(WorkbenchThemeMode.obsidianBlueprint),
            CirrusTextures.Workbench.dockIsland(WorkbenchThemeMode.obsidianBlueprint),
            "themed accessors must cache the first construction"
        );
        assertNotSame(
            CirrusTextures.Workbench.dockIsland(WorkbenchThemeMode.obsidianBlueprint),
            CirrusTextures.Workbench.dockIsland(WorkbenchThemeMode.atelierStone),
            "each theme must resolve its own dock island texture"
        );
        assertNotSame(
            CirrusTextures.Workbench.cardSurface(WorkbenchThemeMode.obsidianBlueprint),
            CirrusTextures.Workbench.cardSurface(WorkbenchThemeMode.atelierStone)
        );
        assertSame(
            CirrusTextures.Workbench.toggleTripleRightUnselected(WorkbenchThemeMode.atelierStone),
            OreUiTextures.Light.btnUp,
            "atelier uses crisp 90-degree OreUI light stone textures"
        );
    }
}

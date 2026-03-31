package dev.vfyjxf.cirrus.test.mixin.jei;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkOverlayMixinConfigTest {

    @Test
    void mixinConfigRegistersBookmarkOverlayAndRecipeTargetButton() throws Exception {
        String config = Files.readString(resolveMixinConfig());

        assertTrue(config.contains("\"jei.BookmarkOverlayMixin\""));
        assertFalse(config.contains("\"jei.GuiIconToggleButtonAccessor\""));
        assertFalse(config.contains("\"jei.RecipesGuiMixin\""));
        assertFalse(config.contains("\"jei.RecipeGuiLayoutsAccessor\""));
        assertFalse(config.contains("\"jei.RecipeLayoutWithButtonsAccessor\""));
    }

    @Test
    void bookmarkOverlayToggleButtonPlaysClickSound() throws Exception {
        String source = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/mixin/jei/BookmarkOverlayMixin.java"));

        assertTrue(source.contains("CirrusUiSound.playClickSound();"));
    }

    @Test
    void bookmarkOverlayButtonsOpenCalculatorAndToggleExecutionOverlayOnly() throws Exception {
        String source = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/mixin/jei/BookmarkOverlayMixin.java"));

        assertTrue(source.contains("Minecraft.getInstance().setScreen(new CalculatorScreen());"));
        assertTrue(source.contains("ExecutionOverlayOpenState.toggle();"));
        assertFalse(source.contains("ExecutionOverlayRoot.toggleGlobalOpen();"));
        assertFalse(source.contains("import mezz.jei.gui.recipes.RecipesGui;"));
        assertFalse(source.contains("recipesGui.onClose();"));
        assertTrue(source.contains("cirrus$activateCalculatorButton"));
    }

    private static Path resolveMixinConfig() {
        return resolvePath("src/main/resources/calculator_cirrus.mixins.json");
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

package dev.vfyjxf.cirrus.ui.execution.cockpit;

import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the live OreUI palette for Execution Cockpit widgets across both container overlays and standalone screens.
 */
public final class ExecutionCockpitTheme {

    private ExecutionCockpitTheme() {}

    public static WorkbenchThemeMode mode(@Nullable Widget widget) {
        if (widget != null) {
            for (Widget cursor = widget; cursor != null; cursor = cursor.parent()) {
                if (cursor instanceof WorkbenchShellContext shell) {
                    return shell.theme();
                }
            }
            if (widget.lifecycle().mounted()) {
                var scene = widget.scene();
                if (scene != null) {
                    WorkbenchThemeMode explicit = explicitCirrusMode(scene.theme());
                    if (explicit != null) {
                        return explicit;
                    }
                }
            }
        }
        try {
            WorkbenchThemeMode configured = ExecutionOverlayClientConfig.workbenchTheme();
            if (configured != null) {
                return configured;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Headless tests run without loaded NeoForge config.
        }
        return WorkbenchThemeMode.defaultMode();
    }

    public static CirrusPalette palette(@Nullable Widget widget) {
        WorkbenchThemeMode resolved = mode(widget);
        if (widget != null && widget.lifecycle().mounted()) {
            var scene = widget.scene();
            if (scene != null && explicitCirrusMode(scene.theme()) == resolved) {
                return CirrusPalette.of(scene.theme(), resolved);
            }
        }
        return CirrusPalette.of(resolved);
    }

    private static @Nullable WorkbenchThemeMode explicitCirrusMode(@Nullable Theme theme) {
        if (theme == null) {
            return null;
        }
        ResourceLocation id = theme.id();
        if (id.equals(WorkbenchThemeMode.obsidianBlueprint.themeId()) || id.getPath().contains("obsidian_blueprint")) {
            return WorkbenchThemeMode.obsidianBlueprint;
        }
        if (id.equals(WorkbenchThemeMode.atelierStone.themeId()) || id.getPath().contains("atelier_stone")) {
            return WorkbenchThemeMode.atelierStone;
        }
        return null;
    }
}

package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHandle;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class CirrusUiTheme {

    public static @Nullable WorkbenchShellContext shellContext(@Nullable Widget widget) {
        for (Widget cursor = widget; cursor != null; cursor = cursor.parent()) {
            if (cursor instanceof WorkbenchShellContext shell) {
                return shell;
            }
        }
        return null;
    }

    public static void bindThemeListener(Widget widget, SceneHandle handle, Runnable onThemeChanged) {
        Objects.requireNonNull(widget, "widget");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(onThemeChanged, "onThemeChanged");
        onThemeChanged.run();
        WorkbenchShellContext shell = shellContext(widget);
        if (shell != null) {
            shell.addListener(onThemeChanged);
            handle.onCleanup(() -> shell.removeListener(onThemeChanged));
        }
    }

    public static WorkbenchThemeMode mode(@Nullable Widget widget) {
        if (widget == null) {
            return WorkbenchThemeMode.atelierStone;
        }
        WorkbenchShellContext shell = shellContext(widget);
        if (shell != null) {
            return shell.theme();
        }
        if (widget.lifecycle().mounted()) {
            WorkbenchThemeMode fromScene = modeFromTheme(widget.scene().theme());
            if (fromScene != null) {
                return fromScene;
            }
        }
        return WorkbenchThemeMode.atelierStone;
    }

    public static CirrusPalette palette(@Nullable Widget widget) {
        WorkbenchThemeMode mode = mode(widget);
        Theme sceneTheme = sceneTheme(widget, mode);
        return sceneTheme != null ? CirrusPalette.of(sceneTheme, mode) : CirrusPalette.of(mode);
    }

    public static CirrusThemeTextures textures(@Nullable Widget widget) {
        WorkbenchThemeMode mode = mode(widget);
        Theme sceneTheme = sceneTheme(widget, mode);
        return sceneTheme != null ? CirrusThemeTextures.of(sceneTheme, mode) : CirrusThemeTextures.of(mode);
    }

    public static boolean isDark(@Nullable Widget widget) {
        return mode(widget) == WorkbenchThemeMode.obsidianBlueprint;
    }

    private static @Nullable Theme sceneTheme(@Nullable Widget widget, WorkbenchThemeMode mode) {
        if (widget != null && widget.lifecycle().mounted()) {
            Theme theme = widget.scene().theme();
            WorkbenchThemeMode fromTheme = modeFromTheme(theme);
            if (fromTheme == null || fromTheme == mode) {
                return theme;
            }
        }
        return null;
    }

    private static @Nullable WorkbenchThemeMode modeFromTheme(@Nullable Theme theme) {
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

    private CirrusUiTheme() {}
}

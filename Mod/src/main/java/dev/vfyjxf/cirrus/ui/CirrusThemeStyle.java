package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.style.StyleVar;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import dev.vfyjxf.cloudlib.api.ui.style.Themes;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class CirrusThemeStyle {

    private static final Map<Theme, UIStyle> rootStyleCache = new WeakHashMap<>();

    private static final class RootProbe extends Widget {}

    public static @Nullable Theme resolveTheme(WorkbenchThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        Theme registered = Themes.get(mode.themeId());
        return registered != null ? registered : Themes.active();
    }

    public static void applyToScene(@Nullable Scene scene, WorkbenchThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (scene == null) {
            return;
        }
        ResourceLocation target = mode.themeId();
        if (Themes.get(target) != null) {
            scene.setTheme(target);
        }
    }

    public static void activate(WorkbenchThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        ResourceLocation target = mode.themeId();
        if (Themes.get(target) == null) {
            return;
        }
        List<ResourceLocation> wanted = List.of(target);
        if (!Themes.activeIds().equals(wanted)) {
            Themes.setActive(wanted);
        }
    }

    public static UIStyle resolveRootStyle(Theme theme) {
        Objects.requireNonNull(theme, "theme");
        synchronized (rootStyleCache) {
            UIStyle cached = rootStyleCache.get(theme);
            if (cached != null) {
                return cached;
            }
            UIStyle resolved = theme.resolve(new RootProbe());
            rootStyleCache.put(theme, resolved);
            return resolved;
        }
    }

    public static int color(@Nullable Theme theme, StyleVar<Integer> var, int fallback) {
        if (theme == null) {
            return fallback;
        }
        Integer value = resolveRootStyle(theme).var(var);
        return value == null ? fallback : value;
    }

    public static int color(WorkbenchThemeMode mode, StyleVar<Integer> var, int fallback) {
        return color(resolveTheme(mode), var, fallback);
    }

    public static int color(StyleVar<Integer> var, int fallback) {
        return color(Themes.active(), var, fallback);
    }

    public static VisualTexture texture(@Nullable Theme theme, StyleVar<VisualTexture> var, VisualTexture fallback) {
        if (theme == null) {
            return fallback;
        }
        VisualTexture value = resolveRootStyle(theme).var(var);
        return value == null || value.isEmpty() ? fallback : value;
    }

    public static VisualTexture texture(WorkbenchThemeMode mode, StyleVar<VisualTexture> var, VisualTexture fallback) {
        return texture(resolveTheme(mode), var, fallback);
    }

    public static VisualTexture texture(StyleVar<VisualTexture> var, VisualTexture fallback) {
        return texture(Themes.active(), var, fallback);
    }

    private CirrusThemeStyle() {}
}

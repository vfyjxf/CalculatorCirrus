package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public record CirrusThemeTextures(
    VisualTexture dockIsland,
    VisualTexture cardSurface,
    VisualTexture cardHover,
    VisualTexture cardSelected,
    VisualTexture flat,
    VisualTexture inset,
    VisualTexture recessed,
    VisualTexture slotBase,
    VisualTexture slotDark,
    VisualTexture buttonUp,
    VisualTexture buttonHover,
    VisualTexture buttonDown,
    VisualTexture ctaPrimary,
    VisualTexture ctaDanger,
    VisualTexture switchOn,
    VisualTexture switchOnHover,
    VisualTexture switchOff,
    VisualTexture switchOffHover,
    VisualTexture scrollTrack,
    VisualTexture scrollThumb,
    VisualTexture scrollThumbHorizontal,
    VisualTexture scrollTrackFill,
    VisualTexture minimapFrame,
    VisualTexture meterTrack,
    VisualTexture toggleTripleLeftUnselected,
    VisualTexture toggleTripleLeftSelected,
    VisualTexture toggleTripleMiddleLeft,
    VisualTexture toggleTripleMiddleMiddle,
    VisualTexture toggleTripleMiddleRight,
    VisualTexture toggleTripleRightSelected,
    VisualTexture toggleTripleRightUnselected
) {

    public static final CirrusThemeTextures atelierStone = new CirrusThemeTextures(
        OreUiTextures.Light.dock,
        OreUiTextures.Light.card,
        OreUiTextures.Light.cardHover,
        OreUiTextures.Light.cardSelected,
        OreUiTextures.Light.flat,
        OreUiTextures.Light.inset,
        OreUiTextures.Light.recessed,
        OreUiTextures.Light.slotBase,
        OreUiTextures.Light.slotDark,
        OreUiTextures.Light.btnUp,
        OreUiTextures.Light.btnHover,
        OreUiTextures.Light.btnDown,
        OreUiTextures.Light.ctaPrimary,
        OreUiTextures.Light.ctaDanger,
        OreUiTextures.Light.switchOn,
        OreUiTextures.Light.switchOnHover,
        OreUiTextures.Light.switchOff,
        OreUiTextures.Light.switchOffHover,
        OreUiTextures.Light.scrollTrackTex,
        OreUiTextures.Light.scrollThumbTex,
        OreUiTextures.Light.scrollThumbTex,
        OreUiTextures.Light.scrollTrackTex,
        OreUiTextures.Light.minimapFrame,
        OreUiTextures.Light.meterTrackTex,
        OreUiTextures.Light.btnUp,
        OreUiTextures.Light.cardSelected,
        OreUiTextures.Light.btnUp,
        OreUiTextures.Light.card,
        OreUiTextures.Light.btnUp,
        OreUiTextures.Light.cardSelected,
        OreUiTextures.Light.btnUp
    );

    public static final CirrusThemeTextures obsidianBlueprint = new CirrusThemeTextures(
        OreUiTextures.Dark.dock,
        OreUiTextures.Dark.card,
        OreUiTextures.Dark.cardHover,
        OreUiTextures.Dark.cardSelected,
        OreUiTextures.Dark.flat,
        OreUiTextures.Dark.inset,
        OreUiTextures.Dark.recessed,
        OreUiTextures.Dark.slotBase,
        OreUiTextures.Dark.slotDark,
        OreUiTextures.Dark.btnUp,
        OreUiTextures.Dark.btnHover,
        OreUiTextures.Dark.btnDown,
        OreUiTextures.Dark.ctaPrimary,
        OreUiTextures.Dark.ctaDanger,
        OreUiTextures.Dark.switchOn,
        OreUiTextures.Dark.switchOnHover,
        OreUiTextures.Dark.switchOff,
        OreUiTextures.Dark.switchOffHover,
        OreUiTextures.Dark.scrollTrackTex,
        OreUiTextures.Dark.scrollThumbTex,
        OreUiTextures.Dark.scrollThumbTex,
        OreUiTextures.Dark.scrollTrackTex,
        OreUiTextures.Dark.minimapFrame,
        OreUiTextures.Dark.meterTrackTex,
        OreUiTextures.Dark.btnUp,
        OreUiTextures.Dark.cardSelected,
        OreUiTextures.Dark.btnUp,
        OreUiTextures.Dark.card,
        OreUiTextures.Dark.btnUp,
        OreUiTextures.Dark.cardSelected,
        OreUiTextures.Dark.btnUp
    );

    private static final Map<Theme, EnumMap<WorkbenchThemeMode, CirrusThemeTextures>> cache = new WeakHashMap<>();

    public static CirrusThemeTextures declared(WorkbenchThemeMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case obsidianBlueprint -> obsidianBlueprint;
            case atelierStone -> atelierStone;
        };
    }

    public static CirrusThemeTextures of(WorkbenchThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        return of(CirrusThemeStyle.resolveTheme(mode), mode);
    }

    public static CirrusThemeTextures of(@Nullable Theme theme, WorkbenchThemeMode fallbackMode) {
        Objects.requireNonNull(fallbackMode, "fallbackMode");
        CirrusThemeTextures fallback = declared(fallbackMode);
        if (theme == null || isBuiltinCirrusTheme(theme)) {
            return fallback;
        }
        synchronized (cache) {
            EnumMap<WorkbenchThemeMode, CirrusThemeTextures> byMode = cache
                    .computeIfAbsent(theme, ignored -> new EnumMap<>(WorkbenchThemeMode.class));
            CirrusThemeTextures cached = byMode.get(fallbackMode);
            if (cached != null) {
                return cached;
            }
            CirrusThemeTextures resolved = resolve(theme, fallback);
            byMode.put(fallbackMode, resolved);
            return resolved;
        }
    }

    private static boolean isBuiltinCirrusTheme(Theme theme) {
        return theme.id().equals(WorkbenchThemeMode.obsidianBlueprint.themeId())
                || theme.id().equals(WorkbenchThemeMode.atelierStone.themeId());
    }

    private static CirrusThemeTextures resolve(Theme theme, CirrusThemeTextures fallback) {
        return new CirrusThemeTextures(
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texDock, fallback.dockIsland),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texCard, fallback.cardSurface),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texCardHover, fallback.cardHover),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texCardSelected, fallback.cardSelected),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texFlat, fallback.flat),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texInset, fallback.inset),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texRecessed, fallback.recessed),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texSlot, fallback.slotBase),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texSlotDark, fallback.slotDark),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texBtnUp, fallback.buttonUp),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texBtnHover, fallback.buttonHover),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texBtnDown, fallback.buttonDown),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texCtaPrimary, fallback.ctaPrimary),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texCtaDanger, fallback.ctaDanger),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texSwitchOn, fallback.switchOn),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texSwitchOnHover, fallback.switchOnHover),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texSwitchOff, fallback.switchOff),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texSwitchOffHover, fallback.switchOffHover),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texScrollTrack, fallback.scrollTrack),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texScrollThumb, fallback.scrollThumb),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texScrollThumbHorizontal, fallback.scrollThumbHorizontal),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texScrollTrackFill, fallback.scrollTrackFill),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texMinimapFrame, fallback.minimapFrame),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texMeterTrack, fallback.meterTrack),
            CirrusThemeStyle
                    .texture(theme, CirrusPaletteVars.texTripleLeftUnselected, fallback.toggleTripleLeftUnselected),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texTripleLeftSelected, fallback.toggleTripleLeftSelected),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texTripleMiddleLeft, fallback.toggleTripleMiddleLeft),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texTripleMiddleMiddle, fallback.toggleTripleMiddleMiddle),
            CirrusThemeStyle.texture(theme, CirrusPaletteVars.texTripleMiddleRight, fallback.toggleTripleMiddleRight),
            CirrusThemeStyle
                    .texture(theme, CirrusPaletteVars.texTripleRightSelected, fallback.toggleTripleRightSelected),
            CirrusThemeStyle
                    .texture(theme, CirrusPaletteVars.texTripleRightUnselected, fallback.toggleTripleRightUnselected)
        );
    }
}

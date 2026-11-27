package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.vfyjxf.cirrus.util.CirrusLocations.ofMod;

/**
 * Routes icon sprites to dedicated {@code icon_dark/} PNGs in dark mode while leaving originals untouched in light mode.
 */
public final class CirrusThemeIcons {

    private static final String lightIconPrefix = "icon/";
    private static final String darkIconPrefix = "icon_dark/";
    private static final Map<ResourceLocation, ImageTexture> darkCache = new ConcurrentHashMap<>();

    private CirrusThemeIcons() {}

    public static ImageTexture resolve(ImageTexture icon, @Nullable WorkbenchThemeMode mode) {
        if (icon == null || mode != WorkbenchThemeMode.obsidianBlueprint) {
            return icon;
        }
        return darkVariant(icon);
    }

    public static ImageTexture resolve(ImageTexture icon, @Nullable Widget widget) {
        return resolve(icon, CirrusUiTheme.mode(widget));
    }

    public static SizedTexture resolveSized(SizedTexture icon, @Nullable WorkbenchThemeMode mode) {
        if (icon instanceof ImageTexture image) {
            return resolve(image, mode);
        }
        return icon;
    }

    public static SizedTexture resolveSized(SizedTexture icon, @Nullable Widget widget) {
        return resolveSized(icon, CirrusUiTheme.mode(widget));
    }

    private static ImageTexture darkVariant(ImageTexture light) {
        ResourceLocation location = light.location();
        return darkCache.computeIfAbsent(location, key -> {
            String path = key.getPath();
            String relative = extractIconRelativePath(path);
            if (relative == null) {
                return light;
            }
            String resourcePath = "assets/" + key.getNamespace() + "/textures/cirrus/gui/" + darkIconPrefix + relative
                    + ".png";
            if (CirrusThemeIcons.class.getClassLoader().getResource(resourcePath) == null) {
                return light;
            }
            return ImageTexture.sprite(ofMod(darkIconPrefix + relative), light.width(), light.height());
        });
    }

    private static @Nullable String extractIconRelativePath(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith(lightIconPrefix)) {
            return stripPng(path.substring(lightIconPrefix.length()));
        }
        int marker = path.indexOf("/gui/icon/");
        if (marker >= 0) {
            return stripPng(path.substring(marker + "/gui/icon/".length()));
        }
        return null;
    }

    private static String stripPng(String value) {
        return value.endsWith(".png") ? value.substring(0, value.length() - 4) : value;
    }
}

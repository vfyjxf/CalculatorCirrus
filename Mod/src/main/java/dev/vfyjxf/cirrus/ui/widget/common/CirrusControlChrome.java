package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import net.minecraft.client.gui.Font;
import org.jspecify.annotations.Nullable;

public final class CirrusControlChrome {
    private static final int iconYOffset = -1;
    private static final int checkboxIconYOffset = -1;

    private CirrusControlChrome() {}

    public static void renderButtonBackground(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        boolean down,
        boolean hovered,
        boolean enabled
    ) {
        renderButtonBackground(canvas, CirrusThemeTextures.atelierStone, x, y, width, height, down, hovered, enabled);
    }

    public static void renderButtonBackground(
        SceneCanvas canvas,
        @Nullable Widget widget,
        int x,
        int y,
        int width,
        int height,
        boolean down,
        boolean hovered,
        boolean enabled
    ) {
        renderButtonBackground(canvas, CirrusUiTheme.textures(widget), x, y, width, height, down, hovered, enabled);
    }

    public static void renderButtonBackground(
        SceneCanvas canvas,
        CirrusThemeTextures textures,
        int x,
        int y,
        int width,
        int height,
        boolean down,
        boolean hovered,
        boolean enabled
    ) {
        canvas.texture(
            down ? textures.buttonDown() : enabled && hovered ? textures.buttonHover() : textures.buttonUp(),
            x,
            y,
            Math.max(0, width),
            Math.max(0, height)
        );
    }

    public static int centeredIconX(int width, int iconWidth) {
        return Math.max(0, (width - iconWidth) / 2);
    }

    public static int centeredIconY(int height, int iconHeight, boolean down) {
        return centeredIconY(height, iconHeight, iconYOffset + (down ? 1 : 0));
    }

    public static int centeredCheckboxIconY(int height, int iconHeight) {
        return centeredIconY(height, iconHeight, checkboxIconYOffset);
    }

    public static int centeredIconY(int height, int iconHeight, int offset) {
        return Math.max(0, (height - iconHeight) / 2 + offset);
    }

    public static int centeredTextY(int rowY, int rowHeight, @Nullable Font font) {
        int lineHeight = font == null ? 9 : font.lineHeight;
        int glyphHeight = Math.max(7, lineHeight - 1);
        return rowY + Math.max(0, (rowHeight - glyphHeight) / 2);
    }

    public static void renderCenteredIcon(
        SceneCanvas canvas,
        SizedTexture icon,
        int x,
        int y,
        int width,
        int height,
        boolean down
    ) {
        int iconX = x + centeredIconX(width, icon.width());
        int iconY = y + centeredIconY(height, icon.height(), down);
        canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
    }

    public static int iconYOffset() {
        return iconYOffset;
    }

    public static int checkboxIconYOffset() {
        return checkboxIconYOffset;
    }
}

package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;

public final class CirrusControlChrome {
    private static final int iconYOffset = -1;
    private static final int checkboxIconYOffset = -1;

    private CirrusControlChrome() {
    }

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
        canvas.texture(
            down ? CirrusTextures.Toggle.Button.down : enabled && hovered ? CirrusTextures.Toggle.Button.hover : CirrusTextures.Toggle.Button.up,
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

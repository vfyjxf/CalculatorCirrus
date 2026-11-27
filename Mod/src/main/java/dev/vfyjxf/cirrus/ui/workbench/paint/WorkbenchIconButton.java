package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;

import java.util.Objects;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public class WorkbenchIconButton extends Widget {
    public static final int defaultSize = 18;
    private static final int iconInset = 2;

    private final VisualTexture icon;
    private final Runnable action;
    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private boolean enabled = true;

    public WorkbenchIconButton(VisualTexture icon, Runnable action, int width, int height) {
        this.icon = Objects.requireNonNull(icon, "icon");
        this.action = action == null ? () -> {} : action;
        useStyle(sizeOf(width, height));
        onMouseClick((input, clickCount, context) -> {
            if (!enabled || !input.isLeftClick()) {
                return EventDispatch.pass;
            }
            action.run();
            CirrusUiSound.playClickSound();
            context.consume();
            return EventDispatch.consumed;
        });
    }

    public static WorkbenchIconButton of(VisualTexture icon, Runnable action) {
        return new WorkbenchIconButton(icon, action, defaultSize, defaultSize);
    }

    public static WorkbenchIconButton of(VisualTexture icon, Runnable action, int width, int height) {
        return new WorkbenchIconButton(icon, action, width, height);
    }

    public WorkbenchIconButton palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public WorkbenchIconButton enabled(boolean enabled) {
        this.enabled = enabled;
        setActive(enabled);
        setInteractive(enabled);
        return this;
    }

    public boolean enabled() {
        return enabled;
    }

    public VisualTexture icon() {
        return icon;
    }

    public int iconTint() {
        CirrusPalette theme = palette.get();
        if (!enabled) {
            return theme.textDisabled();
        }
        return hovered() ? theme.focusRing() : theme.textPrimary();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusPalette theme = palette.get();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        OreUiPaint.raisedButton(canvas, 0, 0, width, height, theme, false, enabled && hovered(), false, false);
        VisualTexture resolved = icon instanceof ImageTexture image
                ? CirrusThemeIcons.resolve(image, OreUiPaint.themeOf(theme))
                : icon;
        int iconWidth = resolved instanceof SizedTexture sized ? sized.width() : Math.max(1, width - iconInset * 2);
        int iconHeight = resolved instanceof SizedTexture sized ? sized.height() : Math.max(1, height - iconInset * 2);
        iconWidth = Math.min(iconWidth, width);
        iconHeight = Math.min(iconHeight, height);
        int iconX = (width - iconWidth) / 2;
        int iconY = (height - iconHeight) / 2;
        if (!enabled) {
            canvas.color(OreUiPaint.withAlpha(0xFFFFFFFF, 0.45f));
        }
        canvas.texture(resolved, iconX, iconY, iconWidth, iconHeight);
        if (!enabled) {
            canvas.resetColor();
        }
    }
}

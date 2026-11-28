package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.OreUiTextures;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchBreakpoint;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Z3 left mode rail: four mode icons in one tint, a 2px bright-cyan indicator and a version ring at the bottom.
 */
public class LeftModeRailWidget extends Widget implements OreUiTooltipProvider {
    public static final int modeCount = 4;
    public static final int topInset = 8;
    public static final int iconGap = 8;
    public static final int indicatorWidth = ChromeButtons.indicatorWidth;
    public static final double ringFullVersion = 16.0;
    public static final int ringMaxSize = 14;
    public static final int ringInset = 6;

    private static final WorkbenchMode[] modes = {WorkbenchMode.planner, WorkbenchMode.facilities,
            WorkbenchMode.database, WorkbenchMode.settings};

    private final WorkbenchShellContext shellContext;
    private final OreUiMotion.SmoothValue activeTileY = new OreUiMotion.SmoothValue();
    private final OreUiMotion.SmoothValue ringProgress = new OreUiMotion.SmoothValue();

    public LeftModeRailWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        WorkbenchShellLayout.Rect region = shellLayout().leftRail();
        addStyleClass("workbench-rail");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.dockIsland(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(region.width(), region.height()));
        setInteractive(true);
        onMouseClick((input, clickCount, context1) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            if (shellContext.zenMode()) {
                shellContext.setZenMode(false);
                context1.consume();
                return EventDispatch.consumed;
            }
            var local = input.mouseRelative(this);
            if (activateModeAt(local.x(), local.y())) {
                context1.consume();
                return EventDispatch.consumed;
            }
            if (activateVersionRing(local.x(), local.y())) {
                context1.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    public static WorkbenchMode modeAt(int index) {
        return modes[Math.max(0, Math.min(modes.length - 1, index))];
    }

    public static WorkbenchMode[] modes() {
        return modes.clone();
    }

    public int modeIconSize() {
        WorkbenchBreakpoint breakpoint = shellContext.breakpoint();
        int iconSize = Math.round(breakpoint.modeRailIconSize());
        int railWidth = Math.max(1, width() > 0 ? width() : Math.round(shellContext.leftRailWidth()));
        return Math.max(8, Math.min(iconSize, Math.max(0, railWidth - 2)));
    }

    public int iconX() {
        int railWidth = Math.max(1, width() > 0 ? width() : Math.round(shellContext.leftRailWidth()));
        return Math.max(indicatorWidth, (railWidth - modeIconSize()) / 2);
    }

    public int iconY(int index) {
        return topInset + index * (modeIconSize() + iconGap);
    }

    public int ringSize() {
        int railWidth = Math.max(1, width() > 0 ? width() : Math.round(shellContext.leftRailWidth()));
        return Math.max(0, Math.min(ringMaxSize, railWidth - 4));
    }

    public int ringX() {
        return Math.max(0, (Math.max(1, width()) - ringSize()) / 2);
    }

    public int ringY() {
        int height = Math.max(0, height());
        return Math.max(0, height - ringSize() - ringInset);
    }

    public @Nullable WorkbenchMode modeAt(double localX, double localY) {
        if (shellContext.zenMode()) {
            return activeMode();
        }
        int iconSize = modeIconSize();
        for (int index = 0; index < modes.length; index++) {
            if (ChromeText.hit(localX, localY, iconX(), iconY(index), iconSize, iconSize)) {
                return modes[index];
            }
        }
        return null;
    }

    public boolean activateModeAt(double localX, double localY) {
        WorkbenchMode mode = modeAt(localX, localY);
        return mode != null && activateMode(mode);
    }

    public boolean activateMode(WorkbenchMode mode) {
        if (mode == null) {
            return false;
        }
        if (shellContext.mode() == mode) {
            return true;
        }
        shellContext.setMode(mode);
        return true;
    }

    public boolean activateVersionRing(double localX, double localY) {
        if (shellContext.zenMode() || ringSize() <= 0) {
            return false;
        }
        int size = ringSize();
        if (!ChromeText.hit(localX, localY, ringX(), ringY(), size, size)) {
            return false;
        }
        shellContext.requestReplan();
        return true;
    }

    public double ringFraction() {
        long version = Math.max(0L, shellContext.activeSnapshot().runtimeGraphVersion());
        if (version <= 0L) {
            return 0.0;
        }
        return Math.min(1.0, version / ringFullVersion);
    }

    public WorkbenchMode activeMode() {
        return shellContext.mode();
    }

    public VisualTexture modeIcon(WorkbenchMode mode) {
        return modeIcon(mode, shellContext.mode() == mode, false);
    }

    public VisualTexture modeIcon(WorkbenchMode mode, boolean active, boolean hovered) {
        var base = switch (Objects.requireNonNull(mode, "mode")) {
            case planner -> active
                    ? CirrusTextures.Icon.calculatorSelect
                    : hovered ? CirrusTextures.Icon.calculatorHover : CirrusTextures.Icon.calculatorUnselect;
            case facilities -> active
                    ? CirrusTextures.Icon.contextSelect
                    : hovered ? CirrusTextures.Icon.contextHover : CirrusTextures.Icon.contextUnselect;
            case database -> active
                    ? CirrusTextures.Icon.databaseSelect
                    : hovered ? CirrusTextures.Icon.databaseHover : CirrusTextures.Icon.databaseUnselect;
            case settings -> active
                    ? CirrusTextures.Icon.settingsSelect
                    : hovered ? CirrusTextures.Icon.settingsHover : CirrusTextures.Icon.settingsUnselect;
        };
        return CirrusThemeIcons.resolve(base, shellContext.theme());
    }

    public int iconTint(WorkbenchMode mode, boolean hovered) {
        if (shellContext.mode() == mode || hovered) {
            return 0xFFFFFFFF;
        }
        return OreUiPaint.isDark(shellContext.palette())
                ? OreUiPaint.withAlpha(0xFFFFFFFF, 0.88f)
                : 0xFFFFFFFF;
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        if (shellContext.zenMode()) {
            return OreUiRichTooltip.builder("Exit Zen Mode").badge("TAB", OreUiRichTooltip.Tone.costP)
                    .text("Click or press Tab to restore full layout.", OreUiRichTooltip.Tone.secondary).build();
        }
        WorkbenchMode hoveredMode = modeAt(localX, localY);
        if (hoveredMode != null) {
            return switch (hoveredMode) {
                case planner ->
                    OreUiRichTooltip.builder("Production Planner").badge("PLANNER", OreUiRichTooltip.Tone.accent)
                            .text(
                                "Spatial dependency graph, plan cards and decision co-pilot.",
                                OreUiRichTooltip.Tone.secondary
                            ).build();
                case facilities ->
                    OreUiRichTooltip.builder("Facilities & Rules").badge("RULES", OreUiRichTooltip.Tone.costK)
                            .text(
                                "Owned machines, progression and persistent world rules.",
                                OreUiRichTooltip.Tone.secondary
                            ).build();
                case database ->
                    OreUiRichTooltip.builder("Recipe Database").badge("EXPLORER", OreUiRichTooltip.Tone.positive)
                            .text(
                                "Browse recipes by mod/system and send targets directly to Planner.",
                                OreUiRichTooltip.Tone.secondary
                            ).build();
                case settings ->
                    OreUiRichTooltip.builder("Settings & Diagnostics").badge("CONFIG", OreUiRichTooltip.Tone.secondary)
                            .text(
                                "Theme, default layouts, solver budgets and graph stats.",
                                OreUiRichTooltip.Tone.secondary
                            ).build();
            };
        }
        return null;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        CirrusPalette palette = shellContext.palette();
        int iconSize = modeIconSize();
        if (iconSize <= 0) {
            return;
        }
        boolean dark = OreUiPaint.isDark(palette);
        if (shellContext.zenMode()) {
            int boxW = Math.max(12, width() - 2);
            int boxH = Math.max(12, height() - 2);
            boolean hover = ChromeText.hit(mouseX, mouseY, 0, 0, width(), height());
            if (dark) {
                OreUiPaint.raisedCard(canvas, 1, 1, boxW, boxH, palette, hover, true);
            } else {
                canvas.texture(
                    hover ? OreUiTextures.Light.railActiveHover : OreUiTextures.Light.railActive,
                    1,
                    1,
                    boxW,
                    boxH
                );
            }
            int drawSize = Math.min(14, Math.max(8, Math.min(boxW, boxH) - 8));
            int drawX = 1 + (boxW - drawSize) / 2;
            int drawY = 1 + Math.max(2, (boxH - 2 - drawSize) / 2);
            ChromeButtons.icon(canvas, modeIcon(activeMode(), true, hover), drawX, drawY, drawSize, 0xFFFFFFFF);
            return;
        }
        int tileW = Math.max(iconSize + 4, width() - 4);
        int tileH = iconSize + 6;
        int tileX = Math.max(0, (width() - tileW) / 2);
        int activeIndex = 0;
        for (int index = 0; index < modes.length; index++) {
            if (shellContext.mode() == modes[index]) {
                activeIndex = index;
                break;
            }
        }
        int targetActiveY = Math.max(2, iconY(activeIndex) - 3);
        int glideY = Math.round(activeTileY.update(targetActiveY, 20f));
        boolean activeHover = ChromeText.hit(mouseX, mouseY, tileX, targetActiveY, tileW, tileH);
        if (glideY + tileH <= height() + 4) {
            if (dark) {
                OreUiPaint.raisedCard(canvas, tileX, glideY, tileW, tileH, palette, activeHover, true);
            } else {
                canvas.texture(
                    activeHover ? OreUiTextures.Light.railActiveHover : OreUiTextures.Light.railActive,
                    tileX,
                    glideY,
                    tileW,
                    tileH
                );
            }
        }
        for (int index = 0; index < modes.length; index++) {
            WorkbenchMode mode = modes[index];
            int y = iconY(index);
            if (y + iconSize > height()) {
                break;
            }
            boolean active = shellContext.mode() == mode;
            int tileY = Math.max(2, y - 3);
            boolean hover = ChromeText.hit(mouseX, mouseY, tileX, tileY, tileW, tileH);
            if (!active && hover) {
                if (dark) {
                    OreUiPaint.raisedButton(canvas, tileX, tileY, tileW, tileH, palette, false, true, false, false);
                } else {
                    canvas.texture(OreUiTextures.Light.railHover, tileX, tileY, tileW, tileH);
                }
            }
            int drawIconSize = Math.min(16, iconSize);
            int drawX = tileX + Math.max(2, (tileW - drawIconSize) / 2);
            int drawY = tileY + Math.max(2, (tileH - 2 - drawIconSize) / 2);
            ChromeButtons
                    .icon(canvas, modeIcon(mode, active, hover), drawX, drawY, drawIconSize, iconTint(mode, hover));
        }
        renderVersionRing(canvas, palette);
    }

    private void renderVersionRing(SceneCanvas canvas, CirrusPalette palette) {
        int size = ringSize();
        if (size <= 0) {
            return;
        }
        int x = ringX();
        int y = ringY();
        double fraction = ringProgress.update((float) ringFraction(), 10f);
        float centerX = x + size / 2f;
        float centerY = y + size / 2f;
        float outerRadius = size / 2f;
        float innerRadius = Math.max(1f, outerRadius - 2f);
        float stroke = Math.max(1f, outerRadius * (float) (2.0 * Math.PI / RingWidget.segmentCount));
        for (int index = 0; index < RingWidget.segmentCount; index++) {
            double angle = Math.toRadians(RingWidget.segmentAngle(index, RingWidget.segmentCount, -90, 360));
            boolean lit = RingWidget.segmentLit(index, RingWidget.segmentCount, fraction);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            canvas.line(
                centerX + cos * innerRadius,
                centerY + sin * innerRadius,
                centerX + cos * outerRadius,
                centerY + sin * outerRadius,
                stroke,
                lit ? palette.focusRing() : palette.hairlineBorder()
            );
        }
    }

    private WorkbenchShellLayout shellLayout() {
        return WorkbenchShellLayout.compute(
            shellContext.shellWidth(),
            shellContext.shellHeight(),
            shellContext.breakpoint(),
            shellContext.zenMode(),
            shellContext.leftDockWidth(),
            shellContext.rightDockWidth()
        );
    }
}

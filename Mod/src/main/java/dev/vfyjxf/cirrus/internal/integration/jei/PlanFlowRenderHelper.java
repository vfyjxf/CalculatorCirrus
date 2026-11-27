package dev.vfyjxf.cirrus.internal.integration.jei;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.flow.DagEdgeRenderer;
import dev.vfyjxf.cirrus.ui.workbench.flow.DagHitTester;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisual;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisualRegistry;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Calculator-consistent OreUI drawing primitives for JEI Plan Flow tooltips and recipe layouts,
 * featuring GPU vector chamfered conduits, unified material slot pedestals, and dual-theme icon routing.
 */
final class PlanFlowRenderHelper {

    private PlanFlowRenderHelper() {}

    static WorkbenchThemeMode activeThemeMode() {
        try {
            WorkbenchThemeMode configured = ExecutionOverlayClientConfig.workbenchTheme();
            if (configured != null) {
                return configured;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Headless tests have no NeoForge config context.
        }
        return WorkbenchThemeMode.defaultMode();
    }

    static boolean reducedMotion() {
        try {
            return ExecutionOverlayClientConfig.workbenchReducedMotion();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    static CirrusPalette activePalette(WorkbenchThemeMode mode) {
        return CirrusPalette.of(mode);
    }

    static void drawHeaderBand(GuiGraphics g, int x, int y, int w, int h, CirrusPalette p) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, OreUiPaint.cardFooter(p));
        g.fill(x, y + h, x + w, y + h + 1, OreUiPaint.cardDivider(p));
    }

    static void drawFooterBand(GuiGraphics g, int x, int y, int w, int h, CirrusPalette p) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, OreUiPaint.cardDivider(p));
        if (h > 1) {
            g.fill(x, y + 1, x + w, y + h, OreUiPaint.cardFooter(p));
        }
    }

    static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    static void drawCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int arm, int color) {
        if (w < arm * 2 || h < arm * 2) {
            return;
        }
        g.fill(x, y, x + arm, y + 1, color);
        g.fill(x, y, x + 1, y + arm, color);
        g.fill(x + w - arm, y, x + w, y + 1, color);
        g.fill(x + w - 1, y, x + w, y + arm, color);
        g.fill(x, y + h - 1, x + arm, y + h, color);
        g.fill(x, y + h - arm, x + 1, y + h, color);
        g.fill(x + w - arm, y + h - 1, x + w, y + h, color);
        g.fill(x + w - 1, y + h - arm, x + w, y + h, color);
    }

    static void drawSelectedPlate(GuiGraphics g, int x, int y, int w, int h, CirrusPalette p, boolean hovered) {
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean dark = OreUiPaint.isDark(p);
        int accent = p.focusRing();
        int face = dark
                ? OreUiPaint.withAlpha(accent, hovered ? 0.28f : 0.20f)
                : OreUiPaint.withAlpha(accent, hovered ? 0.18f : 0.12f);
        int border = OreUiPaint.withAlpha(accent, dark ? 0.88f : 0.78f);
        int topSpec = OreUiPaint.lighten(accent, dark ? 0.42f : 0.30f);
        int ledge = OreUiPaint.withAlpha(accent, dark ? 0.38f : 0.26f);
        g.fill(x, y, x + w, y + h, p.cardSurface());
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        if (w > 2 && h > 3) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, OreUiPaint.withAlpha(topSpec, dark ? 0.45f : 0.55f));
            g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, ledge);
        }
        drawBorder(g, x, y, w, h, border);
    }

    static void drawTab(
        GuiGraphics g,
        Font font,
        int x,
        int y,
        int w,
        int h,
        ImageTexture icon,
        int iconW,
        int iconH,
        String label,
        boolean selected,
        boolean hovered,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        if (selected) {
            drawSelectedPlate(g, x, y, w, h, p, hovered);
            g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, p.focusRing());
        } else {
            VisualTexture bg = hovered
                    ? CirrusTextures.Workbench.buttonHover(theme)
                    : CirrusTextures.Workbench.buttonUp(theme);
            bg.render(g, x, y, w, h);
        }
        int contentInset = 5;
        int gap = 4;
        int textMax = Math.max(8, w - contentInset * 2 - iconW - gap);
        String fitted = fit(font, label, textMax);
        int totalW = iconW + gap + font.width(fitted);
        int startX = x + Math.max(contentInset, (w - totalW) / 2);
        // Raised OreUI buttons reserve 2px top border/highlight and 4px bottom highlight/ledge/border.
        int topInset = 2;
        int botInset = selected ? 2 : 4;
        int faceH = Math.max(1, h - topInset - botInset);
        int drawIconH = Math.min(iconH, faceH);
        int drawIconW = drawIconH == iconH ? iconW : Math.max(1, Math.round((float) iconW * drawIconH / Math.max(1, iconH)));
        int iconY = y + topInset + (faceH - drawIconH) / 2;
        int textY = y + topInset + (faceH - 8) / 2;
        CirrusThemeIcons.resolve(icon, theme).render(g, startX, iconY, drawIconW, drawIconH);
        int ink = selected ? p.textPrimary() : (hovered ? p.textPrimary() : p.textSecondary());
        g.drawString(font, fitted, startX + drawIconW + gap, textY, ink, false);
    }

    static void drawIconButton(
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        boolean hovered,
        boolean selected,
        boolean primary,
        VisualTexture icon,
        int iconW,
        int iconH,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        if (selected) {
            drawSelectedPlate(g, x, y, w, h, p, hovered);
        } else if (primary && hovered) {
            CirrusTextures.Workbench.ctaPrimary(theme).render(g, x, y, w, h);
        } else {
            VisualTexture bg = hovered
                    ? CirrusTextures.Workbench.buttonHover(theme)
                    : CirrusTextures.Workbench.buttonUp(theme);
            bg.render(g, x, y, w, h);
        }
        VisualTexture resolved = icon instanceof ImageTexture img ? CirrusThemeIcons.resolve(img, theme) : icon;
        int topInset = 2;
        int botInset = selected ? 2 : 4;
        int faceW = Math.max(1, w - 4);
        int faceH = Math.max(1, h - topInset - botInset);
        int drawW = Math.min(iconW, faceW);
        int drawH = Math.min(iconH, faceH);
        int ix = x + 2 + (faceW - drawW) / 2;
        int iy = y + topInset + (faceH - drawH) / 2;
        resolved.render(g, ix, iy, drawW, drawH);
    }

    static void drawPopoverFrame(
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int shadow = OreUiPaint.outerInk(p);
        g.fill(x + 2, y + 3, x + w + 3, y + h + 4, OreUiPaint.withAlpha(shadow, 0.24f));
        g.fill(x + 1, y + 2, x + w + 2, y + h + 3, OreUiPaint.withAlpha(shadow, 0.34f));
        CirrusTextures.Workbench.dockIsland(theme).render(g, x, y, w, h);
        drawBorder(g, x, y, w, h, shadow);
    }

    static void drawMenuTriggerButton(
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        boolean hovered,
        boolean open,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        if (open) {
            drawSelectedPlate(g, x, y, w, h, p, hovered);
        } else {
            VisualTexture bg = hovered
                    ? CirrusTextures.Workbench.buttonHover(theme)
                    : CirrusTextures.Workbench.buttonUp(theme);
            bg.render(g, x, y, w, h);
        }
        ImageTexture gear = (open || hovered) ? CirrusTextures.Icon.settingSelected : CirrusTextures.Icon.setting;
        ImageTexture caret = open
                ? CirrusTextures.Icon.arrowDownSelected
                : (hovered ? CirrusTextures.Icon.arrowUpSelected : CirrusTextures.Icon.arrowUp);
        int topInset = 2;
        int botInset = open ? 2 : 4;
        int faceH = Math.max(1, h - topInset - botInset);
        int gearDim = Math.min(10, faceH);
        int caretDim = Math.min(10, faceH);
        int gearY = y + topInset + (faceH - gearDim) / 2;
        int caretY = y + topInset + (faceH - caretDim) / 2;
        CirrusThemeIcons.resolve(gear, theme).render(g, x + 4, gearY, gearDim, gearDim);
        CirrusThemeIcons.resolve(caret, theme).render(g, x + w - 13, caretY, caretDim, caretDim);
    }

    static void drawCompactTextButton(
        GuiGraphics g,
        Font font,
        int x,
        int y,
        int w,
        int h,
        String label,
        @Nullable ImageTexture icon,
        int iconW,
        int iconH,
        boolean hovered,
        boolean active,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        if (active) {
            drawSelectedPlate(g, x, y, w, h, p, hovered);
        } else {
            VisualTexture bg = hovered
                    ? CirrusTextures.Workbench.buttonHover(theme)
                    : CirrusTextures.Workbench.buttonUp(theme);
            bg.render(g, x, y, w, h);
        }
        int topInset = 2;
        int botInset = active ? 2 : 4;
        int faceH = Math.max(1, h - topInset - botInset);
        int textY = y + topInset + Math.max(0, (faceH - 8) / 2);
        int ink = active ? p.focusRing() : (hovered ? p.textPrimary() : p.textSecondary());
        if (icon != null && iconW > 0) {
            int gap = 2;
            int drawIconH = Math.min(iconH, faceH);
            int drawIconW = drawIconH == iconH ? iconW : Math.max(1, Math.round((float) iconW * drawIconH / Math.max(1, iconH)));
            String fitted = fit(font, label, Math.max(6, w - drawIconW - gap - 4));
            int totalW = drawIconW + gap + font.width(fitted);
            int sx = x + Math.max(2, (w - totalW) / 2);
            int iy = y + topInset + Math.max(0, (faceH - drawIconH) / 2);
            CirrusThemeIcons.resolve(icon, theme).render(g, sx, iy, drawIconW, drawIconH);
            g.drawString(font, fitted, sx + drawIconW + gap, textY, ink, false);
        } else {
            String fitted = fit(font, label, Math.max(6, w - 4));
            g.drawString(font, fitted, x + Math.max(2, (w - font.width(fitted)) / 2), textY, ink, false);
        }
    }

    static void drawFlatMeter(
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        double fraction,
        int fillColor,
        CirrusPalette p
    ) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, OreUiPaint.meterBorder(p));
        if (w <= 2 || h <= 2) {
            return;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, OreUiPaint.meterTrack(p));
        double clamped = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
        int fillW = (int) Math.round((w - 2) * clamped);
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, fillColor);
            if (h >= 5) {
                g.fill(x + 1, y + 1, x + 1 + fillW, y + 2, OreUiPaint.lighten(fillColor, 0.22f));
            }
        }
    }

    static int measureTagPillWidth(Font font, String label) {
        return font.width(label) + 8;
    }

    static int drawTagPill(GuiGraphics g, Font font, int x, int y, int h, String label, int inkColor, CirrusPalette p) {
        int pillH = Math.max(11, h);
        int width = font.width(label) + 8;
        boolean dark = OreUiPaint.isDark(p);
        int fill = OreUiPaint.withAlpha(inkColor, dark ? 0.22f : 0.13f);
        int border = OreUiPaint.withAlpha(inkColor, dark ? 0.62f : 0.45f);
        g.fill(x, y, x + width, y + pillH, fill);
        drawBorder(g, x, y, width, pillH, border);
        g.drawString(font, label, x + 4, y + Math.max(1, (pillH - 8) / 2), inkColor, false);
        return width;
    }

    static void drawOreSlot(
        GuiGraphics g,
        int x,
        int y,
        int size,
        boolean hovered,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        CirrusTextures.Workbench.slotBase(theme).render(g, x, y, size, size);
        if (hovered) {
            g.fill(x + 1, y + 1, x + size - 1, y + size - 1, OreUiPaint.withAlpha(p.focusRing(), 0.18f));
            drawBorder(g, x, y, size, size, p.focusRing());
        }
    }

    /**
     * Proportional expected-vs-missing split progress bar: green (have) on the left, red (missing) on the right.
     */
    static void drawSplitProgressBar(
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        double haveRatio,
        CirrusPalette p
    ) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, OreUiPaint.meterBorder(p));
        if (w <= 2 || h <= 2) {
            return;
        }
        int ix = x + 1;
        int iy = y + 1;
        int iw = w - 2;
        int ih = h - 2;
        double clamped = Double.isFinite(haveRatio) ? Math.max(0.0, Math.min(1.0, haveRatio)) : 0.0;
        int haveW = (int) Math.round(iw * clamped);
        int haveColor = p.costS();
        int missingColor = p.ruleHardBan();
        if (haveW > 0) {
            g.fill(ix, iy, ix + haveW, iy + ih, haveColor);
            if (ih >= 2) {
                g.fill(ix, iy, ix + haveW, iy + 1, OreUiPaint.lighten(haveColor, 0.22f));
            }
        }
        if (haveW < iw) {
            g.fill(ix + haveW, iy, ix + iw, iy + ih, missingColor);
            if (ih >= 2) {
                g.fill(ix + haveW, iy, ix + iw, iy + 1, OreUiPaint.lighten(missingColor, 0.16f));
            }
        }
    }

    /**
     * Compact material slot: recessed OreUI slot with 16x16 item, bottom-right expected count,
     * top-right missing count (red when short), and flush proportional have-vs-missing bar underneath.
     */
    static void drawCompactMaterialSlot(
        GuiGraphics g,
        Font font,
        int x,
        int y,
        int w,
        int h,
        @Nullable ResourceKey resource,
        String expectedText,
        String missingText,
        double haveRatio,
        boolean ready,
        boolean hovered,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        int barH = 4;
        int slotH = Math.max(18, h - barH);
        CirrusTextures.Workbench.slotBase(theme).render(g, x, y, w, slotH);
        if (hovered) {
            g.fill(x + 1, y + 1, x + w - 1, y + slotH - 1, OreUiPaint.withAlpha(p.focusRing(), 0.18f));
            drawBorder(g, x, y, w, slotH, p.focusRing());
        }

        int itemSize = 16;
        int itemX = x + (w - itemSize) / 2;
        int itemY = y + Math.max(1, (slotH - itemSize) / 2);
        renderContent(g, resource, itemX, itemY, itemSize, p, theme);

        boolean dark = OreUiPaint.isDark(p);
        int expectedInk = ready ? OreUiPaint.lighten(p.costS(), dark ? 0.22f : 0.10f) : p.textOnAccent();
        drawSlotCornerLabel(g, font, x, y, w, slotH, expectedText, false, expectedInk, p, 215f);
        if (!ready && !missingText.isEmpty()) {
            int missingInk = OreUiPaint.lighten(p.ruleHardBan(), dark ? 0.34f : 0.22f);
            drawSlotCornerLabel(g, font, x, y, w, slotH, missingText, true, missingInk, p, 217f);
        }

        drawSplitProgressBar(g, x, y + slotH, w, barH, haveRatio, p);
    }

    /**
     * Compact step progress slot matching the material slot geometry in the Steps filter mode.
     */
    static void drawCompactStepSlot(
        GuiGraphics g,
        Font font,
        int x,
        int y,
        int w,
        int h,
        @Nullable ResourceKey resource,
        String amountText,
        int accent,
        boolean done,
        boolean hovered,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        int barH = 4;
        int slotH = Math.max(18, h - barH);
        CirrusTextures.Workbench.slotBase(theme).render(g, x, y, w, slotH);
        if (done) {
            g.fill(x + 1, y + 1, x + w - 1, y + slotH - 1, OreUiPaint.withAlpha(p.costS(), 0.14f));
        }
        if (hovered) {
            drawBorder(g, x, y, w, slotH, p.focusRing());
        } else if (done) {
            drawBorder(g, x, y, w, slotH, OreUiPaint.withAlpha(p.costS(), 0.72f));
        }

        int itemSize = 16;
        renderContent(g, resource, x + (w - itemSize) / 2, y + Math.max(1, (slotH - itemSize) / 2), itemSize, p, theme);
        drawSmallCheckbox(g, x + 1, y + 1, done, hovered, 220f);
        drawSlotCornerLabel(g, font, x, y, w, slotH, amountText, false, done ? p.costS() : p.textOnAccent(), p, 215f);
        drawFlatMeter(g, x, y + slotH, w, barH, done ? 1.0 : 0.25, done ? p.costS() : accent, p);
    }

    private static void drawSlotCornerLabel(
        GuiGraphics g,
        Font font,
        int slotX,
        int slotY,
        int slotW,
        int slotH,
        String text,
        boolean topRight,
        int ink,
        CirrusPalette p,
        float zOffset
    ) {
        if (text.isEmpty() || slotW < 14 || slotH < 14) {
            return;
        }
        int rawW = Math.max(1, font.width(text));
        float maxW = Math.max(8f, slotW - 3f);
        boolean compact = slotH <= 19;
        float preferred = compact ? (topRight ? 0.60f : 0.68f) : (topRight ? 0.66f : 0.72f);
        float scale = Math.max(0.46f, Math.min(preferred, maxW / rawW));
        float scaledW = rawW * scale;
        float scaledH = 8f * scale;
        float marginY = compact ? 1.0f : 1.5f;
        float tx = slotX + slotW - 1.5f - scaledW;
        float ty = topRight ? (slotY + marginY) : (slotY + slotH - marginY - scaledH);

        int bgX = Math.max(slotX + 1, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(slotY + 1, (int) Math.floor(ty - 0.5f));
        int bgRight = Math.min(slotX + slotW - 1, (int) Math.ceil(tx + scaledW + 1.0f));
        int bgBot = Math.min(slotY + slotH - 1, (int) Math.ceil(ty + scaledH + 0.5f));

        g.pose().pushPose();
        g.pose().translate(0f, 0f, zOffset);
        if (bgRight > bgX && bgBot > bgY) {
            g.fill(bgX, bgY, bgRight, bgBot, OreUiPaint.withAlpha(OreUiPaint.outerInk(p), 0.72f));
        }
        g.pose().translate(tx, ty, 1f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, ink, true);
        g.pose().popPose();
    }

    /**
     * Sleek tapered OreUI crafting recipe arrow matching OreUiRecipeGrid.drawRecipeArrow.
     */
    static void drawRecipeArrow(GuiGraphics g, int x0, int x1, int midY, CirrusPalette p, int accent, boolean accented) {
        int span = x1 - x0;
        if (span < 6) {
            return;
        }
        boolean dark = OreUiPaint.isDark(p);
        int fill = accented
                ? accent
                : (dark ? OreUiPaint.lighten(p.textMuted(), 0.26f) : p.textSecondary());
        int shadow = dark
                ? OreUiPaint.withAlpha(OreUiPaint.outerInk(p), 0.72f)
                : OreUiPaint.withAlpha(p.hairlineBorder(), 0.50f);
        int headLen = Math.min(4, Math.max(3, span / 3));
        int stemEnd = Math.max(x0 + 2, x1 - headLen);
        g.fill(x0, midY + 1, stemEnd, midY + 2, shadow);
        g.fill(x0, midY - 1, stemEnd, midY + 1, fill);
        for (int i = 0; i < headLen; i++) {
            int colX = x1 - 1 - i;
            int halfH = i + 1;
            if (colX < x0) {
                break;
            }
            g.fill(colX, midY + halfH, colX + 1, midY + halfH + 1, shadow);
            g.fill(colX, midY - halfH, colX + 1, midY + halfH, fill);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void renderContent(
        GuiGraphics g,
        @Nullable ResourceKey resource,
        int x,
        int y,
        int size,
        CirrusPalette p,
        WorkbenchThemeMode theme
    ) {
        if (resource == null || size <= 0) {
            return;
        }
        ContentStack<?, ?> stack = JeiPlanBookmarkBridge.resolveDisplayContent(resource);
        if (stack != null && !stack.isEmpty()) {
            try {
                StackRenderer renderer = StackRenderer.getStackRenderer(stack.type());
                renderer.renderAt(g, stack, x, y, size, size, 0f);
                return;
            } catch (RuntimeException | LinkageError ignored) {
                // Fall back to direct item or emblem rendering below.
            }
        }
        if (ValueTypeVisualRegistry.isValueResourceKey(resource)) {
            drawValueEmblem(g, resource.contentTypeId(), x, y, size, p, theme);
            return;
        }
        var fallback = JeiPlanBookmarkBridge.resolveDisplayStack(resource);
        if (size == 16) {
            g.renderFakeItem(fallback, x, y);
        } else {
            g.pose().pushPose();
            g.pose().translate(x, y, 0f);
            float scale = size / 16.0f;
            g.pose().scale(scale, scale, 1f);
            g.renderFakeItem(fallback, 0, 0);
            g.pose().popPose();
        }
    }

    static void drawSlotContent(
        GuiGraphics g,
        @Nullable ResourceKey resource,
        int slotX,
        int slotY,
        int slotSize,
        boolean hovered,
        WorkbenchThemeMode theme,
        CirrusPalette p
    ) {
        drawOreSlot(g, slotX, slotY, slotSize, hovered, theme, p);
        int itemSize = slotSize >= 20 ? 16 : Math.max(8, slotSize - 2);
        int ox = slotX + (slotSize - itemSize) / 2;
        int oy = slotY + (slotSize - itemSize) / 2;
        renderContent(g, resource, ox, oy, itemSize, p, theme);
    }

    static void drawSlotCount(
        GuiGraphics g,
        Font font,
        int slotX,
        int slotY,
        int slotSize,
        long amount,
        boolean showSingle,
        CirrusPalette p,
        float zOffset
    ) {
        drawSlotCount(g, font, slotX, slotY, slotSize, slotSize, amount, showSingle, p, zOffset);
    }

    static void drawSlotCount(
        GuiGraphics g,
        Font font,
        int slotX,
        int slotY,
        int slotW,
        int slotH,
        long amount,
        boolean showSingle,
        CirrusPalette p,
        float zOffset
    ) {
        String text = amount <= 1L ? (showSingle && amount == 1L ? "1" : "") : OreUiSlotAmount.format(amount);
        if (text.isEmpty() || slotW < 12 || slotH < 12) {
            return;
        }
        int rawW = Math.max(1, font.width(text));
        int rawH = 8;
        float preferred = preferredCountScale(Math.min(slotW, slotH), text.length());
        float maxW = Math.max(6f, slotW - 2.5f);
        float scale = Math.max(0.48f, Math.min(preferred, maxW / rawW));
        float scaledW = rawW * scale;
        float scaledH = rawH * scale;
        float tx = slotX + slotW - 1.5f - scaledW;
        float ty = slotY + slotH - 1.5f - scaledH;

        int bgX = Math.max(slotX + 1, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(slotY + 1, (int) Math.floor(ty - 0.5f));
        int bgRight = Math.min(slotX + slotW - 1, (int) Math.ceil(tx + scaledW + 1.0f));
        int bgBot = Math.min(slotY + slotH - 1, (int) Math.ceil(ty + scaledH + 1.0f));

        g.pose().pushPose();
        g.pose().translate(0f, 0f, zOffset);
        if (bgRight > bgX && bgBot > bgY) {
            g.fill(bgX, bgY, bgRight, bgBot, OreUiPaint.withAlpha(OreUiPaint.outerInk(p), 0.68f));
        }
        g.pose().translate(tx, ty, 1f);
        g.pose().scale(scale, scale, 1f);
        int ink = tierCountColor(amount, p);
        g.drawString(font, text, 0, 0, ink, true);
        g.pose().popPose();
    }

    static void drawSmallCheckbox(GuiGraphics g, int x, int y, boolean checked, boolean hovered, float zOffset) {
        ImageTexture tex = checked
                ? (hovered ? CirrusTextures.Toggle.Checked.Small.selectHover : CirrusTextures.Toggle.Checked.Small.select)
                : (hovered
                        ? CirrusTextures.Toggle.Checked.Small.unselectHover
                        : CirrusTextures.Toggle.Checked.Small.unselect);
        g.pose().pushPose();
        g.pose().translate(0f, 0f, zOffset);
        tex.render(g, x, y, 7, 7);
        g.pose().popPose();
    }

    static void drawRegularCheckbox(GuiGraphics g, int x, int y, boolean checked, boolean hovered) {
        ImageTexture tex = checked
                ? (hovered ? CirrusTextures.Toggle.Checked.selectHover : CirrusTextures.Toggle.Checked.select)
                : (hovered ? CirrusTextures.Toggle.Checked.unselectHover : CirrusTextures.Toggle.Checked.unselect);
        tex.render(g, x, y, 10, 10);
    }

    static void drawOreScrollbar(
        GuiGraphics g,
        int x,
        int y,
        int w,
        int h,
        int scrollPx,
        int maxScroll,
        WorkbenchThemeMode theme
    ) {
        CirrusTextures.Workbench.scrollTrack(theme).render(g, x, y, w, h);
        if (maxScroll <= 0 || h < 12) {
            return;
        }
        int thumbH = Math.max(10, Math.round((float) h * h / (h + maxScroll)));
        int thumbY = y + Math.round((float) (h - thumbH) * scrollPx / maxScroll);
        CirrusTextures.Workbench.scrollThumb(theme).render(g, x, thumbY, w, thumbH);
    }

    /**
     * Multi-layer GPU vector DAG conduit matching Calculator Workbench's DagEdgeRenderer:
     * 45-degree chamfered PCB route, contrast casing, resource-tinted body, bright metallic specular filament,
     * animated flow pulse / active directional chevrons, beveled source socket, and vector arrowhead terminal.
     */
    static void drawDagConduit(
        GuiGraphics g,
        PlanFlowViewModel.DagEdgeLayout edge,
        boolean vertical,
        int coreColor,
        boolean highlighted,
        boolean dimmed,
        long timeMs,
        boolean reducedMotion,
        CirrusPalette p
    ) {
        float[] pts = conduitPoints(edge, vertical);
        if (pts.length < 4) {
            return;
        }
        boolean dark = OreUiPaint.isDark(p);
        float dim = dimmed ? 0.16f : 1.0f;
        float thickness = highlighted ? 2.5f : 1.9f;
        int fadedCore = OreUiPaint.withAlpha(coreColor, dim);
        int casing = OreUiPaint.withAlpha(dark ? OreUiPaint.outerInk(p) : p.cardSurface(), dim * (dark ? 0.94f : 0.95f));
        int specular = OreUiPaint.withAlpha(
            OreUiPaint.lighten(coreColor, dark ? 0.48f : 0.34f),
            dim * (highlighted ? 0.95f : 0.62f)
        );

        float trim = edge.kind() == PlanFlowViewModel.EdgeKind.machine ? 4.2f : 6.4f;
        float[] bodyPts = trimEnd(pts, trim);

        // Soft glowing aura on highlighted / traced conduits.
        if (highlighted) {
            float pulse = 0.84f + 0.16f * OreUiMotion.pulse01(timeMs, 1700L, reducedMotion);
            int glow = OreUiPaint.withAlpha(coreColor, dim * 0.28f * pulse);
            drawRibbon(g, bodyPts, thickness + 4.2f, glow);
        }

        if (edge.kind() == PlanFlowViewModel.EdgeKind.machine) {
            double dashPhase = (!reducedMotion && highlighted) ? (Math.floorMod(timeMs, 1100L) / 1100.0) * 9.0 : 0.0;
            drawDashedRibbon(g, bodyPts, thickness + 1.8f, casing, 5.4f, 3.6f, dashPhase);
            drawDashedRibbon(g, bodyPts, thickness, fadedCore, 4.8f, 4.2f, dashPhase);
        } else if (edge.kind() == PlanFlowViewModel.EdgeKind.autoLine) {
            float[] railA = offsetPolyline(bodyPts, -1.25f);
            float[] railB = offsetPolyline(bodyPts, 1.25f);
            drawRibbon(g, bodyPts, thickness + 3.0f, casing);
            drawRibbon(g, railA, 1.05f, fadedCore);
            drawRibbon(g, railB, 1.05f, specular);
        } else {
            drawRibbon(g, bodyPts, thickness + 2.0f, casing);
            drawRibbon(g, bodyPts, thickness, fadedCore);
            drawRibbon(g, bodyPts, Math.max(0.65f, thickness * 0.34f), specular);
        }

        // Animated energy flow chevrons on traced paths; gentle ambient packet on idle conduits.
        if (highlighted && !reducedMotion) {
            drawFlowChevrons(g, bodyPts, specular, dim, timeMs);
        } else if (!reducedMotion && !dimmed) {
            drawAmbientPulse(g, bodyPts, specular, dim, timeMs, edge.from().step().stepId().hashCode());
        }

        drawSourceSocket(g, pts[0], pts[1], edge.kind(), casing, fadedCore, specular);
        drawTargetTerminal(g, pts, edge.kind(), casing, fadedCore, specular, highlighted);
    }

    private static float[] conduitPoints(PlanFlowViewModel.DagEdgeLayout edge, boolean vertical) {
        float x0 = edge.x0();
        float y0 = edge.y0();
        float x1 = edge.x1();
        float y1 = edge.y1();
        float ratio = Math.max(0.18f, Math.min(0.82f, edge.channelRatio()));
        if (!vertical) {
            if (Math.abs(y1 - y0) < 1.2f && x1 > x0 + 4f) {
                return new float[]{x0, y0, x1, y1};
            }
            if (x1 > x0 + 8f) {
                float spanX = x1 - x0;
                float midX = spanX > 150f
                        ? x1 - (10f + (1f - ratio) * 20f)
                        : x0 + spanX * ratio;
                return DagHitTester.chamferPolyline(new float[]{x0, y0, midX, y0, midX, y1, x1, y1}, 6.5f);
            }
            float dropY = Math.max(edge.from().worldY() + edge.from().h(), edge.to().worldY() + edge.to().h()) + 8f;
            return DagHitTester.chamferPolyline(
                new float[]{x0, y0, x0 + 7f, y0, x0 + 7f, dropY, x1 - 7f, dropY, x1 - 7f, y1, x1, y1},
                5.0f
            );
        }
        if (Math.abs(x1 - x0) < 1.2f && y1 > y0 + 4f) {
            return new float[]{x0, y0, x1, y1};
        }
        float spanY = y1 - y0;
        float midY = Math.abs(spanY) > 100f
                ? y1 - Math.signum(spanY) * (9f + (1f - ratio) * 16f)
                : y0 + spanY * ratio;
        return DagHitTester.chamferPolyline(new float[]{x0, y0, x0, midY, x1, midY, x1, y1}, 6.5f);
    }

    private static void drawSourceSocket(
        GuiGraphics g,
        float x,
        float y,
        PlanFlowViewModel.EdgeKind kind,
        int casing,
        int core,
        int specular
    ) {
        if (kind == PlanFlowViewModel.EdgeKind.machine) {
            drawDiamond(g, x, y, 3.2f, casing);
            drawDiamond(g, x, y, 2.0f, core);
            return;
        }
        drawQuad(g, x - 2.6f, y - 2.6f, x + 2.6f, y - 2.6f, x + 2.6f, y + 2.6f, x - 2.6f, y + 2.6f, casing);
        drawQuad(g, x - 1.6f, y - 1.6f, x + 1.6f, y - 1.6f, x + 1.6f, y + 1.6f, x - 1.6f, y + 1.6f, core);
        drawQuad(g, x - 0.7f, y - 0.7f, x + 0.7f, y - 0.7f, x + 0.7f, y + 0.7f, x - 0.7f, y + 0.7f, specular);
    }

    private static void drawTargetTerminal(
        GuiGraphics g,
        float[] pts,
        PlanFlowViewModel.EdgeKind kind,
        int casing,
        int core,
        int specular,
        boolean highlighted
    ) {
        int count = pts.length / 2;
        if (count < 2) {
            return;
        }
        float tipX = pts[(count - 1) * 2];
        float tipY = pts[(count - 1) * 2 + 1];
        float fromX = tipX;
        float fromY = tipY;
        for (int i = count - 2; i >= 0; i--) {
            fromX = pts[i * 2];
            fromY = pts[i * 2 + 1];
            if (Math.hypot(tipX - fromX, tipY - fromY) >= 0.4f) {
                break;
            }
        }
        float dx = tipX - fromX;
        float dy = tipY - fromY;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.25f) {
            return;
        }
        float ux = dx / dist;
        float uy = dy / dist;
        float dockX = tipX - ux * 0.8f;
        float dockY = tipY - uy * 0.8f;

        if (kind == PlanFlowViewModel.EdgeKind.machine) {
            float cx = dockX - ux * 2.0f;
            float cy = dockY - uy * 2.0f;
            drawDiamond(g, cx, cy, highlighted ? 4.2f : 3.6f, casing);
            drawDiamond(g, cx, cy, highlighted ? 2.9f : 2.5f, core);
            return;
        }

        float len = highlighted ? 7.6f : 6.6f;
        float halfBase = highlighted ? 3.5f : 3.0f;
        drawTriangleArrow(g, fromX, fromY, dockX + ux * 0.7f, dockY + uy * 0.7f, len + 1.5f, halfBase + 0.9f, casing);
        drawTriangleArrow(g, fromX, fromY, dockX, dockY, len, halfBase, core);
        drawTriangleArrow(g, fromX, fromY, dockX - ux * 0.8f, dockY - uy * 0.8f, len * 0.46f, halfBase * 0.36f, specular);
    }

    private static void drawAmbientPulse(GuiGraphics g, float[] pts, int color, float dim, long timeMs, int seed) {
        float length = polylineLength(pts);
        if (length < 22f) {
            return;
        }
        float phase = (Math.abs(seed) % 19) / 19f;
        float t = OreUiMotion.cycle01(timeMs, 2400L, phase, false);
        float trail = Math.max(0f, t - Math.min(0.18f, 10f / length));
        float[] a = samplePolyline(pts, trail);
        float[] b = samplePolyline(pts, t);
        float edgeFade = Math.min(1f, Math.min(t * 6f, (1f - t) * 6f));
        int pulseInk = OreUiPaint.withAlpha(color, dim * 0.52f * edgeFade);
        drawRibbon(g, new float[]{a[0], a[1], b[0], b[1]}, 2.1f, pulseInk);
    }

    private static void drawFlowChevrons(GuiGraphics g, float[] pts, int color, float dim, long timeMs) {
        float length = polylineLength(pts);
        if (length < 16f) {
            return;
        }
        int count = Math.max(1, Math.min(4, WorkbenchPaint.flowParticleCount(length)));
        int ink = OreUiPaint.withAlpha(color, dim * 0.95f);
        int trailInk = OreUiPaint.withAlpha(color, dim * 0.40f);
        for (int i = 0; i < count; i++) {
            double t = WorkbenchPaint.flowParticleT(timeMs, length, i);
            double tBack = Math.max(0.0, t - Math.min(0.10, 3.8 / length));
            double tTail = Math.max(0.0, t - Math.min(0.20, 10.0 / length));
            float[] head = samplePolyline(pts, t);
            float[] tail = samplePolyline(pts, tBack);
            float[] wake = samplePolyline(pts, tTail);
            drawRibbon(g, new float[]{wake[0], wake[1], head[0], head[1]}, 2.2f, trailInk);
            drawTriangleArrow(g, tail[0], tail[1], head[0], head[1], 3.8f, 1.8f, ink);
        }
    }

    private static void drawRibbon(GuiGraphics g, float[] pts, float thickness, int color) {
        int count = pts.length / 2;
        if (count < 2 || thickness <= 0.05f || ((color >>> 24) & 0xFF) == 0) {
            return;
        }
        float half = thickness * 0.5f;
        float[] leftX = new float[count];
        float[] leftY = new float[count];
        float[] rightX = new float[count];
        float[] rightY = new float[count];
        for (int i = 0; i < count; i++) {
            float px = pts[i * 2];
            float py = pts[i * 2 + 1];
            float[] nPrev = i > 0 ? segmentNormal(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], px, py) : null;
            float[] nNext = i + 1 < count ? segmentNormal(px, py, pts[(i + 1) * 2], pts[(i + 1) * 2 + 1]) : null;
            float nx;
            float ny;
            float scale = 1.0f;
            if (nPrev != null && nNext != null) {
                float mx = nPrev[0] + nNext[0];
                float my = nPrev[1] + nNext[1];
                float mLen = (float) Math.hypot(mx, my);
                if (mLen > 1e-4f) {
                    nx = mx / mLen;
                    ny = my / mLen;
                    float dot = nx * nNext[0] + ny * nNext[1];
                    scale = dot > 0.35f ? Math.min(1.65f, 1.0f / dot) : 1.0f;
                } else {
                    nx = nNext[0];
                    ny = nNext[1];
                }
            } else if (nNext != null) {
                nx = nNext[0];
                ny = nNext[1];
            } else if (nPrev != null) {
                nx = nPrev[0];
                ny = nPrev[1];
            } else {
                nx = 0f;
                ny = 1f;
            }
            float offset = half * scale;
            leftX[i] = px + nx * offset;
            leftY[i] = py + ny * offset;
            rightX[i] = px - nx * offset;
            rightY[i] = py - ny * offset;
        }
        for (int i = 0; i < count - 1; i++) {
            drawQuad(g, leftX[i], leftY[i], leftX[i + 1], leftY[i + 1], rightX[i + 1], rightY[i + 1], rightX[i], rightY[i], color);
        }
    }

    private static void drawDashedRibbon(
        GuiGraphics g,
        float[] pts,
        float thickness,
        int color,
        float dash,
        float gap,
        double phaseOffset
    ) {
        double total = polylineLength(pts);
        if (total <= 1e-3) {
            return;
        }
        double period = Math.max(2.0, dash + gap);
        double offset = phaseOffset % period;
        if (offset < 0.0) {
            offset += period;
        }
        for (double d = offset - period; d < total; d += period) {
            double s0 = Math.max(0.0, d);
            double s1 = Math.min(total, d + dash);
            if (s1 - s0 <= 0.4) {
                continue;
            }
            float[] a = samplePolyline(pts, s0 / total);
            float[] b = samplePolyline(pts, s1 / total);
            drawRibbon(g, new float[]{a[0], a[1], b[0], b[1]}, thickness, color);
        }
    }

    private static void drawTriangleArrow(
        GuiGraphics g,
        float fromX,
        float fromY,
        float tipX,
        float tipY,
        float arrowLen,
        float halfBase,
        int color
    ) {
        float dx = tipX - fromX;
        float dy = tipY - fromY;
        float length = (float) Math.hypot(dx, dy);
        if (length < 0.25f) {
            return;
        }
        float ux = dx / length;
        float uy = dy / length;
        float nx = -uy;
        float ny = ux;
        float baseX = tipX - ux * arrowLen;
        float baseY = tipY - uy * arrowLen;
        float leftX = baseX + nx * halfBase;
        float leftY = baseY + ny * halfBase;
        float rightX = baseX - nx * halfBase;
        float rightY = baseY - ny * halfBase;
        drawQuad(g, tipX, tipY, leftX, leftY, rightX, rightY, tipX, tipY, color);
    }

    private static void drawDiamond(GuiGraphics g, float cx, float cy, float r, int color) {
        drawQuad(g, cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy, color);
    }

    private static void drawQuad(
        GuiGraphics g,
        float x0,
        float y0,
        float x1,
        float y1,
        float x2,
        float y2,
        float x3,
        float y3,
        int color
    ) {
        if (((color >>> 24) & 0xFF) == 0) {
            return;
        }
        Matrix4f mat = g.pose().last().pose();
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        float shoelace = x0 * y1 - x1 * y0 + x1 * y2 - x2 * y1 + x2 * y3 - x3 * y2 + x3 * y0 - x0 * y3;
        if (shoelace > 0f) {
            vc.addVertex(mat, x0, y0, 0f).setColor(color);
            vc.addVertex(mat, x3, y3, 0f).setColor(color);
            vc.addVertex(mat, x2, y2, 0f).setColor(color);
            vc.addVertex(mat, x1, y1, 0f).setColor(color);
        } else {
            vc.addVertex(mat, x0, y0, 0f).setColor(color);
            vc.addVertex(mat, x1, y1, 0f).setColor(color);
            vc.addVertex(mat, x2, y2, 0f).setColor(color);
            vc.addVertex(mat, x3, y3, 0f).setColor(color);
        }
    }

    private static float[] offsetPolyline(float[] pts, float offset) {
        int count = pts.length / 2;
        float[] out = new float[pts.length];
        for (int i = 0; i < count; i++) {
            float px = pts[i * 2];
            float py = pts[i * 2 + 1];
            float[] nPrev = i > 0 ? segmentNormal(pts[(i - 1) * 2], pts[(i - 1) * 2 + 1], px, py) : null;
            float[] nNext = i + 1 < count ? segmentNormal(px, py, pts[(i + 1) * 2], pts[(i + 1) * 2 + 1]) : null;
            float nx;
            float ny;
            if (nPrev != null && nNext != null) {
                float mx = nPrev[0] + nNext[0];
                float my = nPrev[1] + nNext[1];
                float mLen = (float) Math.hypot(mx, my);
                nx = mLen > 1e-4f ? mx / mLen : nNext[0];
                ny = mLen > 1e-4f ? my / mLen : nNext[1];
            } else if (nNext != null) {
                nx = nNext[0];
                ny = nNext[1];
            } else if (nPrev != null) {
                nx = nPrev[0];
                ny = nPrev[1];
            } else {
                nx = 0f;
                ny = 1f;
            }
            out[i * 2] = px + nx * offset;
            out[i * 2 + 1] = py + ny * offset;
        }
        return out;
    }

    private static float @Nullable [] segmentNormal(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            return null;
        }
        return new float[]{-dy / len, dx / len};
    }

    private static float[] trimEnd(float[] pts, float trimDistance) {
        int count = pts.length / 2;
        if (count < 2 || trimDistance <= 0.1f) {
            return pts;
        }
        double total = polylineLength(pts);
        if (total <= trimDistance + 2.0) {
            return pts;
        }
        double remaining = trimDistance;
        int endIndex = count - 1;
        while (endIndex > 0) {
            float ax = pts[(endIndex - 1) * 2];
            float ay = pts[(endIndex - 1) * 2 + 1];
            float bx = pts[endIndex * 2];
            float by = pts[endIndex * 2 + 1];
            double seg = Math.hypot(bx - ax, by - ay);
            if (seg >= remaining && seg > 1e-4) {
                double ratio = (seg - remaining) / seg;
                float[] trimmed = new float[(endIndex + 1) * 2];
                System.arraycopy(pts, 0, trimmed, 0, endIndex * 2);
                trimmed[endIndex * 2] = (float) (ax + (bx - ax) * ratio);
                trimmed[endIndex * 2 + 1] = (float) (ay + (by - ay) * ratio);
                return trimmed;
            }
            remaining -= seg;
            endIndex--;
        }
        return pts;
    }

    private static float polylineLength(float[] pts) {
        int count = pts.length / 2;
        float total = 0f;
        for (int i = 0; i < count - 1; i++) {
            total += (float) Math.hypot(pts[(i + 1) * 2] - pts[i * 2], pts[(i + 1) * 2 + 1] - pts[i * 2 + 1]);
        }
        return total;
    }

    private static float[] samplePolyline(float[] pts, double t) {
        int count = pts.length / 2;
        if (count <= 1) {
            return count == 1 ? new float[]{pts[0], pts[1]} : new float[]{0f, 0f};
        }
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double total = polylineLength(pts);
        if (total <= 1e-4) {
            return new float[]{pts[0], pts[1]};
        }
        double target = total * clamped;
        double acc = 0.0;
        for (int i = 0; i < count - 1; i++) {
            float ax = pts[i * 2];
            float ay = pts[i * 2 + 1];
            float bx = pts[(i + 1) * 2];
            float by = pts[(i + 1) * 2 + 1];
            double seg = Math.hypot(bx - ax, by - ay);
            if (acc + seg >= target && seg > 1e-4) {
                double local = (target - acc) / seg;
                return new float[]{(float) (ax + (bx - ax) * local), (float) (ay + (by - ay) * local)};
            }
            acc += seg;
        }
        return new float[]{pts[(count - 1) * 2], pts[(count - 1) * 2 + 1]};
    }

    static int conduitColorFor(PlanFlowViewModel.DagEdgeLayout edge, CirrusPalette p) {
        if (edge.kind() == PlanFlowViewModel.EdgeKind.machine) {
            return p.edgeMachine();
        }
        if (edge.kind() == PlanFlowViewModel.EdgeKind.autoLine) {
            return p.linePullStep().accent();
        }
        return DagEdgeRenderer.resourceConduitColor(edge.resource(), p);
    }

    static CirrusPalette.StepTint stepTintFor(String kind, CirrusPalette p) {
        return switch (kind) {
            case "AcquireStep" -> p.acquireStep();
            case "LinePullStep" -> p.linePullStep();
            case "ConvertStep" -> p.convertStep();
            case "CycleLoopStep" -> p.cycleLoopStep();
            case "ProvisionMachineStep" -> p.provisionMachineStep();
            case "SetupLineStep" -> p.setupLineStep();
            default -> p.craftStep();
        };
    }

    static Component stepKindLabel(String kind) {
        return switch (kind) {
            case "AcquireStep" -> CirrusLangs.Workbench.Flow.kindAcquire.get();
            case "LinePullStep" -> CirrusLangs.Workbench.Flow.kindPull.get();
            case "ConvertStep" -> CirrusLangs.Workbench.Flow.kindConvert.get();
            case "CycleLoopStep" -> CirrusLangs.Workbench.Flow.kindCycle.get();
            case "ProvisionMachineStep" -> CirrusLangs.Workbench.Flow.kindBuild.get();
            case "SetupLineStep" -> CirrusLangs.Workbench.Flow.kindLine.get();
            default -> CirrusLangs.Workbench.Flow.kindCraft.get();
        };
    }

    static ImageTexture tabIcon(ImageTexture brightVariant, ImageTexture dimVariant, boolean selected, WorkbenchThemeMode theme) {
        boolean dark = theme == WorkbenchThemeMode.obsidianBlueprint;
        return dark && selected ? brightVariant : dimVariant;
    }

    static SizedTexture gridListSwitchIcon(boolean gridMode, boolean hovered) {
        if (gridMode) {
            return hovered ? CirrusTextures.Toggle.Switch.Calc.gridHover : CirrusTextures.Toggle.Switch.Calc.gridOn;
        }
        return hovered ? CirrusTextures.Toggle.Switch.Calc.listHover : CirrusTextures.Toggle.Switch.Calc.listOn;
    }

    static SizedTexture viewToggleIcon(boolean grid, boolean active, boolean hovered, WorkbenchThemeMode theme) {
        boolean dark = theme == WorkbenchThemeMode.obsidianBlueprint;
        if (grid) {
            if (dark) {
                return (active || hovered) ? CirrusTextures.Toggle.Switch.Calc.gridOn : CirrusTextures.Toggle.Switch.Calc.gridHover;
            }
            return (active || hovered) ? CirrusTextures.Toggle.Switch.Calc.gridOff : CirrusTextures.Toggle.Switch.Calc.gridOn;
        }
        if (dark) {
            return (active || hovered) ? CirrusTextures.Toggle.Switch.Calc.listOn : CirrusTextures.Toggle.Switch.Calc.listHover;
        }
        return (active || hovered) ? CirrusTextures.Toggle.Switch.Calc.listOff : CirrusTextures.Toggle.Switch.Calc.listOn;
    }

    static ImageTexture unitIcon(boolean stack, boolean activeOrHovered) {
        if (stack) {
            return activeOrHovered ? CirrusTextures.Icon.Calc.Unit.stackPressed : CirrusTextures.Icon.Calc.Unit.stack;
        }
        return activeOrHovered ? CirrusTextures.Icon.Calc.Unit.countPressed : CirrusTextures.Icon.Calc.Unit.count;
    }

    static ImageTexture sortIcon(PlanFlowViewModel.MaterialSort sort, boolean hovered) {
        return switch (sort) {
            case missingFirst ->
                hovered ? CirrusTextures.Icon.Calc.Sort.missingFirstPressed : CirrusTextures.Icon.Calc.Sort.missingFirst;
            case amountDesc ->
                hovered ? CirrusTextures.Icon.Calc.Sort.numberPressed : CirrusTextures.Icon.Calc.Sort.number;
            case readyFirst -> hovered
                    ? CirrusTextures.Icon.Calc.Sort.availableFirstPressed
                    : CirrusTextures.Icon.Calc.Sort.availableFirst;
        };
    }

    static String fit(Font font, String text, int maxW) {
        if (maxW <= 4 || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxW) {
            return text;
        }
        String trimmed = font.plainSubstrByWidth(text, Math.max(4, maxW - font.width("...")));
        return trimmed + "...";
    }

    private static void drawValueEmblem(
        GuiGraphics g,
        String typeId,
        int x,
        int y,
        int size,
        CirrusPalette p,
        WorkbenchThemeMode theme
    ) {
        ValueTypeVisual visual = ValueTypeVisualRegistry.resolve(typeId);
        int accent = visual.accentColor(p);
        g.fill(x, y, x + size, y + size, OreUiPaint.withAlpha(accent, OreUiPaint.isDark(p) ? 0.22f : 0.14f));
        drawBorder(g, x, y, size, size, OreUiPaint.withAlpha(accent, 0.72f));
        ImageTexture icon = visual.icon();
        if (icon != null) {
            int iconDim = Math.min(9, Math.max(5, size - 4));
            CirrusThemeIcons.resolve(icon, theme)
                    .render(g, x + (size - iconDim) / 2, y + (size - iconDim) / 2, iconDim, iconDim);
        }
    }

    private static float preferredCountScale(int slotSize, int len) {
        if (slotSize <= 14) {
            return len <= 2 ? 0.62f : 0.52f;
        }
        if (slotSize <= 18) {
            if (len <= 2) {
                return 0.76f;
            }
            return len == 3 ? 0.64f : 0.56f;
        }
        if (len <= 2) {
            return 0.80f;
        }
        return len == 3 ? 0.70f : 0.60f;
    }

    private static int tierCountColor(long amount, CirrusPalette p) {
        if (amount >= 1_000_000L) {
            return OreUiPaint.lighten(p.focusRing(), 0.25f);
        }
        if (amount >= 1_000L) {
            return OreUiPaint.lighten(p.costH(), 0.25f);
        }
        return p.textOnAccent();
    }
}

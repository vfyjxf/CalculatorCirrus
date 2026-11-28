package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.config.ExecutionOverlayClientConfig;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellLayout;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Screen-space bottom-right OreUI notification toast stack supporting structured titles, wrapped details, badges, undo actions, and smooth slide-in entry.
 */
public class ToastStackWidget extends Widget {
    public static final long autoDismissMillis = 6000L;
    public static final long appearMillis = 110L;
    public static final int maxVisible = WorkbenchShellLayout.toastMaxVisible;
    public static final int rowHeight = (int) WorkbenchShellLayout.toastRowHeight;
    public static final int gap = (int) WorkbenchShellLayout.toastGap;
    private static final int undoWidth = 42;
    private static final int padX = 8;
    private static final int padY = 6;
    private static final int iconBox = 14;
    private static final int lineStep = 10;
    private static final double backOutConstant = 1.70158;

    public enum Tone {
        info, success, warning, danger
    }

    public record Toast(
        Component title,
        @Nullable Component detail,
        @Nullable Component badge,
        Tone tone,
        @Nullable Runnable undoAction,
        long bornAtMillis
    ) {
        public Toast(Component message, @Nullable Runnable undoAction, long bornAtMillis) {
            this(message, null, null, Tone.info, undoAction, bornAtMillis);
        }

        public Component message() {
            return title;
        }
    }

    private record RenderedCard(Toast toast, Rect cardRect, @Nullable Rect undoRect, Rect closeRect) {}

    private final WorkbenchShellContext shellContext;
    private final Deque<Toast> toasts = new ArrayDeque<>();
    private final List<RenderedCard> renderedCards = new ArrayList<>();
    private boolean reducedMotion = ExecutionOverlayClientConfig.workbenchReducedMotion();
    private long clockOffsetMillis;

    public ToastStackWidget(WorkbenchShellContext context) {
        this.shellContext = Objects.requireNonNull(context, "context");
        WorkbenchShellLayout.Rect region = shellLayout().toastStack();
        addStyleClass("workbench-toast");
        defaultStyle(UIStyle.of(background(CirrusTextures.Workbench.cardSurface(context.theme()))));
        useStyle(positionAbsolute(), sizeOf(region.width(), region.height()));
        setInteractive(true);
        setVisible(false);
        setTickable(true);
        onTick(this::pruneExpired);
        onMouseClicked((input, clickContext) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            if (hitsAnyToast(local.x(), local.y())) {
                clickContext.consume();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
        onMouseClick((input, clickCount, clickContext) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = input.mouseRelative(this);
            double lx = local.x();
            double ly = local.y();
            if (!renderedCards.isEmpty()) {
                for (int i = renderedCards.size() - 1; i >= 0; i--) {
                    RenderedCard card = renderedCards.get(i);
                    if (!card.cardRect().contains(lx, ly) && !card.closeRect().contains(lx, ly)) {
                        continue;
                    }
                    if (card.closeRect().contains(lx, ly)) {
                        removeToast(card.toast());
                        CirrusUiSound.playClickSound();
                    } else if (card.undoRect() != null
                            && card.undoRect().contains(lx, ly)
                            && card.toast().undoAction() != null) {
                        runUndo(card.toast());
                        CirrusUiSound.playClickSound();
                    } else {
                        removeToast(card.toast());
                        CirrusUiSound.playClickSound();
                    }
                    clickContext.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            }
            Toast toast = toastAtRow((int) ly);
            if (toast == null) {
                return EventDispatch.pass;
            }
            if (undoRect(toast).contains(lx, ly) && toast.undoAction() != null) {
                runUndo(toast);
            } else {
                removeToast(toast);
            }
            CirrusUiSound.playClickSound();
            clickContext.consume();
            return EventDispatch.consumed;
        });
    }

    private boolean hitsAnyToast(double lx, double ly) {
        if (!renderedCards.isEmpty()) {
            for (RenderedCard card : renderedCards) {
                if (card.cardRect().contains(lx, ly) || card.closeRect().contains(lx, ly)) {
                    return true;
                }
            }
            return false;
        }
        return toastAtRow((int) ly) != null;
    }

    public void push(Component message) {
        push(message, null);
    }

    public void push(Component message, @Nullable Runnable undoAction) {
        push(Tone.info, message, null, null, undoAction);
    }

    public void push(
        Tone tone,
        Component title,
        @Nullable Component detail,
        @Nullable Component badge,
        @Nullable Runnable undoAction
    ) {
        Objects.requireNonNull(title, "title");
        Tone safeTone = tone == null ? Tone.info : tone;
        toasts.addLast(new Toast(title, detail, badge, safeTone, undoAction, clock()));
        while (toasts.size() > maxVisible) {
            toasts.removeFirst();
        }
        setVisible(true);
    }

    public int messageCount() {
        return toasts.size();
    }

    public List<Component> messages() {
        List<Component> out = new ArrayList<>(toasts.size());
        for (Toast toast : toasts) {
            out.add(toast.message());
        }
        return List.copyOf(out);
    }

    public List<Toast> toasts() {
        return List.copyOf(toasts);
    }

    public @Nullable Runnable undoAction(int index) {
        Toast toast = toastAt(index);
        return toast == null ? null : toast.undoAction();
    }

    public boolean undo(int index) {
        Toast toast = toastAt(index);
        if (toast == null || toast.undoAction() == null) {
            return false;
        }
        runUndo(toast);
        return true;
    }

    public void dismiss(int index) {
        Toast toast = toastAt(index);
        if (toast != null) {
            removeToast(toast);
        }
    }

    public int advance(long deltaMillis) {
        if (deltaMillis > 0L) {
            clockOffsetMillis += deltaMillis;
        }
        return pruneExpired();
    }

    public int pruneExpired() {
        boolean changed = false;
        long now = clock();
        while (!toasts.isEmpty() && now - toasts.peekFirst().bornAtMillis() >= autoDismissMillis) {
            toasts.removeFirst();
            changed = true;
        }
        if (changed && toasts.isEmpty()) {
            setVisible(false);
        } else if (changed) {
            setVisible(true);
        }
        return toasts.size();
    }

    public void clear() {
        toasts.clear();
        renderedCards.clear();
        setVisible(false);
    }

    public ToastStackWidget reducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        return this;
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    public long clock() {
        return System.currentTimeMillis() + clockOffsetMillis;
    }

    public double appearProgress(long nowMillis) {
        if (reducedMotion) {
            return 1.0;
        }
        Toast newest = toasts.peekLast();
        if (newest == null) {
            return 0.0;
        }
        double raw = (nowMillis - newest.bornAtMillis()) / (double) appearMillis;
        return backOut(Math.max(0.0, Math.min(1.0, raw)));
    }

    public static double backOut(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double shifted = clamped - 1.0;
        return 1.0 + (backOutConstant + 1.0) * shifted * shifted * shifted + backOutConstant * shifted * shifted;
    }

    public WorkbenchShellContext shellContext() {
        return shellContext;
    }

    public int rowY(int index) {
        return index * (rowHeight + gap);
    }

    public @Nullable Toast toastAt(int index) {
        if (index < 0 || index >= toasts.size()) {
            return null;
        }
        int cursor = 0;
        for (Toast toast : toasts) {
            if (cursor == index) {
                return toast;
            }
            cursor++;
        }
        return null;
    }

    public @Nullable Toast toastAtRow(int localY) {
        for (int index = 0; index < toasts.size(); index++) {
            int y = rowY(index);
            if (localY >= y && localY < y + rowHeight) {
                return toastAt(index);
            }
        }
        return null;
    }

    private Rect undoRect(Toast toast) {
        int index = indexOf(toast);
        int width = width();
        return new Rect(Math.max(0, width - undoWidth - 4), rowY(Math.max(0, index)), undoWidth, rowHeight);
    }

    private int indexOf(Toast toast) {
        int index = 0;
        for (Toast candidate : toasts) {
            if (candidate == toast) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private void runUndo(Toast toast) {
        Runnable action = toast.undoAction();
        removeToast(toast);
        if (action != null) {
            action.run();
        }
    }

    private void removeToast(Toast toast) {
        toasts.remove(toast);
        if (toasts.isEmpty()) {
            renderedCards.clear();
            setVisible(false);
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        renderedCards.clear();
        if (toasts.isEmpty()) {
            return;
        }
        CirrusPalette palette = shellContext.palette();
        int w = width();
        int h = height();
        if (w <= 0 || h <= 0) {
            return;
        }
        Font font = canvas.font();
        long now = clock();

        // Stack newest toast cleanly at bottom-right and older toasts above it without truncation.
        List<Toast> ordered = new ArrayList<>(toasts);
        int cursorBottom = h;
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Toast toast = ordered.get(i);
            int accent = toneColor(toast.tone(), palette);

            int textXOffset = padX + iconBox + 7;
            int closeSize = 14;
            int rawBadgeW = toast.badge() == null ? 0 : ChromeText.width(canvas, toast.badge()) + 10;
            int maxBadgeW = Math.max(44, ((w - textXOffset - closeSize - padX - 10) * 42) / 100);
            int badgeW = Math.min(rawBadgeW, maxBadgeW);
            int headerTextMaxW = Math.max(56, w - textXOffset - closeSize - padX - 6 - (badgeW > 0 ? badgeW + 4 : 0));
            int bodyTextMaxW = Math.max(48, w - textXOffset - padX - 2);

            List<FormattedCharSequence> titleLines = wrapLines(font, toast.title(), headerTextMaxW);
            List<FormattedCharSequence> detailLines = toast.detail() == null
                    ? List.of()
                    : wrapLines(font, toast.detail(), bodyTextMaxW);

            int titleBlockH = Math.max(iconBox, titleLines.size() * lineStep);
            int detailBlockH = detailLines.isEmpty() ? 0 : 3 + detailLines.size() * lineStep;
            int undoBlockH = toast.undoAction() == null ? 0 : 18;
            int cardH = Math.max(36, padY + titleBlockH + detailBlockH + undoBlockH + padY + 4);

            int baseY = cursorBottom - cardH;
            cursorBottom = baseY - gap;
            if (baseY + cardH < -4) {
                break;
            }

            float ease = ChromeMotion
                    .cubicOutProgress(now - toast.bornAtMillis(), ChromeMotion.overlayAppearMillis, reducedMotion);
            float alpha = Math.max(0.15f, ease);
            int slideX = reducedMotion ? 0 : Math.round((1f - ease) * 18f);
            int cardX = slideX;

            canvas.shadow(cardX, baseY, w, cardH, 2f, 4f, 10f, palette.dropShadow());
            OreUiPaint.popover(canvas, cardX, baseY, w, cardH, palette);
            canvas.strokeRect(cardX, baseY, w, cardH, OreUiPaint.withAlpha(accent, 0.72f * alpha), 1);

            // Semantic icon badge
            int iconX = cardX + padX;
            int iconY = baseY + padY;
            canvas.fill(iconX, iconY, iconBox, iconBox, OreUiPaint.withAlpha(accent, 0.22f * alpha));
            canvas.strokeRect(iconX, iconY, iconBox, iconBox, OreUiPaint.withAlpha(accent, 0.85f * alpha), 1);
            Component glyph = Component.literal(toneGlyph(toast.tone()));
            int gw = ChromeText.width(canvas, glyph);
            ChromeText.draw(
                canvas,
                glyph,
                iconX + Math.max(1, (iconBox - gw) / 2),
                ChromeText.centeredY(iconY, iconBox, canvas),
                ChromeMotion.modulateAlpha(accent, alpha)
            );

            // Close button [x]
            Rect closeRect = new Rect(cardX + w - padX - closeSize, baseY + padY - 1, closeSize, closeSize);
            boolean closeHover = closeRect.contains(mouseX, mouseY);
            if (closeHover) {
                canvas.fill(
                    closeRect.x(),
                    closeRect.y(),
                    closeRect.width(),
                    closeRect.height(),
                    OreUiPaint.withAlpha(palette.cardSurfaceHover(), 0.90f * alpha)
                );
                canvas.strokeRect(
                    closeRect.x(),
                    closeRect.y(),
                    closeRect.width(),
                    closeRect.height(),
                    OreUiPaint.withAlpha(palette.focusRing(), 0.85f * alpha),
                    1
                );
            }
            Component xMark = Component.literal("\u00d7");
            int xw = ChromeText.width(canvas, xMark);
            ChromeText.draw(
                canvas,
                xMark,
                closeRect.x() + Math.max(1, (closeSize - xw) / 2),
                ChromeText.centeredY(closeRect.y(), closeSize, canvas),
                ChromeMotion.modulateAlpha(closeHover ? palette.textPrimary() : palette.textMuted(), alpha)
            );

            // Optional badge pill next to close, strictly bounded inside card header
            if (toast.badge() != null && badgeW > 0) {
                int bx = Math.max(cardX + textXOffset + 58, closeRect.x() - badgeW - 4);
                int actualBadgeW = Math.max(24, closeRect.x() - 4 - bx);
                int fill = OreUiPaint.isDark(palette)
                        ? OreUiPaint.withAlpha(accent, 0.20f)
                        : OreUiPaint.withAlpha(accent, 0.12f);
                int border = OreUiPaint.withAlpha(accent, OreUiPaint.isDark(palette) ? 0.62f : 0.45f);
                canvas.fill(bx, baseY + padY, actualBadgeW, 13, fill);
                canvas.strokeRect(bx, baseY + padY, actualBadgeW, 13, border, 1);
                ChromeText.drawFitted(
                    canvas,
                    toast.badge(),
                    bx + 5,
                    ChromeText.centeredY(baseY + padY, 13, canvas),
                    Math.max(10, actualBadgeW - 10),
                    accent
                );
            }

            // Title lines (wrapped, never truncated)
            int ty = baseY + padY + 2;
            int titleInk = ChromeMotion.modulateAlpha(palette.textPrimary(), alpha);
            for (FormattedCharSequence line : titleLines) {
                if (font != null) {
                    canvas.text(line, cardX + textXOffset, ty, titleInk, false);
                } else {
                    ChromeText.draw(canvas, toast.title(), cardX + textXOffset, ty, titleInk);
                }
                ty += lineStep;
            }

            // Detail lines (wrapped, never truncated)
            int bodyY = baseY + padY + titleBlockH + 3;
            int detailInk = ChromeMotion.modulateAlpha(palette.textSecondary(), alpha);
            for (FormattedCharSequence line : detailLines) {
                if (font != null) {
                    canvas.text(line, cardX + textXOffset, bodyY, detailInk, false);
                } else if (toast.detail() != null) {
                    ChromeText.draw(canvas, toast.detail(), cardX + textXOffset, bodyY, detailInk);
                }
                bodyY += lineStep;
            }

            // Optional Undo button
            Rect undoBtnRect = null;
            if (toast.undoAction() != null) {
                Component undoLabel = CirrusLangs.Workbench.Topbar.undo.get();
                int uw = Math.max(undoWidth, ChromeText.width(canvas, undoLabel) + 12);
                int uh = 15;
                int ux = cardX + textXOffset;
                int uy = baseY + cardH - padY - uh - 2;
                undoBtnRect = new Rect(ux, uy, uw, uh);
                boolean uHov = undoBtnRect.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(canvas, ux, uy, uw, uh, palette, false, uHov, false, false);
                ChromeText.draw(
                    canvas,
                    undoLabel,
                    ux + (uw - ChromeText.width(canvas, undoLabel)) / 2,
                    ChromeText.centeredY(uy, uh, canvas),
                    ChromeMotion.modulateAlpha(palette.focusRing(), alpha)
                );
            }

            // Countdown bar along bottom inner edge
            long elapsed = Math.max(0L, now - toast.bornAtMillis());
            float remain = Math.max(0f, Math.min(1f, 1f - (elapsed / (float) autoDismissMillis)));
            int trackW = Math.max(0, w - 2);
            int barW = Math.round(trackW * remain);
            canvas.fill(cardX + 1, baseY + cardH - 3, trackW, 2, OreUiPaint.cardDivider(palette));
            if (barW > 0) {
                canvas.fill(cardX + 1, baseY + cardH - 3, barW, 2, OreUiPaint.withAlpha(accent, 0.90f * alpha));
            }

            renderedCards.add(new RenderedCard(toast, new Rect(cardX, baseY, w, cardH), undoBtnRect, closeRect));
        }
    }

    private static List<FormattedCharSequence> wrapLines(@Nullable Font font, Component text, int maxWidth) {
        if (font == null) {
            return List.of(text.getVisualOrderText());
        }
        List<FormattedCharSequence> split = font.split(text, Math.max(24, maxWidth));
        return split.isEmpty() ? List.of(text.getVisualOrderText()) : split;
    }

    private static int toneColor(Tone tone, CirrusPalette palette) {
        return switch (tone) {
            case success -> palette.costS();
            case warning -> palette.costH();
            case danger -> palette.ruleHardBan();
            case info -> palette.focusRing();
        };
    }

    private static String toneGlyph(Tone tone) {
        return switch (tone) {
            case success -> "\u2713";
            case warning -> "!";
            case danger -> "\u00d7";
            case info -> "i";
        };
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

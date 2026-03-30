package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayLaneSegment;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayViewModel;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Shared renderer for execution-overlay components.
 * <p>
 * The full overlay and standalone preview widgets both use this class so
 * component-level rendering can be validated independently.
 */
public final class ExecutionOverlayComponentRenderer {

    //region Palette
    public static final int textPrimary = 0xFF3F3F3F;
    public static final int textSecondary = 0xFF555555;
    public static final int textDim = 0xFF666666;
    public static final int textWarning = 0xFFC44949;

    private static final int activeCardTint = 0x225A8FC9;
    private static final int pausedCardTint = 0x22BF8B2A;
    private static final int failedCardTint = 0x22C35A5A;
    private static final int progressTrackColor = 0xFF8F8F8F;
    private static final int pendingSegmentColor = 0xFFA6A6A6;
    private static final int runningSegmentColor = 0xFF4F86DB;
    private static final int pausedSegmentColor = 0xFFBC8A2D;
    private static final int completedSegmentColor = 0xFF5A9856;
    private static final int failedSegmentColor = 0xFFC35A5A;
    //endregion

    //region Metrics
    private static final int iconSize = 16;
    private static final int rowIconInset = 4;
    private static final int laneSegmentWidth = 9;
    private static final int laneSegmentHeight = 4;
    private static final int laneGap = 3;
    //endregion

    private ExecutionOverlayComponentRenderer() {
    }

    //region Models
    public record HeaderModel(
        String title,
        int completedSteps,
        int totalSteps,
        ExecutionFlowStatus status,
        String statusLabel,
        @Nullable String sideHint
    ) {
    }

    public record FooterModel(
        @Nullable ItemStack icon,
        String primaryText,
        String secondaryText,
        boolean warning
    ) {
    }

    public record NodeCardModel(
        ExecutionOverlayNode node,
        boolean showSubtitle
    ) {
    }
    //endregion

    //region Model helpers
    public static HeaderModel headerModelOf(ExecutionOverlayViewModel viewModel) {
        return new HeaderModel(
            viewModel.title().getString(),
            Math.max(0, Math.min(viewModel.totalStepCount(), viewModel.completedStepCount())),
            Math.max(0, viewModel.totalStepCount()),
            viewModel.status(),
            viewModel.statusLabel().getString(),
            viewModel.sideHint() == null ? null : viewModel.sideHint().getString()
        );
    }

    public static FooterModel footerModelOf(ExecutionOverlayViewModel viewModel, float zoom) {
        ItemStack icon = viewModel.footerIcon() == null ? ItemStack.EMPTY : viewModel.footerIcon().copy();
        String primary = footerPrimaryText(viewModel, icon);
        String secondary = footerSecondaryText(viewModel, zoom);
        return new FooterModel(icon, primary, secondary, viewModel.footerWarning());
    }

    public static NodeCardModel cardModelOf(ExecutionOverlayNode node, int cardHeight) {
        boolean showSubtitle = cardHeight >= 40 && node.subtitle() != null;
        return new NodeCardModel(node, showSubtitle);
    }
    //endregion

    //region Rendering
    public static void drawHeader(SceneCanvas canvas, Font font, HeaderModel model, int x, int y, int width, int height, int panelPadding) {
        canvas.texture(CirrusTextures.Background.frame, x, y, width, height);
        canvas.texture(CirrusTextures.Background.inset, x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4));

        String title = trim(font, model.title(), width - panelPadding * 2 - 52);
        canvas.drawString(title, x + panelPadding, y + 5, textPrimary, false);

        int total = Math.max(0, model.totalSteps());
        int done = Math.max(0, Math.min(total, model.completedSteps()));
        String progressText = done + "/" + total;
        canvas.drawString(progressText, x + width - panelPadding - font.width(progressText), y + 5, textSecondary, false);

        canvas.texture(CirrusTextures.Background.Sign.bar, x + panelPadding, y + 15, Math.max(8, width - panelPadding * 2), 3);
        String statusText = model.statusLabel();
        canvas.drawString(statusText, x + panelPadding, y + 20, statusColor(model.status()), false);
        String side = model.sideHint() == null ? "" : model.sideHint();
        String sideTrimmed = trim(font, side, Math.max(0, width - panelPadding * 2 - 46));
        canvas.drawString(sideTrimmed, x + width - panelPadding - font.width(sideTrimmed), y + 20, textDim, false);
    }

    public static void drawFooter(SceneCanvas canvas, Font font, FooterModel model, int x, int y, int width, int height, int panelPadding) {
        canvas.texture(CirrusTextures.Background.frame, x, y, width, height);
        canvas.texture(CirrusTextures.Background.inset, x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4));

        int textX = x + panelPadding;
        if (model.icon() != null && !model.icon().isEmpty()) {
            drawItem(canvas, model.icon(), x + panelPadding, y + 5, iconSize);
            textX = x + panelPadding + iconSize + 4;
        }

        int primaryColor = model.warning() ? textWarning : textPrimary;
        canvas.drawString(trim(font, model.primaryText(), width - (textX - x) - panelPadding), textX, y + 6, primaryColor, false);
        canvas.drawString(trim(font, model.secondaryText(), width - panelPadding * 2), x + panelPadding, y + 20, textDim, false);

        if (model.warning()) {
            canvas.texture(CirrusTextures.Icon.Sign.Amount.missing, x + width - panelPadding - 6, y + 6, 5, 5);
        }
    }

    public static void drawNodeCard(SceneCanvas canvas, Font font, NodeCardModel model, int x, int y, int width, int height) {
        var node = model.node();
        int statusColor = statusColor(node.status());
        boolean compactRow = height <= 40 || node.depth() >= 2;
        if (compactRow) {
            drawCompactNodeRow(canvas, font, node, x, y, width, height, statusColor);
            return;
        }
        canvas.texture(CirrusTextures.Background.Border.light, x, y, width, height);
        canvas.texture(CirrusTextures.Background.flat, x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4));
        canvas.texture(CirrusTextures.Background.Sign.bar, x + 4, y + 4, Math.max(8, width - 8), 3);
        canvas.fill(x + 4, y + 4, Math.max(8, width - 8), 1, statusColor);

        if (node.status() instanceof ExecutionFlowStatus.Running && node.activeBranch()) {
            canvas.fill(x + 1, y + 1, width - 2, height - 2, activeCardTint);
        } else if (node.status() instanceof ExecutionFlowStatus.Paused) {
            canvas.fill(x + 1, y + 1, width - 2, height - 2, pausedCardTint);
        } else if (node.status() instanceof ExecutionFlowStatus.Failed) {
            canvas.fill(x + 1, y + 1, width - 2, height - 2, failedCardTint);
        }

        int iconX = x + rowIconInset;
        int iconY = y + 7;
        if (node.iconStack() != null && !node.iconStack().isEmpty()) {
            drawItem(canvas, node.iconStack(), iconX, iconY, iconSize);
        } else {
            canvas.texture(CirrusTextures.Background.Slot.dark, iconX - 1, iconY - 1, iconSize + 2, iconSize + 2);
        }

        int textStart = iconX + iconSize + 6;

        String statusLabel = statusShort(node.status());
        int statusW = Math.max(0, font.width(statusLabel) + 6);
        int statusX = x + width - statusW - 6;
        canvas.texture(CirrusTextures.Background.dark, statusX, y + 6, statusW, 8);
        canvas.drawString(statusLabel, statusX + 3, y + 7, statusColor, false);

        int textWidth = Math.max(20, statusX - textStart - 4);
        int textY = y + 6;
        String title = node.label().getString();
        String firstRaw = font.plainSubstrByWidth(title, textWidth);
        if (firstRaw.isEmpty()) {
            firstRaw = trim(font, title, textWidth);
        }
        String first = trim(font, firstRaw, textWidth);
        String remainder = title.length() > firstRaw.length() ? title.substring(firstRaw.length()).trim() : "";
        canvas.drawString(first, textStart, textY, 0xFFEFF4F8, false);
        textY += 9;
        if (!remainder.isEmpty()) {
            canvas.drawString(trim(font, remainder, textWidth), textStart, textY, 0xFFE0E7EE, false);
        } else {
            String fallback = model.showSubtitle() && node.subtitle() != null
                ? node.subtitle().getString()
                : laneProgressText(node);
            canvas.drawString(trim(font, fallback, textWidth), textStart, textY, 0xFFD3DCE6, false);
        }

        int infoWidth = Math.max(16, width - 10);
        int statusY = y + Math.max(27, height - 20);
        String stateLine = statusText(node.status()) + " · " + currentSegmentLabel(node);
        canvas.drawString(trim(font, stateLine, infoWidth), x + 5, statusY, statusColor, false);
        int detailY = y + Math.max(35, height - 12);
        String detail = model.showSubtitle() && node.subtitle() != null && !node.subtitle().getString().isBlank()
            ? node.subtitle().getString()
            : laneProgressText(node);
        canvas.drawString(trim(font, detail, infoWidth), x + 5, detailY, 0xFFD5DEE7, false);
    }

    private static void drawCompactNodeRow(
        SceneCanvas canvas,
        Font font,
        ExecutionOverlayNode node,
        int x,
        int y,
        int width,
        int height,
        int statusColor
    ) {
        canvas.texture(CirrusTextures.Background.Border.light, x, y, width, height);
        canvas.texture(CirrusTextures.Background.inset, x + 2, y + 2, Math.max(0, width - 4), Math.max(0, height - 4));

        int iconSizeCompact = Math.max(11, Math.min(14, height - 12));
        int iconX = x + 5;
        int iconY = y + Math.max(4, (height - iconSizeCompact) / 2 - 1);
        if (node.iconStack() != null && !node.iconStack().isEmpty()) {
            drawItem(canvas, node.iconStack(), iconX, iconY, iconSizeCompact);
        } else {
            canvas.texture(CirrusTextures.Background.Slot.dark, iconX - 1, iconY - 1, iconSizeCompact + 2, iconSizeCompact + 2);
        }

        String statusTag = statusShort(node.status());
        int statusW = font.width(statusTag) + 6;
        int statusX = x + width - statusW - 5;
        int statusY = y + 4;
        canvas.texture(CirrusTextures.Background.dark, statusX, statusY, statusW, 8);
        canvas.drawString(statusTag, statusX + 3, statusY + 1, statusColor, false);

        int textX = iconX + iconSizeCompact + 6;
        int textW = Math.max(24, statusX - textX - 4);
        String step = currentSegmentLabel(node);
        String single = trim(font, node.label().getString() + " · " + step, textW);
        canvas.drawString(single, textX, y + 5, textPrimary, false);
    }
    //endregion

    //region Helpers
    public static int statusColor(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> runningSegmentColor;
            case ExecutionFlowStatus.Paused ignored -> pausedSegmentColor;
            case ExecutionFlowStatus.Completed ignored -> completedSegmentColor;
            case ExecutionFlowStatus.Failed ignored -> failedSegmentColor;
            default -> pendingSegmentColor;
        };
    }

    public static void drawSegmentedProgressBar(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        float progress,
        int segments,
        int fillColor
    ) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.texture(CirrusTextures.Background.Slot.dark, x, y, w, h);
        if (w <= 2 || h <= 2) {
            return;
        }
        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = w - 2;
        int innerH = h - 2;
        canvas.fill(innerX, innerY, innerW, innerH, 0xFF1A2027);

        int segmentCount = Math.max(1, segments);
        int gap = segmentCount <= 4 ? 1 : 0;
        int usable = innerW - gap * Math.max(0, segmentCount - 1);
        if (usable <= 0) {
            return;
        }
        int baseSegW = Math.max(1, usable / segmentCount);
        int remainder = Math.max(0, usable - baseSegW * segmentCount);
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        float segmentSpan = 1.0f / segmentCount;
        int cursorX = innerX;
        for (int i = 0; i < segmentCount; i++) {
            int segW = baseSegW + (i < remainder ? 1 : 0);
            if (segW <= 0) {
                continue;
            }
            canvas.fill(cursorX, innerY, segW, innerH, 0xFF2D3540);
            float segStart = i * segmentSpan;
            float segEnd = segStart + segmentSpan;
            float localRatio = clamped <= segStart
                ? 0.0f
                : (clamped >= segEnd ? 1.0f : (clamped - segStart) / segmentSpan);
            int fillW = Math.max(0, Math.min(segW, Math.round(segW * localRatio)));
            if (fillW > 0) {
                canvas.fill(cursorX, innerY, fillW, innerH, fillColor);
                if (innerH > 1) {
                    canvas.fill(cursorX, innerY, fillW, 1, 0x66FFFFFF);
                }
            }
            cursorX += segW + gap;
        }
    }

    private static void drawLane(SceneCanvas canvas, ImmutableList<ExecutionOverlayLaneSegment> segments, int x, int y, int maxX) {
        int currentX = x;
        for (int i = 0; i < segments.size(); i++) {
            if (currentX + laneSegmentWidth > maxX) {
                break;
            }
            var segment = segments.get(i);
            canvas.texture(CirrusTextures.Background.Slot.dark, currentX, y, laneSegmentWidth, laneSegmentHeight);
            canvas.fill(currentX + 1, y + 1, laneSegmentWidth - 2, laneSegmentHeight - 2, statusColor(segment.status()));
            if (segment.current()) {
                drawRect(canvas, currentX - 1, y - 1, laneSegmentWidth + 2, laneSegmentHeight + 2, 0xFFE8EEF5);
            }
            currentX += laneSegmentWidth;
            if (i < segments.size() - 1 && currentX + laneGap <= maxX) {
                canvas.texture(CirrusTextures.Background.dark, currentX, y + 1, laneGap, Math.max(1, laneSegmentHeight - 2));
                currentX += laneGap;
            }
        }
    }

    private static void drawItem(SceneCanvas canvas, ItemStack stack, int x, int y, int size) {
        canvas.texture(CirrusTextures.Background.Slot.dark, x - 1, y - 1, size + 2, size + 2);
        canvas.pushTransform();
        canvas.translate(x, y);
        canvas.scale(size / 16f);
        canvas.renderItem(stack, 0, 0);
        canvas.renderItemDecorations(stack, 0, 0);
        canvas.popTransform();
    }

    private static void drawRect(SceneCanvas canvas, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.fill(x, y, w, 1, color);
        canvas.fill(x, y + h - 1, w, 1, color);
        canvas.fill(x, y, 1, h, color);
        canvas.fill(x + w - 1, y, 1, h, color);
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        String result = text;
        while (result.length() > 1 && font.width(result) + ellipsisWidth > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private static String footerPrimaryText(ExecutionOverlayViewModel viewModel, @Nullable ItemStack icon) {
        if (icon != null && !icon.isEmpty()) {
            return icon.getHoverName().getString() + " x" + icon.getCount();
        }
        if (viewModel.footerSummary() != null) {
            return viewModel.footerSummary().getString();
        }
        return "No execution data";
    }

    private static String footerSecondaryText(ExecutionOverlayViewModel viewModel, float zoom) {
        if (viewModel.footerWarning()) {
            if (viewModel.footerDetail() != null) {
                return viewModel.footerDetail().getString();
            }
            return "Inventory warning";
        }
        return "Zoom " + Math.round(zoom * 100f) + "% | Ctrl+Wheel";
    }

    private static String statusShort(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> "RUN";
            case ExecutionFlowStatus.Paused ignored -> "PAUSE";
            case ExecutionFlowStatus.Completed ignored -> "DONE";
            case ExecutionFlowStatus.Failed ignored -> "ERR";
            case ExecutionFlowStatus.Pending ignored -> "WAIT";
        };
    }

    private static String statusText(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> "Running";
            case ExecutionFlowStatus.Paused ignored -> "Paused";
            case ExecutionFlowStatus.Completed ignored -> "Completed";
            case ExecutionFlowStatus.Failed ignored -> "Failed";
            case ExecutionFlowStatus.Pending ignored -> "Pending";
        };
    }

    private static String currentSegmentLabel(ExecutionOverlayNode node) {
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if (segment.current()) {
                return segment.label().getString();
            }
        }
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if (segment.status() instanceof ExecutionFlowStatus.Pending) {
                return segment.label().getString();
            }
        }
        return "Idle";
    }

    private static String laneProgressText(ExecutionOverlayNode node) {
        int total = node.laneSegments().size();
        if (total <= 0) {
            return "0/0";
        }
        int done = 0;
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if (segment.status() instanceof ExecutionFlowStatus.Completed) {
                done++;
            }
        }
        return done + "/" + total + " completed";
    }

    private static float laneRatio(ExecutionOverlayNode node) {
        int total = node.laneSegments().size();
        if (total <= 0) {
            return 0.0f;
        }
        int done = 0;
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if (segment.status() instanceof ExecutionFlowStatus.Completed) {
                done++;
            }
        }
        return Math.max(0.0f, Math.min(1.0f, done / (float) total));
    }
    //endregion
}

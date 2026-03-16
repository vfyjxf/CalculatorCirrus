package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayLaneSegment;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayViewModel;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only left-side overlay that renders execution progress inside other screens.
 */
public class ExecutionOverlayWidget extends Widget {

    //region layout constants

    public static final float preferredWidthRatio = 0.32f;
    public static final int minPanelWidth = 132;
    public static final int maxPanelWidth = 220;
    public static final int headerHeight = 26;
    public static final int footerHeight = 48;

    private static final int panelPadding = 6;
    private static final int rowHeight = 18;
    private static final int rowGap = 2;
    private static final int treeIndent = 12;
    private static final int laneSegmentWidth = 12;
    private static final int laneSegmentHeight = 6;
    private static final int laneGap = 4;
    private static final int laneStartOffset = 88;

    //endregion

    //region palette

    private static final int textPrimary = 0xFF2244AA;
    private static final int textSecondary = 0xFF556699;
    private static final int textDim = 0xFF7788AA;
    private static final int lineColor = 0xFF8B8B8B;
    private static final int activeLineColor = 0xFF3366AA;
    private static final int pendingSegmentColor = 0xFF8E8E8E;
    private static final int runningSegmentColor = 0xFF3F6FD1;
    private static final int pausedSegmentColor = 0xFFB97A1E;
    private static final int completedSegmentColor = 0xFF4B8B47;
    private static final int failedSegmentColor = 0xFF9E3E3E;

    //endregion

    private @Nullable ExecutionSession session;
    private @Nullable ExecutionOverlayViewModel viewModel;
    private int scrollOffset;

    public ExecutionOverlayWidget() {
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, ctx) -> {
            if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
                return EventDispatch.pass;
            }
            int maxScroll = Math.max(0, contentHeight() - bodyHeight(height(), headerHeight, footerHeight));
            if (maxScroll <= 0) {
                return EventDispatch.pass;
            }
            if (scrollY > 0) {
                scrollOffset = Math.max(0, scrollOffset - rowHeight);
            } else if (scrollY < 0) {
                scrollOffset = Math.min(maxScroll, scrollOffset + rowHeight);
            }
            ctx.cancel();
            return EventDispatch.consumed;
        });
    }

    //region state

    public void setSession(@Nullable ExecutionSession session) {
        this.session = session;
        this.viewModel = session == null ? null : ExecutionOverlayViewModel.derive(session);
        this.scrollOffset = 0;
    }

    public void refreshFromHost(ExecutionOverlayHost host) {
        setSession(host.executionOverlaySession());
    }

    public @Nullable ExecutionOverlayViewModel viewModel() {
        return viewModel;
    }

    public @Nullable ExecutionSession session() {
        return session;
    }

    //endregion

    //region layout helpers

    public static int preferredPanelWidth(int availableWidth) {
        return Math.max(minPanelWidth, Math.min(maxPanelWidth, Math.round(availableWidth * preferredWidthRatio)));
    }

    public static int bodyHeight(int totalHeight, int headerHeight, int footerHeight) {
        return Math.max(0, totalHeight - headerHeight - footerHeight);
    }

    private int contentHeight() {
        if (viewModel == null) {
            return 0;
        }
        return viewModel.visibleNodes().size() * (rowHeight + rowGap);
    }

    //endregion

    //region rendering

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        canvas.texture(CirrusTextures.Background.Outlined.inset, 0, 0, width(), height());
        if (viewModel == null) {
            drawEmpty(canvas);
            return;
        }

        Font font = Minecraft.getInstance().font;
        drawHeader(canvas, font);
        drawFooter(canvas, font);
        drawBody(canvas, font);
    }

    private void drawEmpty(SceneCanvas canvas) {
        canvas.drawString("No execution", panelPadding, panelPadding, textDim, false);
    }

    private void drawHeader(SceneCanvas canvas, Font font) {
        canvas.texture(CirrusTextures.Background.Outlined.inset, 2, 2, width() - 4, headerHeight - 2);
        canvas.drawString(trim(font, viewModel.title().getString(), width() - panelPadding * 2 - 38), panelPadding, 6, textPrimary, false);
        String progress = viewModel.completedStepCount() + "/" + viewModel.totalStepCount();
        canvas.drawString(progress, width() - panelPadding - font.width(progress), 6, textSecondary, false);
        canvas.texture(CirrusTextures.Background.Sign.bar, panelPadding, headerHeight - 7, width() - panelPadding * 2, 4);
        canvas.drawString(viewModel.statusText(), panelPadding, 15, statusColor(viewModel.statusText()), false);
        if (viewModel.sideHint() != null) {
            String sideText = trim(font, viewModel.sideHint().getString(), width() - panelPadding * 2 - 48);
            canvas.drawString(sideText, width() - panelPadding - font.width(sideText), 15, textDim, false);
        }
    }

    private void drawFooter(SceneCanvas canvas, Font font) {
        int footerY = height() - footerHeight;
        canvas.texture(CirrusTextures.Background.Outlined.inset, 2, footerY, width() - 4, footerHeight - 2);
        if (viewModel.footerSummary() != null) {
            canvas.drawString(trim(font, viewModel.footerSummary().getString(), width() - panelPadding * 2), panelPadding, footerY + 6, textPrimary, false);
        }
        if (viewModel.footerDetail() != null) {
            canvas.drawString(trim(font, viewModel.footerDetail().getString(), width() - panelPadding * 2), panelPadding, footerY + 16, textSecondary, false);
        }
        for (int i = 0; i < Math.min(2, viewModel.footerSteps().size()); i++) {
            var line = trim(font, viewModel.footerSteps().get(i).getString(), width() - panelPadding * 2 - 6);
            canvas.drawString("- " + line, panelPadding, footerY + 27 + i * 9, textDim, false);
        }
    }

    private void drawBody(SceneCanvas canvas, Font font) {
        int bodyY = headerHeight;
        int bodyH = bodyHeight(height(), headerHeight, footerHeight);
        var abs = absolutePos();
        canvas.withClip(abs.x(), abs.y() + bodyY, width(), bodyH, () -> {
            int y = bodyY + panelPadding - scrollOffset;
            for (ExecutionOverlayNode node : viewModel.visibleNodes()) {
                if (y + rowHeight < bodyY || y > bodyY + bodyH) {
                    y += rowHeight + rowGap;
                    continue;
                }
                drawRow(canvas, font, node, y);
                y += rowHeight + rowGap;
            }
        });
    }

    private void drawRow(SceneCanvas canvas, Font font, ExecutionOverlayNode node, int y) {
        int lineX = panelPadding + node.depth() * treeIndent;
        int cardX = lineX + 10;
        int cardW = Math.max(42, width() - cardX - panelPadding);
        canvas.fill(lineX, y, 1, rowHeight + rowGap, node.activeBranch() ? activeLineColor : lineColor);
        canvas.texture(CirrusTextures.Background.Outlined.inset, cardX, y, cardW, rowHeight);

        String label = trim(font, node.label().getString(), laneStart(cardX, cardW) - cardX - 8);
        canvas.drawString(label, cardX + 4, y + 4, textPrimary, false);
        if (node.subtitle() != null) {
            canvas.drawString(trim(font, node.subtitle().getString(), laneStart(cardX, cardW) - cardX - 8), cardX + 4, y + 11, textDim, false);
        }
        drawLane(canvas, node.laneSegments(), laneStart(cardX, cardW), y + 6, cardX + cardW - 4);
    }

    private void drawLane(SceneCanvas canvas, ImmutableList<ExecutionOverlayLaneSegment> segments, int x, int y, int maxX) {
        int currentX = x;
        for (int i = 0; i < segments.size(); i++) {
            if (currentX + laneSegmentWidth > maxX) {
                break;
            }
            var segment = segments.get(i);
            canvas.texture(CirrusTextures.Background.Slot.dark, currentX, y, laneSegmentWidth, laneSegmentHeight);
            canvas.fill(currentX + 1, y + 1, laneSegmentWidth - 2, laneSegmentHeight - 2, statusColor(segment.statusText()));
            if (segment.current()) {
                canvas.texture(CirrusTextures.Background.Outlined.flat, currentX - 1, y - 1, laneSegmentWidth + 2, laneSegmentHeight + 2);
            }
            currentX += laneSegmentWidth;
            if (i < segments.size() - 1 && currentX + laneGap <= maxX) {
                canvas.texture(CirrusTextures.Background.Sign.bar, currentX, y + 1, laneGap, 4);
                currentX += laneGap;
            }
        }
    }

    //endregion

    //region helpers

    private static int laneStart(int cardX, int cardW) {
        return cardX + Math.max(56, cardW - laneStartOffset);
    }

    private static int statusColor(String statusText) {
        return switch (statusText) {
            case "running" -> runningSegmentColor;
            case "paused" -> pausedSegmentColor;
            case "completed" -> completedSegmentColor;
            case "failed" -> failedSegmentColor;
            default -> pendingSegmentColor;
        };
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

    //endregion
}

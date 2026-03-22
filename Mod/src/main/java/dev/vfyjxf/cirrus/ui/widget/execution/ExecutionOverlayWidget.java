package dev.vfyjxf.cirrus.ui.widget.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayLaneSegment;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlayViewModel;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Read-only left-side overlay that renders execution progress inside other screens.
 */
public class ExecutionOverlayWidget extends Widget {

    //region layout constants

    public static final float preferredWidthRatio = 0.30f;
    public static final int minPanelWidth = 180;
    public static final int maxPanelWidth = 300;
    public static final int headerHeight = 34;
    public static final int footerHeight = 44;

    private static final int panelPadding = 7;
    private static final int graphPaddingX = 10;
    private static final int graphPaddingY = 8;
    private static final int nodeWidth = 148;
    private static final int nodeHeight = 30;
    private static final int nodeGapX = 24;
    private static final int nodeGapY = 14;
    private static final int laneSegmentWidth = 9;
    private static final int laneSegmentHeight = 4;
    private static final int laneGap = 3;
    private static final int iconSize = 14;
    private static final int rowIconInset = 2;
    private static final int connectorThickness = 1;
    private static final int connectorKneeOffset = 10;
    private static final int focusScrollPadding = 10;

    private static final float defaultZoom = 0.92f;
    private static final float minZoom = 0.65f;
    private static final float maxZoom = 1.45f;
    private static final float zoomStep = 0.08f;

    private static final LayoutDirection layoutDirection = new LayoutDirection.LeftToRight();

    //endregion

    //region palette

    private static final int textPrimary = 0xFFFFFFFF;
    private static final int textSecondary = 0xFFF3EED8;
    private static final int textDim = 0xFFE2D9BE;
    private static final int textWarning = 0xFFD86A6A;
    private static final int activeCardTint = 0x26426FBF;
    private static final int pausedCardTint = 0x26B97A1E;
    private static final int failedCardTint = 0x309E3E3E;
    private static final int progressTrackColor = 0xFF686868;
    private static final int pendingSegmentColor = 0xFF8E8E8E;
    private static final int runningSegmentColor = 0xFF4B81D8;
    private static final int pausedSegmentColor = 0xFFB97A1E;
    private static final int completedSegmentColor = 0xFF4B8B47;
    private static final int failedSegmentColor = 0xFF9E3E3E;
    private static final int connectorColor = 0xFF6A6A6A;
    private static final int activeConnectorColor = 0xFF5C9AF2;

    //endregion

    private @Nullable ExecutionSession session;
    private @Nullable ExecutionOverlayViewModel viewModel;
    private int scrollX;
    private int scrollY;
    private float zoom = defaultZoom;
    private @Nullable String lastFocusNodeId;

    public ExecutionOverlayWidget() {
        onMouseScrolled((mouseX, mouseY, wheelX, wheelY, ctx) -> {
            if (mouseX < 0 || mouseY < 0 || mouseX > width() || mouseY > height()) {
                return EventDispatch.pass;
            }
            int bodyY = headerHeight;
            int bodyH = bodyHeight(height(), headerHeight, footerHeight);
            if (mouseY < bodyY || mouseY > bodyY + bodyH) {
                return EventDispatch.pass;
            }
            if (viewModel == null) {
                return EventDispatch.pass;
            }

            if (Screen.hasControlDown()) {
                float previousZoom = zoom;
                if (wheelY > 0) {
                    zoom = Math.min(maxZoom, zoom + zoomStep);
                } else if (wheelY < 0) {
                    zoom = Math.max(minZoom, zoom - zoomStep);
                }
                if (Math.abs(previousZoom - zoom) < 0.0001f) {
                    return EventDispatch.pass;
                }
                var layout = buildLayout();
                if (layout != null) {
                    clampScroll(layout);
                    alignScrollToFocus(layout, true);
                }
                ctx.cancel();
                return EventDispatch.consumed;
            }

            var layout = buildLayout();
            if (layout == null) {
                return EventDispatch.pass;
            }
            LayoutMetrics metrics = layoutMetrics();
            int maxScrollX = maxScrollX(layout);
            int maxScrollY = maxScrollY(layout);
            int step = Math.max(12, metrics.nodeHeight() / 2);
            if (Math.abs(wheelX) > Math.abs(wheelY) && maxScrollX > 0) {
                if (wheelX > 0) {
                    this.scrollX = Math.max(0, this.scrollX - step);
                } else if (wheelX < 0) {
                    this.scrollX = Math.min(maxScrollX, this.scrollX + step);
                }
            } else if (maxScrollY > 0) {
                if (wheelY > 0) {
                    this.scrollY = Math.max(0, this.scrollY - step);
                } else if (wheelY < 0) {
                    this.scrollY = Math.min(maxScrollY, this.scrollY + step);
                }
            }
            clampScroll(layout);
            ctx.cancel();
            return EventDispatch.consumed;
        });
    }

    //region state

    public void setSession(@Nullable ExecutionSession session) {
        var previousFocus = lastFocusNodeId;
        this.session = session;
        this.viewModel = session == null ? null : ExecutionOverlayViewModel.derive(session);
        if (viewModel == null) {
            scrollX = 0;
            scrollY = 0;
            lastFocusNodeId = null;
            return;
        }
        var layout = buildLayout();
        if (layout == null) {
            return;
        }
        clampScroll(layout);
        boolean focusChanged = !Objects.equals(previousFocus, viewModel.focusNodeId());
        alignScrollToFocus(layout, focusChanged);
        lastFocusNodeId = viewModel.focusNodeId();
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

    private LayoutMetrics layoutMetrics() {
        int scaledNodeWidth = Math.max(122, Math.round(nodeWidth * zoom));
        int scaledNodeHeight = Math.max(28, Math.round(nodeHeight * zoom));
        int scaledGapX = Math.max(14, Math.round(nodeGapX * zoom));
        int scaledGapY = Math.max(10, Math.round(nodeGapY * zoom));
        int scaledPaddingX = Math.max(8, Math.round(graphPaddingX * zoom));
        int scaledPaddingY = Math.max(6, Math.round(graphPaddingY * zoom));
        return new LayoutMetrics(scaledPaddingX, scaledPaddingY, scaledNodeWidth, scaledNodeHeight, scaledGapX, scaledGapY);
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
        var layout = buildLayout();
        if (layout != null) {
            clampScroll(layout);
            alignScrollToFocus(layout, false);
            drawBody(canvas, font, layout);
        }
    }

    private void drawEmpty(SceneCanvas canvas) {
        canvas.drawString(Component.translatable("cirrus.ui.execution.overlay.empty").getString(), panelPadding, panelPadding, textDim, true);
    }

    private void drawHeader(SceneCanvas canvas, Font font) {
        canvas.texture(CirrusTextures.Background.Outlined.inset, 2, 2, width() - 4, headerHeight - 2);

        String title = trim(font, viewModel.title().getString(), width() - panelPadding * 2 - 66);
        canvas.drawString(title, panelPadding, 5, textPrimary, true);

        int total = Math.max(0, viewModel.totalStepCount());
        int done = Math.max(0, Math.min(total, viewModel.completedStepCount()));
        int percent = total <= 0 ? 0 : Math.round(done * 100f / total);
        String progressText = done + "/" + total;
        canvas.drawString(progressText, width() - panelPadding - font.width(progressText), 5, textSecondary, true);

        int barX = panelPadding;
        int barY = 16;
        int barW = width() - panelPadding * 2;
        canvas.fill(barX, barY, barW, 4, progressTrackColor);
        int fillW = total <= 0 ? 0 : Math.round((barW - 2) * (done / (float) total));
        if (fillW > 0) {
            canvas.fill(barX + 1, barY + 1, fillW, 2, statusColor(viewModel.status()));
        }

        String statusText = viewModel.statusLabel().getString() + " " + percent + "%";
        canvas.drawString(statusText, panelPadding, 24, statusColor(viewModel.status()), true);
        String sideText = "";
        if (viewModel.sideHint() != null) {
            sideText = viewModel.sideHint().getString();
        }
        String sideTrimmed = trim(font, sideText, width() - panelPadding * 2 - 50);
        canvas.drawString(sideTrimmed, width() - panelPadding - font.width(sideTrimmed), 24, textDim, true);
    }

    private void drawFooter(SceneCanvas canvas, Font font) {
        int footerY = height() - footerHeight;
        canvas.texture(CirrusTextures.Background.Outlined.inset, 2, footerY, width() - 4, footerHeight - 2);

        int textX = panelPadding;
        ItemStack icon = viewModel.footerIcon();
        if (icon != null && !icon.isEmpty()) {
            drawItem(canvas, icon, panelPadding, footerY + 5, iconSize);
            textX = panelPadding + iconSize + 4;
        }

        String primary = footerPrimaryText(icon);
        int primaryColor = viewModel.footerWarning() ? textWarning : textPrimary;
        canvas.drawString(trim(font, primary, width() - textX - panelPadding), textX, footerY + 6, primaryColor, true);

        String secondary = footerSecondaryText();
        canvas.drawString(trim(font, secondary, width() - panelPadding * 2), panelPadding, footerY + 19, textDim, true);

        if (viewModel.footerWarning()) {
            canvas.texture(CirrusTextures.Icon.Sign.Amount.missing, width() - panelPadding - 6, footerY + 6, 5, 5);
        }
    }

    private String footerPrimaryText(@Nullable ItemStack icon) {
        if (icon != null && !icon.isEmpty()) {
            return icon.getHoverName().getString() + " x" + icon.getCount();
        }
        if (viewModel.footerSummary() != null) {
            return viewModel.footerSummary().getString();
        }
        return Component.translatable("cirrus.ui.execution.overlay.empty").getString();
    }

    private String footerSecondaryText() {
        if (viewModel.footerWarning()) {
            if (viewModel.footerDetail() != null) {
                return viewModel.footerDetail().getString();
            }
            return "Inventory warning";
        }
        return "Zoom " + Math.round(zoom * 100f) + "% | Ctrl+Wheel";
    }

    private void drawBody(SceneCanvas canvas, Font font, GraphLayoutResult layout) {
        int bodyY = headerHeight;
        int bodyH = bodyHeight(height(), headerHeight, footerHeight);
        var abs = absolutePos();
        canvas.withClip(abs.x(), abs.y() + bodyY, width(), bodyH, () -> {
            for (GraphLayoutNode node : layout.flattened()) {
                drawConnectors(canvas, node, bodyY);
            }
            for (GraphLayoutNode node : layout.flattened()) {
                int x = node.x() - scrollX;
                int y = bodyY + node.y() - scrollY;
                if (x + node.width() < 0 || x > width() || y + node.height() < bodyY || y > bodyY + bodyH) {
                    continue;
                }
                drawNode(canvas, font, node.node(), x, y, node.width(), node.height());
            }
        });
    }

    private void drawConnectors(SceneCanvas canvas, GraphLayoutNode parent, int bodyY) {
        if (parent.children().isEmpty()) {
            return;
        }
        if (layoutDirection instanceof LayoutDirection.LeftToRight) {
            drawLeftToRightConnectorGroup(canvas, parent, bodyY);
        } else {
            drawTopToBottomConnectorGroup(canvas, parent, bodyY);
        }
    }

    private void drawLeftToRightConnectorGroup(SceneCanvas canvas, GraphLayoutNode parent, int bodyY) {
        int startX = parent.right() - scrollX;
        int startY = bodyY + parent.centerY() - scrollY;
        if (parent.children().size() == 1) {
            GraphLayoutNode child = parent.children().getFirst();
            int endX = child.x() - scrollX;
            int endY = bodyY + child.centerY() - scrollY;
            int midX = startX + Math.max(connectorKneeOffset, (endX - startX) / 2);
            int color = child.node().activeBranch() ? activeConnectorColor : connectorColor;
            drawHorizontal(canvas, startX, startY, midX, color);
            drawVertical(canvas, midX, startY, endY, color);
            drawHorizontal(canvas, midX, endY, endX, color);
            drawConnectorJoint(canvas, midX, endY, color);
            return;
        }

        int firstY = Integer.MAX_VALUE;
        int lastY = Integer.MIN_VALUE;
        int nearestChildX = Integer.MAX_VALUE;
        for (GraphLayoutNode child : parent.children()) {
            int childY = bodyY + child.centerY() - scrollY;
            firstY = Math.min(firstY, childY);
            lastY = Math.max(lastY, childY);
            nearestChildX = Math.min(nearestChildX, child.x() - scrollX);
        }
        int trunkX = startX + Math.max(connectorKneeOffset, (nearestChildX - startX) / 2);
        drawHorizontal(canvas, startX, startY, trunkX, connectorColor);
        drawVertical(canvas, trunkX, firstY, lastY, connectorColor);
        drawConnectorJoint(canvas, trunkX, startY, connectorColor);

        for (GraphLayoutNode child : parent.children()) {
            int childY = bodyY + child.centerY() - scrollY;
            int childX = child.x() - scrollX;
            int color = child.node().activeBranch() ? activeConnectorColor : connectorColor;
            drawHorizontal(canvas, trunkX, childY, childX, color);
            drawConnectorJoint(canvas, trunkX, childY, color);
        }
    }

    private void drawTopToBottomConnectorGroup(SceneCanvas canvas, GraphLayoutNode parent, int bodyY) {
        int startX = parent.centerX() - scrollX;
        int startY = bodyY + parent.bottom() - scrollY;
        if (parent.children().size() == 1) {
            GraphLayoutNode child = parent.children().getFirst();
            int endX = child.centerX() - scrollX;
            int endY = bodyY + child.y() - scrollY;
            int midY = startY + Math.max(connectorKneeOffset, (endY - startY) / 2);
            int color = child.node().activeBranch() ? activeConnectorColor : connectorColor;
            drawVertical(canvas, startX, startY, midY, color);
            drawHorizontal(canvas, startX, midY, endX, color);
            drawVertical(canvas, endX, midY, endY, color);
            drawConnectorJoint(canvas, endX, midY, color);
            return;
        }

        int firstX = Integer.MAX_VALUE;
        int lastX = Integer.MIN_VALUE;
        int nearestChildY = Integer.MAX_VALUE;
        for (GraphLayoutNode child : parent.children()) {
            int childX = child.centerX() - scrollX;
            firstX = Math.min(firstX, childX);
            lastX = Math.max(lastX, childX);
            nearestChildY = Math.min(nearestChildY, bodyY + child.y() - scrollY);
        }
        int trunkY = startY + Math.max(connectorKneeOffset, (nearestChildY - startY) / 2);
        drawVertical(canvas, startX, startY, trunkY, connectorColor);
        drawHorizontal(canvas, firstX, trunkY, lastX, connectorColor);
        drawConnectorJoint(canvas, startX, trunkY, connectorColor);

        for (GraphLayoutNode child : parent.children()) {
            int childX = child.centerX() - scrollX;
            int childY = bodyY + child.y() - scrollY;
            int color = child.node().activeBranch() ? activeConnectorColor : connectorColor;
            drawVertical(canvas, childX, trunkY, childY, color);
            drawConnectorJoint(canvas, childX, trunkY, color);
        }
    }

    private void drawHorizontal(SceneCanvas canvas, int firstX, int y, int secondX, int color) {
        int x = Math.min(firstX, secondX);
        int width = Math.max(connectorThickness, Math.abs(secondX - firstX));
        canvas.fill(x, y, width, connectorThickness, color);
    }

    private void drawVertical(SceneCanvas canvas, int x, int firstY, int secondY, int color) {
        int y = Math.min(firstY, secondY);
        int height = Math.max(connectorThickness, Math.abs(secondY - firstY));
        canvas.fill(x, y, connectorThickness, height, color);
    }

    private void drawConnectorJoint(SceneCanvas canvas, int x, int y, int color) {
        canvas.fill(x - 1, y - 1, 3, 3, color);
    }

    private void drawNode(SceneCanvas canvas, Font font, ExecutionOverlayNode node, int cardX, int y, int cardW, int cardH) {
        canvas.texture(CirrusTextures.Background.Outlined.inset, cardX, y, cardW, cardH);
        if (node.status() instanceof ExecutionFlowStatus.Running && node.activeBranch()) {
            canvas.fill(cardX + 1, y + 1, cardW - 2, cardH - 2, activeCardTint);
        } else if (node.status() instanceof ExecutionFlowStatus.Paused) {
            canvas.fill(cardX + 1, y + 1, cardW - 2, cardH - 2, pausedCardTint);
        } else if (node.status() instanceof ExecutionFlowStatus.Failed) {
            canvas.fill(cardX + 1, y + 1, cardW - 2, cardH - 2, failedCardTint);
        }

        int textStart = cardX + 4;
        if (node.iconStack() != null && !node.iconStack().isEmpty()) {
            drawItem(canvas, node.iconStack(), cardX + rowIconInset, y + rowIconInset, iconSize);
            textStart = cardX + iconSize + 6;
        }

        int textWidth = Math.max(0, cardX + cardW - textStart - 6);
        String label = trim(font, node.label().getString(), textWidth);
        boolean showSubtitle = cardH >= 32
            && node.subtitle() != null
            && (node.activeBranch() || node.status() instanceof ExecutionFlowStatus.Paused || node.status() instanceof ExecutionFlowStatus.Failed);

        if (showSubtitle) {
            canvas.drawString(label, textStart, y + 4, textPrimary, true);
            canvas.drawString(trim(font, node.subtitle().getString(), textWidth), textStart, y + 16, textDim, true);
        } else {
            int baseline = y + Math.max(3, (cardH - 8) / 2);
            canvas.drawString(label, textStart, baseline, textPrimary, true);
        }

        int laneY = y + cardH - 6;
        drawLane(canvas, node.laneSegments(), cardX + 4, laneY, cardX + cardW - 4);
    }

    private void drawLane(SceneCanvas canvas, ImmutableList<ExecutionOverlayLaneSegment> segments, int x, int y, int maxX) {
        int currentX = x;
        for (int i = 0; i < segments.size(); i++) {
            if (currentX + laneSegmentWidth > maxX) {
                break;
            }
            var segment = segments.get(i);
            canvas.texture(CirrusTextures.Background.Slot.dark, currentX, y, laneSegmentWidth, laneSegmentHeight);
            canvas.fill(currentX + 1, y + 1, laneSegmentWidth - 2, laneSegmentHeight - 2, statusColor(segment.status()));
            if (segment.current()) {
                canvas.texture(CirrusTextures.Background.Outlined.flat, currentX - 1, y - 1, laneSegmentWidth + 2, laneSegmentHeight + 2);
            }
            currentX += laneSegmentWidth;
            if (i < segments.size() - 1 && currentX + laneGap <= maxX) {
                canvas.texture(CirrusTextures.Background.Sign.bar, currentX, y, laneGap, laneSegmentHeight);
                currentX += laneGap;
            }
        }
    }

    //endregion

    //region helpers

    private static int statusColor(ExecutionFlowStatus status) {
        return switch (status) {
            case ExecutionFlowStatus.Running ignored -> runningSegmentColor;
            case ExecutionFlowStatus.Paused ignored -> pausedSegmentColor;
            case ExecutionFlowStatus.Completed ignored -> completedSegmentColor;
            case ExecutionFlowStatus.Failed ignored -> failedSegmentColor;
            default -> pendingSegmentColor;
        };
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

    private void clampScroll(GraphLayoutResult layout) {
        scrollX = Math.max(0, Math.min(maxScrollX(layout), scrollX));
        scrollY = Math.max(0, Math.min(maxScrollY(layout), scrollY));
    }

    private void alignScrollToFocus(GraphLayoutResult layout, boolean centerOnFocus) {
        if (layout.focusNode() == null) {
            return;
        }
        GraphLayoutNode focus = layout.focusNode();
        int bodyH = bodyHeight(height(), headerHeight, footerHeight);
        int focusLeft = focus.x();
        int focusRight = focus.right();
        int focusTop = focus.y();
        int focusBottom = focus.bottom();

        if (centerOnFocus) {
            scrollX = focusLeft - Math.max(0, (width() - focus.width()) / 2);
            scrollY = focusTop - Math.max(0, (bodyH - focus.height()) / 2);
            clampScroll(layout);
            return;
        }
        if (focusLeft < scrollX + focusScrollPadding) {
            scrollX = Math.max(0, focusLeft - focusScrollPadding);
        } else if (focusRight > scrollX + width() - focusScrollPadding) {
            scrollX = Math.max(0, Math.min(maxScrollX(layout), focusRight - width() + focusScrollPadding));
        }
        if (focusTop < scrollY + focusScrollPadding) {
            scrollY = Math.max(0, focusTop - focusScrollPadding);
        } else if (focusBottom > scrollY + bodyH - focusScrollPadding) {
            scrollY = Math.max(0, Math.min(maxScrollY(layout), focusBottom - bodyH + focusScrollPadding));
        }
    }

    private int maxScrollX(GraphLayoutResult layout) {
        return Math.max(0, layout.contentWidth() - width());
    }

    private int maxScrollY(GraphLayoutResult layout) {
        return Math.max(0, layout.contentHeight() - bodyHeight(height(), headerHeight, footerHeight));
    }

    private @Nullable GraphLayoutResult buildLayout() {
        if (viewModel == null) {
            return null;
        }
        LayoutMetrics metrics = layoutMetrics();
        GraphLayoutNode root;
        if (layoutDirection instanceof LayoutDirection.LeftToRight) {
            int[] nextY = new int[]{metrics.paddingY()};
            root = layoutNodeLeftToRight(viewModel.root(), 0, nextY, metrics);
        } else {
            int[] nextX = new int[]{metrics.paddingX()};
            root = layoutNodeTopToBottom(viewModel.root(), 0, nextX, metrics);
        }

        var flattened = Lists.mutable.<GraphLayoutNode>empty();
        collect(root, flattened);
        int contentWidth = 0;
        int contentHeight = 0;
        GraphLayoutNode focus = null;
        for (GraphLayoutNode node : flattened) {
            contentWidth = Math.max(contentWidth, node.right() + metrics.paddingX());
            contentHeight = Math.max(contentHeight, node.bottom() + metrics.paddingY());
            if (viewModel.focusNodeId() != null && viewModel.focusNodeId().equals(node.node().nodeId())) {
                focus = node;
            }
        }
        return new GraphLayoutResult(flattened.toImmutable(), contentWidth, contentHeight, focus);
    }

    private GraphLayoutNode layoutNodeLeftToRight(ExecutionOverlayNode node, int depth, int[] nextY, LayoutMetrics metrics) {
        var children = Lists.mutable.<GraphLayoutNode>empty();
        if (node.expanded()) {
            for (ExecutionOverlayNode child : node.children()) {
                children.add(layoutNodeLeftToRight(child, depth + 1, nextY, metrics));
            }
        }

        int x = metrics.paddingX() + depth * (metrics.nodeWidth() + metrics.gapX());
        int y;
        if (children.isEmpty()) {
            y = nextY[0];
            nextY[0] += metrics.nodeHeight() + metrics.gapY();
        } else {
            int first = children.getFirst().centerY();
            int last = children.getLast().centerY();
            y = (first + last) / 2 - metrics.nodeHeight() / 2;
        }
        return new GraphLayoutNode(node, x, y, metrics.nodeWidth(), metrics.nodeHeight(), children.toImmutable());
    }

    private GraphLayoutNode layoutNodeTopToBottom(ExecutionOverlayNode node, int depth, int[] nextX, LayoutMetrics metrics) {
        var children = Lists.mutable.<GraphLayoutNode>empty();
        if (node.expanded()) {
            for (ExecutionOverlayNode child : node.children()) {
                children.add(layoutNodeTopToBottom(child, depth + 1, nextX, metrics));
            }
        }

        int y = metrics.paddingY() + depth * (metrics.nodeHeight() + metrics.gapY());
        int x;
        if (children.isEmpty()) {
            x = nextX[0];
            nextX[0] += metrics.nodeWidth() + metrics.gapX();
        } else {
            int first = children.getFirst().centerX();
            int last = children.getLast().centerX();
            x = (first + last) / 2 - metrics.nodeWidth() / 2;
        }
        return new GraphLayoutNode(node, x, y, metrics.nodeWidth(), metrics.nodeHeight(), children.toImmutable());
    }

    private void collect(GraphLayoutNode node, org.eclipse.collections.api.list.MutableList<GraphLayoutNode> out) {
        out.add(node);
        for (GraphLayoutNode child : node.children()) {
            collect(child, out);
        }
    }

    private record LayoutMetrics(
        int paddingX,
        int paddingY,
        int nodeWidth,
        int nodeHeight,
        int gapX,
        int gapY
    ) {
    }

    private record GraphLayoutNode(
        ExecutionOverlayNode node,
        int x,
        int y,
        int width,
        int height,
        ImmutableList<GraphLayoutNode> children
    ) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        int centerX() {
            return x + width / 2;
        }

        int centerY() {
            return y + height / 2;
        }
    }

    private record GraphLayoutResult(
        ImmutableList<GraphLayoutNode> flattened,
        int contentWidth,
        int contentHeight,
        @Nullable GraphLayoutNode focusNode
    ) {
    }

    private sealed interface LayoutDirection permits LayoutDirection.LeftToRight, LayoutDirection.TopToBottom {
        record LeftToRight() implements LayoutDirection {
        }

        record TopToBottom() implements LayoutDirection {
        }
    }

    //endregion
}

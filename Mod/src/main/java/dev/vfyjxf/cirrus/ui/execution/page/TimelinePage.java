package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionTelemetrySnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriver;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayLaneSegment;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayNode;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayRecipePreview;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayViewModel;
import dev.vfyjxf.cirrus.ui.CirrusMouse;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeTooltipPreview;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;

public class TimelinePage extends Widget {

    private static final int pad = ExecutionOverlayUi.bodyPadding;
    private static final int overviewHeight = 30;
    private static final int overviewHeightCompact = 26;
    private static final int overviewHeightDense = 22;
    private static final int overviewGap = 4;
    private static final int overviewInset = 4;
    private static final int overviewStatusCell = 16;
    private static final int overviewProgressHeight = 12;
    private static final int overviewProgressMinWidth = 28;
    private static final int overviewSlotGap = 3;
    private static final int overviewArrowWidth = 10;
    private static final int laneTop = 2;
    private static final int laneWidth = 2;
    private static final int laneIndent = 10;
    private static final int nodeRowHeight = 30;
    private static final int nodeRowHeightCompact = 28;
    private static final int nodeRowHeightDense = 26;
    private static final int nodeRowGap = 2;
    private static final int nodeStatusDot = 5;
    private static final int laneStripHeight = 5;
    private static final int laneStripGap = 1;
    private static final int laneStripMinGroupWidth = 4;
    private static final int laneStripMaxGroups = 24;
    private static final int laneStripRepeatPatternMaxLength = 16;
    private static final int timelineScrollbarWidth = 4;
    private static final int timelineScrollbarWidthDense = 3;
    private static final int timelineScrollbarVisualWidth = 2;
    private static final int timelineBodyInset = 3;
    private static final int timelineBodyInsetDense = 2;

    private final ExecutionOverlayViewState state;
    private final List<InteractiveArea> interactiveAreas = new ArrayList<>();

    private float scrollY;
    private float targetScrollY;
    private boolean draggingScrollbar;
    private float scrollbarDragOffsetY;
    private boolean statusButtonPressed;
    private final ScrollState wheelScrollState = CirrusScroll.longVerticalState(18);

    public TimelinePage(ExecutionOverlayViewState state) {
        this.state = state;
        useStyle(background(Background.flat));
        setTickable(true);

        onMouseScrolled((mouseX, mouseY, scrollX, scrollDelta, context) -> {
            Rect2i timelineArea = timelineBodyArea();
            if (timelineArea.getWidth() <= 0 || timelineArea.getHeight() <= 0) {
                return EventDispatch.pass;
            }
            int localX = (int) Math.round(mouseX);
            int localY = (int) Math.round(mouseY);
            if (!ExecutionOverlayUi.contains(timelineArea, localX, localY)) {
                return EventDispatch.pass;
            }
            targetScrollY = clamp(targetScrollY + wheelScrollState.wheelDelta(0, scrollDelta).y(), 0f, maxScroll());
            return EventDispatch.consumed;
        });
        onMouseClicked((input, context) -> {
            if (!input.isLeftClick()) {
                stopScrollbarDrag();
                statusButtonPressed = false;
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            int mouseX = (int) Math.round(relative.x());
            int mouseY = (int) Math.round(relative.y());

            Rect2i thumb = scrollbarThumbArea();
            if (thumb != null && ExecutionOverlayUi.contains(thumb, mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarDragOffsetY = mouseY - thumb.getY();
                return EventDispatch.consumed;
            }
            stopScrollbarDrag();

            Rect2i track = scrollbarTrackArea();
            if (track != null && ExecutionOverlayUi.contains(track, mouseX, mouseY)) {
                targetScrollY = jumpScrollTo(mouseY);
                scrollY = targetScrollY;
                return EventDispatch.consumed;
            }

            Rect2i statusButton = statusButtonArea();
            if (ExecutionOverlayUi.contains(statusButton, mouseX, mouseY)) {
                statusButtonPressed = true;
                return EventDispatch.consumed;
            }

            for (InteractiveArea area : interactiveAreas) {
                if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY)) {
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            int mouseX = (int) Math.round(relative.x());
            int mouseY = (int) Math.round(relative.y());
            if (!ExecutionOverlayUi.contains(statusButtonArea(), mouseX, mouseY)) {
                return EventDispatch.pass;
            }
            togglePlayback(input);
            statusButtonPressed = false;
            context.consume();
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (!draggingScrollbar) {
                return EventDispatch.pass;
            }
            if (!input.isLeftClick() || !CirrusMouse.leftButtonDown()) {
                stopScrollbarDrag();
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            targetScrollY = dragScrollTo((float) relative.y());
            scrollY = targetScrollY;
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            if (!draggingScrollbar) {
                statusButtonPressed = false;
                return EventDispatch.pass;
            }
            stopScrollbarDrag();
            statusButtonPressed = false;
            return EventDispatch.consumed;
        });
        onMouseLeave((mouseX, mouseY, context) -> {
            stopScrollbarDrag();
            statusButtonPressed = false;
        });
    }

    @Override
    public void tick() {
        super.tick();
        float delta = targetScrollY - scrollY;
        if (Math.abs(delta) < 0.5f) {
            scrollY = targetScrollY;
        } else {
            scrollY += delta * 0.35f;
        }
        float max = maxScroll();
        if (scrollY > max) {
            scrollY = max;
        }
        if (targetScrollY > max) {
            targetScrollY = max;
        }
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        interactiveAreas.clear();

        Font font = context().font();
        int pagePad = resolvePagePadding(width(), height());
        int x = pagePad;
        int y = pagePad;
        int availableWidth = Math.max(0, width() - pagePad * 2);
        int bottom = height() - pagePad;
        int overviewHeight = resolveOverviewHeight(width(), height());

        if (state.calculating()) {
            drawCenteredMessage(
                canvas,
                font,
                new Rect2i(x, y, availableWidth, Math.max(0, bottom - y)),
                ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculating)
            );
            return;
        }

        ExecutionDriverSnapshot snapshot = state.snapshot();
        ExecutionOverlayViewModel viewModel = snapshot == null ? null : snapshot.viewModel();
        ExecutionTelemetrySnapshot telemetry = state.telemetry();

        if (snapshot == null || viewModel == null) {
            drawCenteredMessage(
                canvas,
                font,
                new Rect2i(x, y, availableWidth, Math.max(0, bottom - y)),
                ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.empty)
            );
            return;
        }

        drawOverviewBar(canvas, font, new Rect2i(x, y, availableWidth, overviewHeight), viewModel, telemetry, mouseX, mouseY);
        y += overviewHeight + resolveOverviewGap(width(), height());

        Rect2i timelineArea = new Rect2i(x, y, availableWidth, Math.max(0, bottom - y));
        drawTimelineList(canvas, font, timelineArea, viewModel, mouseX, mouseY);
    }

    private void drawCenteredMessage(SceneCanvas canvas, Font font, Rect2i area, String message) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0) {
            return;
        }
        canvas.drawString(
            ExecutionOverlayUi.trim(font, message, Math.max(0, area.getWidth() - 4)),
            area.getX() + 2,
            area.getY() + Math.max(0, (area.getHeight() - font.lineHeight) / 2),
            ExecutionOverlayUi.textSecondary,
            false
        );
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        for (InteractiveArea area : interactiveAreas) {
            if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY)) {
                return area.tooltip();
            }
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    //region overview

    private void drawOverviewBar(
        SceneCanvas canvas,
        Font font,
        Rect2i area,
        ExecutionOverlayViewModel viewModel,
        @Nullable ExecutionTelemetrySnapshot telemetry,
        int mouseX,
        int mouseY
    ) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0) {
            return;
        }

        int inset = resolveOverviewInset(area.getWidth(), area.getHeight());

        int innerX = area.getX() + inset;
        int innerY = area.getY() + inset;
        int innerWidth = Math.max(0, area.getWidth() - inset * 2);
        int innerHeight = Math.max(0, area.getHeight() - inset * 2);
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }

        ExecutionOverlayNode currentNode = currentNode(viewModel);
        ExecutionOverlayNode nextNode = nextNode(viewModel);

        int statusWidth = overviewStatusCell;
        boolean showFocus = innerWidth >= 102 && area.getHeight() >= overviewHeightCompact;
        int focusWidth = showFocus ? ExecutionOverlayUi.slotSize * 2 + overviewArrowWidth + overviewSlotGap * 2 : 0;

        int cursorX = innerX;
        Rect2i statusArea = new Rect2i(cursorX, innerY + Math.max(0, (innerHeight - overviewStatusCell) / 2), statusWidth, overviewStatusCell);
        cursorX += statusWidth + 4;

        int trailingWidth = showFocus ? focusWidth + 4 : 0;
        int progressWidth = Math.max(overviewProgressMinWidth, innerX + innerWidth - cursorX - trailingWidth);
        Rect2i progressArea = new Rect2i(cursorX, innerY + Math.max(0, (innerHeight - overviewProgressHeight) / 2), progressWidth, overviewProgressHeight);
        cursorX += progressWidth + 4;

        @Nullable Rect2i currentArea = null;
        @Nullable Rect2i nextArea = null;
        if (showFocus) {
            currentArea = new Rect2i(cursorX, innerY + Math.max(0, (innerHeight - ExecutionOverlayUi.slotSize) / 2), ExecutionOverlayUi.slotSize, ExecutionOverlayUi.slotSize);
            cursorX += ExecutionOverlayUi.slotSize + overviewSlotGap;
            drawArrowIcon(canvas, cursorX, innerY + Math.max(0, (innerHeight - overviewArrowWidth) / 2));
            cursorX += overviewArrowWidth + overviewSlotGap;
            nextArea = new Rect2i(cursorX, innerY + Math.max(0, (innerHeight - ExecutionOverlayUi.slotSize) / 2), ExecutionOverlayUi.slotSize, ExecutionOverlayUi.slotSize);
        }

        boolean playbackRunning = playbackRunning();
        drawOverviewStatus(canvas, statusArea, playbackRunning, mouseX, mouseY);
        drawOverviewProgress(canvas, font, progressArea, viewModel, telemetry);
        if (currentArea != null) {
            ExecutionOverlayUi.drawSlot(canvas, focusContent(currentNode), currentArea.getX(), currentArea.getY());
        }
        if (nextArea != null) {
            ExecutionOverlayUi.drawSlot(canvas, focusContent(nextNode), nextArea.getX(), nextArea.getY());
        }

        interactiveAreas.add(new InteractiveArea(statusArea, summaryStatusTooltip(viewModel, telemetry, playbackRunning)));
        interactiveAreas.add(new InteractiveArea(progressArea, summaryProgressTooltip(viewModel, telemetry)));
        if (currentArea != null) {
            interactiveAreas.add(new InteractiveArea(currentArea, currentNodeTooltip(currentNode, telemetry)));
        }
        if (nextArea != null) {
            interactiveAreas.add(new InteractiveArea(nextArea, nodeTooltip(nextNode, telemetry)));
        }
    }

    private void drawOverviewStatus(SceneCanvas canvas, Rect2i area, boolean playbackRunning, int mouseX, int mouseY) {
        boolean hovered = ExecutionOverlayUi.contains(area, mouseX, mouseY);
        boolean down = statusButtonPressed || playbackRunning;
        CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), down, hovered, true);
        drawPlaybackIcon(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), playbackRunning, down);
    }

    private void drawOverviewProgress(
        SceneCanvas canvas,
        Font font,
        Rect2i area,
        ExecutionOverlayViewModel viewModel,
        @Nullable ExecutionTelemetrySnapshot telemetry
    ) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0) {
            return;
        }
        canvas.texture(Background.inset, area.getX(), area.getY(), area.getWidth(), area.getHeight());
        int trackX = area.getX() + 1;
        int trackY = area.getY() + 1;
        int trackWidth = Math.max(0, area.getWidth() - 2);
        int trackHeight = Math.max(0, area.getHeight() - 2);
        int accentColor = telemetry != null && telemetry.paused()
            ? ExecutionOverlayUi.warning
            : ExecutionOverlayUi.accent;
        canvas.fill(trackX, trackY, Math.round(trackWidth * progressRatio(viewModel, telemetry)), trackHeight, accentColor);

        String label = progressLabel(viewModel, telemetry);
        int textColor = telemetry != null && telemetry.errorSummary() != null && !telemetry.errorSummary().isBlank()
            ? ExecutionOverlayUi.warning
            : ExecutionOverlayUi.titleColor;
        canvas.drawString(
            ExecutionOverlayUi.trim(font, label, Math.max(0, area.getWidth() - 6)),
            area.getX() + 3,
            area.getY() + 2,
            textColor,
            false
        );
    }

    private void drawArrowIcon(SceneCanvas canvas, int x, int y) {
        canvas.texture(CirrusTextures.Icon.arrowRight, x, y, CirrusTextures.Icon.arrowRight.width(), CirrusTextures.Icon.arrowRight.height());
    }

    //endregion

    //region list

    private void drawTimelineList(
        SceneCanvas canvas,
        Font font,
        Rect2i area,
        ExecutionOverlayViewModel viewModel,
        int mouseX,
        int mouseY
    ) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0) {
            return;
        }

        canvas.texture(Background.Outlined.flat, area.getX(), area.getY(), area.getWidth(), area.getHeight());

        Rect2i clipArea = timelineBodyArea(area);
        if (clipArea.getWidth() <= 0 || clipArea.getHeight() <= 0) {
            return;
        }

        int contentHeight = timelineContentHeight(viewModel.visibleNodes());
        syncScrollBounds(contentHeight, clipArea.getHeight());

        int absX = absolutePos().x() + clipArea.getX();
        int absY = absolutePos().y() + clipArea.getY();
        canvas.pushClip(absX, absY, clipArea.getWidth(), clipArea.getHeight());
        canvas.pushTransform();
        canvas.translate(0, -scrollY);

        int rowY = clipArea.getY();
        int rowHeight = resolveNodeRowHeight(width(), height());
        int rowGap = resolveNodeRowGap(width(), height());
        int hoveredX = mouseX;
        int hoveredY = mouseY + (int) Math.round(scrollY);
        for (ExecutionOverlayNode node : viewModel.visibleNodes()) {
            Rect2i row = new Rect2i(clipArea.getX(), rowY, clipArea.getWidth(), rowHeight);
            drawTimelineRow(canvas, font, row, node, hoveredX, hoveredY);
            addTimelineRowInteractiveAreas(row, node, state.telemetry(), (int) Math.round(scrollY));
            rowY += rowHeight + rowGap;
        }

        canvas.popTransform();
        canvas.popClip();

        drawScrollbar(canvas, clipArea, contentHeight);
    }

    private void drawTimelineRow(SceneCanvas canvas, Font font, Rect2i area, ExecutionOverlayNode node, int mouseX, int mouseY) {
        boolean hovered = ExecutionOverlayUi.contains(area, mouseX, mouseY);
        boolean active = node.activeBranch();
        if (active || hovered) {
            canvas.fill(
                area.getX(),
                area.getY(),
                area.getWidth(),
                area.getHeight(),
                active ? withAlpha(ExecutionOverlayUi.accent, 0x18) : 0x10FFFFFF
            );
        }

        int accentColor = statusDotColor(node);
        if (active) {
            canvas.fill(area.getX() + 1, area.getY() + 1, 2, Math.max(0, area.getHeight() - 2), withAlpha(accentColor, 0xD8));
        }

        int depth = Math.max(0, node.depth());
        int railX = resolveRailX(area, depth);
        if (depth > 0) {
            canvas.fill(railX - 6, area.getY() + laneTop, laneWidth, Math.max(0, area.getHeight() - laneTop * 2), withAlpha(ExecutionOverlayUi.textSecondary, 0x50));
        }
        canvas.fill(railX - nodeStatusDot / 2, area.getY() + 8, nodeStatusDot, nodeStatusDot, accentColor);

        int slotX = railX + 4;
        int slotY = area.getY() + Math.max(0, (area.getHeight() - ExecutionOverlayUi.slotSize) / 2);
        ExecutionOverlayUi.drawSlot(canvas, focusContent(node), slotX, slotY);

        int contentX = slotX + ExecutionOverlayUi.slotSize + 5;
        int right = area.getX() + area.getWidth() - 5;
        Rect2i stripArea = new Rect2i(
            contentX,
            area.getY() + Math.max(0, (area.getHeight() - laneStripHeight) / 2),
            Math.max(0, right - contentX),
            laneStripHeight
        );

        drawLaneStrip(canvas, stripArea, node.laneSegments());
    }

    private void addTimelineRowInteractiveAreas(
        Rect2i row,
        ExecutionOverlayNode node,
        @Nullable ExecutionTelemetrySnapshot telemetry,
        int scrollOffset
    ) {
        Rect2i stripArea = laneStripArea(row, node);
        if (stripArea.getWidth() > 0 && stripArea.getHeight() > 0 && node.laneSegments().notEmpty()) {
            for (SegmentArea segmentArea : segmentAreas(stripArea, node.laneSegments())) {
                Rect2i bounds = segmentArea.bounds();
                interactiveAreas.add(new InteractiveArea(
                    new Rect2i(bounds.getX(), bounds.getY() - scrollOffset, bounds.getWidth(), bounds.getHeight()),
                    segmentTooltip(node, segmentArea.group(), telemetry)
                ));
            }
        }
        interactiveAreas.add(new InteractiveArea(
            new Rect2i(row.getX(), row.getY() - scrollOffset, row.getWidth(), row.getHeight()),
            nodeTooltip(node, telemetry)
        ));
    }

    private void drawScrollbar(SceneCanvas canvas, Rect2i clipArea, int contentHeight) {
        Rect2i track = scrollbarTrackArea();
        if (track == null) {
            return;
        }
        if (contentHeight <= clipArea.getHeight()) {
            return;
        }
        Rect2i thumb = scrollbarThumbArea();
        if (thumb != null) {
            int visualWidth = Math.min(timelineScrollbarVisualWidth, thumb.getWidth());
            int visualX = thumb.getX() + Math.max(0, (thumb.getWidth() - visualWidth) / 2);
            canvas.fill(visualX, thumb.getY(), visualWidth, thumb.getHeight(), withAlpha(ExecutionOverlayUi.textSecondary, 0x88));
        }
    }

    private void drawLaneStrip(SceneCanvas canvas, Rect2i area, ImmutableList<ExecutionOverlayLaneSegment> segments) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0) {
            return;
        }

        canvas.fill(area.getX(), area.getY(), area.getWidth(), area.getHeight(), withAlpha(ExecutionOverlayUi.textSecondary, 0x20));
        if (segments.isEmpty()) {
            canvas.fill(area.getX(), area.getY() + 1, area.getWidth(), Math.max(1, area.getHeight() - 2), withAlpha(ExecutionOverlayUi.textSecondary, 0x38));
            return;
        }

        for (SegmentArea segmentArea : segmentAreas(area, segments)) {
            Rect2i bounds = segmentArea.bounds();
            LaneSegmentGroup group = segmentArea.group();
            ExecutionOverlayLaneSegment segment = group.representative();
            int height = group.current() ? area.getHeight() : Math.max(1, area.getHeight() - 1);
            int y = area.getY() + (group.current() ? 0 : 1);
            canvas.fill(
                bounds.getX(),
                y,
                bounds.getWidth(),
                height,
                withAlpha(statusDotColor(segment.statusText(), group.current()), group.current() ? 0xF0 : 0xCC)
            );
            if (group.count() > 1 && bounds.getWidth() > 3 && height > 2) {
                canvas.fill(
                    bounds.getX() + 1,
                    y + height - 1,
                    Math.max(1, bounds.getWidth() - 2),
                    1,
                    withAlpha(0xFFFFFFFF, 0x50)
                );
            }
        }
    }

    private Rect2i laneStripArea(Rect2i area, ExecutionOverlayNode node) {
        int depth = Math.max(0, node.depth());
        int railX = resolveRailX(area, depth);
        int slotX = railX + 4;
        int contentX = slotX + ExecutionOverlayUi.slotSize + 5;
        int right = area.getX() + area.getWidth() - 5;
        return new Rect2i(
            contentX,
            area.getY() + Math.max(0, (area.getHeight() - laneStripHeight) / 2),
            Math.max(0, right - contentX),
            laneStripHeight
        );
    }

    private List<SegmentArea> segmentAreas(Rect2i area, ImmutableList<ExecutionOverlayLaneSegment> segments) {
        if (area.getWidth() <= 0 || area.getHeight() <= 0 || segments.isEmpty()) {
            return List.of();
        }
        List<LaneSegmentGroup> groups = compressedLaneGroups(segments, area.getWidth());
        if (groups.isEmpty()) {
            return List.of();
        }
        ArrayList<SegmentArea> out = new ArrayList<>(groups.size());
        int x = area.getX();
        for (int index = 0; index < groups.size(); index++) {
            LaneSegmentGroup group = groups.get(index);
            int remainingGroups = groups.size() - index;
            int remainingWidth = area.getX() + area.getWidth() - x - Math.max(0, remainingGroups - 1) * laneStripGap;
            int width = Math.max(1, remainingWidth / Math.max(1, remainingGroups));
            int height = group.current() ? area.getHeight() : Math.max(1, area.getHeight() - 1);
            int y = area.getY() + (group.current() ? 0 : 1);
            out.add(new SegmentArea(new Rect2i(x, y, width, height), group));
            x += width + laneStripGap;
        }
        return out;
    }

    static List<LaneSegmentGroup> compressedLaneGroups(ImmutableList<ExecutionOverlayLaneSegment> segments, int stripWidth) {
        if (segments.isEmpty()) {
            return List.of();
        }
        ArrayList<String> fingerprints = new ArrayList<>(segments.size());
        for (ExecutionOverlayLaneSegment segment : segments) {
            fingerprints.add(segmentFingerprint(segment));
        }

        List<LaneSegmentGroup> groups = compressRepeatedGroups(segments, fingerprints, 0, segments.size());
        int maxGroups = resolveLaneStripGroupCount(stripWidth, segments.size());
        if (groups.size() <= maxGroups) {
            return groups;
        }
        return compressGroupRanges(groups, 0, groups.size(), maxGroups);
    }

    private static List<LaneSegmentGroup> compressRepeatedGroups(
        ImmutableList<ExecutionOverlayLaneSegment> segments,
        List<String> fingerprints,
        int startInclusive,
        int endExclusive
    ) {
        if (startInclusive >= endExclusive) {
            return List.of();
        }
        if (endExclusive - startInclusive == 1) {
            return List.of(groupForRange(segments, startInclusive, endExclusive));
        }

        RepeatedBlock block = bestRepeatedBlock(fingerprints, startInclusive, endExclusive);
        if (block == null) {
            ArrayList<LaneSegmentGroup> out = new ArrayList<>(endExclusive - startInclusive);
            for (int index = startInclusive; index < endExclusive; index++) {
                out.add(groupForRange(segments, index, index + 1));
            }
            return out;
        }

        ArrayList<LaneSegmentGroup> out = new ArrayList<>();
        out.addAll(compressRepeatedGroups(segments, fingerprints, startInclusive, block.startIndex()));
        out.add(groupForRange(segments, block.startIndex(), block.endIndex()));
        out.addAll(compressRepeatedGroups(segments, fingerprints, block.endIndex(), endExclusive));
        return out;
    }

    private static List<LaneSegmentGroup> compressGroupRanges(
        List<LaneSegmentGroup> groups,
        int startInclusive,
        int endExclusive,
        int groupCount
    ) {
        if (startInclusive >= endExclusive) {
            return List.of();
        }
        int length = endExclusive - startInclusive;
        int actualGroups = Math.max(1, Math.min(groupCount, length));
        if (actualGroups == length) {
            ArrayList<LaneSegmentGroup> out = new ArrayList<>(length);
            for (int index = startInclusive; index < endExclusive; index++) {
                out.add(groups.get(index));
            }
            return out;
        }

        int currentIndex = currentGroupIndex(groups, startInclusive, endExclusive);
        if (currentIndex < 0 || actualGroups < 3) {
            return mergeGroupRange(groups, startInclusive, endExclusive, actualGroups);
        }

        int beforeCount = currentIndex - startInclusive;
        int afterCount = endExclusive - currentIndex - 1;
        int remainingGroups = Math.max(0, actualGroups - 1);
        int beforeGroups = beforeCount <= 0 ? 0 : Math.max(1, Math.round(remainingGroups * (beforeCount / (float) Math.max(1, beforeCount + afterCount))));
        beforeGroups = Math.min(beforeGroups, beforeCount);
        int afterGroups = Math.max(0, remainingGroups - beforeGroups);
        if (beforeCount > 0 && beforeGroups == 0) {
            beforeGroups = 1;
            afterGroups = Math.max(0, remainingGroups - beforeGroups);
        }
        if (afterCount > 0 && afterGroups == 0 && remainingGroups > 0) {
            afterGroups = 1;
            if (beforeGroups > 1) {
                beforeGroups--;
            }
        }

        ArrayList<LaneSegmentGroup> out = new ArrayList<>(Math.max(1, actualGroups));
        out.addAll(mergeGroupRange(groups, startInclusive, currentIndex, beforeGroups));
        out.add(groups.get(currentIndex));
        out.addAll(mergeGroupRange(groups, currentIndex + 1, endExclusive, afterGroups));
        return out;
    }

    private static List<LaneSegmentGroup> mergeGroupRange(
        List<LaneSegmentGroup> groups,
        int startInclusive,
        int endExclusive,
        int groupCount
    ) {
        if (startInclusive >= endExclusive) {
            return List.of();
        }
        int length = endExclusive - startInclusive;
        int actualGroups = Math.max(1, Math.min(groupCount, length));
        if (actualGroups == length) {
            ArrayList<LaneSegmentGroup> out = new ArrayList<>(length);
            for (int index = startInclusive; index < endExclusive; index++) {
                out.add(groups.get(index));
            }
            return out;
        }

        ArrayList<LaneSegmentGroup> out = new ArrayList<>(actualGroups);
        for (int groupIndex = 0; groupIndex < actualGroups; groupIndex++) {
            int groupStart = startInclusive + (int) Math.floor(groupIndex * (double) length / actualGroups);
            int groupEnd = startInclusive + (int) Math.floor((groupIndex + 1d) * length / actualGroups);
            groupEnd = Math.max(groupStart + 1, groupEnd);
            groupEnd = Math.min(groupEnd, endExclusive);
            LaneSegmentGroup representativeGroup = groups.get(groupEnd - 1);
            int currentIndex = currentGroupIndex(groups, groupStart, groupEnd);
            if (currentIndex >= 0) {
                representativeGroup = groups.get(currentIndex);
            }
            out.add(new LaneSegmentGroup(
                groups.get(groupStart).startIndex(),
                groups.get(groupEnd - 1).endIndex(),
                representativeGroup.representative(),
                currentIndex >= 0
            ));
        }
        return out;
    }

    private static int resolveLaneStripGroupCount(int width, int segmentCount) {
        if (width <= 0 || segmentCount <= 0) {
            return 1;
        }
        int byWidth = Math.max(1, (width + laneStripGap) / (laneStripMinGroupWidth + laneStripGap));
        return Math.max(1, Math.min(laneStripMaxGroups, Math.min(segmentCount, byWidth)));
    }

    private static @Nullable RepeatedBlock bestRepeatedBlock(
        List<String> fingerprints,
        int startInclusive,
        int endExclusive
    ) {
        int length = endExclusive - startInclusive;
        if (length < 4) {
            return null;
        }

        RepeatedBlock best = null;
        int maxPeriod = Math.min(laneStripRepeatPatternMaxLength, length / 2);
        for (int blockStart = startInclusive; blockStart < endExclusive - 1; blockStart++) {
            int remaining = endExclusive - blockStart;
            int periodLimit = Math.min(maxPeriod, remaining / 2);
            for (int period = 1; period <= periodLimit; period++) {
                int repeatCount = 1;
                while (matchesRepeatedPattern(fingerprints, blockStart, period, repeatCount + 1, endExclusive)) {
                    repeatCount++;
                }
                if (repeatCount < 2) {
                    continue;
                }
                int blockEnd = blockStart + period * repeatCount;
                int blockLength = blockEnd - blockStart;
                if (blockLength < 4) {
                    continue;
                }
                int savings = blockLength - 1;
                if (savings < 3) {
                    continue;
                }
                int score = savings * 10 + blockLength + repeatCount * 5;
                if (best == null || score > best.score()) {
                    best = new RepeatedBlock(blockStart, blockEnd, period, repeatCount, score);
                }
            }
        }
        return best;
    }

    private static boolean matchesRepeatedPattern(
        List<String> fingerprints,
        int startIndex,
        int period,
        int repeatCount,
        int endExclusive
    ) {
        int blockLength = period * repeatCount;
        if (startIndex + blockLength > endExclusive) {
            return false;
        }
        for (int index = period; index < blockLength; index++) {
            if (!fingerprints.get(startIndex + index).equals(fingerprints.get(startIndex + index - period))) {
                return false;
            }
        }
        return true;
    }

    private static int currentGroupIndex(List<LaneSegmentGroup> groups, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (groups.get(index).current()) {
                return index;
            }
        }
        return -1;
    }

    private static LaneSegmentGroup groupForRange(
        ImmutableList<ExecutionOverlayLaneSegment> segments,
        int startInclusive,
        int endExclusive
    ) {
        if (startInclusive >= endExclusive) {
            throw new IllegalArgumentException("range must be non-empty");
        }
        int currentIndex = findCurrentIndex(segments, startInclusive, endExclusive);
        ExecutionOverlayLaneSegment representative = segments.get(endExclusive - 1);
        boolean current = currentIndex >= 0;
        if (currentIndex >= 0) {
            representative = segments.get(currentIndex);
        }
        return new LaneSegmentGroup(startInclusive, endExclusive, representative, current);
    }

    private static String segmentFingerprint(ExecutionOverlayLaneSegment segment) {
        StringBuilder builder = new StringBuilder(segment.stepKey());
        ExecutionOverlayRecipePreview preview = segment.recipePreview();
        if (preview != null && preview.recipe() != null) {
            builder.append('|');
            builder.append(preview.recipe().id() == null ? preview.recipe().systemId() : preview.recipe().id());
        }
        return builder.toString();
    }

    private record RepeatedBlock(int startIndex, int endIndex, int periodLength, int repeatCount, int score) {
    }

    private static int findCurrentIndex(
        ImmutableList<ExecutionOverlayLaneSegment> segments,
        int startInclusive,
        int endExclusive
    ) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (segments.get(index).current()) {
                return index;
            }
        }
        return -1;
    }

    //endregion

    //region tooltip

    static Tooltip debugNodeTooltip(ExecutionOverlayNode node) {
        return nodeTooltip(node, null);
    }

    private static Tooltip nodeTooltip(@Nullable ExecutionOverlayNode node, @Nullable ExecutionTelemetrySnapshot telemetry) {
        Tooltip tooltip = Tooltip.create();
        if (node == null) {
            tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.empty));
            return tooltip;
        }
        RecipeContent content = focusContent(node);
        TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(content);
        if (stack != null) {
            tooltip.setStack(stack);
        }
        tooltip.add(nodeFlowTooltip(node, telemetry));
        appendRecipePreview(tooltip, node.recipePreview());
        return tooltip;
    }

    private Tooltip currentNodeTooltip(@Nullable ExecutionOverlayNode node, @Nullable ExecutionTelemetrySnapshot telemetry) {
        return nodeTooltip(node, telemetry);
    }

    private Tooltip segmentTooltip(
        ExecutionOverlayNode node,
        LaneSegmentGroup group,
        @Nullable ExecutionTelemetrySnapshot telemetry
    ) {
        Tooltip tooltip = Tooltip.create();
        RecipeContent content = focusContent(node);
        TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(content);
        if (stack != null) {
            tooltip.setStack(stack);
        }
        tooltip.add(segmentFlowTooltip(group));
        ExecutionOverlayRecipePreview preview = group.representative().recipePreview();
        appendRecipePreview(tooltip, segmentRecipePreview(node, preview));
        return tooltip;
    }

    private static RichTooltipComponent nodeFlowTooltip(ExecutionOverlayNode node, @Nullable ExecutionTelemetrySnapshot telemetry) {
        RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
        int accent = statusDotColor(node);
        String progressText = node.laneSegments().isEmpty() ? null : nodeProgressLabel(node);
        builder.section(ExecutionOverlayUi.localizedStatus(node.statusText()), accent);
        builder.text(node.label().getString(), ExecutionOverlayUi.tooltipTitleColor, false);
        if (node.subtitle() != null && !node.subtitle().getString().isBlank() && (node.laneSegments().isEmpty() || "paused".equals(node.statusText()) || "failed".equals(node.statusText()))) {
            builder.text(node.subtitle().getString(), ExecutionOverlayUi.tooltipTextSecondary, false);
        }
        ExecutionOverlayLaneSegment step = currentOrFirstLane(node);
        if (step != null) {
            builder.kv(
                ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.step),
                ExecutionOverlayUi.tooltipTextSecondary,
                step.label().getString(),
                step.current() ? ExecutionOverlayUi.tooltipAccent : ExecutionOverlayUi.tooltipTextPrimary
            );
            appendDescription(builder, step.description());
        }
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.progress),
            ExecutionOverlayUi.tooltipTextSecondary,
            progressText == null ? "-" : progressText,
            ExecutionOverlayUi.tooltipAccent
        );
        if (node.laneSegments().notEmpty()) {
            builder.bar(nodeProgressRatio(node), accent, 104);
        }
        return builder.build();
    }

    private static RichTooltipComponent segmentFlowTooltip(LaneSegmentGroup group) {
        RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
        ExecutionOverlayLaneSegment segment = group.representative();
        int accent = statusDotColor(segment.statusText(), segment.current());
        builder.section(ExecutionOverlayUi.localizedStatus(segment.statusText()), accent);
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.steps),
            ExecutionOverlayUi.tooltipTextSecondary,
            Integer.toString(group.count()),
            ExecutionOverlayUi.tooltipAccent
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.range),
            ExecutionOverlayUi.tooltipTextSecondary,
            (group.startIndex() + 1) + "-" + group.endIndex(),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.text(segment.label().getString(), ExecutionOverlayUi.tooltipTitleColor, false);
        appendDescription(builder, segment.description());
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.status),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.localizedStatus(segment.statusText()),
            accent
        );
        if (segment.recipePreview() != null) {
            builder.kv(
                ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.batch),
                ExecutionOverlayUi.tooltipTextSecondary,
                Integer.toString(segment.recipePreview().times()),
                ExecutionOverlayUi.tooltipAccent
            );
        }
        return builder.build();
    }

    private static void appendDescription(RichTooltipComponent.Builder builder, @Nullable net.minecraft.network.chat.Component description) {
        if (description == null || description.getString().isBlank()) {
            return;
        }
        builder.text(description.getString(), ExecutionOverlayUi.tooltipTextSecondary, false);
    }

    private static void appendRecipePreview(Tooltip tooltip, @Nullable ExecutionOverlayRecipePreview preview) {
        TooltipComponent component = ExecutionRecipeTooltipPreview.createLeftAligned(preview);
        if (component != null) {
            tooltip.add(component);
        }
    }

    private static @Nullable ExecutionOverlayRecipePreview segmentRecipePreview(
        ExecutionOverlayNode node,
        @Nullable ExecutionOverlayRecipePreview segmentPreview
    ) {
        if (completeRecipePreview(segmentPreview)) {
            return segmentPreview;
        }
        ExecutionOverlayRecipePreview nodePreview = node.recipePreview();
        return nodePreview == null ? segmentPreview : nodePreview;
    }

    private static boolean completeRecipePreview(@Nullable ExecutionOverlayRecipePreview preview) {
        return preview != null && preview.inputs().notEmpty() && preview.outputs().notEmpty();
    }

    private Tooltip summaryStatusTooltip(ExecutionOverlayViewModel viewModel, @Nullable ExecutionTelemetrySnapshot telemetry, boolean playbackRunning) {
        Tooltip tooltip = Tooltip.create();
        String status = telemetry == null ? viewModel.statusText() : telemetry.status();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Summary.execution));
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(ExecutionOverlayUi.localizedStatus(status)));
        tooltip.add(ExecutionOverlayUi.tooltipBody(
            playbackRunning
                ? CirrusLangs.Execution.Overlay.Control.playbackPause
                : CirrusLangs.Execution.Overlay.Control.playbackStart
        ));
        tooltip.add(ExecutionOverlayUi.tooltipBody(CirrusLangs.Execution.Overlay.Trace.forceReopen));
        if (telemetry != null) {
            String errorSummary = telemetry.errorSummary();
            if (errorSummary != null && !errorSummary.isBlank()) {
                tooltip.add(ExecutionOverlayUi.tooltipWarningText(errorSummary));
            } else {
                for (String warning : telemetry.warnings()) {
                    if (warning == null || warning.isBlank()) {
                        continue;
                    }
                    tooltip.add(ExecutionOverlayUi.tooltipBodyText(warning));
                    break;
                }
            }
        }
        return tooltip;
    }

    private Tooltip summaryProgressTooltip(ExecutionOverlayViewModel viewModel, @Nullable ExecutionTelemetrySnapshot telemetry) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitle(CirrusLangs.Execution.Overlay.Tooltip.progress));
        tooltip.add(ExecutionOverlayUi.tooltipAccentText(progressLabel(viewModel, telemetry)));
        if (telemetry != null) {
            RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
            if (telemetry.current() != null) {
                builder.kv(
                    ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.current),
                    ExecutionOverlayUi.tooltipTextSecondary,
                    telemetry.current().label(),
                    ExecutionOverlayUi.tooltipTextPrimary
                );
            }
            if (telemetry.next() != null) {
                builder.kv(
                    ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Tooltip.next),
                    ExecutionOverlayUi.tooltipTextSecondary,
                    telemetry.next().label(),
                    ExecutionOverlayUi.tooltipTextPrimary
                );
            }
            tooltip.add(builder.build());
        }
        return tooltip;
    }

    //endregion

    //region helpers

    private @Nullable ExecutionOverlayNode currentNode(ExecutionOverlayViewModel viewModel) {
        if (viewModel.focusVisibleIndex() >= 0 && viewModel.focusVisibleIndex() < viewModel.visibleNodes().size()) {
            return viewModel.visibleNodes().get(viewModel.focusVisibleIndex());
        }
        for (ExecutionOverlayNode node : viewModel.visibleNodes()) {
            if ("running".equals(node.statusText()) || "paused".equals(node.statusText())) {
                return node;
            }
        }
        return viewModel.visibleNodes().isEmpty() ? null : viewModel.visibleNodes().getFirst();
    }

    private @Nullable ExecutionOverlayNode nextNode(ExecutionOverlayViewModel viewModel) {
        ExecutionOverlayNode current = currentNode(viewModel);
        boolean seenCurrent = current == null;
        for (ExecutionOverlayNode node : viewModel.visibleNodes()) {
            if (!seenCurrent) {
                if (node == current) {
                    seenCurrent = true;
                }
                continue;
            }
            if ("pending".equals(node.statusText())) {
                return node;
            }
        }
        return null;
    }

    private static RecipeContent focusContent(@Nullable ExecutionOverlayNode node) {
        if (node == null || node.content() == null || node.content().isEmpty()) {
            return RecipeContent.empty();
        }
        return node.content().copy();
    }

    private String progressLabel(ExecutionOverlayViewModel viewModel, @Nullable ExecutionTelemetrySnapshot telemetry) {
        int completed = telemetry == null ? viewModel.completedStepCount() : telemetry.completedSteps();
        int total = telemetry == null ? viewModel.totalStepCount() : telemetry.totalSteps();
        return completed + "/" + total;
    }

    private float progressRatio(ExecutionOverlayViewModel viewModel, @Nullable ExecutionTelemetrySnapshot telemetry) {
        if (telemetry != null) {
            return (float) telemetry.progress();
        }
        int total = Math.max(0, viewModel.totalStepCount());
        if (total <= 0) {
            return 0f;
        }
        return clamp(viewModel.completedStepCount() / (float) total, 0f, 1f);
    }

    private static String nodeProgressLabel(ExecutionOverlayNode node) {
        if (node.laneSegments().isEmpty()) {
            return "-";
        }
        int completed = 0;
        for (ExecutionOverlayLaneSegment laneSegment : node.laneSegments()) {
            if ("completed".equals(laneSegment.statusText())) {
                completed++;
            }
        }
        return completed + "/" + node.laneSegments().size();
    }

    private static float nodeProgressRatio(ExecutionOverlayNode node) {
        if (node.laneSegments().isEmpty()) {
            return 0f;
        }
        int completed = 0;
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if ("completed".equals(segment.statusText())) {
                completed++;
            }
        }
        return clamp(completed / (float) node.laneSegments().size(), 0f, 1f);
    }

    private static int statusDotColor(ExecutionOverlayNode node) {
        return statusDotColor(node.statusText(), currentLane(node));
    }

    private static int statusDotColor(String status, boolean current) {
        int base = ExecutionOverlayUi.statusColor(status);
        return current ? base : withAlpha(base, 0xCC);
    }

    private static boolean currentLane(ExecutionOverlayNode node) {
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if (segment.current()) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable ExecutionOverlayLaneSegment currentOrFirstLane(ExecutionOverlayNode node) {
        for (ExecutionOverlayLaneSegment segment : node.laneSegments()) {
            if (segment.current()) {
                return segment;
            }
        }
        return node.laneSegments().isEmpty() ? null : node.laneSegments().getFirst();
    }

    private void drawPlaybackIcon(SceneCanvas canvas, int x, int y, int width, int height, boolean playbackRunning, boolean down) {
        ImageTexture icon = playbackRunning ? CirrusTextures.Icon.pause : CirrusTextures.Icon.start;
        CirrusControlChrome.renderCenteredIcon(canvas, icon, x, y, width, height, down);
    }

    private int timelineContentHeight(ImmutableList<ExecutionOverlayNode> nodes) {
        int rowHeight = resolveNodeRowHeight(width(), height());
        int rowGap = resolveNodeRowGap(width(), height());
        int total = 0;
        for (ExecutionOverlayNode ignored : nodes) {
            total += rowHeight + rowGap;
        }
        return Math.max(0, total - rowGap);
    }

    private void syncScrollBounds(int contentHeight, int viewportHeight) {
        float max = Math.max(0f, contentHeight - viewportHeight);
        scrollY = clamp(scrollY, 0f, max);
        targetScrollY = clamp(targetScrollY, 0f, max);
    }

    private Rect2i timelineBodyArea() {
        int pagePad = resolvePagePadding(width(), height());
        int x = pagePad;
        int y = pagePad + resolveOverviewHeight(width(), height()) + resolveOverviewGap(width(), height());
        int width = Math.max(0, width() - pagePad * 2);
        int bottom = height() - pagePad;
        int height = Math.max(0, bottom - y);
        return timelineBodyArea(new Rect2i(x, y, width, height));
    }

    private Rect2i timelineBodyArea(Rect2i panelArea) {
        int inset = resolveTimelineBodyInset(width(), height());
        int innerX = panelArea.getX() + inset;
        int innerY = panelArea.getY() + inset;
        int innerWidth = Math.max(0, panelArea.getWidth() - inset * 2);
        int innerHeight = Math.max(0, panelArea.getHeight() - inset * 2);
        int contentWidth = Math.max(0, innerWidth - resolveTimelineScrollbarWidth(width(), height()) - 2);
        return new Rect2i(innerX, innerY, contentWidth, innerHeight);
    }

    private @Nullable Rect2i scrollbarTrackArea() {
        Rect2i timelineBody = timelineBodyArea();
        if (timelineBody.getHeight() <= 0) {
            return null;
        }
        int verticalInset = Math.min(2, Math.max(0, timelineBody.getHeight() / 4));
        return new Rect2i(
            timelineBody.getX() + timelineBody.getWidth() + 2,
            timelineBody.getY() + verticalInset,
            resolveTimelineScrollbarWidth(width(), height()),
            Math.max(0, timelineBody.getHeight() - verticalInset * 2)
        );
    }

    private @Nullable Rect2i scrollbarThumbArea() {
        Rect2i track = scrollbarTrackArea();
        if (track == null) {
            return null;
        }
        Rect2i timelineBody = timelineBodyArea();
        int contentHeight = timelineContentHeight(visibleNodes());
        if (contentHeight <= timelineBody.getHeight() || contentHeight <= 0) {
            return null;
        }
        int thumbHeight = Math.max(14, Math.min(track.getHeight(), (int) ((double) timelineBody.getHeight() / contentHeight * track.getHeight())));
        int travel = Math.max(0, track.getHeight() - thumbHeight);
        int thumbY = track.getY();
        if (travel > 0 && maxScroll() > 0f) {
            thumbY += Math.round((scrollY / maxScroll()) * travel);
        }
        return new Rect2i(track.getX(), thumbY, track.getWidth(), thumbHeight);
    }

    private Rect2i statusButtonArea() {
        int pagePad = resolvePagePadding(width(), height());
        int overviewHeight = resolveOverviewHeight(width(), height());
        int availableWidth = Math.max(0, width() - pagePad * 2);
        int inset = resolveOverviewInset(availableWidth, overviewHeight);
        int innerHeight = Math.max(0, overviewHeight - inset * 2);
        return new Rect2i(
            pagePad + inset,
            pagePad + inset + Math.max(0, (innerHeight - overviewStatusCell) / 2),
            overviewStatusCell,
            overviewStatusCell
        );
    }

    private float jumpScrollTo(int mouseY) {
        Rect2i track = scrollbarTrackArea();
        Rect2i thumb = scrollbarThumbArea();
        if (track == null || thumb == null) {
            return 0f;
        }
        int travel = Math.max(1, track.getHeight() - thumb.getHeight());
        float top = clamp(mouseY - thumb.getHeight() / 2f - track.getY(), 0f, travel);
        return top / travel * maxScroll();
    }

    private float dragScrollTo(float mouseY) {
        Rect2i track = scrollbarTrackArea();
        Rect2i thumb = scrollbarThumbArea();
        if (track == null || thumb == null) {
            return 0f;
        }
        int travel = Math.max(1, track.getHeight() - thumb.getHeight());
        float top = clamp(mouseY - scrollbarDragOffsetY - track.getY(), 0f, travel);
        return top / travel * maxScroll();
    }

    private void stopScrollbarDrag() {
        draggingScrollbar = false;
        scrollbarDragOffsetY = 0f;
    }

    private float maxScroll() {
        Rect2i body = timelineBodyArea();
        return Math.max(0f, timelineContentHeight(visibleNodes()) - body.getHeight());
    }

    static int resolvePagePadding(int width, int height) {
        return width < 112 || height < 152 ? 4 : pad;
    }

    static int resolveOverviewHeight(int width, int height) {
        if (width < 96 || height < 128) {
            return overviewHeightDense;
        }
        if (width < 120 || height < 152) {
            return overviewHeightCompact;
        }
        return overviewHeight;
    }

    static int resolveOverviewGap(int width, int height) {
        return width < 112 || height < 152 ? 3 : overviewGap;
    }

    static int resolveOverviewInset(int width, int height) {
        return width < 96 || height < 128 ? 3 : overviewInset;
    }

    static int resolveNodeRowHeight(int width, int height) {
        if (width < 96 || height < 128) {
            return nodeRowHeightDense;
        }
        if (width < 120 || height < 152) {
            return nodeRowHeightCompact;
        }
        return nodeRowHeight;
    }

    static int resolveNodeRowGap(int width, int height) {
        return width < 112 || height < 152 ? 2 : nodeRowGap;
    }

    static int resolveLaneIndent(int width, int height) {
        if (width < 96 || height < 128) {
            return 6;
        }
        if (width < 120 || height < 152) {
            return 8;
        }
        return laneIndent;
    }

    static int resolveTimelineScrollbarWidth(int width, int height) {
        return width < 96 || height < 128 ? timelineScrollbarWidthDense : timelineScrollbarWidth;
    }

    static int resolveTimelineBodyInset(int width, int height) {
        return width < 96 || height < 128 ? timelineBodyInsetDense : timelineBodyInset;
    }

    private int resolveRailX(Rect2i area, int depth) {
        int baseX = area.getX() + 6;
        int desiredX = area.getX() + 8 + Math.min(36, depth * resolveLaneIndent(area.getWidth(), area.getHeight()));
        int maxX = area.getX() + Math.max(6, area.getWidth() - ExecutionOverlayUi.slotSize - 10);
        return Math.max(baseX, Math.min(desiredX, maxX));
    }

    private ImmutableList<ExecutionOverlayNode> visibleNodes() {
        ExecutionDriverSnapshot snapshot = state.snapshot();
        if (snapshot == null || snapshot.viewModel() == null) {
            return Lists.immutable.empty();
        }
        return snapshot.viewModel().visibleNodes();
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean autoAdvanceEnabled() {
        ExecutionDriverSnapshot snapshot = state.snapshot();
        return snapshot != null && snapshot.autoAdvanceEnabled();
    }

    private boolean sessionPaused() {
        ExecutionTelemetrySnapshot telemetry = state.telemetry();
        return telemetry != null && telemetry.paused();
    }

    private boolean sessionCompleted() {
        ExecutionTelemetrySnapshot telemetry = state.telemetry();
        return telemetry != null && telemetry.completed();
    }

    private boolean playbackRunning() {
        return autoAdvanceEnabled() && !sessionCompleted();
    }

    private void togglePlayback(@Nullable InputContext input) {
        state.flushTargetTimes();
        if (state.calculating()) {
            return;
        }
        ExecutionDriver driver = state.driver();
        if (driver == null) {
            return;
        }
        if (input != null && input.isShiftDown()) {
            driver.forceReopen();
            ExecutionOverlayUi.playClickSound();
            return;
        }
        if (sessionCompleted()) {
            return;
        }
        if (playbackRunning()) {
            if (autoAdvanceEnabled()) {
                driver.toggleAutoAdvance();
            }
            driver.pause();
        } else {
            if (sessionPaused()) {
                driver.resume();
            }
            if (!autoAdvanceEnabled()) {
                driver.toggleAutoAdvance();
            }
            driver.advanceSteps(1);
        }
        ExecutionOverlayUi.playClickSound();
    }

    //endregion

    private record InteractiveArea(Rect2i bounds, Tooltip tooltip) {
    }

    record LaneSegmentGroup(int startIndex, int endIndex, ExecutionOverlayLaneSegment representative, boolean current) {
        int count() {
            return Math.max(1, endIndex - startIndex);
        }
    }

    private record SegmentArea(Rect2i bounds, LaneSegmentGroup group) {
    }
}

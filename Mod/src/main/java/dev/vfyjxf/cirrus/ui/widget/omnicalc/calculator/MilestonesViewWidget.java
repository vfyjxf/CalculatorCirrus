package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.DebugRecipeProvider;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Pos;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

public class MilestonesViewWidget extends CompositeWidget<Widget> {

    //region Data & Metrics
    private static final PhaseColumnData[] phases = buildPhases();
    private static final int summaryProgressInset = 0;
    private static final int phaseProgressInset = 4;
    private static final int stepProgressInset = 0;
    private static final int summaryProgressFill = textWhite;
    private static final int progressFill = green;
    //endregion

    private final MilestoneBoardWidget board;

    public MilestonesViewWidget() {
        useStyle(
                flexColumn(),
                sizeFull(),
                paddingHorizontal(4),
                paddingVertical(4),
                gap(4)
        );

        onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.texture(Background.flat, 0, 0, width(), height()));

        addWidget(createSummaryHeader());
        board = new MilestoneBoardWidget();
        addWidget(board);
    }

    int phaseColumnCount() {
        return phases.length;
    }

    void debugSetPhaseScroll(int phaseIndex, float scroll) {
        board.debugSetPhaseScroll(phaseIndex, scroll);
    }

    float debugPhaseScroll(int phaseIndex) {
        return board.debugPhaseScroll(phaseIndex);
    }

    boolean debugStepCardsShowStageText() {
        return false;
    }

    boolean debugStepCardsShowAmountBadge() {
        return false;
    }

    boolean debugPhaseHeadersUseExternalProgressBar() {
        return false;
    }

    boolean debugAmountTextUsesShadow() {
        return true;
    }

    int debugAmountTextColor() {
        return textWhite;
    }

    int debugSummaryProgressInset() {
        return summaryProgressInset;
    }

    int debugPhaseProgressInset() {
        return phaseProgressInset;
    }

    int debugStepCardProgressInset() {
        return stepProgressInset;
    }

    boolean debugSummaryProgressTouchesHorizontalEdges() {
        return true;
    }

    boolean debugSummaryProgressTouchesBottomEdge() {
        return true;
    }

    boolean debugSummaryProgressMergedTopBorder() {
        return true;
    }

    boolean debugSummaryHeaderShowsSubtitle() {
        return false;
    }

    boolean debugPhaseHeadersShowSubtitle() {
        return false;
    }

    boolean debugProgressUsesUnifiedFillColor() {
        return true;
    }

    boolean debugManualToggleUsesSwitchTexture() {
        return false;
    }

    boolean debugSummaryHeaderUsesMinimalChrome() {
        return true;
    }

    String debugSummaryHeaderTitle() {
        return "Milestone Progress";
    }

    String debugSummaryHeaderProgressText() {
        return summaryProgressText();
    }

    boolean debugUsesPlaceholderHeaderTexture() {
        return false;
    }

    boolean debugSupportsManualStepToggle() {
        return true;
    }

    boolean debugPhaseColumnsReserveScrollbarSpace() {
        return true;
    }

    int debugPhaseScrollbarWidth() {
        return MilestoneBoardWidget.verticalScrollbarWidth;
    }

    static boolean shouldRouteToHorizontalScroll(boolean shiftDown, float scrollX, boolean overHorizontalBar, boolean overPhaseBody) {
        if (shiftDown || Math.abs(scrollX) > 0.01f) return true;
        if (overPhaseBody) return false;
        return overHorizontalBar;
    }

    void debugToggleManualStep(int phaseIndex, int stepIndex) {
        board.debugToggleManualStep(phaseIndex, stepIndex);
    }

    boolean debugManualStepChecked(int phaseIndex, int stepIndex) {
        return board.debugManualStepChecked(phaseIndex, stepIndex);
    }

    private Widget createSummaryHeader() {
        var header = new WidgetGroup<>();
        header.useStyle(
                flexColumn(),
                paddingTop(4),
                paddingBottom(0),
                gap(0)
        );
        var titlePanel = new WidgetGroup<>();
        titlePanel.useStyle(
                flexColumn(),
                paddingTop(4),
                paddingBottom(5)
        );
        titlePanel.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.texture(Background.Outlined.flat, 0, 0, self.width(), self.height());
            canvas.texture(Background.flat, 2, 2, self.width() - 4, self.height() - 4);
        });

        var titleRow = new WidgetGroup<>();
        titleRow.useStyle(flexRow(), alignItemsCenter(), gap(4), paddingHorizontal(6), heightOf(20));

        var icon = new Widget();
        icon.useStyle(sizeOf(12, 12));
        icon.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.texture(Icon.Calc.Task.tabSelected, 0, 0, 12, 12);
        });
        titleRow.addWidget(icon);

        var title = LabelWidget.of(Component.literal("Milestone Progress"))
                .setColor(textPrimary)
                .setShadow(false);
        titleRow.addWidget(title);

        titleRow.addWidget(SpacerWidget.create());

        var progress = LabelWidget.of(Component.literal(summaryProgressText()))
                .setColor(textSecondary)
                .setShadow(false);
        titleRow.addWidget(progress);

        titlePanel.addWidget(titleRow);
        header.addWidget(titlePanel);

        var summaryBarStrip = new Widget();
        summaryBarStrip.useStyle(heightOf(7));
        summaryBarStrip.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            int border = borderMain;
            int trackColor = borderSecondary;
            // Merge with title panel bottom border: no extra top border on this strip.
            canvas.fill(0, 0, 1, 7, border);
            canvas.fill(w - 1, 0, 1, 7, border);
            canvas.fill(0, 6, w, 1, border);
            canvas.fill(1, 0, w - 2, 6, trackColor);

            int trackW = Math.max(0, w - 2 - summaryProgressInset * 2);
            int fillW = Math.max(8, Math.round((trackW - 2) * summaryProgressRatio()));
            canvas.fill(2 + summaryProgressInset, 1, fillW, 3, summaryProgressFill);
        });
        header.addWidget(summaryBarStrip);

        var summaryDivider = new Widget();
        summaryDivider.useStyle(heightOf(1));
        summaryDivider.onRender((canvas, mx, my, pt, self, ctx) ->
                canvas.fill(0, 0, self.width(), 1, borderSecondary));
        header.addWidget(summaryDivider);

        return header;
    }

    private static PhaseColumnData[] buildPhases() {
        ProcessGraph graph = DebugRecipeProvider.createDebugProcessGraph();
        return new PhaseColumnData[]{
                new PhaseColumnData("PHASE 1", "4/12", 0.33f, new StepCardData[]{
                        step(graph, "elem_0_0", false, false),
                        step(graph, "elem_1_4", false, false),
                        step(graph, "alloy_2", false, true),
                        step(graph, "cplate_2", false, false)
                }),
                new PhaseColumnData("PHASE 2", "3/10", 0.30f, new StepCardData[]{
                        step(graph, "circuit_4", false, false),
                        step(graph, "circuit_14", false, true),
                        step(graph, "circuit_27", false, false),
                        step(graph, "supercon_3", false, false)
                }),
                new PhaseColumnData("PHASE 3", "2/8", 0.25f, new StepCardData[]{
                        step(graph, "machine_3", false, true),
                        step(graph, "machine_12", false, false),
                        step(graph, "module_4", false, false),
                        step(graph, "system_2", false, false)
                }),
                new PhaseColumnData("PHASE 4", "1/6", 0.16f, new StepCardData[]{
                        step(graph, "final_0", false, true),
                        step(graph, "final_4", false, false),
                        step(graph, "final_8", false, false),
                        step(graph, "final_9", false, true)
                })
        };
    }

    private static StepCardData step(ProcessGraph graph, String nodeId, boolean complete, boolean tracked) {
        ProcessNode node = graph.node(nodeId);
        if (node == null) {
            return new StepCardData("Missing Node", "Unavailable", "x0",
                    new ItemStack(Items.BARRIER), complete, tracked);
        }
        ItemStack icon = findVisual(node);
        String detail = node.outputs().isEmpty() ? node.machine() : node.outputs().getFirst().materialName();
        return new StepCardData(node.machine(), detail, formatAmount(node), icon, complete, tracked);
    }

    private static String formatAmount(ProcessNode node) {
        if (!node.outputs().isEmpty()) {
            return "x" + node.outputs().getFirst().amount();
        }
        if (!node.inputs().isEmpty()) {
            return "x" + node.inputs().getFirst().amount();
        }
        return "x0";
    }

    private static ItemStack findVisual(ProcessNode node) {
        for (MaterialSlot output : node.outputs()) {
            if (output.itemStack() != null && !output.itemStack().isEmpty()) {
                return output.itemStack().copy();
            }
        }
        if (!node.machineOptions().isEmpty()) {
            ItemStack machine = node.machineOptions().getFirst().icon();
            if (machine != null && !machine.isEmpty()) {
                return machine.copy();
            }
        }
        return new ItemStack(Items.STRUCTURE_VOID);
    }

    private static String summaryProgressText() {
        return summaryCompletedSteps() + " / " + summaryTotalSteps();
    }

    private static int summaryCompletedSteps() {
        int completed = 0;
        for (PhaseColumnData phase : phases) {
            completed += parsePhaseCountPart(phase.count, 0);
        }
        return completed;
    }

    private static int summaryTotalSteps() {
        int total = 0;
        for (PhaseColumnData phase : phases) {
            total += parsePhaseCountPart(phase.count, 1);
        }
        return total;
    }

    private static float summaryProgressRatio() {
        int total = summaryTotalSteps();
        if (total <= 0) return 0f;
        return (float) summaryCompletedSteps() / total;
    }

    private static int parsePhaseCountPart(String count, int index) {
        String[] parts = count.split("/");
        if (index < 0 || index >= parts.length) return 0;
        return Integer.parseInt(parts[index].trim());
    }

    private record PhaseColumnData(
            String label,
            String count,
            float progress,
            StepCardData[] steps
    ) {}

    private record StepCardData(
            String machine,
            String detail,
            String amount,
            ItemStack icon,
            boolean complete,
            boolean tracked
    ) {}

    private final class MilestoneBoardWidget extends Widget {

        private static final int boardPadding = 4;
        private static final int columnWidth = 186;
        private static final int columnGap = 8;
        private static final int columnHeaderHeight = 28;
        private static final int cardHeight = 56;
        private static final int cardGap = 6;
        private static final int horizontalScrollbarHeight = 10;
        private static final int verticalScrollbarWidth = 10;
        private static final int cardAreaY = columnHeaderHeight + 6;
        private static final int maxPhaseBodyHeight = cardHeight * 4 + cardGap * 3 + 8;
        private static final int phaseScrollBottomPadding = 12;
        private static final int columnInnerX = 4;
        private static final float scrollSmooth = 0.35f;
        private static final int manualToggleWidth = 10;
        private static final int manualToggleHeight = 10;
        private static final int manualToggleY = 32;

        private float scrollX;
        private float targetScrollX;
        private final float[] phaseScrollY = new float[phases.length];
        private final float[] targetPhaseScrollY = new float[phases.length];
        private final boolean[][] manualStepChecks = buildManualChecks();
        private DragMode dragMode = DragMode.NONE;
        private int dragPhase = -1;
        private float dragOffset;

        private enum DragMode { NONE, HORIZONTAL, PHASE_VERTICAL }

        private MilestoneBoardWidget() {
            useStyle(flexGrow(1), minHeight(0));

            onMouseClicked((input, ctx) -> {
                FloatPos local = input.mouseRelative(this);
                double mx = local.x();
                double my = local.y();
                ManualToggleHit toggleHit = manualToggleAt(mx, my);
                if (toggleHit != null) {
                    toggleManualStep(toggleHit.phaseIndex(), toggleHit.stepIndex());
                    ctx.cancel();
                    return EventDispatch.consumed;
                }
                if (isOnHorizontalThumb(mx, my)) {
                    dragMode = DragMode.HORIZONTAL;
                    dragOffset = (float) (mx - horizontalThumbX());
                    return EventDispatch.consumed;
                }
                int phase = phaseAt(mx, my);
                if (phase >= 0 && isOnPhaseVerticalThumb(phase, mx, my)) {
                    dragMode = DragMode.PHASE_VERTICAL;
                    dragPhase = phase;
                    dragOffset = (float) (my - phaseThumbY(phase, boardViewH()));
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });

            onMouseReleased((input, ctx) -> {
                dragMode = DragMode.NONE;
                dragPhase = -1;
                dragOffset = 0;
                return EventDispatch.pass;
            });

            onMouseDragged((input, deltaX, deltaY, ctx) -> {
                if (dragMode == DragMode.NONE) return EventDispatch.pass;
                FloatPos local = input.mouseRelative(this);
                if (dragMode == DragMode.HORIZONTAL) {
                    scrollX = dragHorizontal(local.x());
                    targetScrollX = scrollX;
                } else if (dragPhase >= 0) {
                    phaseScrollY[dragPhase] = dragPhaseVertical(dragPhase, local.y());
                    targetPhaseScrollY[dragPhase] = phaseScrollY[dragPhase];
                }
                ctx.cancel();
                return EventDispatch.consumed;
            });

            onMouseScrolled((mouseX, mouseY, sx, sy, ctx) -> {
                int step = 26;
                FloatPos local = sceneToLocal(mouseX, mouseY);
                int phase = phaseAt(local.x(), local.y());
                boolean overPhaseBody = phase >= 0 && local.y() >= boardPadding + cardAreaY && local.y() <= boardPadding + cardAreaY + phaseBodyH(boardViewH());
                int hBarY = boardPadding + boardViewH() + 1;
                boolean overHorizontalBar = local.y() >= hBarY && local.y() <= hBarY + horizontalScrollbarHeight;
                boolean horizontalIntent = shouldRouteToHorizontalScroll(
                        Screen.hasShiftDown(),
                        (float) sx,
                        overHorizontalBar,
                        overPhaseBody
                );
                if (horizontalIntent) {
                    float delta = Math.abs(sx) > 0.01f ? (float) sx : (float) -sy;
                    targetScrollX = clamp(targetScrollX + delta * step, 0, maxScrollX());
                } else {
                    if (phase >= 0) {
                        targetPhaseScrollY[phase] = clamp(targetPhaseScrollY[phase] - (float) sy * step,
                                0, maxPhaseScroll(phase, boardViewH()));
                    } else {
                        targetScrollX = clamp(targetScrollX - (float) sy * step, 0, maxScrollX());
                    }
                }
                ctx.cancel();
                return EventDispatch.consumed;
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            Font font = Minecraft.getInstance().font;
            int w = width();
            int h = height();
            int viewW = boardViewW();
            int viewH = boardViewH();

            scrollX += (targetScrollX - scrollX) * scrollSmooth;
            if (Math.abs(scrollX - targetScrollX) < 0.5f) scrollX = targetScrollX;
            for (int i = 0; i < phaseScrollY.length; i++) {
                phaseScrollY[i] += (targetPhaseScrollY[i] - phaseScrollY[i]) * scrollSmooth;
                if (Math.abs(phaseScrollY[i] - targetPhaseScrollY[i]) < 0.5f) {
                    phaseScrollY[i] = targetPhaseScrollY[i];
                }
            }

            drawPanel(canvas, 0, 0, w, h);

            Pos abs = absolutePos();
            canvas.pushClip(abs.x() + boardPadding, abs.y() + boardPadding, viewW, viewH);
            canvas.pushTransform();
            canvas.translate(boardPadding - scrollX, boardPadding);

            int colX = 0;
            for (int i = 0; i < phases.length; i++) {
                renderColumn(canvas, font, phases[i], i, colX, viewH, abs);
                colX += columnWidth + columnGap;
            }

            canvas.popTransform();
            canvas.popClip();

            renderHorizontalScrollbar(canvas, viewW, viewH);
        }

        private void renderColumn(SceneCanvas canvas, Font font, PhaseColumnData phase, int phaseIndex,
                                  int x, int viewH, Pos boardAbs) {
            drawPanel(canvas, x, 0, columnWidth, viewH);
            canvas.drawString(phase.label, x + 10, 9, textPrimary, false);

            int countW = font.width(phase.count);
            canvas.drawString(phase.count, x + columnWidth - countW - 10, 9, textSecondary, false);

            int progressTrackX = x + phaseProgressInset;
            int progressTrackY = columnHeaderHeight - 4;
            int progressTrackW = columnWidth - phaseProgressInset * 2;
            int progressFillW = Math.max(2, (int) ((progressTrackW - 2) * phase.progress));
            canvas.texture(Background.inset, progressTrackX, progressTrackY, progressTrackW, 5);
            canvas.fill(progressTrackX + 1, progressTrackY + 1, progressFillW, 3, progressFill);

            int bodyH = phaseBodyH(viewH);
            int innerW = columnWidth - 12 - verticalScrollbarWidth;
            int clipX = boardAbs.x() + boardPadding + x - Math.round(scrollX) + columnInnerX;
            int clipY = boardAbs.y() + boardPadding + cardAreaY;

            canvas.pushClip(clipX, clipY, innerW, bodyH);
            canvas.pushTransform();
            canvas.translate(0, cardAreaY - phaseScrollY[phaseIndex]);

            int cardY = 0;
            for (int stepIndex = 0; stepIndex < phase.steps.length; stepIndex++) {
                StepCardData step = phase.steps[stepIndex];
                renderCard(canvas, font, step, phaseIndex, stepIndex, x + columnInnerX, cardY, innerW);
                cardY += cardHeight + cardGap;
            }

            canvas.popTransform();
            canvas.popClip();

            int trackX = x + columnWidth - verticalScrollbarWidth - 4;
            canvas.texture(Background.inset, trackX, cardAreaY, verticalScrollbarWidth, bodyH);
            canvas.texture(Icon.scrollBar, trackX + 1, phaseThumbY(phaseIndex, viewH) - boardPadding,
                    verticalScrollbarWidth - 2, phaseThumbHeight(phaseIndex, viewH));
        }

        private void renderCard(SceneCanvas canvas, Font font, StepCardData step, int phaseIndex, int stepIndex, int x, int y, int w) {
            canvas.texture(Background.flat, x, y, w, cardHeight);
            if (step.tracked) {
                canvas.texture(Background.Sign.bar, x + 2, y + 2, Math.max(16, w - 4), 2);
            }

            canvas.texture(Background.Slot.dark, x + 8, y + 8, 22, 22);
            if (!step.icon.isEmpty()) {
                canvas.renderItem(step.icon, x + 11, y + 11);
            }

            canvas.drawString(trim(step.machine, 18), x + 36, y + 8, textPrimary, false);
            canvas.drawString(trim(step.detail, 20), x + 36, y + 20, textSecondary, false);

            int amountW = font.width(step.amount);
            int amountX = x + w - amountW - 24;
            canvas.drawString(step.amount, amountX, y + 8, textWhite, true);

            boolean manualChecked = debugManualStepChecked(phaseIndex, stepIndex);
            int toggleX = x + w - manualToggleWidth - 8;
            int labelW = font.width("完成");
            int labelX = toggleX - labelW - 6;
            canvas.drawString("完成", labelX, y + manualToggleY + 1, textSecondary, false);
            int toggleY = y + manualToggleY;
            canvas.texture(Background.Slot.dark, toggleX - 1, toggleY - 1, manualToggleWidth + 2, manualToggleHeight + 2);
            canvas.texture(manualChecked ? Toggle.Checked.Small.select : Toggle.Checked.Small.unselect,
                    toggleX + 1, toggleY + 1, manualToggleWidth - 2, manualToggleHeight - 2);

            int barX = x + stepProgressInset;
            int barY = y + cardHeight - 6;
            int barW = w - stepProgressInset * 2;
            int barFillW = Math.max(10, (barW - 2) * (manualChecked || step.complete ? 1 : (step.tracked ? 3 : 2)) / 4);
            canvas.texture(Background.inset, barX, barY, barW, 6);
            canvas.fill(barX + 1, barY + 1, barFillW, 4, progressFill);

            canvas.texture(step.tracked ? Icon.Calc.Task.trackSelected : Icon.Calc.Task.track,
                    x + w - 18, y + 7, 13, 13);
        }

        private void renderHorizontalScrollbar(SceneCanvas canvas, int viewW, int viewH) {
            int hBarY = boardPadding + viewH + 1;
            int hThumbW = horizontalThumbWidth(viewW);
            int hThumbX = horizontalThumbX();
            canvas.texture(Background.inset, boardPadding, hBarY, viewW, horizontalScrollbarHeight);
            canvas.texture(Icon.scrollBar, hThumbX + 1, hBarY + 1, hThumbW - 2, 8);
        }

        private boolean isOnHorizontalThumb(double mx, double my) {
            int viewW = boardViewW();
            int y = boardPadding + boardViewH() + 1;
            return mx >= horizontalThumbX() && mx <= horizontalThumbX() + horizontalThumbWidth(viewW)
                    && my >= y && my <= y + horizontalScrollbarHeight;
        }

        private int phaseAt(double mx, double my) {
            int viewH = boardViewH();
            if (my < boardPadding || my > boardPadding + viewH) return -1;
            for (int i = 0; i < phases.length; i++) {
                int x = phaseScreenX(i);
                if (mx >= x && mx <= x + columnWidth) {
                    return i;
                }
            }
            return -1;
        }

        private boolean isOnPhaseVerticalThumb(int phase, double mx, double my) {
            int x = phaseScreenX(phase) + columnWidth - verticalScrollbarWidth - 4;
            int viewH = boardViewH();
            return mx >= x && mx <= x + verticalScrollbarWidth
                    && my >= phaseThumbY(phase, viewH) && my <= phaseThumbY(phase, viewH) + phaseThumbHeight(phase, viewH);
        }

        private int phaseScreenX(int phase) {
            return boardPadding + phase * (columnWidth + columnGap) - Math.round(scrollX);
        }

        private int horizontalThumbX() {
            int viewW = boardViewW();
            int track = Math.max(1, viewW - horizontalThumbWidth(viewW));
            float progress = maxScrollX() <= 0 ? 0 : scrollX / maxScrollX();
            return boardPadding + Math.round(track * progress);
        }

        private int horizontalThumbWidth(int viewW) {
            return Math.max(18, Math.min(viewW, viewW * viewW / Math.max(contentWidth(), 1)));
        }

        private int phaseThumbY(int phase, int viewH) {
            int bodyH = phaseBodyH(viewH);
            int track = Math.max(1, bodyH - phaseThumbHeight(phase, viewH));
            float progress = maxPhaseScroll(phase, viewH) <= 0 ? 0 : phaseScrollY[phase] / maxPhaseScroll(phase, viewH);
            return boardPadding + cardAreaY + Math.round(track * progress);
        }

        private int phaseThumbHeight(int phase, int viewH) {
            int bodyH = phaseBodyH(viewH);
            return Math.max(18, Math.min(bodyH, bodyH * bodyH / Math.max(phaseContentHeight(phases[phase]), 1)));
        }

        private float dragHorizontal(double mouseX) {
            int viewW = boardViewW();
            int thumbW = horizontalThumbWidth(viewW);
            int track = Math.max(1, viewW - thumbW);
            float left = clamp((float) mouseX - dragOffset - boardPadding, 0, track);
            return maxScrollX() <= 0 ? 0 : left / track * maxScrollX();
        }

        private float dragPhaseVertical(int phase, double mouseY) {
            int viewH = boardViewH();
            int thumbH = phaseThumbHeight(phase, viewH);
            int track = Math.max(1, phaseBodyH(viewH) - thumbH);
            float top = clamp((float) mouseY - dragOffset - boardPadding - cardAreaY, 0, track);
            return maxPhaseScroll(phase, viewH) <= 0 ? 0 : top / track * maxPhaseScroll(phase, viewH);
        }

        private int phaseBodyH(int viewH) {
            int dynamicBodyH = Math.max(32, viewH - cardAreaY - 2);
            return Math.min(dynamicBodyH, maxPhaseBodyHeight);
        }

        private int phaseContentHeight(PhaseColumnData phase) {
            return phase.steps.length * cardHeight + Math.max(0, phase.steps.length - 1) * cardGap;
        }

        private int contentWidth() {
            return phases.length * columnWidth + Math.max(0, phases.length - 1) * columnGap;
        }

        private int boardViewW() {
            return width() - boardPadding * 2;
        }

        private int boardViewH() {
            return height() - boardPadding * 2 - horizontalScrollbarHeight - 2;
        }

        private int maxScrollX() {
            return Math.max(0, contentWidth() - boardViewW());
        }

        private int maxPhaseScroll(int phase, int viewH) {
            return Math.max(0, phaseContentHeight(phases[phase]) + phaseScrollBottomPadding - phaseBodyH(viewH));
        }

        private boolean[][] buildManualChecks() {
            boolean[][] checks = new boolean[phases.length][];
            for (int i = 0; i < phases.length; i++) {
                checks[i] = new boolean[phases[i].steps.length];
            }
            return checks;
        }

        private void toggleManualStep(int phaseIndex, int stepIndex) {
            if (!isValidStep(phaseIndex, stepIndex)) return;
            manualStepChecks[phaseIndex][stepIndex] = !manualStepChecks[phaseIndex][stepIndex];
        }

        private boolean isValidStep(int phaseIndex, int stepIndex) {
            return phaseIndex >= 0 && phaseIndex < manualStepChecks.length
                    && stepIndex >= 0 && stepIndex < manualStepChecks[phaseIndex].length;
        }

        private @Nullable ManualToggleHit manualToggleAt(double mx, double my) {
            int viewH = boardViewH();
            int phaseIndex = phaseAt(mx, my);
            if (phaseIndex < 0) return null;
            int phaseX = phaseScreenX(phaseIndex);
            int innerW = columnWidth - 12 - verticalScrollbarWidth;
            if (mx < phaseX + columnInnerX || mx > phaseX + columnInnerX + innerW) return null;
            if (my < boardPadding + cardAreaY || my > boardPadding + cardAreaY + phaseBodyH(viewH)) return null;
            double contentY = my - boardPadding - cardAreaY + phaseScrollY[phaseIndex];
            for (int stepIndex = 0; stepIndex < phases[phaseIndex].steps.length; stepIndex++) {
                int cardY = stepIndex * (cardHeight + cardGap);
                int toggleX = phaseX + columnInnerX + innerW - manualToggleWidth - 8;
                int toggleY = boardPadding + cardAreaY - Math.round(phaseScrollY[phaseIndex]) + cardY + manualToggleY;
                if (mx >= toggleX && mx <= toggleX + manualToggleWidth
                        && my >= toggleY && my <= toggleY + manualToggleHeight
                        && contentY >= cardY && contentY <= cardY + cardHeight) {
                    return new ManualToggleHit(phaseIndex, stepIndex);
                }
            }
            return null;
        }

        private void debugSetPhaseScroll(int phaseIndex, float scroll) {
            if (phaseIndex < 0 || phaseIndex >= phaseScrollY.length) return;
            phaseScrollY[phaseIndex] = Math.max(0, scroll);
            targetPhaseScrollY[phaseIndex] = phaseScrollY[phaseIndex];
        }

        private float debugPhaseScroll(int phaseIndex) {
            if (phaseIndex < 0 || phaseIndex >= phaseScrollY.length) return 0;
            return phaseScrollY[phaseIndex];
        }

        private void debugToggleManualStep(int phaseIndex, int stepIndex) {
            toggleManualStep(phaseIndex, stepIndex);
        }

        private boolean debugManualStepChecked(int phaseIndex, int stepIndex) {
            return isValidStep(phaseIndex, stepIndex) && manualStepChecks[phaseIndex][stepIndex];
        }

        private record ManualToggleHit(int phaseIndex, int stepIndex) {}
    }

    private static void drawPanel(SceneCanvas canvas, int x, int y, int w, int h) {
        canvas.texture(Background.Outlined.flat, x, y, w, h);
        canvas.texture(Background.flat, x + 2, y + 2, w - 4, h - 4);
    }

    private static String trim(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 2) + "..";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

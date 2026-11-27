package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetBottom;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Aggregate action bar shown while two or more DAG steps are selected.
 */
public class MultiSelectActionBarWidget extends Widget {

    public static final int height = 20;
    public static final int minWidth = 380;
    public static final int maxWidth = 520;
    public static final int chipGap = 6;
    public static final int chipPadding = 5;
    public static final int summaryInset = 7;

    private static final String keySelectionSummary = "cirrus.ui.workbench.flow.selection.summary";
    private static final String keyAlignLeft = "cirrus.ui.workbench.flow.align_left";
    private static final String keyAlignTop = "cirrus.ui.workbench.flow.align_top";
    private static final String keyAlignRow = "cirrus.ui.workbench.flow.align_row";
    private static final String keyClearSelection = "cirrus.ui.workbench.flow.clear_selection";

    public interface FeedbackSink {

        void send(HumanFeedback feedback);
    }

    public record Chip(DagHitTester.RectF bounds, ChipAction action, Component label) {}

    public enum ChipAction {
        batchBan, batchOwn, alignLeft, alignTop, alignRow, clear
    }

    private Supplier<CirrusPalette> palette = () -> CirrusPalette.obsidianBlueprint;
    private List<DagNodeBox> selection = List.of();
    private @Nullable Consumer<Set<String>> onClearSelection;
    private @Nullable FeedbackSink feedbackSink;
    private @Nullable Consumer<DagSelectionMath.AlignKind> onAlign;
    private GuidanceScope scope = GuidanceScope.session;
    private boolean persistToWorld;
    private long lastActionTimeMs;

    private MultiSelectActionBarWidget() {
        useStyle(sizeOf(minWidth, height), positionAbsolute(), insetBottom(30f), insetLeft(0f), insetRight(0f));
        setFocusable(false);
        onMouseClicked((input, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            FloatPos local = sceneToLocal(input.mouseX(), input.mouseY());
            Chip chip = chipAt(local.x, local.y);
            if (chip == null) {
                return EventDispatch.pass;
            }
            activate(chip.action());
            lastActionTimeMs = System.currentTimeMillis();
            context.consume();
            return EventDispatch.consumed;
        });
    }

    public static MultiSelectActionBarWidget create() {
        return new MultiSelectActionBarWidget();
    }

    public MultiSelectActionBarWidget palette(Supplier<CirrusPalette> palette) {
        this.palette = Objects.requireNonNull(palette, "palette");
        return this;
    }

    public MultiSelectActionBarWidget onClearSelection(@Nullable Consumer<Set<String>> onClearSelection) {
        this.onClearSelection = onClearSelection;
        return this;
    }

    public MultiSelectActionBarWidget feedbackSink(@Nullable FeedbackSink sink) {
        this.feedbackSink = sink;
        return this;
    }

    public MultiSelectActionBarWidget onAlign(@Nullable Consumer<DagSelectionMath.AlignKind> onAlign) {
        this.onAlign = onAlign;
        return this;
    }

    public MultiSelectActionBarWidget scope(GuidanceScope scope) {
        this.scope = scope == null ? GuidanceScope.session : scope;
        return this;
    }

    public MultiSelectActionBarWidget persistToWorld(boolean persistToWorld) {
        this.persistToWorld = persistToWorld;
        return this;
    }

    public MultiSelectActionBarWidget selection(List<DagNodeBox> selection) {
        this.selection = selection == null ? List.of() : List.copyOf(selection);
        boolean visible = this.selection.size() >= 2;
        setVisible(visible);
        setActive(visible);
        setInteractive(visible);
        return this;
    }

    public List<DagNodeBox> selection() {
        return selection;
    }

    public boolean activeSelection() {
        return selection.size() >= 2;
    }

    public DagSelectionMath.CostTotals totals() {
        return DagSelectionMath.aggregateNodes(selection);
    }

    public long lastActionTimeMs() {
        return lastActionTimeMs;
    }

    public List<Chip> chips() {
        return layoutChips(Minecraft.getInstance().font::width, width());
    }

    public static List<Chip> layoutChips(ToIntFunction<Component> widthOf, int barWidth) {
        List<Chip> chips = new ArrayList<>();
        int cursor = summaryWidth(widthOf);
        int y = (height - 12) / 2;
        List<ChipAction> actions = List.of(
            ChipAction.batchBan,
            ChipAction.batchOwn,
            ChipAction.alignLeft,
            ChipAction.alignTop,
            ChipAction.alignRow,
            ChipAction.clear
        );
        for (ChipAction action : actions) {
            Component label = labelOf(action);
            int chipWidth = widthOf.applyAsInt(label) + chipPadding * 2;
            if (cursor + chipWidth > Math.min(barWidth, maxWidth) - summaryInset) {
                break;
            }
            chips.add(new Chip(new DagHitTester.RectF(cursor, y, chipWidth, 12), action, label));
            cursor += chipWidth + chipGap;
        }
        return chips;
    }

    public static Component labelOf(ChipAction action) {
        return switch (action) {
            case batchBan -> CirrusLangs.Workbench.Action.batchBan.get();
            case batchOwn -> CirrusLangs.Workbench.Action.batchOwn.get();
            case alignLeft -> Component.translatable(keyAlignLeft);
            case alignTop -> Component.translatable(keyAlignTop);
            case alignRow -> Component.translatable(keyAlignRow);
            case clear -> Component.translatable(keyClearSelection);
        };
    }

    private static int summaryWidth(ToIntFunction<Component> widthOf) {
        return widthOf.applyAsInt(summaryOf(3, DagSelectionMath.CostTotals.zero)) + summaryInset * 2;
    }

    public static Component summaryOf(int stepCount, DagSelectionMath.CostTotals totals) {
        DagSelectionMath.CostTotals value = totals == null ? DagSelectionMath.CostTotals.zero : totals;
        return Component.translatable(
            keySelectionSummary,
            stepCount,
            humanText(value),
            WorkbenchFormatters.formatSeconds(value.passive()),
            WorkbenchFormatters.formatCompactCount(value.capital()),
            WorkbenchFormatters.formatCompactCount(value.scarcity()),
            WorkbenchFormatters.formatCompactCount(value.cognitive())
        );
    }

    private static String humanText(DagSelectionMath.CostTotals totals) {
        return WorkbenchFormatters.formatSeconds(totals.human());
    }

    private @Nullable Chip chipAt(double localX, double localY) {
        for (Chip chip : chips()) {
            if (chip.bounds().contains(localX, localY)) {
                return chip;
            }
        }
        return null;
    }

    public void activate(ChipAction action) {
        switch (action) {
            case batchBan -> batchBan();
            case batchOwn -> batchOwn();
            case alignLeft -> align(DagSelectionMath.AlignKind.left);
            case alignTop -> align(DagSelectionMath.AlignKind.top);
            case alignRow -> align(DagSelectionMath.AlignKind.row);
            case clear -> clearSelection();
        }
        lastActionTimeMs = System.currentTimeMillis();
    }

    public void batchBan() {
        FeedbackSink sink = feedbackSink;
        if (sink == null) {
            return;
        }
        DagSelectionMath.sendBatchBan(selection, scope, persistToWorld, sink::send);
    }

    public void batchOwn() {
        FeedbackSink sink = feedbackSink;
        if (sink == null) {
            return;
        }
        DagSelectionMath.sendBatchOwn(selection, sink::send);
    }

    public void align(DagSelectionMath.AlignKind kind) {
        if (onAlign != null) {
            onAlign.accept(kind);
        }
    }

    public void clearSelection() {
        if (onClearSelection != null) {
            onClearSelection.accept(Set.of());
        }
        selection(List.of());
    }

    public static List<Component> machineLabels(List<DagNodeBox> nodes) {
        List<Component> labels = new ArrayList<>();
        for (DagNodeBox node : nodes) {
            PlanStep step = node.step();
            if (step instanceof AcquireStep acquire) {
                labels.add(Component.literal(acquire.resource().shortString()));
            }
        }
        return labels;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        if (!activeSelection()) {
            return;
        }
        CirrusPalette theme = palette.get();
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        canvas.shadow(0, 0, width, height, 3f, 2f, 6f, theme.dropShadow());
        canvas.fill(0, 0, width, height, theme.popoverSurface());
        canvas.strokeRect(0, 0, width, height, theme.focusRing(), 1);
        Component summary = summaryOf(selection.size(), totals());
        canvas.drawString(
            Component.literal(
                StepNodeCardRenderer.ellipsize(
                    canvas.font(),
                    summary.getString(),
                    summaryWidth(canvas.font()::width) - summaryInset
                )
            ),
            summaryInset,
            (height - 8) / 2,
            theme.textPrimary(),
            false
        );
        for (Chip chip : chips()) {
            int x = Math.round(chip.bounds().x());
            int y = Math.round(chip.bounds().y());
            int chipWidth = Math.round(chip.bounds().width());
            int chipHeight = Math.round(chip.bounds().height());
            boolean hovered = mouseX >= x && mouseX <= x + chipWidth && mouseY >= y && mouseY <= y + chipHeight;
            canvas.fill(x, y, chipWidth, chipHeight, hovered ? theme.cardSurfaceHover() : theme.recessedSurface());
            canvas.strokeRect(x, y, chipWidth, chipHeight, theme.hairlineBorder(), 1);
            canvas.drawString(
                Component.literal(
                    StepNodeCardRenderer.ellipsize(canvas.font(), chip.label().getString(), chipWidth - chipPadding * 2)
                ),
                x + chipPadding,
                y + 2,
                hovered ? theme.focusRing() : theme.textSecondary()
            );
        }
    }
}

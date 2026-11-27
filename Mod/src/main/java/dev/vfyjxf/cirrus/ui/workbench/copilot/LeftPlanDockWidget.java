package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiMotion;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cirrus.ui.workbench.state.WorkbenchUiSnapshot;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.drag.DragConsumer;
import dev.vfyjxf.cloudlib.api.ui.drag.DragContext;
import dev.vfyjxf.cloudlib.api.ui.drag.DragProvider;
import dev.vfyjxf.cloudlib.api.ui.drag.DraggableElement;
import dev.vfyjxf.cloudlib.api.ui.style.Styles;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.ui.widget.InfoBarWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.insetTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.positionAbsolute;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeStretch;

/**
 * Left Pareto plan dock: card/scatter dual state, OreUI plan cards, five-axis relative bars and a drag compare slot.
 */
public class LeftPlanDockWidget extends WidgetGroup<Widget> {

    public static final int headerHeight = 18;
    public static final int cardHeight = 112;
    public static final int expandedExtraHeight = 36;
    public static final int compareSlotHeight = 32;
    public static final int meterWidth = 84;

    private final CoPilotHost host;
    private final ModeHeader header;
    private final PlanCardList cardList;
    private final ParetoScatterPlotWidget scatter;
    private @Nullable Runnable onCollapse;
    private Mode mode = Mode.cards;

    public enum Mode {
        cards, scatter
    }

    public LeftPlanDockWidget(CoPilotHost host) {
        this.host = Objects.requireNonNull(host, "host");
        addStyleClass("workbench-dock");
        useStyle(flexColumn(), gap(2), padding(3), sizeStretch());

        this.header = new ModeHeader(host, this::setMode, () -> {
            if (onCollapse != null) {
                onCollapse.run();
            }
        });
        header.useStyle(heightOf(headerHeight), flexShrink(0));
        addWidget(header);

        this.cardList = new PlanCardList();
        cardList.useStyle(flexColumn(), flexGrow(1), gap(2), flexShrink(1));
        addWidget(cardList);

        this.scatter = new ParetoScatterPlotWidget(host);
        scatter.useStyle(flexGrow(1), flexShrink(1), minWidth(0), minHeight(0));
        addWidget(scatter);

        setMode(Mode.cards);
    }

    public LeftPlanDockWidget onCollapse(@Nullable Runnable onCollapse) {
        this.onCollapse = onCollapse;
        this.header.showCollapse = onCollapse != null;
        return this;
    }

    public CoPilotHost host() {
        return host;
    }

    public Mode mode() {
        return mode;
    }

    public PlanCardList cardList() {
        return cardList;
    }

    public ParetoScatterPlotWidget scatter() {
        return scatter;
    }

    public boolean canShowScatter() {
        return planCount() >= 2;
    }

    public int planCount() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        return snapshot == null || snapshot.result() == null ? 0 : snapshot.result().paretoPlans().size();
    }

    public LeftPlanDockWidget setMode(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        boolean scatterVisible = mode == Mode.scatter;
        scatter.setVisible(scatterVisible);
        scatter.useStyle(scatterVisible ? displayFlex() : displayNone());
        cardList.setVisible(!scatterVisible);
        cardList.useStyle(scatterVisible ? displayNone() : displayFlex());
        header.mode = mode;
        return this;
    }

    public LeftPlanDockWidget toggleMode() {
        return setMode(mode == Mode.cards ? Mode.scatter : Mode.cards);
    }

    public void refresh() {
        cardList.rebuild();
    }

    public boolean dropCompare(DragPayload payload) {
        return cardList.dropCompare(payload);
    }

    public boolean dropCompareAt(DragPayload payload, double localX, double localY) {
        return cardList.dropCompareAt(payload, localX, localY);
    }

    public boolean compareMode() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        return snapshot != null && snapshot.comparePlanId() != null;
    }

    public List<PromptDeltaFormat.Chip> compareChips() {
        WorkbenchUiSnapshot snapshot = host.snapshot();
        if (snapshot == null) {
            return List.of();
        }
        ProductionPlan selected = snapshot.selectedPlan().orElse(null);
        ProductionPlan compare = snapshot.comparePlan().orElse(null);
        if (selected == null || compare == null) {
            return List.of();
        }
        double[] delta = PromptDeltaFormat.signedDelta(selected.totalCost(), compare.totalCost());
        List<PromptDeltaFormat.Chip> chips = new ArrayList<>(delta.length);
        ParetoGeometry.Axis[] axes = ParetoGeometry.Axis.values();
        for (int index = 0; index < axes.length && index < delta.length; index++) {
            if (Math.abs(delta[index]) < PromptDeltaFormat.chipEpsilon) {
                continue;
            }
            ParetoGeometry.Axis axis = axes[index];
            chips.add(
                new PromptDeltaFormat.Chip(
                    axis,
                    delta[index],
                    PromptDeltaFormat.styleClass(axis),
                    Component.literal(
                        axis.shortLabel().getString() + " "
                                + PromptDeltaFormat.signedDeltaText(delta[index], axis.inSeconds())
                    )
                )
            );
        }
        return List.copyOf(chips);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        int width = width();
        int height = height();
        if (width <= 0 || height <= 0) {
            return;
        }
        VisualTexture styled = style().get(Styles.background);
        if (styled == null || styled.isEmpty()) {
            canvas.texture(CirrusUiTheme.textures(this).dockIsland(), 0, 0, width, height);
        }
    }

    /** Card/scatter dual-state header. */
    static final class ModeHeader extends Widget implements OreUiTooltipProvider {

        private static final int collapseButtonWidth = 18;
        private final CoPilotHost host;
        private final Consumer<Mode> onPick;
        private final Runnable onCollapse;
        private final OreUiMotion.SmoothValue indicatorX = new OreUiMotion.SmoothValue();
        private final OreUiMotion.SmoothValue indicatorW = new OreUiMotion.SmoothValue();
        private Mode mode = Mode.cards;
        private boolean showCollapse;

        ModeHeader(CoPilotHost host, Consumer<Mode> onPick, Runnable onCollapse) {
            this.host = host;
            this.onPick = onPick;
            this.onCollapse = onCollapse;
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                Rect collapse = collapseRect();
                if (collapse != null && collapse.contains(local.x(), local.y())) {
                    onCollapse.run();
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
                Mode picked = tabRect(true).contains(local.x(), local.y())
                        ? Mode.cards
                        : tabRect(false).contains(local.x(), local.y()) ? Mode.scatter : null;
                if (picked == null) {
                    return EventDispatch.pass;
                }
                onPick.accept(picked);
                CirrusUiSound.playClickSound();
                context.consume();
                return EventDispatch.consumed;
            });
        }

        @Nullable
        Rect collapseRect() {
            if (!showCollapse || width() < 70) {
                return null;
            }
            return new Rect(width() - collapseButtonWidth, 1, collapseButtonWidth, Math.max(10, height() - 2));
        }

        Rect tabRect(boolean cards) {
            int reserve = collapseRect() != null ? collapseButtonWidth + 2 : 0;
            int avail = Math.max(40, width() - reserve);
            int half = Math.max(20, (avail - 2) / 2);
            int rightW = Math.max(20, avail - half - 2);
            return new Rect(cards ? 0 : half + 2, 1, cards ? half : rightW, Math.max(10, height() - 2));
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            Rect collapse = collapseRect();
            if (collapse != null && collapse.contains(localX, localY)) {
                return OreUiRichTooltip.builder("Collapse Plans Panel").badge("FOLD", OreUiRichTooltip.Tone.accent)
                        .text("Hides the left plan cards dock to widen the canvas.", OreUiRichTooltip.Tone.secondary)
                        .build();
            }
            return null;
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            Rect cardsRect = tabRect(true);
            Rect scatterRect = tabRect(false);
            renderTab(
                canvas,
                cardsRect,
                CirrusLangs.Workbench.Pareto.cardsMode.get(),
                mode == Mode.cards,
                mouseX,
                mouseY
            );
            renderTab(
                canvas,
                scatterRect,
                CirrusLangs.Workbench.Pareto.scatterMode.get(),
                mode == Mode.scatter,
                mouseX,
                mouseY
            );
            Rect collapse = collapseRect();
            if (collapse != null) {
                CirrusPalette palette = host.palette();
                boolean hover = collapse.contains(mouseX, mouseY);
                OreUiPaint.raisedButton(
                    canvas,
                    collapse.x(),
                    collapse.y(),
                    collapse.width(),
                    collapse.height(),
                    palette,
                    false,
                    hover,
                    false,
                    false
                );
                ChromeButtons.doubleChevronLeft(
                    canvas,
                    collapse.x() + (collapse.width() - 7) / 2,
                    collapse.y() + Math.max(2, (collapse.height() - 2 - 7) / 2),
                    hover ? palette.textPrimary() : palette.textSecondary()
                );
            }
            Rect active = mode == Mode.cards ? cardsRect : scatterRect;
            int gx = Math.round(indicatorX.update(active.x() + 2, 22f));
            int gw = Math.max(6, Math.round(indicatorW.update(active.width() - 4, 22f)));
            canvas.fill(gx, active.bottom() - 2, gw, 2, host.palette().focusRing());
        }

        private void renderTab(
            SceneCanvas canvas,
            Rect rect,
            Component label,
            boolean selected,
            int mouseX,
            int mouseY
        ) {
            CirrusPalette palette = host.palette();
            boolean hover = rect.contains(mouseX, mouseY);
            if (selected) {
                OreUiPaint.raisedCard(canvas, rect.x(), rect.y(), rect.width(), rect.height(), palette, hover, true);
            } else {
                OreUiPaint.raisedButton(
                    canvas,
                    rect.x(),
                    rect.y(),
                    rect.width(),
                    rect.height(),
                    palette,
                    false,
                    hover,
                    false,
                    false
                );
            }
            Component fitted = ChromeText.fit(canvas, label, Math.max(0, rect.width() - 6));
            int textWidth = ChromeText.width(canvas, fitted);
            canvas.drawString(
                fitted,
                rect.x() + Math.max(3, (rect.width() - textWidth) / 2),
                ChromeText.centeredY(rect.y(), rect.height() - 1, canvas),
                selected ? palette.textPrimary() : palette.textSecondary(),
                false
            );
        }
    }

    /** Vertical plan card list plus the drag compare slot at the bottom. */
    public final class PlanCardList extends WidgetGroup<Widget> {

        private final WidgetGroup<Widget> cardsBox;
        private final CompareSlot compareSlot;

        PlanCardList() {
            this.cardsBox = new WidgetGroup<>();
            cardsBox.useStyle(flexColumn(), flexGrow(1), gap(2), minHeight(0));
            addWidget(cardsBox);
            this.compareSlot = new CompareSlot();
            compareSlot.useStyle(heightOf(compareSlotHeight), flexShrink(0));
            addWidget(compareSlot);
            rebuild();
        }

        public void rebuild() {
            List<Widget> existing = new ArrayList<>(cardsBox.children());
            for (Widget widget : existing) {
                cardsBox.remove(widget);
            }
            WorkbenchUiSnapshot snapshot = host.snapshot();
            if (snapshot != null && snapshot.result() != null) {
                int index = 0;
                for (ProductionPlan plan : snapshot.result().paretoPlans()) {
                    PlanCard card = new PlanCard(plan, index++);
                    card.useStyle(heightOf(cardHeight), flexShrink(0));
                    cardsBox.addWidget(card);
                }
            }
        }

        public CompareSlot compareSlot() {
            return compareSlot;
        }

        public List<ProductionPlan> plans() {
            WorkbenchUiSnapshot snapshot = host.snapshot();
            if (snapshot == null || snapshot.result() == null) {
                return List.of();
            }
            return snapshot.result().paretoPlans();
        }

        public boolean dropCompare(DragPayload payload) {
            if (!(payload instanceof DragPayload.PlanCompareDrag compare)) {
                return false;
            }
            host.setComparePlan(compare.planId());
            return true;
        }

        public boolean dropCompareAt(DragPayload payload, double localX, double localY) {
            if (!compareSlotRect().contains(localX, localY)) {
                return false;
            }
            return dropCompare(payload);
        }

        public Rect compareSlotRect() {
            return new Rect(0, Math.max(0, height() - compareSlotHeight), Math.max(1, width()), compareSlotHeight);
        }
    }

    /** One plan card: strategy name, certificate shield, five relative bars and counts. */
    public final class PlanCard extends WidgetGroup<Widget> implements OreUiTooltipProvider {

        private final ProductionPlan plan;
        private final int index;
        private final List<CostVector> reference;
        private boolean expanded;
        private long expandStartedAtMs;

        PlanCard(ProductionPlan plan, int index) {
            this.plan = Objects.requireNonNull(plan, "plan");
            this.index = index;
            this.reference = planCosts();
            addMeterBars();
            onMount(
                (scene, context, handle) -> CoPilotDnd
                        .attachProvider(scene, PlanCard.this, handle, new CardDragProvider())
            );
            onMouseClick((input, clickCount, context) -> {
                if (input.isRightClick()) {
                    WorkbenchUiSnapshot snapshot = host.snapshot();
                    String selectedId = snapshot == null ? null : snapshot.selectedPlanId();
                    String compareId = snapshot == null ? null : snapshot.comparePlanId();
                    if (plan.planId().equals(compareId)) {
                        host.setComparePlan(null);
                    } else if (!plan.planId().equals(selectedId)) {
                        host.setComparePlan(plan.planId());
                    } else {
                        host.setComparePlan(null);
                    }
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
                if (!input.isLeftClick()) {
                    return EventDispatch.pass;
                }
                FloatPos local = input.mouseRelative(this);
                int footerY = height() - 3 - 18;
                if (local.y() >= footerY && local.x() >= width() - 24) {
                    expanded = !expanded;
                    expandStartedAtMs = expanded ? OreUiMotion.nowMs() : 0L;
                    useStyle(heightOf(cardHeight + (expanded ? expandedExtraHeight : 0)), flexShrink(0));
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
                host.selectPlan(plan.planId());
                CirrusUiSound.playClickSound();
                context.consume();
                return EventDispatch.consumed;
            });
        }

        public ProductionPlan plan() {
            return plan;
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            CirrusPalette palette = host.palette();
            ProductionPlan active = host.snapshot().selectedPlan().orElse(null);
            boolean isCurrent = active != null && active.planId().equals(plan.planId());
            Component title = Component.translatable(plan.strategyLabelKey());
            OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(title)
                    .badge(
                        isCurrent ? "ACTIVE PLAN" : "CANDIDATE",
                        isCurrent ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.accent
                    )
                    .subtitle(
                        plan.steps().size() + " steps \u00b7 "
                                + plan.preparation().toAcquireFromWorld().entries().size() + " gather \u00b7 "
                                + plan.preparation().machinesToProvision().size() + " build"
                    );
            CostVector cost = plan.totalCost();
            List<OreUiRichTooltip.Pill> pills = new ArrayList<>();
            pills.add(
                OreUiRichTooltip.Pill.of(
                    "Work " + WorkbenchFormatters.formatSeconds(cost.humanActiveSeconds()),
                    OreUiRichTooltip.Tone.costH
                )
            );
            pills.add(
                OreUiRichTooltip.Pill.of(
                    "Wait " + WorkbenchFormatters.formatSeconds(cost.passiveWaitSeconds()),
                    OreUiRichTooltip.Tone.costP
                )
            );
            pills.add(
                OreUiRichTooltip.Pill
                        .of("Equip " + CoPilotPaint.decimal(cost.machineSetupCapital(), 1), OreUiRichTooltip.Tone.costK)
            );
            builder.pills(pills);
            if (!isCurrent && active != null) {
                CostVector base = active.totalCost();
                double dWork = cost.humanActiveSeconds() - base.humanActiveSeconds();
                double dWait = cost.passiveWaitSeconds() - base.passiveWaitSeconds();
                double dEquip = cost.machineSetupCapital() - base.machineSetupCapital();
                builder.divider();
                builder.keyValue(
                    Component.literal("vs Active Work"),
                    Component.literal((dWork >= 0 ? "+" : "") + WorkbenchFormatters.formatSeconds(dWork)),
                    dWork <= 0 ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.warning
                );
                builder.keyValue(
                    Component.literal("vs Active Wait"),
                    Component.literal((dWait >= 0 ? "+" : "") + WorkbenchFormatters.formatSeconds(dWait)),
                    dWait <= 0 ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.warning
                );
                builder.keyValue(
                    Component.literal("vs Active Equip"),
                    Component.literal((dEquip >= 0 ? "+" : "") + CoPilotPaint.decimal(dEquip, 1)),
                    dEquip <= 0 ? OreUiRichTooltip.Tone.positive : OreUiRichTooltip.Tone.warning
                );
            }
            if (!plan.surplusByproducts().isEmpty()) {
                builder.keyValue(
                    Component.literal("Surplus Outputs"),
                    Component.literal(plan.surplusByproducts().size() + " items"),
                    OreUiRichTooltip.Tone.costS
                );
            }
            builder.footer(
                isCurrent
                        ? CirrusLangs.Workbench.Pareto.cardFooterActive.get().getString()
                        : CirrusLangs.Workbench.Pareto.cardFooterCandidate.get().getString()
            );
            return builder.build();
        }

        public int cardIndex() {
            return index;
        }

        public DragPayload payload() {
            return new DragPayload.PlanCompareDrag(plan.planId(), plan.strategyLabelKey());
        }

        private void addMeterBars() {
            ParetoGeometry.Axis[] axes = ParetoGeometry.Axis.values();
            for (int axisIndex = 0; axisIndex < axes.length; axisIndex++) {
                ParetoGeometry.Axis axis = axes[axisIndex];
                InfoBarWidget bar = InfoBarWidget.segmented(
                    InfoBarWidget.Segment.of(() -> relativeFill(axis), "cost-" + PromptDeltaFormat.styleClass(axis))
                );
                bar.addStyleClass("cirrus-meter-thin");
                bar.useStyle(
                    positionAbsolute(),
                    insetLeft(52),
                    insetRight(42),
                    insetTop(27 + axisIndex * 13),
                    heightOf(5)
                );
                addWidget(bar);
            }
        }

        public double relativeFill(ParetoGeometry.Axis axis) {
            double value = axis.valueOf(plan.totalCost());
            double max = 0.0;
            for (CostVector cost : reference) {
                max = Math.max(max, axis.valueOf(cost));
            }
            if (!(max > 0.0)) {
                return 0.0;
            }
            return ParetoGeometry.clamp01(value / max);
        }

        private List<CostVector> planCosts() {
            WorkbenchUiSnapshot snapshot = host.snapshot();
            if (snapshot == null || snapshot.result() == null) {
                return List.of(plan.totalCost());
            }
            List<CostVector> costs = new ArrayList<>();
            for (ProductionPlan candidate : snapshot.result().paretoPlans()) {
                costs.add(candidate.totalCost());
            }
            return costs.isEmpty() ? List.of(plan.totalCost()) : List.copyOf(costs);
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = host.palette();
            int width = width();
            int height = height();
            if (width <= 0 || height <= 0) {
                return;
            }
            WorkbenchUiSnapshot snapshot = host.snapshot();
            boolean selected = snapshot != null && plan.planId().equals(snapshot.selectedPlanId());
            boolean compared = snapshot != null && plan.planId().equals(snapshot.comparePlanId());
            OreUiPaint.raisedCard(canvas, 0, 0, width, height, palette, hovered(), selected);
            if (compared) {
                canvas.strokeRect(0, 0, width, height, palette.costH(), 2);
            }
            int bandH = 18;
            OreUiPaint.headerBand(canvas, 0, 0, width, bandH, palette, selected);

            VerificationCertificate certificate = plan.certificate();
            boolean valid = certificate.valid();
            Component statusLabel = valid
                    ? CirrusLangs.Workbench.Pareto.badgeReady.get()
                    : CirrusLangs.Workbench.Pareto.badgeWarn.get();
            int pillW = canvas.font().width(statusLabel) + 10;
            int pillX = Math.max(24, width - pillW - 5);
            OreUiPaint.tagPill(
                canvas,
                pillX,
                3,
                13,
                statusLabel,
                valid ? palette.costS() : palette.ruleHardBan(),
                palette
            );
            int titleW = Math.max(20, pillX - 10);
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, Component.translatable(plan.strategyLabelKey()), titleW),
                6,
                ChromeText.centeredY(1, bandH, canvas),
                palette.textPrimary()
            );

            renderAxisRows(canvas, palette, width);

            if (expanded) {
                int detailY = 25 + 5 * 13 + 3;
                int detailH = expandedExtraHeight - 4;
                if (expandStartedAtMs == 0L) {
                    expandStartedAtMs = OreUiMotion.nowMs();
                }
                float reveal = OreUiMotion.cubicOut(
                    OreUiMotion.nowMs() - expandStartedAtMs,
                    OreUiMotion.panelSlideMs,
                    OreUiMotion.reducedMotion()
                );
                int clipH = Math.max(4, Math.round(detailH * reveal));
                canvas.withClip(6, detailY, Math.max(12, width - 12), clipH, () -> {
                    canvas.fill(6, detailY, Math.max(12, width - 12), detailH, OreUiPaint.cardFooter(palette));
                    canvas.strokeRect(
                        6,
                        detailY,
                        Math.max(12, width - 12),
                        detailH,
                        OreUiPaint.cardDivider(palette),
                        1
                    );
                    int acquireCount = plan.preparation().toAcquireFromWorld().entries().size();
                    int machineCount = plan.preparation().machinesToProvision().size();
                    int surplusCount = plan.surplusByproducts().size();
                    CoPilotPaint.text(
                        canvas,
                        CoPilotPaint.fit(
                            canvas,
                            Component.literal("Gather " + acquireCount + " \u00b7 Build " + machineCount),
                            Math.max(12, width - 20)
                        ),
                        10,
                        detailY + 5,
                        palette.textSecondary()
                    );
                    CoPilotPaint.text(
                        canvas,
                        CoPilotPaint.fit(
                            canvas,
                            Component.literal("Steps " + plan.steps().size() + " \u00b7 Surplus " + surplusCount),
                            Math.max(12, width - 20)
                        ),
                        10,
                        detailY + 17,
                        palette.textMuted()
                    );
                });
            } else {
                expandStartedAtMs = 0L;
            }

            int footerH = 18;
            int footerY = height - 3 - footerH;
            OreUiPaint.footerBand(canvas, 0, footerY, width, footerH, palette, selected);
            CoPilotPaint.text(
                canvas,
                CoPilotPaint.fit(canvas, summaryLine(), Math.max(16, width - 28)),
                6,
                ChromeText.centeredY(footerY + 1, footerH - 1, canvas),
                palette.textSecondary()
            );
            // Expand/collapse chevron affordance
            Component chevron = Component.literal(expanded ? "▾" : "▸");
            CoPilotPaint.text(
                canvas,
                chevron,
                width - 13,
                ChromeText.centeredY(footerY + 1, footerH - 1, canvas),
                palette.textMuted()
            );
        }

        private void renderAxisRows(SceneCanvas canvas, CirrusPalette palette, int width) {
            ParetoGeometry.Axis[] axes = ParetoGeometry.Axis.values();
            for (int axisIndex = 0; axisIndex < axes.length; axisIndex++) {
                ParetoGeometry.Axis axis = axes[axisIndex];
                int rowY = 25 + axisIndex * 13;
                int color = palette.costColor(axisIndex);
                drawAxisGlyph(canvas, 6, rowY, axisIndex, color);
                Component label = CoPilotPaint.fit(canvas, axis.shortLabel(), 34);
                canvas.drawString(label, 16, rowY, color, false);
                double raw = axis.valueOf(plan.totalCost());
                String valueText = axis.inSeconds()
                        ? WorkbenchFormatters.formatSeconds(raw)
                        : WorkbenchFormatters.formatCompactCount(raw);
                CoPilotPaint.textRight(canvas, Component.literal(valueText), width - 6, rowY, palette.textSecondary());
            }
        }

        private void drawAxisGlyph(SceneCanvas canvas, int x, int y, int axisIndex, int color) {
            switch (axisIndex) {
                case 0 -> {
                    // Work: tiny hammer/wrench silhouette (7x7)
                    canvas.fill(x + 1, y + 1, 5, 2, color);
                    canvas.fill(x + 3, y + 3, 1, 4, color);
                }
                case 1 -> {
                    // Wait: clock square with hands (7x7)
                    canvas.strokeRect(x, y + 1, 7, 6, color, 1);
                    canvas.fill(x + 3, y + 2, 1, 3, color);
                    canvas.fill(x + 3, y + 4, 2, 1, color);
                }
                case 2 -> {
                    // Equip: gear/anvil block (7x7)
                    canvas.fill(x + 1, y + 2, 5, 4, color);
                    canvas.fill(x + 2, y + 1, 3, 1, color);
                    canvas.fill(x + 3, y + 3, 1, 2, host.palette().cardSurface());
                }
                case 3 -> {
                    // Stock: crate/chest (7x7)
                    canvas.strokeRect(x, y + 1, 7, 6, color, 1);
                    canvas.fill(x + 1, y + 3, 5, 1, color);
                    canvas.fill(x + 3, y + 3, 1, 2, color);
                }
                default -> {
                    // Steps: 3-bar checklist (7x7)
                    canvas.fill(x, y + 1, 2, 1, color);
                    canvas.fill(x + 3, y + 1, 4, 1, color);
                    canvas.fill(x, y + 3, 2, 1, color);
                    canvas.fill(x + 3, y + 3, 4, 1, color);
                    canvas.fill(x, y + 5, 2, 1, color);
                    canvas.fill(x + 3, y + 5, 4, 1, color);
                }
            }
        }

        private Component summaryLine() {
            int steps = plan.steps().size();
            int machines = plan.preparation().machinesToProvision().size();
            if (machines > 0) {
                return CirrusLangs.Workbench.Pareto.cardSummary.get(steps, machines);
            }
            return CirrusLangs.Workbench.Pareto.cardSummaryNoMachines.get(steps);
        }

        final class CardDragProvider implements DragProvider {

            @Override
            public boolean draggable(@Nullable Scene dragScene, InputContext input, DragContext dragContext) {
                return parent() != null && isMouseOver(input);
            }

            @Override
            public @Nullable DraggableElement<?> getDraggableElement(
                @Nullable Scene dragScene,
                InputContext input,
                DragContext dragContext
            ) {
                if (parent() == null || !isMouseOver(input)) {
                    return null;
                }
                return CoPilotDnd.Carried.of(PlanCard.this, payload());
            }
        }
    }

    /** Bottom drag compare slot: accepts a PlanCompareDrag from any plan card and shows the five deltas while hovering. */
    public final class CompareSlot extends Widget implements OreUiTooltipProvider {

        CompareSlot() {
            setInteractive(true);
            onMount(
                (scene, context, handle) -> CoPilotDnd
                        .attachConsumer(scene, CompareSlot.this, handle, new SlotConsumer())
            );
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick() && !input.isRightClick()) {
                    return EventDispatch.pass;
                }
                if (compareMode()) {
                    host.setComparePlan(null);
                    CirrusUiSound.playClickSound();
                    context.consume();
                    return EventDispatch.consumed;
                }
                return EventDispatch.pass;
            });
        }

        @Override
        public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
            WorkbenchUiSnapshot snapshot = host.snapshot();
            ProductionPlan selected = snapshot == null ? null : snapshot.selectedPlan().orElse(null);
            ProductionPlan compared = snapshot == null ? null : snapshot.comparePlan().orElse(null);
            int planCount = cardList.plans().size();
            OreUiRichTooltip.Builder builder = OreUiRichTooltip
                    .builder(CirrusLangs.Workbench.Pareto.compareSlotTitle.get());
            if (compared != null && selected != null && !compared.planId().equals(selected.planId())) {
                Component comparedName = Component.translatable(compared.strategyLabelKey());
                builder.badge("DIFF", OreUiRichTooltip.Tone.accent);
                builder.text(
                    CirrusLangs.Workbench.Pareto.compareSlotTooltipClear.get(comparedName),
                    OreUiRichTooltip.Tone.secondary
                );
                return builder.build();
            }
            if (planCount < 2) {
                builder.badge("1 PLAN", OreUiRichTooltip.Tone.muted);
                builder.text(
                    CirrusLangs.Workbench.Pareto.compareSlotTooltipSingle.get(),
                    OreUiRichTooltip.Tone.secondary
                );
                return builder.build();
            }
            builder.badge("COMPARE", OreUiRichTooltip.Tone.accent);
            builder.text(CirrusLangs.Workbench.Pareto.compareSlotTooltipBody.get(), OreUiRichTooltip.Tone.secondary);
            return builder.build();
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette palette = host.palette();
            int width = width();
            int height = height();
            if (width <= 0 || height <= 0) {
                return;
            }
            boolean comparing = compareMode();
            canvas.fill(0, 0, width, height, palette.recessedSurface());
            int border = hovered() || comparing ? palette.focusRing() : palette.textDisabled();
            CoPilotPaint.dashedRect(canvas, 0, 0, width, height, border);
            List<PromptDeltaFormat.Chip> chips = compareChips();
            if (chips.isEmpty()) {
                WorkbenchUiSnapshot snapshot = host.snapshot();
                boolean sameAsActive = snapshot != null
                        && snapshot.comparePlanId() != null
                        && snapshot.comparePlanId().equals(snapshot.selectedPlanId());
                Component rawHint;
                if (sameAsActive) {
                    rawHint = CirrusLangs.Workbench.Pareto.compareSlotSamePlanHint.get();
                } else if (cardList.plans().size() < 2) {
                    rawHint = CirrusLangs.Workbench.Pareto.compareSlotSingleHint.get();
                } else {
                    rawHint = CirrusLangs.Workbench.Pareto.compareSlotHint.get();
                }
                Component hint = CoPilotPaint.fit(canvas, rawHint, Math.max(8, width - 8));
                canvas.drawString(
                    hint,
                    4,
                    ChromeText.centeredY(0, height, canvas),
                    sameAsActive ? palette.costH() : palette.textMuted(),
                    false
                );
                return;
            }
            int cursor = 4;
            int limit = width - 4;
            int row = 4;
            for (PromptDeltaFormat.Chip chip : chips) {
                int chipWidth = CoPilotPaint.chipWidth(canvas, chip.label());
                if (cursor + chipWidth > limit) {
                    cursor = 4;
                    row += CoPilotPaint.chipHeight + 2;
                }
                if (row + CoPilotPaint.chipHeight > height) {
                    return;
                }
                CoPilotPaint.chip(canvas, cursor, row, chip.label(), palette, chip.styleClass());
                cursor += chipWidth + 3;
            }
        }

        final class SlotConsumer implements DragConsumer {

            @Override
            public boolean consume(DraggableElement<?> element, DragContext context) {
                if (!(element instanceof CoPilotDnd.Carried carried)) {
                    return false;
                }
                if (carried.cancelled()) {
                    return true;
                }
                if (!CoPilotDnd.consumable(carried.payload(), false, context.getCurrent(), absoluteBounds())) {
                    return false;
                }
                return dropCompare(carried.payload());
            }
        }
    }
}

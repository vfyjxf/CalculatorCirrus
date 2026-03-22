package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.SimpleMathCalculatorWidget;
import dev.vfyjxf.cirrus.ui.widget.flowchart.FlowChartTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.flowchart.RecipeStepsWidget;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyles;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.ui.widget.ButtonWidget;
import dev.vfyjxf.cloudlib.ui.widget.DividerWidget;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static dev.vfyjxf.cloudlib.api.ui.effect.UIEffects.autoFocus;
import static dev.vfyjxf.cloudlib.api.ui.effect.UIEffects.floating;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.flip;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;
import static dev.vfyjxf.taffy.style.TaffyDimension.percent;

/**
 * Calculator main widget.
 * <p>
 * Flow:
 * <ol>
 *   <li>User selects a target item and quantity</li>
 *   <li>Before calculate: shows a rough resource summary (TargetSummaryCard)</li>
 *   <li>Click "Calculate" button: computes the process graph</li>
 *   <li>After calculate: expands the RecipeStepsWidget (windowed) with detailed steps &amp; statistics</li>
 * </ol>
 */
public class CalculatorWidget extends CompositeWidget<Widget> {

    private final RowWidget header;
    private final TargetWidget targetWidget;
    private final NumberInputWidget craftCount;
    private final ButtonWidget calculateBtn;
    private final ButtonWidget matBtn;

    /** Pre-calculation rough summary. */
    private TargetSummaryCard summaryCard;
    /** Post-calculation recipe steps view (windowed). */
    private RecipeStepsWidget stepsWidget;
    /** Status label shown after calculation. */
    private LabelWidget statusLabel;

    private boolean calculated = false;

    public CalculatorWidget() {
        useStyle(
            UIStyles.flexColumn(),
            sizeFull()
        );

        // ── Header row: target slot + count input + calculate button ──
        header = RowWidget.create();
        header.useStyle(
            flexRow(),
            background(Background.dark),
            border(1),
            columnGap(3),
            marginTop(3),
            marginHorizontal(5),
            flexGrow(1),
            maxHeight(20),
            alignItemsCenter()
        );
        addWidget(header);

        targetWidget = header.addWidget(new TargetWidget());

        craftCount = header.addWidget(NumberInputWidget.create(1)
                                                       .setMinValue(1)
                                                       .setScrollStep(1)
                                                       .onCalculatorOpen(this::openCalculatorPopup)
        );
        craftCount.useStyle(
            marginVertical(2),
            widthOf(percent(0.2f)),
            heightOf(14)
        );

        calculateBtn = header.addWidget(
            ButtonWidget.of(Component.translatable("cirrus.ui.calc.calculate"), this::onCalculate)
                        .setTextures(Background.flat, Background.Border.light, Background.inset)
                        .setTextColor(0xDDDDDD)
        );
        calculateBtn.useStyle(
            marginVertical(1),
            marginLeft(2),
            heightOf(16),
            minWidth(50)
        );

        matBtn = header.addWidget(
            ButtonWidget.of(Component.translatable("cirrus.ui.calc.mat"), this::onToggleMat)
                        .setTextures(Background.flat, Background.Border.light, Background.inset)
                        .setTextColor(0xDDDDDD)
        );
        matBtn.useStyle(
            marginVertical(1),
            marginLeft(2),
            heightOf(16),
            minWidth(30)
        );
        matBtn.setVisible(false);

        addWidget(DividerWidget.horizontal());

        // ── Pre-calculation: rough summary card ──
        showPreCalculationView();
    }

    // ═══════════════════ View switching ═══════════════════

    /**
     * Shows the rough resource summary before calculation.
     */
    private void showPreCalculationView() {
        // Remove previous post-calc widgets if any
        if (stepsWidget != null) {
            removeWidget(stepsWidget);
            stepsWidget = null;
        }
        if (statusLabel != null) {
            removeWidget(statusLabel);
            statusLabel = null;
        }

        ProjectedRecipe debugView = DebugRecipeProvider.createDebugView();
        summaryCard = addWidget(new TargetSummaryCard(debugView));
        summaryCard.useStyle(flexGrow(1));

        calculated = false;
        calculateBtn.setEnabled(true);
        matBtn.setVisible(false);
    }

    /**
     * Switches to the post-calculation view: recipe steps widget (windowed) + statistics.
     */
    private void showPostCalculationView() {
        // Remove pre-calc summary
        if (summaryCard != null) {
            removeWidget(summaryCard);
            summaryCard = null;
        }

        // Status label (between header and steps)
        statusLabel = addWidget(
            LabelWidget.of(Component.translatable("cirrus.ui.calc.calculated"))
                       .setColor(0x55FF55)
                       .setShadow(true)
        );
        statusLabel.useStyle(
            marginHorizontal(6),
            marginTop(2)
        );

        // Windowed recipe steps widget
        var graph = DebugRecipeProvider.createDebugProcessGraph();
        stepsWidget = addWidget(new RecipeStepsWidget());
        stepsWidget.setGraph(graph)
                   .setCraftCount(craftCount.getValue());
        stepsWidget.useStyle(
            flexGrow(1),
            margin(4)
        );

        // Show target output in header slot
        var sinks = graph.sinks();
        if (!sinks.isEmpty()) {
            ProcessNode targetNode = sinks.get(0);
            var outputs = targetNode.outputs();
            if (!outputs.isEmpty() && outputs.get(0).itemStack() != null) {
                targetWidget.setTargetItem(outputs.get(0).itemStack(), targetNode, craftCount.getValue());
            }
        }

        matBtn.setVisible(true);
        calculated = true;
    }

    // ═══════════════════ Actions ═══════════════════

    private void onCalculate() {
        if (calculated) {
            // Re-calculate: reset to pre-calc then immediately expand
            showPreCalculationView();
        }
        showPostCalculationView();
        calculateBtn.setLabel(Component.translatable("cirrus.ui.calc.recalculate"));
    }

    private void onToggleMat() {
        if (stepsWidget != null) {
            stepsWidget.toggleMaterialPanel();
        }
    }

    private void openCalculatorPopup(NumberInputWidget source, int mouseX, int mouseY) {
        var countCalc = SimpleMathCalculatorWidget.create();
        countCalc.setInitialValue(source.getValue())
                 .onResultConfirmed(source::setValue)
                 .onClose(() -> removeWidget(countCalc));

        int headerCenterX = header.absolutePos().x() + header.width() / 2;
        int crossAxisSkid = mouseX - headerCenterX;

        countCalc.useEffect(
            autoFocus(),
            floating(header, FloatingPlacement.bottom, offset(2, crossAxisSkid), flip(), shift(4))
        );
        addWidget(countCalc);
    }

    // ═══════════════════ Target Widget ═══════════════════

    private static class TargetWidget extends Widget {
        private ItemStack targetItem = ItemStack.EMPTY;

        TargetWidget() {
            useStyle(
                background(Background.Slot.dark),
                sizeOf(18, 18)
            );

            onMouseEnter((mouseX, mouseY, context) -> {
                if (!targetItem.isEmpty() && tooltip().notEmpty()) {
                    scene().setHoverTooltip(tooltip());
                }
            });
            onMouseLeave((mouseX, mouseY, context) -> {
                scene().setHoverTooltip(Tooltip.create());
            });
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            super.renderInternal(canvas, mouseX, mouseY, partialTicks);
            if (!targetItem.isEmpty()) {
                canvas.renderItem(targetItem, 1, 1);
            }
        }

        void setTargetItem(ItemStack stack, ProcessNode targetNode, long craftCount) {
            this.targetItem = stack.copy();
            if (stack.isEmpty()) return;

            Tooltip tip = new Tooltip();

            // Item name as title line
            var tooltipContext = TooltipContext.create();
            var lines = stack.getTooltipLines(tooltipContext, tooltipContext.player(), tooltipContext.flag());
            if (!lines.isEmpty()) {
                tip.add(lines.get(0));
            }

            // Recipe preview card via FlowChartTooltipComponent
            var b = FlowChartTooltipComponent.builder();
            b.section(targetNode.name(), targetNode.tierColor());
            b.gap(2);

            // Machine & tier
            b.kv("\u673A\u5668", 0xFF888888, targetNode.machine(), targetNode.tierColor());
            b.kv("\u7B49\u7EA7", 0xFF888888, targetNode.tierName(), targetNode.tierColor());

            b.divider();

            // Inputs
            if (!targetNode.inputs().isEmpty()) {
                b.section("\u8F93\u5165", 0xFF88CC88);
                b.slots(null, 0, targetNode.inputs());
            }

            // Outputs
            if (!targetNode.outputs().isEmpty()) {
                b.section("\u8F93\u51FA", 0xFFCC8888);
                b.slots(null, 0, targetNode.outputs());
            }

            b.divider();

            // Stats
            b.kv("\u65F6\u957F", targetNode.durationFormatted());
            b.kv("\u901F\u7387", 0xFF888888, targetNode.euFormatted(), 0xFFCC7700);
            long totalEU = (long) targetNode.euPerTick() * targetNode.durationTicks();
            String euStr = totalEU < 1000 ? totalEU + " EU"
                    : totalEU < 1_000_000 ? String.format("%.1fK EU", totalEU / 1000.0)
                    : String.format("%.1fM EU", totalEU / 1_000_000.0);
            b.kv("\u603B EU", 0xFF888888, euStr, 0xFFFFCC44);
            b.kv("\u5408\u6210\u6570\u91CF", 0xFF888888, "\u00d7 " + craftCount, 0xFFFFFFFF);

            tip.add(b.build());
            setTooltip(tip);
        }
    }

}

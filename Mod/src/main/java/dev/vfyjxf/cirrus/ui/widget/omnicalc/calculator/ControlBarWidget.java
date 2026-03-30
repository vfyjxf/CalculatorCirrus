package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Toggle;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Top control bar for the Calculator page.
 * Styled with texture-backed chrome matching Cirrus shell controls.
 */
public class ControlBarWidget extends CompositeWidget<Widget> {

    public record ControlBarMetrics(int height, int targetSlotSize, int amountInputWidth, int calculateButtonWidth) {}

    //region Metrics
    private static final int barHeight = 30;
    private static final int targetSlotSize = 18;
    private static final int amountInputWidth = 36;
    private static final int calcButtonWidth = 60;
    private static final int calcButtonHeight = 16;
    private static final int calcIconX = 5;
    private static final int calcIconY = 3;
    private static final int calcIconSize = 10;
    private static final int calcTextX = 17;
    private static final int calcTextY = 4;
    //endregion

    //region State
    private final OmniCalculatorView parentView;
    private final TargetSlotWidget targetSlot;
    private final NumberInputWidget amountInput;
    //endregion

    //region Init
    public ControlBarWidget(OmniCalculatorView parentView) {
        this.parentView = parentView;

        useStyle(
                flexRow(),
                alignItemsCenter(),
                gap(2),
                heightOf(barHeight),
                paddingHorizontal(4),
                paddingVertical(4),
                zIndexOf(20)
        );

        onRender((canvas, mx, my, pt, self, ctx) -> renderBarBackground(canvas));

        targetSlot = new TargetSlotWidget();
        addWidget(targetSlot);

        var xLabel = LabelWidget.of(Component.literal("\u00d7"))
                .setColor(textPrimary)
                .setShadow(false);
        xLabel.useStyle(widthOf(8));
        addWidget(xLabel);

        amountInput = NumberInputWidget.create(1)
                .setMinValue(1)
                .setScrollStep(1);
        amountInput.useStyle(widthOf(amountInputWidth), heightOf(14));
        addWidget(amountInput);

        addWidget(SpacerWidget.create());
        addWidget(createCalculateButton());
    }
    //endregion

    //region Public API
    static ControlBarMetrics debugMetrics() {
        return new ControlBarMetrics(barHeight, targetSlotSize, amountInputWidth, calcButtonWidth);
    }

    static boolean debugUsesLegacyStripTexture() {
        return false;
    }

    static boolean debugUsesStandardButtonTexture() {
        return true;
    }

    public void setTargetItem(ItemStack stack) {
        targetSlot.setTargetItem(stack);
    }

    public long getAmount() {
        return amountInput.getValue();
    }
    //endregion

    //region Rendering
    private void renderBarBackground(SceneCanvas canvas) {
        int w = width(), h = height();
        canvas.texture(Background.Outlined.flat, 0, 0, w, h);
        canvas.texture(Background.flat, 2, 2, w - 4, h - 4);
    }

    private Widget createCalculateButton() {
        var calculateButton = new Widget();
        calculateButton.useStyle(sizeOf(calcButtonWidth, calcButtonHeight));
        calculateButton.onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = self.width();
            canvas.texture(self.hovered() ? Toggle.Button.hover : Toggle.Button.up, 0, 0, w, calcButtonHeight);
            canvas.texture(Icon.Calc.calculate, calcIconX, calcIconY, calcIconSize, calcIconSize);
            canvas.drawString("\u5F00\u59CB\u8BA1\u7B97", calcTextX, calcTextY, textPrimary, false);
        });
        calculateButton.onMouseClick((input, clicks, ctx) -> {
            parentView.onCalculate();
            return EventDispatch.consumed;
        });
        return calculateButton;
    }
    //endregion

    //region TargetSlot
    private static final class TargetSlotWidget extends Widget {

        @Nullable
        private ItemStack targetItem;

        private TargetSlotWidget() {
            useStyle(sizeOf(targetSlotSize, targetSlotSize));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            canvas.texture(Background.Slot.dark, 0, 0, width(), height());
            if (targetItem != null && !targetItem.isEmpty()) {
                canvas.renderItem(targetItem, 1, 1);
            }
        }

        private void setTargetItem(ItemStack stack) {
            targetItem = stack == null ? ItemStack.EMPTY : stack.copy();
        }
    }
    //endregion
}

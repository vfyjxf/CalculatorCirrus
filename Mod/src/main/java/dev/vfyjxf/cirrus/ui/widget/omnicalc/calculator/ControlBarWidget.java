package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.SpacerWidget;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.*;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;
import static dev.vfyjxf.taffy.style.TaffyDimension.percent;

/**
 * Top control bar for the Calculator page.
 * Styled with rounded containers matching omnicalc-pro.
 */
public class ControlBarWidget extends CompositeWidget<Widget> {

    private final OmniCalculatorView parentView;
    private final TextFieldWidget searchField;
    private final NumberInputWidget amountInput;

    public ControlBarWidget(OmniCalculatorView parentView) {
        this.parentView = parentView;

        useStyle(
                flexRow(),
                alignItemsCenter(),
                gap(3),
                paddingHorizontal(6),
                paddingVertical(3),
                zIndexOf(20)
        );

        // Render: semi-transparent panel background with bottom border
        onRender((canvas, mx, my, pt, self, ctx) -> {
            int w = width(), h = height();
            canvas.fill(0, 0, w, h, 0xCC111827);
            canvas.fill(0, h - 1, w, 1, BORDER_MAIN);
        });

        // ── Target item slot ──
        var targetSlot = new Widget();
        targetSlot.useStyle(sizeOf(16, 16));
        targetSlot.onRender((canvas, mx, my, pt, self, ctx) -> {
            canvas.roundedRect(0, 0, 16, 16, 3f, BG_HOVER, 1f, BORDER_SEC);
        });
        addWidget(targetSlot);

        // ── Search / item name field ──
        searchField = TextFieldWidget.create("")
                .setPlaceholder("Search item...")
                .setEditable(false);
        searchField.useStyle(
                widthOf(percent(0.35f)),
                heightOf(14),
                marginLeft(3)
        );
        addWidget(searchField);

        // ── "x" label ──
        var xLabel = LabelWidget.of(Component.literal("\u00d7"))
                .setColor(TEXT_SECONDARY)
                .setShadow(true);
        xLabel.useStyle(marginLeft(3), widthOf(6));
        addWidget(xLabel);

        // ── Amount input ──
        amountInput = NumberInputWidget.create(1)
                .setMinValue(1)
                .setScrollStep(1);
        amountInput.useStyle(widthOf(28), heightOf(14));
        addWidget(amountInput);

        // ── Settings button ──
        var settingsWidget = new Widget();
        settingsWidget.useStyle(sizeOf(14, 14), marginLeft(2));
        settingsWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? BG_ELEVATED : BG_HOVER;
            canvas.roundedRect(0, 0, 14, 14, 3f, bg, 1f, BORDER_SEC);
            canvas.drawCenteredString("\u2699", 7, 4, TEXT_SECONDARY);
        });
        addWidget(settingsWidget);

        // ── Spacer ──
        addWidget(SpacerWidget.create());

        // ── Calculate button (rounded, accent blue, with shadow) ──
        var calcWidget = new Widget();
        calcWidget.useStyle(sizeOf(60, 14));
        calcWidget.onRender((canvas, mx, my, pt, self, ctx) -> {
            int bg = self.hovered() ? ACCENT_LIGHT : ACCENT;
            canvas.shadow(0, 1, 60, 14, 4f, 2f, 4f, 0x402563EB);
            canvas.roundedRect(0, 0, 60, 14, 4f, bg, 0, 0);
            canvas.drawCenteredString("\u25B6 Calculate", 30, 4, TEXT_WHITE);
        });
        calcWidget.onMouseClick((input, clicks, ctx) -> {
            parentView.onCalculate();
            return dev.vfyjxf.cloudlib.api.event.EventDispatch.consumed;
        });
        addWidget(calcWidget);
    }

    public void setTargetItem(ItemStack stack) {
        // TODO: render target item in slot
    }

    public long getAmount() {
        return amountInput.getValue();
    }
}

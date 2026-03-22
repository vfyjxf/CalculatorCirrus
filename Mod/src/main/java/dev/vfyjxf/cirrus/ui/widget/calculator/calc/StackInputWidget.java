package dev.vfyjxf.cirrus.ui.widget.calculator.calc;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import dev.vfyjxf.cloudlib.ui.widget.RowWidget;
import dev.vfyjxf.taffy.style.Overflow;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

/**
 * Input resource widget with slot icon, amount label, and progress bar.
 * <pre>
 * [slot] [×amount     ]   ← icon + amount with dark background
 * [■■■green■■■|■■red■■]   ← progress bar (green=exist, red=missing)
 * </pre>
 */
public class StackInputWidget extends CompositeWidget<Widget> {

    private static final int GREEN_COLOR = 0xFF55FF55;
    private static final int RED_COLOR = 0xFFFF5555;
    private static final int BAR_BG_COLOR = 0xFF2A2A2A;
    private static final int BAR_BORDER_COLOR = 0xFF555555;
    private static final int BAR_HEIGHT = 6;

    public StackInputWidget(RecipeContent content, long total, long exist) {
        useStyle(
            flexColumn(),
            rowGap(1)
        );

        //region Row: [slot icon] [amount label]

        var infoRow = addWidget(RowWidget.create());
        infoRow.useStyle(
            flexRow(),
            alignItemsCenter(),
            columnGap(2)
        );

        var slot = infoRow.addWidget(ContentStackWidget.fromContent(content));
        slot.useStyle(background(Background.Slot.dark));

        var amountLabel = infoRow.addWidget(createAmountLabel(content, total, exist));
        amountLabel.useStyle(
            paddingHorizontal(2),
            flexShrink(1),
            flex(1),
            minWidth(0),
            overflow(Overflow.HIDDEN)
        );

        //endregion

        //region Progress bar: green portion = exist, red portion = missing

        var progressBar = addWidget(new Widget());
        progressBar.useStyle(
            widthOf(TaffyDimension.percent(1)),
            heightOf(BAR_HEIGHT)
        );

        progressBar.onRender((canvas, mouseX, mouseY, partialTicks, self, ctx) -> {
            int w = self.width();
            int h = self.height();
            // Draw border
            canvas.strokeRect(0, 0, w, h, BAR_BORDER_COLOR);
            // Fill background inside border
            canvas.fill(1, 1, w - 2, h - 2, BAR_BG_COLOR);
            // Fill green (existing) + red (missing) inside border
            float ratio = total > 0 ? Math.min(1f, (float) exist / total) : 0f;
            int innerW = w - 2;
            int greenW = (int) (innerW * ratio);
            if (greenW > 0) {
                canvas.fill(1, 1, greenW, h - 2, GREEN_COLOR);
            }
            if (greenW < innerW) {
                canvas.fill(1 + greenW, 1, innerW - greenW, h - 2, RED_COLOR);
            }
        });

        // Tooltip on progress bar
        progressBar.setTooltip(buildProgressTooltip(total, exist));

        //endregion

        // Tooltip on amount label
        amountLabel.setTooltip(buildProgressTooltip(total, exist));
    }

    /**
     * Creates the amount label with appropriate formatting based on fulfillment state.
     */
    private static LabelWidget createAmountLabel(RecipeContent content, long total, long exist) {
        boolean isFluid = content instanceof StackContent<?, ?> sc && sc.isFluid();
        String suffix = isFluid ? " mB" : "";

        if (exist > 0 && exist < total) {
            // Partially fulfilled: "exist / total"
            return LabelWidget.of(
                Component.literal(NumberUtils.toSuffix(exist))
                         .withStyle(ChatFormatting.GREEN)
                         .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                         .append(Component.literal(NumberUtils.toSuffix(total) + suffix)
                                          .withStyle(ChatFormatting.WHITE))
            ).setShadow(true);
        } else if (exist >= total && total > 0) {
            // Fully satisfied: "✔ total"
            return LabelWidget.of(
                Component.literal("\u2714 " + NumberUtils.toSuffix(total) + suffix)
            ).setColor(0x55FF55).setShadow(true);
        } else {
            // Nothing available: "×total"
            return LabelWidget.of(
                Component.literal("\u00D7" + NumberUtils.toSuffix(total) + suffix)
            ).setColor(0xFFFFFF).setShadow(true);
        }
    }

    /**
     * Builds a RichTooltip with progress information.
     */
    private static Tooltip buildProgressTooltip(long total, long exist) {
        var tooltip = Tooltip.create();
        tooltip.add(
            Component.translatable("cirrus.ui.summary.progress",
                NumberUtils.toSuffix(exist), NumberUtils.toSuffix(total)
            ).withStyle(ChatFormatting.GRAY)
        );
        float pct = total > 0 ? (float) exist / total * 100f : 0f;
        ChatFormatting pctColor = pct >= 100 ? ChatFormatting.GREEN : ChatFormatting.RED;
        tooltip.add(
            Component.translatable("cirrus.ui.summary.progress.pct",
                String.format("%.1f%%", pct)
            ).withStyle(pctColor)
        );
        return tooltip;
    }
}

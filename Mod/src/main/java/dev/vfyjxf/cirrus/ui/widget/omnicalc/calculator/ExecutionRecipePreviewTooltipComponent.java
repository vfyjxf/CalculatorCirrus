package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Dedicated recipe preview tooltip for execution UI.
 * Uses Cirrus flat background texture and a fixed visual recipe layout.
 */
public final class ExecutionRecipePreviewTooltipComponent implements TooltipComponent {

    private static final int pad = 6;
    private static final int sectionGap = 4;
    private static final int slotSize = 16;
    private static final int slotStep = 18;
    private static final int lineH = 10;
    private static final int rowGap = 2;

    private final ProcessNode node;
    private final long craftCount;

    private ExecutionRecipePreviewTooltipComponent(ProcessNode node, long craftCount) {
        this.node = node;
        this.craftCount = Math.max(1, craftCount);
    }

    public static ExecutionRecipePreviewTooltipComponent of(ProcessNode node, long craftCount) {
        return new ExecutionRecipePreviewTooltipComponent(node, craftCount);
    }

    public static ClientTooltipComponent toClient(ExecutionRecipePreviewTooltipComponent data) {
        return new Client(data);
    }

    private static final class Client implements ClientTooltipComponent {
        private static final int titleColor = 0xFFF5F2E8;
        private static final int subColor = 0xFFD0CBB7;
        private static final int labelColor = 0xFFE7E1CD;
        private static final int valueColor = 0xFFF7F3E7;
        private static final int arrowColor = 0xFFE8DFB8;
        private static final int dividerColor = 0x66A8A08A;

        private static final int catalystTint = 0x5AA775F0;
        private static final int machineTint = 0x6674A8FF;

        private final ExecutionRecipePreviewTooltipComponent data;

        private Client(ExecutionRecipePreviewTooltipComponent data) {
            this.data = data;
        }

        @Override
        public int getWidth(Font font) {
            int leftCols = Math.max(1, data.node.inputs().size());
            int rightCols = Math.max(1, data.node.outputs().size());
            int ioW = leftCols * slotStep + 16 + rightCols * slotStep;
            int headerW = Math.max(
                font.width(data.node.name()),
                font.width(machineTitle(data.node)) + 12
            );
            int footerW = font.width(metaLine(data.node, data.craftCount));
            return Math.max(Math.max(ioW, headerW), footerW) + pad * 2;
        }

        @Override
        public int getHeight() {
            int h = pad;
            h += lineH; // title
            h += lineH; // machine line
            h += rowGap;
            h += lineH; // section labels
            h += slotSize + 2; // io row

            int catalystCount = catalystSlots(data.node).size();
            if (catalystCount > 0) {
                h += sectionGap;
                h += lineH;
                h += slotSize + 2;
            }

            h += sectionGap;
            h += 1;
            h += sectionGap;
            h += lineH; // meta
            h += lineH; // stats
            h += pad - 1;
            return h;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            int w = getWidth(font);
            int h = getHeight();
            CirrusTextures.Background.flat.render(graphics, x, y, w, h);

            int cy = y + pad;
            int left = x + pad;
            int right = x + w - pad;

            // Header
            graphics.drawString(font, trim(font, data.node.name(), w - pad * 2), left, cy, titleColor, true);
            cy += lineH;
            graphics.drawString(font, trim(font, machineTitle(data.node), w - pad * 2), left, cy, subColor, true);
            cy += lineH + rowGap;

            // I/O labels
            String inLabel = Component.translatable("cirrus.ui.execution.tooltip.preview.inputs").getString();
            String outLabel = Component.translatable("cirrus.ui.execution.tooltip.preview.outputs").getString();
            graphics.drawString(font, inLabel, left, cy, labelColor, false);
            int outW = font.width(outLabel);
            graphics.drawString(font, outLabel, right - outW, cy, labelColor, false);
            cy += lineH;

            // I/O row
            var inputs = sortSlots(data.node.inputs());
            var outputs = sortSlots(data.node.outputs());

            int leftX = left;
            for (MaterialSlot slot : inputs) {
                drawSlot(graphics, font, leftX, cy, slot, false, data.craftCount);
                leftX += slotStep;
            }

            int rightCount = Math.max(1, outputs.size());
            int rightStart = right - rightCount * slotStep + (slotStep - slotSize);
            int rx = rightStart;
            for (MaterialSlot slot : outputs) {
                drawSlot(graphics, font, rx, cy, slot, false, data.craftCount);
                rx += slotStep;
            }

            int arrowX = x + w / 2 - 5;
            int arrowY = cy + 4;
            graphics.drawString(font, "\u2192", arrowX, arrowY, arrowColor, false);
            cy += slotSize + 2;

            // Catalyst row (optional)
            var catalysts = catalystSlots(data.node);
            if (catalysts.notEmpty()) {
                cy += sectionGap;
                String cLabel = Component.translatable("cirrus.ui.execution.tooltip.preview.catalysts").getString();
                graphics.drawString(font, cLabel, left, cy, 0xFFCCB7F7, false);
                cy += lineH;

                int cx = left;
                for (MaterialSlot slot : catalysts) {
                    drawSlot(graphics, font, cx, cy, slot, true, data.craftCount);
                    cx += slotStep;
                }
                cy += slotSize + 2;
            }

            // Divider
            cy += sectionGap;
            graphics.fill(left, cy, right, cy + 1, dividerColor);
            cy += sectionGap + 1;

            // Footer
            graphics.drawString(font, trim(font, metaLine(data.node, data.craftCount), w - pad * 2), left, cy, valueColor, false);
            cy += lineH;
            String stats = data.node.durationFormatted() + "  |  " + data.node.euFormatted();
            graphics.drawString(font, trim(font, stats, w - pad * 2), left, cy, subColor, false);
        }

        private static void drawSlot(
            GuiGraphics graphics,
            Font font,
            int x,
            int y,
            MaterialSlot slot,
            boolean catalyst,
            long craftCount
        ) {
            CirrusTextures.Background.Slot.dark.render(graphics, x - 1, y - 1, slotSize + 2, slotSize + 2);
            if (catalyst) {
                graphics.fill(x, y, x + slotSize, y + slotSize, catalystTint);
            }

            long total = Math.max(1, slot.amount()) * craftCount;
            if (slot.itemStack() != null && !slot.itemStack().isEmpty()) {
                ItemStack stack = slot.itemStack().copy();
                stack.setCount((int) Math.min(Integer.MAX_VALUE, total));
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
                return;
            }
            if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(x, y, 0);
                FluidStackRenderer.instance.renderBase(graphics, slot.fluidStack(), slotSize, slotSize, 0);
                pose.popPose();
                drawAmountText(graphics, font, x, y, shortAmount(total));
                return;
            }

            // Fallback for non-item/non-fluid
            int tint = catalyst ? catalystTint : machineTint;
            graphics.fill(x + 2, y + 2, x + slotSize - 2, y + slotSize - 2, tint);
            String txt = shortAmount(total);
            drawAmountText(graphics, font, x, y, txt);
        }

        private static void drawAmountText(GuiGraphics graphics, Font font, int x, int y, String text) {
            int tw = font.width(text);
            graphics.drawString(font, text, x + slotSize - tw, y + slotSize - 8, 0xFFFFFFFF, true);
        }

        private static ImmutableList<MaterialSlot> sortSlots(java.util.List<MaterialSlot> slots) {
            return Lists.immutable.ofAll(slots)
                .toSortedList(Comparator.comparing(slot -> slot.materialName().toLowerCase()))
                .toImmutable();
        }

        private static ImmutableList<MaterialSlot> catalystSlots(ProcessNode node) {
            return Lists.immutable.ofAll(node.inputs())
                .select(MaterialSlot::isCatalyst)
                .toImmutable();
        }

        private static String machineTitle(ProcessNode node) {
            @Nullable MachineInfo current = null;
            for (MachineInfo info : node.machineOptions()) {
                if (info.name().equals(node.machine())) {
                    current = info;
                    break;
                }
            }
            String tier = node.tierName();
            String machine = current != null ? current.name() : node.machine();
            return machine + "  [" + tier + "]";
        }

        private static String metaLine(ProcessNode node, long craftCount) {
            return Component.translatable(
                "cirrus.ui.execution.tooltip.preview.batch",
                "\u00d7" + craftCount
            ).getString();
        }

        private static String trim(Font font, String text, int maxWidth) {
            if (font.width(text) <= maxWidth) return text;
            String ell = "...";
            int ellW = font.width(ell);
            String out = text;
            while (out.length() > 1 && font.width(out) + ellW > maxWidth) {
                out = out.substring(0, out.length() - 1);
            }
            return out + ell;
        }

        private static String shortAmount(long amount) {
            if (amount < 1_000) return String.valueOf(amount);
            if (amount < 1_000_000) return String.format("%.1fk", amount / 1000.0);
            return String.format("%.1fM", amount / 1_000_000.0);
        }
    }
}


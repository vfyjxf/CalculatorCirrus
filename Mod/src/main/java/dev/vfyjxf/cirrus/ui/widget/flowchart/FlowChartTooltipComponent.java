package dev.vfyjxf.cirrus.ui.widget.flowchart;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom graphical tooltip component for the process flow chart.
 * <p>
 * Supports text lines, material slot icon rows (items rendered as 16×16 icons),
 * dividers, and gaps. Used by the flow chart widget to display rich node/edge info.
 */
public final class FlowChartTooltipComponent implements TooltipComponent {

    // ─── Entry types ───

    public sealed interface Entry {
        record TextLine(String text, int color, boolean shadow) implements Entry {}
        record SlotRow(@Nullable String label, int labelColor, List<MaterialSlot> slots) implements Entry {}
        record Divider(int color) implements Entry {}
        record Gap(int height) implements Entry {}

        /** Section header with a colored accent bar on the left edge. */
        record SectionHeader(String title, int titleColor, int accentColor) implements Entry {}

        /** Left-aligned key, right-aligned value pair. */
        record KeyValue(String key, int keyColor, String value, int valueColor) implements Entry {}

        /** Horizontal progress bar showing a fill ratio. */
        record ProgressBar(float ratio, int fillColor, int barWidth) implements Entry {}

        /** 16x16 icon (item or colored fallback) followed by text. */
        record IconText(@Nullable ItemStack icon, int fallbackColor,
                        String text, int textColor,
                        @Nullable String rightText, int rightColor) implements Entry {}

        /** Compact recipe card entry with inputs, outputs, catalysts, and an optional machine. */
        record RecipeCard(
                List<MaterialSlot> inputs,
                List<MaterialSlot> outputs,
                List<MaterialSlot> catalysts,
                @Nullable MaterialSlot machine
        ) implements Entry {
            static RecipeCard empty() {
                return new RecipeCard(List.of(), List.of(), List.of(), null);
            }
        }
    }

    private final List<Entry> entries;

    private FlowChartTooltipComponent(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    // ─── Builder ───

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        public Builder text(String text, int color) {
            entries.add(new Entry.TextLine(text, color, true));
            return this;
        }

        public Builder text(String text, int color, boolean shadow) {
            entries.add(new Entry.TextLine(text, color, shadow));
            return this;
        }

        public Builder slots(@Nullable String label, int labelColor, List<MaterialSlot> slots) {
            entries.add(new Entry.SlotRow(label, labelColor, slots));
            return this;
        }

        public Builder divider() {
            entries.add(new Entry.Divider(0xFF555566));
            return this;
        }

        public Builder divider(int color) {
            entries.add(new Entry.Divider(color));
            return this;
        }

        public Builder gap(int pixels) {
            entries.add(new Entry.Gap(pixels));
            return this;
        }

        public Builder section(String title, int accentColor) {
            entries.add(new Entry.SectionHeader(title, 0xFFEEEEEE, accentColor));
            return this;
        }

        public Builder section(String title, int titleColor, int accentColor) {
            entries.add(new Entry.SectionHeader(title, titleColor, accentColor));
            return this;
        }

        public Builder kv(String key, String value) {
            entries.add(new Entry.KeyValue(key, 0xFF999999, value, 0xFFEEEEEE));
            return this;
        }

        public Builder kv(String key, int keyColor, String value, int valueColor) {
            entries.add(new Entry.KeyValue(key, keyColor, value, valueColor));
            return this;
        }

        public Builder bar(float ratio, int fillColor) {
            entries.add(new Entry.ProgressBar(ratio, fillColor, 120));
            return this;
        }

        public Builder bar(float ratio, int fillColor, int barWidth) {
            entries.add(new Entry.ProgressBar(ratio, fillColor, barWidth));
            return this;
        }

        public Builder iconText(@Nullable ItemStack icon, int fallback,
                                String text, int textColor) {
            entries.add(new Entry.IconText(icon, fallback, text, textColor, null, 0));
            return this;
        }

        public Builder iconText(@Nullable ItemStack icon, int fallback,
                                String text, int textColor,
                                String right, int rightColor) {
            entries.add(new Entry.IconText(icon, fallback, text, textColor, right, rightColor));
            return this;
        }

        public Builder recipeCard(Entry.RecipeCard card) {
            entries.add(card);
            return this;
        }

        public FlowChartTooltipComponent build() {
            return new FlowChartTooltipComponent(entries);
        }
    }

    // ─── Factory for event registration ───

    public static ClientTooltipComponent toClient(FlowChartTooltipComponent data) {
        return new Client(data);
    }

    public static RecipeCardLayout recipeCardLayout(int maxWidth, int inputCount, int outputCount) {
        int slotStep = 18;
        int minCols = Math.max(1, maxWidth / slotStep);
        int inputRows = (int) Math.ceil(inputCount / (double) minCols);
        int outputRows = (int) Math.ceil(outputCount / (double) minCols);
        return new RecipeCardLayout(minCols, inputRows, outputRows);
    }

    public record RecipeCardLayout(int columns, int inputRows, int outputRows) {}

    // ─── Client rendering implementation ───

    private static final class Client implements ClientTooltipComponent {

        private static final int ICON_SIZE = 16;
        private static final int ICON_STEP = 18;
        private static final int PAD_X = 4;
        private static final int TEXT_H = 11;
        private static final int LABEL_H = 10;
        private static final int DIVIDER_H = 6;
        private static final int SECTION_H = 14;
        private static final int KV_H = 11;
        private static final int BAR_H = 10;
        private static final int ICON_TEXT_H = 18;
        private static final int SLOT_BG = 0xFF1A1A2E;
        private static final int SLOT_BD = 0xFF3A3A5A;
        private static final int BAR_BG = 0xFF222244;
        private static final int BAR_BD = 0xFF333366;
        private static final int KV_GAP = 12;
        private static final int ACCENT_W = 3;
        private static final int RECIPE_CARD_MAX_COLS = 7;
        private static final int RECIPE_ARROW_GAP = ICON_STEP;
        private static final int RECIPE_SUBSECTION_GAP = 3;
        private static final int RECIPE_CATALYST_BG = 0x241B102C;
        private static final int RECIPE_MACHINE_BG = 0x20122A3A;
        private static final int RECIPE_CARD_FRAME_PAD = 3;

        private final FlowChartTooltipComponent data;

        Client(FlowChartTooltipComponent data) {
            this.data = data;
        }

        @Override
        public int getWidth(Font font) {
            int max = 0;
            for (Entry e : data.entries) {
                int w = switch (e) {
                    case Entry.TextLine tl -> font.width(tl.text());
                    case Entry.SlotRow sr -> {
                        int labelW = sr.label() != null ? font.width(sr.label()) : 0;
                        int iconsW = sr.slots().size() * ICON_STEP;
                        yield Math.max(labelW, iconsW);
                    }
                    case Entry.Divider ignored -> 40;
                    case Entry.Gap ignored -> 0;
                    case Entry.SectionHeader sh -> ACCENT_W + 5 + font.width(sh.title());
                    case Entry.KeyValue kv -> font.width(kv.key()) + KV_GAP + font.width(kv.value());
                    case Entry.ProgressBar pb -> pb.barWidth();
                    case Entry.IconText it -> {
                        int iw = ICON_SIZE + 5 + font.width(it.text());
                        if (it.rightText() != null) iw += 10 + font.width(it.rightText());
                        yield iw;
                    }
                    case Entry.RecipeCard rc -> {
                        int maxSlots = Math.max(1, Math.max(rc.inputs().size(), rc.outputs().size()));
                        int maxWidth = Math.min(ICON_STEP * maxSlots, ICON_STEP * RECIPE_CARD_MAX_COLS);
                        RecipeCardLayout layout = recipeCardLayout(maxWidth, rc.inputs().size(), rc.outputs().size());
                        int columns = Math.max(1, layout.columns());
                        int gridWidth = columns * ICON_STEP;
                        int mainWidth = gridWidth * 2 + RECIPE_ARROW_GAP;
                        int catalystsWidth = 0;
                        if (!rc.catalysts().isEmpty()) {
                            int catalystColumns = Math.max(1, Math.min(columns * 2 + 1, rc.catalysts().size()));
                            catalystsWidth = catalystColumns * ICON_STEP;
                        }
                        yield Math.max(mainWidth, catalystsWidth);
                    }
                };
                max = Math.max(max, w);
            }
            return max + PAD_X * 2;
        }

        @Override
        public int getHeight() {
            int h = 0;
            for (Entry e : data.entries) {
                h += switch (e) {
                    case Entry.TextLine ignored -> TEXT_H;
                    case Entry.SlotRow sr -> (sr.label() != null ? LABEL_H : 0) + ICON_STEP;
                    case Entry.Divider ignored -> DIVIDER_H;
                    case Entry.Gap g -> g.height();
                    case Entry.SectionHeader ignored -> SECTION_H;
                    case Entry.KeyValue ignored -> KV_H;
                    case Entry.ProgressBar ignored -> BAR_H;
                    case Entry.IconText ignored -> ICON_TEXT_H;
                    case Entry.RecipeCard rc -> {
                        int maxSlots = Math.max(1, Math.max(rc.inputs().size(), rc.outputs().size()));
                        int maxWidth = Math.min(ICON_STEP * maxSlots, ICON_STEP * RECIPE_CARD_MAX_COLS);
                        RecipeCardLayout layout = recipeCardLayout(maxWidth, rc.inputs().size(), rc.outputs().size());
                        int columns = Math.max(1, layout.columns());
                        int rows = Math.max(1, Math.max(layout.inputRows(), layout.outputRows()));
                        int height = rows * ICON_STEP;
                        if (!rc.catalysts().isEmpty()) {
                            int catalystColumns = Math.max(1, Math.min(columns * 2 + 1, rc.catalysts().size()));
                            int catalystRows = (int) Math.ceil(rc.catalysts().size() / (double) catalystColumns);
                            height += RECIPE_SUBSECTION_GAP + catalystRows * ICON_STEP;
                        }
                        yield height + RECIPE_CARD_FRAME_PAD * 2;
                    }
                };
            }
            return h;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            int curY = y;
            int totalW = getWidth(font);

            for (Entry e : data.entries) {
                switch (e) {
                    case Entry.TextLine tl -> {
                        graphics.drawString(font, tl.text(), x + PAD_X, curY, tl.color(), tl.shadow());
                        curY += TEXT_H;
                    }
                    case Entry.SlotRow sr -> {
                        // Optional label
                        if (sr.label() != null) {
                            graphics.drawString(font, sr.label(), x + PAD_X, curY, sr.labelColor(), true);
                            curY += LABEL_H;
                        }
                        // Render each slot as an icon with a mini-slot background
                        int iconX = x + PAD_X;
                        for (MaterialSlot slot : sr.slots()) {
                            renderSlot(font, graphics, slot, iconX, curY);
                            iconX += ICON_STEP;
                        }
                        curY += ICON_STEP;
                    }
                    case Entry.Divider d -> {
                        int dx = x + PAD_X;
                        int dw = totalW - PAD_X * 2;
                        graphics.fill(dx, curY + 2, dx + dw, curY + 3, d.color());
                        int cs = dx + dw / 4, ce = dx + dw * 3 / 4;
                        graphics.fill(cs, curY + 2, ce, curY + 3, brighten(d.color(), 0x22));
                        curY += DIVIDER_H;
                    }
                    case Entry.Gap g -> curY += g.height();
                    case Entry.SectionHeader sh -> {
                        graphics.fill(x + PAD_X, curY + 2,
                                x + PAD_X + ACCENT_W, curY + SECTION_H - 2, sh.accentColor());
                        graphics.drawString(font, sh.title(),
                                x + PAD_X + ACCENT_W + 4, curY + 2, sh.titleColor(), true);
                        curY += SECTION_H;
                    }
                    case Entry.KeyValue kv -> {
                        graphics.drawString(font, kv.key(), x + PAD_X, curY, kv.keyColor(), true);
                        int vw = font.width(kv.value());
                        graphics.drawString(font, kv.value(),
                                x + totalW - PAD_X - vw, curY, kv.valueColor(), true);
                        curY += KV_H;
                    }
                    case Entry.ProgressBar pb -> {
                        int bx2 = x + PAD_X;
                        int bw = totalW - PAD_X * 2;
                        int by = curY + 2;
                        int bh = BAR_H - 4;
                        graphics.fill(bx2, by, bx2 + bw, by + bh, BAR_BG);
                        int fw = Math.max(0, (int) (bw * Math.min(1.0f, pb.ratio())));
                        if (fw > 0) graphics.fill(bx2, by, bx2 + fw, by + bh, pb.fillColor());
                        drawRect(graphics, bx2, by, bw, bh, BAR_BD);
                        curY += BAR_H;
                    }
                    case Entry.IconText it -> {
                        int ix = x + PAD_X;
                        int iy = curY + 1;
                        if (it.icon() != null && !it.icon().isEmpty()) {
                            graphics.fill(ix, iy, ix + ICON_SIZE, iy + ICON_SIZE, SLOT_BG);
                            drawRect(graphics, ix - 1, iy - 1,
                                    ICON_SIZE + 2, ICON_SIZE + 2, SLOT_BD);
                            graphics.renderItem(it.icon(), ix, iy);
                        } else {
                            graphics.fill(ix + 2, iy + 2, ix + ICON_SIZE - 2,
                                    iy + ICON_SIZE - 2, it.fallbackColor());
                            drawRect(graphics, ix, iy, ICON_SIZE, ICON_SIZE,
                                    dimColor(it.fallbackColor()));
                        }
                        int tx = ix + ICON_SIZE + 5;
                        int ty = iy + (ICON_SIZE - 8) / 2;
                        graphics.drawString(font, it.text(), tx, ty, it.textColor(), true);
                        if (it.rightText() != null) {
                            int rw = font.width(it.rightText());
                            graphics.drawString(font, it.rightText(),
                                    x + totalW - PAD_X - rw, ty, it.rightColor(), true);
                        }
                        curY += ICON_TEXT_H;
                    }
                    case Entry.RecipeCard rc -> {
                        int maxSlots = Math.max(1, Math.max(rc.inputs().size(), rc.outputs().size()));
                        int maxWidth = Math.min(ICON_STEP * maxSlots, ICON_STEP * RECIPE_CARD_MAX_COLS);
                        RecipeCardLayout layout = recipeCardLayout(maxWidth, rc.inputs().size(), rc.outputs().size());
                        int columns = Math.max(1, layout.columns());
                        int inputRows = Math.max(1, layout.inputRows());
                        int outputRows = Math.max(1, layout.outputRows());
                        int rows = Math.max(inputRows, outputRows);
                        int gridWidth = columns * ICON_STEP;
                        int rowHeight = rows * ICON_STEP;
                        int recipeW = Math.max(ICON_STEP * 2 + RECIPE_ARROW_GAP, gridWidth * 2 + RECIPE_ARROW_GAP);

                        int frameW = recipeW + RECIPE_CARD_FRAME_PAD * 2;
                        int frameX = x + PAD_X;
                        int contentX = frameX + RECIPE_CARD_FRAME_PAD;
                        int contentY = curY + RECIPE_CARD_FRAME_PAD;
                        int recipeH = rowHeight;
                        if (!rc.catalysts().isEmpty()) {
                            int catalystColumns = Math.max(1, Math.min(columns * 2 + 1, rc.catalysts().size()));
                            int catalystRows = (int) Math.ceil(rc.catalysts().size() / (double) catalystColumns);
                            recipeH += RECIPE_SUBSECTION_GAP + catalystRows * ICON_STEP;
                        }
                        int frameH = recipeH + RECIPE_CARD_FRAME_PAD * 2;
                        CirrusTextures.Background.Outlined.flat.render(graphics, frameX, curY, frameW, frameH);
                        CirrusTextures.Background.dark.render(graphics, frameX + 1, curY + 1, Math.max(1, frameW - 2), Math.max(1, frameH - 2));
                        CirrusTextures.Background.Sign.bar.render(graphics, frameX + 2, curY + 2, Math.max(8, frameW - 4), 2);

                        int leftX = contentX + RECIPE_CARD_FRAME_PAD;
                        int rightX = leftX + gridWidth + RECIPE_ARROW_GAP;
                        int centerX = leftX + gridWidth;
                        int centerW = RECIPE_ARROW_GAP;

                        renderSlotGrid(font, graphics, rc.inputs(), columns, leftX, contentY);
                        renderSlotGrid(font, graphics, rc.outputs(), columns, rightX, contentY);

                        if (rc.machine() != null) {
                            int machineX = centerX + (centerW - ICON_SIZE) / 2;
                            int machineY = contentY;
                            graphics.fill(machineX - 1, machineY - 1, machineX + ICON_SIZE + 1, machineY + ICON_SIZE + 1, RECIPE_MACHINE_BG);
                            renderSlot(font, graphics, rc.machine(), machineX, machineY);
                        }

                        String arrow = "→";
                        int arrowW = font.width(arrow);
                        int arrowX = centerX + (centerW - arrowW) / 2;
                        int arrowCenterY = contentY + (rowHeight - font.lineHeight) / 2;
                        int arrowY = rc.machine() == null ? arrowCenterY : Math.max(contentY + ICON_STEP + 1, arrowCenterY);
                        graphics.drawString(font, arrow, arrowX, arrowY, 0xFFFFFFFF, true);

                        int sectionY = contentY + rowHeight;
                        if (!rc.catalysts().isEmpty()) {
                            sectionY += RECIPE_SUBSECTION_GAP;
                            int catalystColumns = Math.max(1, Math.min(columns * 2 + 1, rc.catalysts().size()));
                            int catalystRows = (int) Math.ceil(rc.catalysts().size() / (double) catalystColumns);
                            int catalystWidth = catalystColumns * ICON_STEP;
                            graphics.fill(leftX - 1, sectionY - 1, leftX + catalystWidth + 1, sectionY + catalystRows * ICON_STEP + 1, RECIPE_CATALYST_BG);
                            renderSlotGrid(font, graphics, rc.catalysts(), catalystColumns, leftX, sectionY);
                            sectionY += catalystRows * ICON_STEP;
                        }
                        curY += frameH;
                    }
                }
            }
        }

        private static void renderSlotGrid(Font font, GuiGraphics graphics,
                                           List<MaterialSlot> slots, int columns,
                                           int startX, int startY) {
            int safeCols = Math.max(1, columns);
            for (int i = 0; i < slots.size(); i++) {
                int col = i % safeCols;
                int row = i / safeCols;
                int iconX = startX + col * ICON_STEP;
                int iconY = startY + row * ICON_STEP;
                renderSlot(font, graphics, slots.get(i), iconX, iconY);
            }
        }

        private static void renderSlot(Font font, GuiGraphics graphics, MaterialSlot slot, int iconX, int iconY) {
            CirrusTextures.Background.Slot.dark.render(graphics, iconX, iconY, ICON_SIZE, ICON_SIZE);

            // Render item icon
            if (slot.itemStack() != null && !slot.itemStack().isEmpty()) {
                graphics.renderItem(slot.itemStack(), iconX, iconY);
                graphics.renderItemDecorations(font, slot.itemStack(), iconX, iconY);
            } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
                // Render fluid texture
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(iconX, iconY, 0);
                FluidStackRenderer.instance.renderBase(
                        graphics, slot.fluidStack(), ICON_SIZE, ICON_SIZE, 0);
                pose.popPose();
                // Amount overlay
                String amt = shortAmount(slot.amount());
                graphics.drawString(font, amt,
                        iconX + ICON_SIZE - font.width(amt),
                        iconY + ICON_SIZE - 8, 0xFFFFFFFF, true);
            } else {
                // Fallback: colored rectangle with type initial
                int typeColor = slot.type().color;
                graphics.fill(iconX + 1, iconY + 1,
                        iconX + ICON_SIZE - 1, iconY + ICON_SIZE - 1, typeColor);
                String initial = slot.type().name().substring(0, 1);
                int tw = font.width(initial);
                graphics.drawString(font, initial,
                        iconX + (ICON_SIZE - tw) / 2,
                        iconY + 4, 0xFFFFFFFF, true);
            }
        }

        private static void drawRect(GuiGraphics g, int x, int y, int w, int h, int color) {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y + 1, x + 1, y + h - 1, color);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
        }

        private static int brighten(int color, int amount) {
            int a = (color >> 24) & 0xFF;
            int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
            int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
            int b = Math.min(255, (color & 0xFF) + amount);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int dimColor(int color) {
            int a = (color >> 24) & 0xFF;
            int r = ((color >> 16) & 0xFF) / 2;
            int g = ((color >> 8) & 0xFF) / 2;
            int b = (color & 0xFF) / 2;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static String shortAmount(int amount) {
            if (amount < 1000) return String.valueOf(amount);
            if (amount < 1_000_000) return String.format("%.1fk", amount / 1000.0);
            return String.format("%.1fM", amount / 1_000_000.0);
        }
    }
}

package dev.vfyjxf.cirrus.ui.tooltip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom graphical tooltip component for rich process / execution previews.
 * <p>
 * Supports text lines, material slot icon rows (items rendered as 16×16 icons),
 * dividers, and gaps. Used by the flow chart widget to display rich node/edge info.
 */
public final class RichTooltipComponent implements TooltipComponent {

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

        /** 16x16 generic recipe-content icon followed by text. */
        record ContentIconText(@Nullable RecipeContent content, int fallbackColor,
                               String text, int textColor,
                               @Nullable String rightText, int rightColor) implements Entry {
            public ContentIconText {
                if (content != null && !content.isEmpty()) {
                    content = content.copy();
                }
            }
        }

        record ContentCandidateGrid(RecipeContent content, int count, int maxVisible) implements Entry {
            public ContentCandidateGrid {
                content = content.copy();
                count = Math.max(0, count);
                maxVisible = Math.max(1, maxVisible);
            }
        }

        /** 16x16 UI texture icon followed by text. */
        record TextureIconText(@Nullable ImageTexture icon, int fallbackColor,
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

        /**
         * JEI-style recipe card with explicit grid columns.
         * <p>
         * This keeps crafting preview geometry stable (typically 3x3 -> 1)
         * instead of auto-expanding columns based on tooltip width.
         */
        record JeiRecipeCard(
                List<MaterialSlot> inputs,
                List<MaterialSlot> outputs,
                List<MaterialSlot> catalysts,
                @Nullable MaterialSlot machine,
                int inputColumns,
                int outputColumns
        ) implements Entry {
            JeiRecipeCard(
                    List<MaterialSlot> inputs,
                    List<MaterialSlot> outputs,
                    List<MaterialSlot> catalysts,
                    @Nullable MaterialSlot machine
            ) {
                this(inputs, outputs, catalysts, machine, 3, 1);
            }
        }
    }

    private final List<Entry> entries;

    private RichTooltipComponent(List<Entry> entries) {
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

        public Builder contentIconText(@Nullable RecipeContent content, int fallback,
                                       String text, int textColor) {
            entries.add(new Entry.ContentIconText(content, fallback, text, textColor, null, 0));
            return this;
        }

        public Builder contentIconText(@Nullable RecipeContent content, int fallback,
                                       String text, int textColor,
                                       @Nullable String right, int rightColor) {
            entries.add(new Entry.ContentIconText(content, fallback, text, textColor, right, rightColor));
            return this;
        }

        public Builder contentCandidates(RecipeContent content, int count, int maxVisible) {
            entries.add(new Entry.ContentCandidateGrid(content, count, maxVisible));
            return this;
        }

        public Builder textureIconText(@Nullable ImageTexture icon, int fallback,
                                       String text, int textColor) {
            entries.add(new Entry.TextureIconText(icon, fallback, text, textColor, null, 0));
            return this;
        }

        public Builder textureIconText(@Nullable ImageTexture icon, int fallback,
                                       String text, int textColor,
                                       @Nullable String right, int rightColor) {
            entries.add(new Entry.TextureIconText(icon, fallback, text, textColor, right, rightColor));
            return this;
        }

        public Builder recipeCard(Entry.RecipeCard card) {
            entries.add(card);
            return this;
        }

        public Builder jeiRecipeCard(Entry.JeiRecipeCard card) {
            entries.add(card);
            return this;
        }

        public RichTooltipComponent build() {
            return new RichTooltipComponent(entries);
        }
    }

    /**
     * Legacy test helper that derives recipe-card row/column layout from width.
     * <p>
     * Kept for contract tests that validate grid wrapping behavior independent of rendering.
     */
    public static RecipeCardLayout recipeCardLayout(int availableWidth, int inputCount, int outputCount) {
        int iconStep = 18;
        int maxCols = 7;
        int columns = Math.max(1, Math.min(maxCols, availableWidth / iconStep));
        int safeInputs = Math.max(0, inputCount);
        int safeOutputs = Math.max(0, outputCount);
        int inputRows = Math.max(1, (int) Math.ceil(safeInputs / (double) columns));
        int outputRows = Math.max(1, (int) Math.ceil(safeOutputs / (double) columns));
        return new RecipeCardLayout(columns, inputRows, outputRows);
    }

    public record RecipeCardLayout(int columns, int inputRows, int outputRows) {
    }

    // ─── Factory for event registration ───

    public static ClientTooltipComponent toClient(RichTooltipComponent data) {
        return new Client(data);
    }

    // ─── Client rendering implementation ───

    private static final class Client implements ClientTooltipComponent {

        private static final int ICON_SIZE = 16;
        private static final int ICON_STEP = 18;
        private static final int PAD_X = 4;
        private static final int TEXT_H = 11;
        private static final int TEXT_WRAP_WIDTH = 180;
        private static final int LABEL_H = 10;
        private static final int DIVIDER_H = 6;
        private static final int SECTION_H = 14;
        private static final int KV_H = 11;
        private static final int BAR_H = 10;
        private static final int ICON_TEXT_H = 18;
        private static final int BAR_BG = 0xFF222244;
        private static final int BAR_BD = 0xFF333366;
        private static final int KV_GAP = 12;
        private static final int RECIPE_CARD_MAX_COLS = 7;
        private static final int RECIPE_ARROW_GAP = ICON_STEP;
        private static final int RECIPE_SUBSECTION_GAP = 3;
        private static final int RECIPE_CATALYST_BG = 0x241B102C;
        private static final int RECIPE_MACHINE_BG = 0x20122A3A;
        private static final int RECIPE_CARD_FRAME_PAD = 3;

        private final RichTooltipComponent data;

        Client(RichTooltipComponent data) {
            this.data = data;
        }

        @Override
        public int getWidth(Font font) {
            int max = 0;
            for (Entry e : data.entries) {
                int w = switch (e) {
                    case Entry.TextLine tl -> wrappedTextWidth(font, tl.text(), TEXT_WRAP_WIDTH);
                    case Entry.SlotRow sr -> {
                        int labelW = sr.label() != null ? plainTextWidth(font, sr.label()) : 0;
                        int iconsW = sr.slots().size() * ICON_STEP;
                        yield Math.max(labelW, iconsW);
                    }
                    case Entry.Divider ignored -> 40;
                    case Entry.Gap ignored -> 0;
                    case Entry.SectionHeader sh -> plainTextWidth(font, sh.title());
                    case Entry.KeyValue kv -> plainTextWidth(font, kv.key()) + KV_GAP + plainTextWidth(font, kv.value());
                    case Entry.ProgressBar pb -> pb.barWidth();
                    case Entry.IconText it -> {
                        int iw = ICON_SIZE + 5 + plainTextWidth(font, it.text());
                        if (it.rightText() != null) iw += 10 + plainTextWidth(font, it.rightText());
                        yield iw;
                    }
                    case Entry.ContentIconText it -> {
                        int iw = ICON_SIZE + 5 + plainTextWidth(font, it.text());
                        if (it.rightText() != null) iw += 10 + plainTextWidth(font, it.rightText());
                        yield iw;
                    }
                    case Entry.ContentCandidateGrid it -> {
                        int visible = Math.min(it.count(), it.maxVisible());
                        int columns = Math.min(6, Math.max(1, visible));
                        int width = columns * ICON_SIZE + Math.max(0, columns - 1) * 3;
                        if (it.count() > visible) {
                            width += 24;
                        }
                        yield width;
                    }
                    case Entry.TextureIconText it -> {
                        int iw = ICON_SIZE + 5 + plainTextWidth(font, it.text());
                        if (it.rightText() != null) iw += 10 + plainTextWidth(font, it.rightText());
                        yield iw;
                    }
                    case Entry.RecipeCard rc -> recipeCardWidth(rc.inputs(), rc.outputs(), rc.catalysts(), null, null);
                    case Entry.JeiRecipeCard rc -> recipeCardWidth(
                        rc.inputs(),
                        rc.outputs(),
                        rc.catalysts(),
                        Integer.valueOf(rc.inputColumns()),
                        Integer.valueOf(rc.outputColumns())
                    );
                };
                max = Math.max(max, w);
            }
            return max + PAD_X * 2;
        }

        @Override
        public int getHeight() {
            Font font = currentFont();
            int h = 0;
            for (Entry e : data.entries) {
                h += switch (e) {
                    case Entry.TextLine tl -> wrappedTextHeight(font, tl.text(), TEXT_WRAP_WIDTH);
                    case Entry.SlotRow sr -> (sr.label() != null ? LABEL_H : 0) + ICON_STEP;
                    case Entry.Divider ignored -> DIVIDER_H;
                    case Entry.Gap g -> g.height();
                    case Entry.SectionHeader ignored -> SECTION_H;
                    case Entry.KeyValue ignored -> KV_H;
                    case Entry.ProgressBar ignored -> BAR_H;
                    case Entry.IconText ignored -> ICON_TEXT_H;
                    case Entry.ContentIconText ignored -> ICON_TEXT_H;
                    case Entry.ContentCandidateGrid it -> {
                        int visible = Math.min(it.count(), it.maxVisible());
                        if (visible <= 0) {
                            yield 0;
                        }
                        int columns = Math.min(6, visible);
                        int rows = (int) Math.ceil(visible / (double) columns);
                        yield rows * ICON_SIZE + Math.max(0, rows - 1) * 3;
                    }
                    case Entry.TextureIconText ignored -> ICON_TEXT_H;
                    case Entry.RecipeCard rc -> recipeCardHeight(rc.inputs(), rc.outputs(), rc.catalysts(), null, null);
                    case Entry.JeiRecipeCard rc -> recipeCardHeight(
                        rc.inputs(),
                        rc.outputs(),
                        rc.catalysts(),
                        Integer.valueOf(rc.inputColumns()),
                        Integer.valueOf(rc.outputColumns())
                    );
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
                        for (String line : wrappedText(font, tl.text(), TEXT_WRAP_WIDTH)) {
                            graphics.drawString(font, line, x + PAD_X, curY, tl.color(), tl.shadow());
                            curY += TEXT_H;
                        }
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
                        graphics.drawString(font, sh.title(), x + PAD_X, curY + 2, sh.titleColor(), true);
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
                            graphics.renderItem(it.icon(), ix, iy);
                        } else {
                            graphics.fill(ix + 2, iy + 2, ix + ICON_SIZE - 2,
                                    iy + ICON_SIZE - 2, it.fallbackColor());
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
                    case Entry.ContentIconText it -> {
                        int ix = x + PAD_X;
                        int iy = curY + 1;
                        if (it.content() != null && it.content().notEmpty() && ExecutionOverlayContentHelper.hasRenderableVisual(it.content())) {
                            renderRecipeContentIcon(graphics, it.content(), ix, iy);
                        } else {
                            graphics.fill(ix + 2, iy + 2, ix + ICON_SIZE - 2, iy + ICON_SIZE - 2, it.fallbackColor());
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
                    case Entry.ContentCandidateGrid it -> {
                        int visible = Math.min(it.count(), it.maxVisible());
                        if (visible <= 0) {
                            break;
                        }
                        int columns = Math.min(6, visible);
                        for (int i = 0; i < visible; i++) {
                            int col = i % columns;
                            int row = i / columns;
                            int sx = x + PAD_X + col * (ICON_SIZE + 3);
                            int sy = curY + row * (ICON_SIZE + 3);
                            renderRecipeContentIcon(graphics, it.content(), i, sx, sy);
                        }
                        if (it.count() > visible) {
                            int rows = (int) Math.ceil(visible / (double) columns);
                            String more = "+" + (it.count() - visible);
                            graphics.drawString(
                                font,
                                more,
                                x + PAD_X + columns * (ICON_SIZE + 3) + 1,
                                curY + rows * ICON_SIZE - 8,
                                0xFF999999,
                                false
                            );
                        }
                        int rows = (int) Math.ceil(visible / (double) columns);
                        curY += rows * ICON_SIZE + Math.max(0, rows - 1) * 3;
                    }
                    case Entry.TextureIconText it -> {
                        int ix = x + PAD_X;
                        int iy = curY + 1;
                        if (it.icon() != null) {
                            renderTextureIcon(graphics, it.icon(), ix, iy);
                        } else {
                            graphics.fill(ix + 2, iy + 2, ix + ICON_SIZE - 2, iy + ICON_SIZE - 2, it.fallbackColor());
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
                        curY += renderRecipeCard(
                            font,
                            graphics,
                            x,
                            curY,
                            rc.inputs(),
                            rc.outputs(),
                            rc.catalysts(),
                            rc.machine(),
                            null,
                            null
                        );
                    }
                    case Entry.JeiRecipeCard rc -> {
                        curY += renderRecipeCard(
                            font,
                            graphics,
                            x,
                            curY,
                            rc.inputs(),
                            rc.outputs(),
                            rc.catalysts(),
                            rc.machine(),
                            Integer.valueOf(rc.inputColumns()),
                            Integer.valueOf(rc.outputColumns())
                        );
                    }
                }
            }
        }

        private static int recipeCardWidth(
                List<MaterialSlot> inputs,
                List<MaterialSlot> outputs,
                List<MaterialSlot> catalysts,
                @Nullable Integer forcedInputColumns,
                @Nullable Integer forcedOutputColumns
        ) {
            RecipeCardGeometry geometry = recipeCardGeometry(inputs, outputs, catalysts, forcedInputColumns, forcedOutputColumns);
            return geometry.frameWidth();
        }

        private static int recipeCardHeight(
                List<MaterialSlot> inputs,
                List<MaterialSlot> outputs,
                List<MaterialSlot> catalysts,
                @Nullable Integer forcedInputColumns,
                @Nullable Integer forcedOutputColumns
        ) {
            RecipeCardGeometry geometry = recipeCardGeometry(inputs, outputs, catalysts, forcedInputColumns, forcedOutputColumns);
            return geometry.frameHeight();
        }

        private static int renderRecipeCard(
                Font font,
                GuiGraphics graphics,
                int baseX,
                int baseY,
                List<MaterialSlot> inputs,
                List<MaterialSlot> outputs,
                List<MaterialSlot> catalysts,
                @Nullable MaterialSlot machine,
                @Nullable Integer forcedInputColumns,
                @Nullable Integer forcedOutputColumns
        ) {
            RecipeCardGeometry geometry = recipeCardGeometry(inputs, outputs, catalysts, forcedInputColumns, forcedOutputColumns);
            int frameX = baseX + PAD_X;
            int frameY = baseY;
            CirrusTextures.Background.Outlined.flat.render(graphics, frameX, frameY, geometry.frameWidth(), geometry.frameHeight());
            CirrusTextures.Background.dark.render(
                graphics,
                frameX + 1,
                frameY + 1,
                Math.max(1, geometry.frameWidth() - 2),
                Math.max(1, geometry.frameHeight() - 2)
            );
            CirrusTextures.Background.Sign.bar.render(
                graphics,
                frameX + 2,
                frameY + 2,
                Math.max(8, geometry.frameWidth() - 4),
                2
            );

            int leftX = frameX + RECIPE_CARD_FRAME_PAD * 2;
            int centerX = leftX + geometry.inputGridWidth();
            int rightX = centerX + RECIPE_ARROW_GAP;
            int centerW = RECIPE_ARROW_GAP;
            int contentY = frameY + RECIPE_CARD_FRAME_PAD;

            renderSlotGrid(font, graphics, inputs, geometry.inputColumns(), leftX, contentY);
            renderSlotGrid(font, graphics, outputs, geometry.outputColumns(), rightX, contentY);

            String arrow = "→";
            int arrowW = font.width(arrow);
            int arrowX = centerX + (centerW - arrowW) / 2;
            int arrowCenterY = contentY + (geometry.mainHeight() - font.lineHeight) / 2;
            int arrowY = arrowCenterY;

            if (machine != null) {
                int machineX = centerX + (centerW - ICON_SIZE) / 2;
                int machineY = contentY;
                graphics.fill(machineX - 1, machineY - 1, machineX + ICON_SIZE + 1, machineY + ICON_SIZE + 1, RECIPE_MACHINE_BG);
                renderSlot(font, graphics, machine, machineX, machineY);
                arrowY = Math.max(contentY + ICON_STEP + 1, arrowCenterY);
            }

            graphics.drawString(font, arrow, arrowX, arrowY, 0xFFFFFFFF, true);

            if (!catalysts.isEmpty()) {
                int catalystY = contentY + geometry.mainHeight() + RECIPE_SUBSECTION_GAP;
                int catalystWidth = geometry.catalystColumns() * ICON_STEP;
                graphics.fill(
                    leftX - 1,
                    catalystY - 1,
                    leftX + catalystWidth + 1,
                    catalystY + geometry.catalystRows() * ICON_STEP + 1,
                    RECIPE_CATALYST_BG
                );
                renderSlotGrid(font, graphics, catalysts, geometry.catalystColumns(), leftX, catalystY);
            }

            return geometry.frameHeight();
        }

        private static RecipeCardGeometry recipeCardGeometry(
                List<MaterialSlot> inputs,
                List<MaterialSlot> outputs,
                List<MaterialSlot> catalysts,
                @Nullable Integer forcedInputColumns,
                @Nullable Integer forcedOutputColumns
        ) {
            int inputCount = Math.max(1, inputs.size());
            int outputCount = Math.max(1, outputs.size());

            int autoColumns = Math.max(1, Math.min(RECIPE_CARD_MAX_COLS, Math.max(inputCount, outputCount)));
            int inputColumns = clampRecipeColumns(forcedInputColumns, autoColumns, inputCount);
            int outputColumns = clampRecipeColumns(forcedOutputColumns, autoColumns, outputCount);

            int inputRows = Math.max(1, (int) Math.ceil(inputCount / (double) inputColumns));
            int outputRows = Math.max(1, (int) Math.ceil(outputCount / (double) outputColumns));
            int mainRows = Math.max(inputRows, outputRows);
            int inputGridWidth = inputColumns * ICON_STEP;
            int outputGridWidth = outputColumns * ICON_STEP;
            int mainWidth = inputGridWidth + RECIPE_ARROW_GAP + outputGridWidth;
            int mainHeight = mainRows * ICON_STEP;

            int catalystColumns = 0;
            int catalystRows = 0;
            int catalystsHeight = 0;
            if (!catalysts.isEmpty()) {
                int spanColumns = Math.max(1, Math.min(RECIPE_CARD_MAX_COLS, inputColumns + outputColumns));
                catalystColumns = Math.max(1, Math.min(spanColumns, catalysts.size()));
                catalystRows = (int) Math.ceil(catalysts.size() / (double) catalystColumns);
                catalystsHeight = RECIPE_SUBSECTION_GAP + catalystRows * ICON_STEP;
            }

            int frameWidth = mainWidth + RECIPE_CARD_FRAME_PAD * 2;
            int frameHeight = mainHeight + catalystsHeight + RECIPE_CARD_FRAME_PAD * 2;
            return new RecipeCardGeometry(
                inputColumns,
                outputColumns,
                inputRows,
                outputRows,
                mainRows,
                inputGridWidth,
                outputGridWidth,
                mainWidth,
                mainHeight,
                catalystColumns,
                catalystRows,
                frameWidth,
                frameHeight
            );
        }

        private static int clampRecipeColumns(@Nullable Integer forcedColumns, int autoColumns, int itemCount) {
            int maxColumns = Math.max(1, Math.min(RECIPE_CARD_MAX_COLS, itemCount));
            int normalizedAuto = Math.max(1, Math.min(maxColumns, autoColumns));
            if (forcedColumns == null) {
                return normalizedAuto;
            }
            int forced = forcedColumns.intValue();
            if (forced <= 0) {
                return normalizedAuto;
            }
            return Math.max(1, Math.min(maxColumns, forced));
        }

        private record RecipeCardGeometry(
                int inputColumns,
                int outputColumns,
                int inputRows,
                int outputRows,
                int mainRows,
                int inputGridWidth,
                int outputGridWidth,
                int mainWidth,
                int mainHeight,
                int catalystColumns,
                int catalystRows,
                int frameWidth,
                int frameHeight
        ) {
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

        private static List<String> wrappedText(@Nullable Font font, String text, int maxWidth) {
            if (font == null) {
                return List.of(text);
            }
            List<String> lines = ExecutionOverlayUi.wrapPlainLines(font, text, maxWidth);
            return lines.isEmpty() ? List.of("") : lines;
        }

        private static @Nullable Font currentFont() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null ? null : minecraft.font;
        }

        private static int wrappedTextWidth(@Nullable Font font, String text, int maxWidth) {
            if (font == null) {
                return text == null ? 0 : Math.min(text.length() * 6, maxWidth);
            }
            int width = 0;
            for (String line : wrappedText(font, text, maxWidth)) {
                width = Math.max(width, font.width(line));
            }
            return width;
        }

        private static int wrappedTextHeight(@Nullable Font font, String text, int maxWidth) {
            return wrappedText(font, text, maxWidth).size() * TEXT_H;
        }

        private static int plainTextWidth(@Nullable Font font, @Nullable String text) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            if (font == null) {
                return text.length() * 6;
            }
            return font.width(text);
        }

        private static void renderRecipeContentIcon(GuiGraphics graphics, RecipeContent content, int x, int y) {
            if (content == null || content.isEmpty()) {
                return;
            }
            int visualIndex = ExecutionOverlayContentHelper.currentVisualIndex(content);
            renderRecipeContentIcon(graphics, content, visualIndex, x, y);
        }

        @SuppressWarnings("unchecked")
        private static void renderRecipeContentIcon(GuiGraphics graphics, RecipeContent content, int visualIndex, int x, int y) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 200.0F);
            Lighting.setupFor3DItems();
            try {
                RecipeContentRenderer<RecipeContent> renderer = recipeContentRenderer(content);
                renderer.render(graphics, content, Math.max(0, visualIndex), ICON_SIZE, ICON_SIZE, 0);
            } finally {
                Lighting.setupForFlatItems();
                graphics.pose().popPose();
            }
        }

        @SuppressWarnings("unchecked")
        private static RecipeContentRenderer<RecipeContent> recipeContentRenderer(RecipeContent content) {
            return (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer.getRenderer(content);
        }

        private static void renderTextureIcon(GuiGraphics graphics, ImageTexture icon, int x, int y) {
            int width = Math.min(ICON_SIZE - 2, icon.width());
            int height = Math.min(ICON_SIZE - 2, icon.height());
            int iconX = x + Math.max(1, (ICON_SIZE - width) / 2);
            int iconY = y + Math.max(1, (ICON_SIZE - height) / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            icon.render(graphics, iconX, iconY, width, height);
            graphics.pose().popPose();
        }
    }

}

package dev.vfyjxf.cirrus.ui.workbench.paint;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptive contiguous OreUI recipe grid ([input matrix] -> [output matrix]) with in-slot counts and overflow handling for huge recipes.
 */
public final class OreUiRecipeGrid {

    public static final int defaultSlotSize = 18;
    public static final int compactSlotSize = 16;
    public static final int arrowGap = 20;

    private OreUiRecipeGrid() {}

    public record SlotEntry(@Nullable ResourceKey resource, @Nullable ContentStack<?, ?> stack, long amount) {

        public static SlotEntry of(@Nullable ResourceKey resource, long amount) {
            return new SlotEntry(resource, null, amount);
        }

        public static SlotEntry ofStack(@Nullable ContentStack<?, ?> stack, long amount) {
            return new SlotEntry(null, stack, amount);
        }

        public @Nullable ContentStack<?, ?> resolvedStack() {
            if (stack != null && !stack.isEmpty()) {
                return stack;
            }
            return resource == null ? null : CoPilotPaint.stackOf(resource, 1L);
        }
    }

    public record Layout(
        int inCols,
        int inRows,
        int inShown,
        int inOverflow,
        int outCols,
        int outRows,
        int outShown,
        int outOverflow,
        int slotSize,
        int step,
        int inGridW,
        int inGridH,
        int outGridW,
        int outGridH,
        int arrowX,
        int outX,
        int width,
        int height
    ) {}

    public static List<SlotEntry> inputsOf(CompiledRecipe recipe) {
        List<SlotEntry> list = new ArrayList<>(recipe.inputs().size());
        for (InputSlot slot : recipe.inputs()) {
            ResourceKey res = slot.candidates().isEmpty() ? null : slot.candidates().get(0);
            list.add(SlotEntry.of(res, Math.max(1L, slot.amountPerBatch())));
        }
        return List.copyOf(list);
    }

    public static List<SlotEntry> outputsOf(CompiledRecipe recipe) {
        List<SlotEntry> list = new ArrayList<>(recipe.outputs().size());
        for (OutputSlot slot : recipe.outputs()) {
            list.add(SlotEntry.of(slot.resource(), Math.max(1L, slot.amountPerBatch())));
        }
        return List.copyOf(list);
    }

    public static Layout measure(int inCount, int outCount, int slotSize, int maxWidth, int maxRows) {
        int safeSlot = Math.max(14, slotSize);
        int step = safeSlot - 1;
        int safeMaxRows = Math.max(1, maxRows);
        int safeOutCount = Math.max(0, outCount);
        int safeInCount = Math.max(0, inCount);

        int outCols = safeOutCount == 0
                ? 0
                : safeOutCount <= 2 ? safeOutCount : Math.min(3, (int) Math.ceil(Math.sqrt(safeOutCount)));
        int outRows = safeOutCount == 0 ? 0 : Math.min(safeMaxRows, (safeOutCount + outCols - 1) / outCols);
        int outCap = outCols * outRows;
        int outShown = safeOutCount <= outCap ? safeOutCount : Math.max(0, outCap - 1);
        int outOverflow = Math.max(0, safeOutCount - outShown);
        int outGridW = outCols == 0 ? 0 : outCols * step + 1;
        int outGridH = outRows == 0 ? 0 : outRows * step + 1;

        boolean hasArrow = safeInCount > 0 && safeOutCount > 0;
        int arrowSpan = hasArrow ? arrowGap : 0;
        int availInW = Math.max(safeSlot, maxWidth - outGridW - arrowSpan);
        int maxInCols = Math.max(1, (availInW - 1) / step);

        int inCols;
        int inRows;
        if (safeInCount == 0) {
            inCols = 0;
            inRows = 0;
        } else if (safeInCount <= maxInCols) {
            inCols = safeInCount;
            inRows = 1;
        } else if (safeInCount <= 9 && maxInCols >= 3 && safeMaxRows >= 3) {
            inCols = 3;
            inRows = Math.min(safeMaxRows, (safeInCount + 2) / 3);
        } else {
            inCols = Math.min(maxInCols, Math.max(2, (safeInCount + safeMaxRows - 1) / safeMaxRows));
            inRows = Math.min(safeMaxRows, (safeInCount + inCols - 1) / inCols);
        }
        int inCap = inCols * inRows;
        int inShown = safeInCount <= inCap ? safeInCount : Math.max(0, inCap - 1);
        int inOverflow = Math.max(0, safeInCount - inShown);
        int inGridW = inCols == 0 ? 0 : inCols * step + 1;
        int inGridH = inRows == 0 ? 0 : inRows * step + 1;

        int arrowX = inGridW;
        int outX = inGridW + arrowSpan;
        int totalW = outX + outGridW;
        int totalH = Math.max(safeSlot, Math.max(inGridH, outGridH));
        return new Layout(
            inCols,
            inRows,
            inShown,
            inOverflow,
            outCols,
            outRows,
            outShown,
            outOverflow,
            safeSlot,
            step,
            inGridW,
            inGridH,
            outGridW,
            outGridH,
            arrowX,
            outX,
            totalW,
            totalH
        );
    }

    public static void render(
        SceneCanvas canvas,
        List<SlotEntry> inputs,
        List<SlotEntry> outputs,
        Layout layout,
        int x,
        int y,
        CirrusPalette palette
    ) {
        if (canvas == null || layout == null) {
            return;
        }
        // Input slot matrix (vertically centered against total grid height).
        if (layout.inCols() > 0 && layout.inRows() > 0) {
            int inY = y + Math.max(0, (layout.height() - layout.inGridH()) / 2);
            renderMatrix(
                canvas,
                inputs,
                x,
                inY,
                layout.inCols(),
                layout.inRows(),
                layout.inShown(),
                layout.inOverflow(),
                layout.slotSize(),
                layout.step(),
                false,
                palette
            );
        }

        if (layout.inCols() > 0 && layout.outCols() > 0) {
            int midY = y + layout.height() / 2;
            int ax0 = x + layout.arrowX() + 3;
            int ax1 = x + layout.outX() - 3;
            drawRecipeArrow(canvas, ax0, ax1, midY, palette, false);
        }

        // Output slot matrix with crisp inset accent rim on the primary output slot.
        if (layout.outCols() > 0 && layout.outRows() > 0) {
            int outY = y + Math.max(0, (layout.height() - layout.outGridH()) / 2);
            renderMatrix(
                canvas,
                outputs,
                x + layout.outX(),
                outY,
                layout.outCols(),
                layout.outRows(),
                layout.outShown(),
                layout.outOverflow(),
                layout.slotSize(),
                layout.step(),
                true,
                palette
            );
        }
    }

    /**
     * Draws a sleek, understated OreUI crafting recipe arrow (distinct from thick DAG conduits).
     */
    public static void drawRecipeArrow(
        SceneCanvas canvas,
        int x0,
        int x1,
        int midY,
        CirrusPalette palette,
        boolean accented
    ) {
        int span = x1 - x0;
        if (span < 6) {
            return;
        }
        boolean dark = OreUiPaint.isDark(palette);
        int fill = accented
                ? palette.focusRing()
                : (dark ? OreUiPaint.lighten(palette.textMuted(), 0.24f) : palette.textSecondary());
        int shadow = dark
                ? OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.70f)
                : OreUiPaint.withAlpha(palette.hairlineBorder(), 0.45f);
        int headLen = Math.min(5, Math.max(3, span / 3));
        int tipX = x1;
        int stemEnd = Math.max(x0 + 2, tipX - headLen);
        int stemW = Math.max(1, stemEnd - x0);

        canvas.fill(x0, midY + 1, stemW, 1, shadow);
        canvas.fill(x0, midY - 1, stemW, 2, fill);

        for (int i = 0; i < headLen; i++) {
            int colX = tipX - 1 - i;
            int halfH = i + 1;
            if (colX < x0) {
                break;
            }
            canvas.fill(colX, midY + halfH, 1, 1, shadow);
            canvas.fill(colX, midY - halfH, 1, halfH * 2, fill);
        }
    }

    private static void renderMatrix(
        SceneCanvas canvas,
        List<SlotEntry> entries,
        int x,
        int y,
        int cols,
        int rows,
        int shown,
        int overflow,
        int slotSize,
        int step,
        boolean outputSide,
        CirrusPalette palette
    ) {
        int totalCells = cols * rows;
        for (int cell = 0; cell < totalCells; cell++) {
            int col = cell % cols;
            int row = cell / cols;
            int sx = x + col * step;
            int sy = y + row * step;
            if (cell < shown && cell < entries.size()) {
                SlotEntry entry = entries.get(cell);
                OreUiSlotAmount.drawSlot(canvas, entry.resolvedStack(), entry.amount(), sx, sy, slotSize, palette);
            } else if (overflow > 0 && cell == totalCells - 1) {
                OreUiPaint.slot(canvas, sx, sy, slotSize, palette);
                Component more = Component.literal("+" + overflow);
                int mw = ChromeText.width(canvas, more);
                ChromeText.draw(
                    canvas,
                    more,
                    sx + Math.max(1, (slotSize - mw) / 2),
                    ChromeText.centeredY(sy, slotSize, canvas),
                    palette.focusRing()
                );
            } else {
                OreUiPaint.slot(canvas, sx, sy, slotSize, palette);
            }
        }
        if (outputSide && shown > 0 && !entries.isEmpty() && slotSize >= 14) {
            canvas.strokeRect(
                x + 1,
                y + 1,
                slotSize - 2,
                slotSize - 2,
                OreUiPaint.withAlpha(palette.costS(), 0.48f),
                1
            );
        }
    }
}

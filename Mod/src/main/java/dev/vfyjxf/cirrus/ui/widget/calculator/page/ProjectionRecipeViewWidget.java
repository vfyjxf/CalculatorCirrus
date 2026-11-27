package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridSize;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemas;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

final class ProjectionRecipeViewWidget extends Widget implements OreUiTooltipProvider {
    private static final int framePadding = 5;
    private static final int slotSize = 18;
    private static final int slotInnerSize = 16;
    private static final int sectionGap = 5;
    private static final int valueSectionGap = 6;
    private static final int maxLooseColumns = 6;
    private static final int arrowGapLeft = 6;
    private static final int arrowGapRight = 8;
    private static final int arrowRenderWidth = 18;
    private static final int arrowRenderHeight = 12;

    private final Layout layout;

    static @Nullable ProjectionRecipeViewWidget create(@Nullable RecipeProjection projection) {
        if (projection == null) {
            return null;
        }
        Layout layout = Layout.build(projection);
        return layout.hasContent() ? new ProjectionRecipeViewWidget(layout) : null;
    }

    private ProjectionRecipeViewWidget(Layout layout) {
        this.layout = layout;
        useStyle(
            sizeOf(layout.width() + framePadding * 2, layout.height() + framePadding * 2),
            boxSizingBorderBox(),
            overflow(Overflow.HIDDEN)
        );
        onHoverTooltip((mouseX, mouseY) -> tooltipAt(mouseX, mouseY));
    }

    int inputCount() {
        return layout.inputCount();
    }

    int outputCount() {
        return layout.outputCount();
    }

    int valueCount() {
        return layout.valueMeters().size();
    }

    boolean usesGrid() {
        return layout.usesGrid();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        canvas.texture(CirrusUiTheme.textures(this).cardSurface(), 0, 0, width(), height());
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        CirrusPalette palette = CirrusUiTheme.palette(this);
        WorkbenchThemeMode theme = CirrusUiTheme.mode(this);

        renderSectionBackgrounds(canvas, baseX, baseY);
        renderSlotBackgrounds(canvas, baseX, baseY);
        if (layout.showArrow()) {
            int ax0 = baseX + layout.arrowX() + 1;
            int ax1 = baseX + layout.arrowX() + arrowRenderWidth - 1;
            int midY = baseY + layout.arrowY() + arrowRenderHeight / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, ax0, ax1, midY, palette, false);
        }
        renderContent(canvas, baseX, baseY, partialTicks);
        renderValueMeters(canvas, baseX, baseY, mouseX, mouseY, palette, theme);
    }

    private void renderValueMeters(
        SceneCanvas canvas,
        int baseX,
        int baseY,
        int mouseX,
        int mouseY,
        CirrusPalette palette,
        WorkbenchThemeMode theme
    ) {
        if (layout.valueMeters().isEmpty()) {
            return;
        }
        if (layout.valueDividerY() >= 0) {
            canvas.fill(baseX, baseY + layout.valueDividerY(), layout.width(), 1, OreUiPaint.cardDivider(palette));
        }
        for (ValueMeter meter : layout.valueMeters()) {
            int mx = baseX + meter.x();
            int my = baseY + meter.y();
            boolean hovered = mouseX >= mx
                    && mouseX < mx + meter.width()
                    && mouseY >= my
                    && mouseY < my + meter.height();
            ValueTypeGaugeRenderer.drawDatabaseMeter(
                canvas,
                meter.entry(),
                mx,
                my,
                meter.width(),
                meter.height(),
                palette,
                theme,
                hovered
            );
        }
    }

    private void renderSectionBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        for (Section section : layout.sections()) {
            CirrusSlotGroupRenderer
                    .renderCells(canvas, this, baseX + section.x(), baseY + section.y(), slotSize, section.cells());
        }
    }

    private void renderSlotBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        // Section backgrounds already render all occupied and empty slot cells as one tight group.
    }

    private void renderContent(SceneCanvas canvas, int baseX, int baseY, float partialTicks) {
        BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch
                .create(canvas.layeredGraphics(), partialTicks);
        for (Slot slot : layout.slots()) {
            RecipeContent content = slot.content();
            if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
                batch.add(
                    content,
                    ExecutionOverlayContentHelper.currentVisualIndex(content),
                    baseX + slot.x() + 1,
                    baseY + slot.y() + 1,
                    slotInnerSize,
                    slotInnerSize
                );
            }
        }
        batch.flush();

        Font font = context().font();
        GuiGraphics graphics = canvas.layeredGraphics();
        for (Slot slot : layout.slots()) {
            if (!ExecutionOverlayContentHelper.hasRenderableVisual(slot.content())) {
                renderFallbackMarker(graphics, baseX + slot.x(), baseY + slot.y());
            }
            renderAmount(font, graphics, baseX, baseY, slot);
        }
    }

    private void renderFallbackMarker(GuiGraphics graphics, int x, int y) {
        CirrusPalette palette = CirrusUiTheme.palette(this);
        int cx = x + slotSize / 2;
        int cy = y + slotSize / 2;
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, palette.textMuted());
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, palette.recessedSurface());
    }

    private void renderAmount(Font font, GuiGraphics graphics, int baseX, int baseY, Slot slot) {
        long amount = slot.content().amount();
        String text = OreUiSlotAmount.format(amount);
        if (text.isEmpty()) {
            return;
        }
        CirrusPalette palette = CirrusUiTheme.palette(this);
        int rawW = Math.max(1, font.width(text));
        float preferred = text.length() <= 2 ? 0.80f : (text.length() == 3 ? 0.68f : 0.58f);
        float scale = Math.max(0.48f, Math.min(preferred, (slotSize - 2.5f) / rawW));
        float scaledW = rawW * scale;
        float scaledH = 8f * scale;
        int sx = baseX + slot.x();
        int sy = baseY + slot.y();
        float tx = sx + slotSize - 1.5f - scaledW;
        float ty = sy + slotSize - 1.5f - scaledH;
        int bgX = Math.max(sx + 1, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(sy + 1, (int) Math.floor(ty - 0.5f));
        int bgW = Math.min(sx + slotSize - 1 - bgX, (int) Math.ceil(scaledW + 2.0f));
        int bgH = Math.min(sy + slotSize - 1 - bgY, (int) Math.ceil(scaledH + 1.5f));
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 200f);
        if (bgW > 0 && bgH > 0) {
            graphics.fill(bgX, bgY, bgX + bgW, bgY + bgH, OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.64f));
        }
        int ink = amount >= 1_000_000L
                ? OreUiPaint.lighten(palette.focusRing(), 0.25f)
                : amount >= 1_000L ? OreUiPaint.lighten(palette.costH(), 0.25f) : palette.textOnAccent();
        graphics.pose().translate(tx, ty, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, ink, true);
        graphics.pose().popPose();
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        if (CirrusUiTheme.shellContext(this) != null) {
            return null;
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        ValueMeter meter = layout.valueMeterAt(localX - baseX, localY - baseY);
        if (meter != null) {
            return ValueTypeGaugeRenderer.richTooltipFor(meter.entry(), CirrusUiTheme.palette(this));
        }
        Slot slot = layout.slotAt(localX - baseX, localY - baseY);
        if (slot == null || slot.content().isEmpty()) {
            return null;
        }
        return RecipeContentSlotWidget.richTooltipFor(slot.content(), null);
    }

    private @Nullable Tooltip tooltipAt(double localX, double localY) {
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        ValueMeter meter = layout.valueMeterAt(localX - baseX, localY - baseY);
        if (meter != null) {
            return ValueTypeGaugeRenderer.tooltipFor(meter.entry());
        }
        Slot slot = layout.slotAt(localX - baseX, localY - baseY);
        if (slot == null || slot.content().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer
                    .getRenderer(slot.content());
            return renderer.tooltip(
                slot.content(),
                ExecutionOverlayContentHelper.currentVisualIndex(slot.content()),
                TooltipContext.create()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record Slot(RecipeContent content, int x, int y) {}

    private record ValueMeter(ValueTypeGaugeRenderer.ValueEntry entry, int x, int y, int width, int height) {}

    private record Section(int x, int y, List<CirrusSlotGroupRenderer.Cell> cells) {
        private Section {
            cells = List.copyOf(cells);
        }

        private int width() {
            CirrusSlotGroupRenderer.Bounds bounds = CirrusSlotGroupRenderer.bounds(cells);
            return bounds.empty() ? 0 : bounds.width(slotSize);
        }

        private int height() {
            CirrusSlotGroupRenderer.Bounds bounds = CirrusSlotGroupRenderer.bounds(cells);
            return bounds.empty() ? 0 : bounds.height(slotSize);
        }
    }

    private record Side(List<Section> sections, List<Slot> slots, int width, int height, boolean usesGrid) {
        private static final Side empty = new Side(List.of(), List.of(), 0, 0, false);

        private Side offset(int x, int y) {
            if (this == empty || x == 0 && y == 0) {
                return this;
            }
            ArrayList<Section> shiftedSections = new ArrayList<>(sections.size());
            for (Section section : sections) {
                shiftedSections.add(new Section(section.x() + x, section.y() + y, section.cells()));
            }
            ArrayList<Slot> shiftedSlots = new ArrayList<>(slots.size());
            for (Slot slot : slots) {
                shiftedSlots.add(new Slot(slot.content(), slot.x() + x, slot.y() + y));
            }
            return new Side(List.copyOf(shiftedSections), List.copyOf(shiftedSlots), width, height, usesGrid);
        }
    }

    private record Layout(
        Side inputs,
        Side outputs,
        List<ValueMeter> valueMeters,
        int inputValueCount,
        int outputValueCount,
        int valueDividerY,
        boolean showArrow,
        int arrowX,
        int arrowY,
        int width,
        int height
    ) {
        static Layout build(RecipeProjection projection) {
            ArrayList<ValueTypeGaugeRenderer.ValueEntry> valueEntries = new ArrayList<>();
            int inValues = 0;
            int outValues = 0;
            for (ClausedContent<?> input : projection.inputs()) {
                if (input.content() instanceof ValueContent<?> value && value.notEmpty()) {
                    valueEntries.add(
                        ValueTypeGaugeRenderer.ValueEntry.fromContent(value, ValueTypeGaugeRenderer.Direction.input)
                    );
                    inValues++;
                }
            }
            for (ClausedContent<?> output : projection.outputs()) {
                if (output.content() instanceof ValueContent<?> value && value.notEmpty()) {
                    valueEntries.add(
                        ValueTypeGaugeRenderer.ValueEntry.fromContent(value, ValueTypeGaugeRenderer.Direction.output)
                    );
                    outValues++;
                }
            }

            Side inputSide = inputSide(projection);
            Side outputSide = contentSide(
                projection.outputs().collect(ClausedContent::content)
                        .select(content -> !(content instanceof ValueContent<?>)).toList()
            );
            boolean hasSlots = !inputSide.slots().isEmpty() || !outputSide.slots().isEmpty();
            boolean arrow = !inputSide.slots().isEmpty() && !outputSide.slots().isEmpty();
            int arrowWidth = arrow ? arrowRenderWidth : 0;
            int arrowHeight = arrow ? arrowRenderHeight : 0;
            int middleWidth = arrow ? arrowGapLeft + arrowWidth + arrowGapRight : 0;
            int slotRowWidth = inputSide.width() + middleWidth + outputSide.width();
            int slotRowHeight = hasSlots ? Math.max(Math.max(inputSide.height(), outputSide.height()), arrowHeight) : 0;

            int meterCols = valueEntries.size() <= 1 ? 1 : 2;
            int minValueStripW = valueEntries.isEmpty()
                    ? 0
                    : meterCols * ValueTypeGaugeRenderer.databaseMeterMinWidth
                            + (meterCols - 1) * ValueTypeGaugeRenderer.databaseMeterGap;
            int totalWidth = Math.max(slotRowWidth, minValueStripW);
            int slotRowOffsetX = Math.max(0, (totalWidth - slotRowWidth) / 2);

            Side placedInputs = inputSide.offset(slotRowOffsetX, centered(slotRowHeight, inputSide.height()));
            Side placedOutputs = outputSide.offset(
                slotRowOffsetX + inputSide.width() + middleWidth,
                centered(slotRowHeight, outputSide.height())
            );

            ArrayList<ValueMeter> meters = new ArrayList<>(valueEntries.size());
            int dividerY = -1;
            int totalHeight = slotRowHeight;
            if (!valueEntries.isEmpty()) {
                int meterTop = hasSlots ? slotRowHeight + valueSectionGap : 0;
                if (hasSlots) {
                    dividerY = slotRowHeight + valueSectionGap / 2;
                }
                int gap = ValueTypeGaugeRenderer.databaseMeterGap;
                int meterH = ValueTypeGaugeRenderer.databaseMeterHeight;
                int cellW = meterCols == 1
                        ? totalWidth
                        : Math.max(ValueTypeGaugeRenderer.databaseMeterMinWidth, (totalWidth - gap) / 2);
                for (int i = 0; i < valueEntries.size(); i++) {
                    int col = i % meterCols;
                    int row = i / meterCols;
                    boolean lastOdd = meterCols == 2 && i == valueEntries.size() - 1 && col == 0;
                    int w = lastOdd ? totalWidth : cellW;
                    int x = col * (cellW + gap);
                    int y = meterTop + row * (meterH + gap);
                    meters.add(new ValueMeter(valueEntries.get(i), x, y, w, meterH));
                }
                int rows = (int) Math.ceil(valueEntries.size() / (double) meterCols);
                totalHeight = meterTop + rows * meterH + Math.max(0, rows - 1) * gap;
            }

            return new Layout(
                placedInputs,
                placedOutputs,
                List.copyOf(meters),
                inValues,
                outValues,
                dividerY,
                arrow,
                slotRowOffsetX + inputSide.width() + arrowGapLeft,
                centered(slotRowHeight, arrowHeight),
                totalWidth,
                totalHeight
            );
        }

        private static Side inputSide(RecipeProjection projection) {
            GridSize gridSize = projection.schema().find(ProjectionSchemas.gridSize);
            if (gridSize == null) {
                return contentSide(
                    projection.inputs().collect(ClausedContent::content)
                            .select(content -> !(content instanceof ValueContent<?>)).toList()
                );
            }

            int gridSlotCount = gridSize.width() * gridSize.height();
            ArrayList<@Nullable RecipeContent> grid = new ArrayList<>(Collections.nCopies(gridSlotCount, null));
            ArrayList<RecipeContent> loose = new ArrayList<>();
            boolean hasAnyGridSlot = false;
            for (ClausedContent<?> input : projection.inputs()) {
                RecipeContent content = input.content();
                if (content.isEmpty() || content instanceof ValueContent<?>) {
                    continue;
                }
                GridIndex gridIndex = input.schema().find(ProjectionSchemas.gridIndex);
                if (gridIndex != null
                        && gridIndex.index() >= 0
                        && gridIndex.index() < gridSlotCount
                        && grid.get(gridIndex.index()) == null) {
                    grid.set(gridIndex.index(), content);
                    hasAnyGridSlot = true;
                } else {
                    loose.add(content);
                }
            }

            if (!hasAnyGridSlot && loose.isEmpty()) {
                return new Side(List.of(), List.of(), 0, 0, true);
            }

            ArrayList<Section> sections = new ArrayList<>();
            ArrayList<Slot> slots = new ArrayList<>();
            int gridWidth = gridSize.width() * slotSize;
            int gridHeight = gridSize.height() * slotSize;
            sections.add(new Section(0, 0, CirrusSlotGroupRenderer.rectCells(gridSize.width(), gridSize.height())));
            for (int index = 0; index < grid.size(); index++) {
                RecipeContent content = grid.get(index);
                if (content == null || content.isEmpty()) {
                    continue;
                }
                int x = (index % gridSize.width()) * slotSize;
                int y = (index / gridSize.width()) * slotSize;
                slots.add(new Slot(content, x, y));
            }

            Side looseSide = contentSide(loose);
            if (!looseSide.slots().isEmpty()) {
                int y = gridHeight + sectionGap;
                Side shifted = looseSide.offset(0, y);
                sections.addAll(shifted.sections());
                slots.addAll(shifted.slots());
                return new Side(
                    List.copyOf(sections),
                    List.copyOf(slots),
                    Math.max(gridWidth, looseSide.width()),
                    y + looseSide.height(),
                    true
                );
            }
            return new Side(List.copyOf(sections), List.copyOf(slots), gridWidth, gridHeight, true);
        }

        private static Side contentSide(List<? extends RecipeContent> contents) {
            ArrayList<RecipeContent> visible = new ArrayList<>();
            for (RecipeContent content : contents) {
                if (content != null && content.notEmpty() && !(content instanceof ValueContent<?>)) {
                    visible.add(content);
                }
            }
            if (visible.isEmpty()) {
                return Side.empty;
            }
            int columns = visible.size() <= 4
                    ? visible.size()
                    : visible.size() <= 9 ? 3 : Math.min(maxLooseColumns, (int) Math.ceil(Math.sqrt(visible.size())));
            int rows = (int) Math.ceil(visible.size() / (double) columns);
            int width = columns * slotSize;
            int height = rows * slotSize;
            ArrayList<Slot> slots = new ArrayList<>(visible.size());
            for (int index = 0; index < visible.size(); index++) {
                slots.add(new Slot(visible.get(index), (index % columns) * slotSize, (index / columns) * slotSize));
            }
            return new Side(
                List.of(new Section(0, 0, CirrusSlotGroupRenderer.rowMajorCells(visible.size(), columns))),
                List.copyOf(slots),
                width,
                height,
                false
            );
        }

        private static int centered(int container, int content) {
            return Math.max(0, (container - content) / 2);
        }

        private boolean hasContent() {
            return !inputs.slots().isEmpty() || !outputs.slots().isEmpty() || !valueMeters.isEmpty();
        }

        private boolean usesGrid() {
            return inputs.usesGrid();
        }

        private int inputCount() {
            return inputs.slots().size() + inputValueCount;
        }

        private int outputCount() {
            return outputs.slots().size() + outputValueCount;
        }

        private List<Slot> slots() {
            ArrayList<Slot> slots = new ArrayList<>(inputs.slots().size() + outputs.slots().size());
            slots.addAll(inputs.slots());
            slots.addAll(outputs.slots());
            return List.copyOf(slots);
        }

        private List<Section> sections() {
            ArrayList<Section> sections = new ArrayList<>(inputs.sections().size() + outputs.sections().size());
            sections.addAll(inputs.sections());
            sections.addAll(outputs.sections());
            return List.copyOf(sections);
        }

        private @Nullable Slot slotAt(double x, double y) {
            for (Slot slot : slots()) {
                if (x >= slot.x() && x < slot.x() + slotSize && y >= slot.y() && y < slot.y() + slotSize) {
                    return slot;
                }
            }
            return null;
        }

        private @Nullable ValueMeter valueMeterAt(double x, double y) {
            for (ValueMeter meter : valueMeters) {
                if (x >= meter.x()
                        && x < meter.x() + meter.width()
                        && y >= meter.y()
                        && y < meter.y() + meter.height()) {
                    return meter;
                }
            }
            return null;
        }
    }
}

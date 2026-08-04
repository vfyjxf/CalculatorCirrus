package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridSize;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemas;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

final class ProjectionRecipeViewWidget extends Widget {
    private static final int framePadding = 4;
    private static final int slotSize = 18;
    private static final int slotInnerSize = 16;
    private static final int sectionGap = 5;
    private static final int maxLooseColumns = 4;
    private static final int arrowGapLeft = 7;
    private static final int arrowGapRight = 10;

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
        return layout.inputSlots().size();
    }

    int outputCount() {
        return layout.outputSlots().size();
    }

    boolean usesGrid() {
        return layout.usesGrid();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        canvas.texture(CirrusTextures.Background.Outlined.flat, 0, 0, width(), height());
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);

        renderSectionBackgrounds(canvas, baseX, baseY);
        renderSlotBackgrounds(canvas, baseX, baseY);
        if (layout.showArrow()) {
            CirrusTextures.Icon.Middle.arrowRight.render(
                canvas.layeredGraphics(),
                baseX + layout.arrowX(),
                baseY + layout.arrowY(),
                CirrusTextures.Icon.Middle.arrowRight.width(),
                CirrusTextures.Icon.Middle.arrowRight.height()
            );
        }
        renderContent(canvas, baseX, baseY, partialTicks);
    }

    private void renderSectionBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        for (Section section : layout.sections()) {
            CirrusSlotGroupRenderer.renderCells(
                canvas,
                baseX + section.x(),
                baseY + section.y(),
                slotSize,
                section.cells()
            );
        }
    }

    private void renderSlotBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        // Section backgrounds already render all occupied and empty slot cells as one tight group.
    }

    private void renderContent(SceneCanvas canvas, int baseX, int baseY, float partialTicks) {
        BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch.create(canvas.layeredGraphics(), partialTicks);
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
        var icon = CirrusTextures.Icon.Sign.Amount.available;
        icon.render(
            graphics,
            x + (slotSize - icon.width()) / 2,
            y + (slotSize - icon.height()) / 2,
            icon.width(),
            icon.height()
        );
    }

    private void renderAmount(Font font, GuiGraphics graphics, int baseX, int baseY, Slot slot) {
        long amount = slot.content().amount();
        if (amount <= 1) {
            return;
        }
        String text = NumberUtils.toSuffix(amount);
        graphics.drawString(
            font,
            text,
            baseX + slot.x() + slotSize - 1 - font.width(text),
            baseY + slot.y() + slotSize - 9,
            0xFFFFFFFF,
            true
        );
    }

    private @Nullable Tooltip tooltipAt(double localX, double localY) {
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        Slot slot = layout.slotAt(localX - baseX, localY - baseY);
        if (slot == null || slot.content().isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer.getRenderer(slot.content());
            return renderer.tooltip(
                slot.content(),
                ExecutionOverlayContentHelper.currentVisualIndex(slot.content()),
                TooltipContext.create()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record Slot(RecipeContent content, int x, int y) {
    }

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
        boolean showArrow,
        int arrowX,
        int arrowY,
        int width,
        int height
    ) {
        static Layout build(RecipeProjection projection) {
            Side inputSide = inputSide(projection);
            Side outputSide = contentSide(projection.outputs().collect(ClausedContent::content).toList());
            boolean arrow = !inputSide.slots().isEmpty() && !outputSide.slots().isEmpty();
            int arrowWidth = arrow ? CirrusTextures.Icon.Middle.arrowRight.width() : 0;
            int arrowHeight = arrow ? CirrusTextures.Icon.Middle.arrowRight.height() : 0;
            int middleWidth = arrow ? arrowGapLeft + arrowWidth + arrowGapRight : 0;
            int height = Math.max(Math.max(inputSide.height(), outputSide.height()), arrowHeight);
            Side placedInputs = inputSide.offset(0, centered(height, inputSide.height()));
            Side placedOutputs = outputSide.offset(inputSide.width() + middleWidth, centered(height, outputSide.height()));
            return new Layout(
                placedInputs,
                placedOutputs,
                arrow,
                inputSide.width() + arrowGapLeft,
                centered(height, arrowHeight),
                inputSide.width() + middleWidth + outputSide.width(),
                height
            );
        }

        private static Side inputSide(RecipeProjection projection) {
            GridSize gridSize = projection.schema().find(ProjectionSchemas.gridSize);
            if (gridSize == null) {
                return contentSide(projection.inputs().collect(ClausedContent::content).toList());
            }

            int gridSlotCount = gridSize.width() * gridSize.height();
            ArrayList<@Nullable RecipeContent> grid = new ArrayList<>(Collections.nCopies(gridSlotCount, null));
            ArrayList<RecipeContent> loose = new ArrayList<>();
            for (ClausedContent<?> input : projection.inputs()) {
                RecipeContent content = input.content();
                if (content.isEmpty()) {
                    continue;
                }
                GridIndex gridIndex = input.schema().find(ProjectionSchemas.gridIndex);
                if (gridIndex != null && gridIndex.index() >= 0 && gridIndex.index() < gridSlotCount && grid.get(gridIndex.index()) == null) {
                    grid.set(gridIndex.index(), content);
                } else {
                    loose.add(content);
                }
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
                if (content != null && content.notEmpty()) {
                    visible.add(content);
                }
            }
            if (visible.isEmpty()) {
                return Side.empty;
            }
            int columns = Math.min(maxLooseColumns, visible.size());
            int rows = (int) Math.ceil(visible.size() / (double) columns);
            int width = columns * slotSize;
            int height = rows * slotSize;
            ArrayList<Slot> slots = new ArrayList<>(visible.size());
            for (int index = 0; index < visible.size(); index++) {
                slots.add(new Slot(
                    visible.get(index),
                    (index % columns) * slotSize,
                    (index / columns) * slotSize
                ));
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
            return !inputs.slots().isEmpty() || !outputs.slots().isEmpty();
        }

        private boolean usesGrid() {
            return inputs.usesGrid();
        }

        private List<Slot> inputSlots() {
            return inputs.slots();
        }

        private List<Slot> outputSlots() {
            return outputs.slots();
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
    }
}

package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionRecipePreviewLayout;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeTooltipPreview;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public final class ExecutionRecipeLayoutPreviewWidget extends Widget implements OreUiTooltipProvider {
    private static final int framePadding = 5;

    private final ExecutionRecipePreviewLayout.Layout layout;

    public static @Nullable ExecutionRecipeLayoutPreviewWidget create(
        @Nullable RecipeEntry<?> recipe,
        @Nullable RecipeProjection projection,
        @Nullable ProjectedRecipe projected
    ) {
        return create(
            ExecutionRecipeTooltipPreview.previewInputSlots(recipe, projection, projected),
            ExecutionRecipeTooltipPreview.previewOutputSlots(projection, projected)
        );
    }

    public static @Nullable ExecutionRecipeLayoutPreviewWidget create(
        List<@Nullable MaterialSlot> inputs,
        List<MaterialSlot> outputs
    ) {
        ExecutionRecipePreviewLayout.Layout layout = ExecutionRecipePreviewLayout.build(inputs, outputs);
        return layout.hasSlots() ? new ExecutionRecipeLayoutPreviewWidget(layout) : null;
    }

    private ExecutionRecipeLayoutPreviewWidget(ExecutionRecipePreviewLayout.Layout layout) {
        this.layout = layout;
        useStyle(
            sizeOf(layout.width() + framePadding * 2, layout.height() + framePadding * 2),
            boxSizingBorderBox(),
            overflow(Overflow.HIDDEN)
        );
        onHoverTooltip((mouseX, mouseY) -> tooltipAt(mouseX, mouseY));
    }

    public int inputSlotCount() {
        return layout.inputSlotCount();
    }

    public int outputSlotCount() {
        return layout.outputSlotCount();
    }

    public int sectionCount() {
        return layout.sectionCount();
    }

    public boolean typedSections() {
        return layout.hasTypedSections();
    }

    public int valueCount() {
        return layout.valueCount();
    }

    public boolean framed() {
        return true;
    }

    public boolean normalRecipeLayout() {
        return layout.showArrow();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        canvas.texture(CirrusUiTheme.textures(this).flat(), 0, 0, width(), height());
        int baseX = frameOffsetX();
        int baseY = frameOffsetY();
        CirrusPalette palette = CirrusUiTheme.palette(this);
        WorkbenchThemeMode theme = CirrusUiTheme.mode(this);

        renderSlotBackgrounds(canvas, baseX, baseY);
        if (layout.showArrow()) {
            int arrowW = CirrusTextures.Icon.Middle.arrowRight.width();
            int arrowH = CirrusTextures.Icon.Middle.arrowRight.height();
            int ax0 = baseX + layout.arrowX() - 4;
            int ax1 = baseX + layout.arrowX() + arrowW + 7;
            int midY = baseY + layout.arrowY() + arrowH / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, ax0, ax1, midY, palette, false);
        }
        renderSlotContents(canvas, baseX, baseY, partialTicks);
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
        if (layout.valueEntries().isEmpty()) {
            return;
        }
        if (layout.valueDividerY() >= 0) {
            canvas.fill(baseX, baseY + layout.valueDividerY(), layout.width(), 1, OreUiPaint.cardDivider(palette));
        }
        for (ExecutionRecipePreviewLayout.ValueEntryBox box : layout.valueEntries()) {
            int mx = baseX + box.x();
            int my = baseY + box.y();
            boolean hovered = mouseX >= mx && mouseX < mx + box.width() && mouseY >= my && mouseY < my + box.height();
            ValueTypeGaugeRenderer
                    .drawDatabaseMeter(canvas, box.entry(), mx, my, box.width(), box.height(), palette, theme, hovered);
        }
    }

    private void renderSlotBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        for (ExecutionRecipePreviewLayout.Section section : layout.inputSections()) {
            renderSectionBackground(canvas, section, baseX, baseY);
        }
        for (ExecutionRecipePreviewLayout.Section section : layout.outputSections()) {
            renderSectionBackground(canvas, section, baseX, baseY);
        }
    }

    private void renderSectionBackground(
        SceneCanvas canvas,
        ExecutionRecipePreviewLayout.Section section,
        int baseX,
        int baseY
    ) {
        CirrusSlotGroupRenderer.renderCells(
            canvas,
            this,
            baseX + section.x(),
            baseY + section.y(),
            ExecutionRecipePreviewLayout.slotSize,
            section.cells()
        );
    }

    private void renderSlotContents(SceneCanvas canvas, int baseX, int baseY, float partialTicks) {
        BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch
                .create(canvas.layeredGraphics(), partialTicks);
        for (ExecutionRecipePreviewLayout.SlotEntry entry : layout.inputs()) {
            submitSlot(batch, entry, baseX, baseY);
        }
        for (ExecutionRecipePreviewLayout.SlotEntry entry : layout.outputs()) {
            submitSlot(batch, entry, baseX, baseY);
        }
        batch.flush();

        for (ExecutionRecipePreviewLayout.SlotEntry entry : layout.inputs()) {
            renderFallbackMarker(context().font(), canvas.layeredGraphics(), entry, baseX, baseY);
            renderAmount(context().font(), canvas.layeredGraphics(), entry, baseX, baseY);
        }
        for (ExecutionRecipePreviewLayout.SlotEntry entry : layout.outputs()) {
            renderFallbackMarker(context().font(), canvas.layeredGraphics(), entry, baseX, baseY);
            renderAmount(context().font(), canvas.layeredGraphics(), entry, baseX, baseY);
        }
    }

    private void submitSlot(
        BatchableRecipeContentRenderer.Batch batch,
        ExecutionRecipePreviewLayout.SlotEntry entry,
        int baseX,
        int baseY
    ) {
        RecipeContent content = ExecutionRecipePreviewLayout.slotContent(entry.slot());
        if (!ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
            return;
        }
        batch.add(
            content,
            ExecutionOverlayContentHelper.currentVisualIndex(content),
            baseX + entry.x() + 1,
            baseY + entry.y() + 1,
            ExecutionRecipePreviewLayout.slotInnerSize,
            ExecutionRecipePreviewLayout.slotInnerSize
        );
    }

    private void renderFallbackMarker(
        Font font,
        GuiGraphics graphics,
        ExecutionRecipePreviewLayout.SlotEntry entry,
        int baseX,
        int baseY
    ) {
        MaterialSlot slot = entry.slot();
        if (slot == null
                || ExecutionOverlayContentHelper.hasRenderableVisual(ExecutionRecipePreviewLayout.slotContent(slot))) {
            return;
        }
        int color = slot.isCatalyst() ? 0xFFE5B567 : 0xFF7FA6C4;
        int cx = baseX + entry.x() + ExecutionRecipePreviewLayout.slotSize / 2;
        int cy = baseY + entry.y() + ExecutionRecipePreviewLayout.slotSize / 2;
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF1F242D);
    }

    private void renderAmount(
        Font font,
        GuiGraphics graphics,
        ExecutionRecipePreviewLayout.SlotEntry entry,
        int baseX,
        int baseY
    ) {
        MaterialSlot slot = entry.slot();
        if (slot == null) {
            return;
        }
        int slotX = baseX + entry.x();
        int slotY = baseY + entry.y();
        renderAmount(font, graphics, slot.amount(), slotX, slotY);
    }

    private void renderAmount(Font font, GuiGraphics graphics, int amount, int slotX, int slotY) {
        if (amount <= 1) {
            return;
        }
        String text = NumberUtils.toSuffix(amount);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(
            font,
            text,
            slotX + ExecutionRecipePreviewLayout.slotSize - 1 - font.width(text),
            slotY + ExecutionRecipePreviewLayout.slotSize - 9,
            0xFFFFFFFF,
            true
        );
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
        ExecutionRecipePreviewLayout.ValueEntryBox box = layout
                .valueAt(localX - frameOffsetX(), localY - frameOffsetY());
        if (box != null) {
            return ValueTypeGaugeRenderer.richTooltipFor(box.entry(), CirrusUiTheme.palette(this));
        }
        RecipeContent content = contentAt(localX, localY);
        if (content == null || content.isEmpty()) {
            return null;
        }
        return RecipeContentSlotWidget.richTooltipFor(content, null);
    }

    private @Nullable Tooltip tooltipAt(double localX, double localY) {
        ExecutionRecipePreviewLayout.ValueEntryBox box = layout
                .valueAt(localX - frameOffsetX(), localY - frameOffsetY());
        if (box != null) {
            return ValueTypeGaugeRenderer.tooltipFor(box.entry());
        }
        RecipeContent content = contentAt(localX, localY);
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer
                    .getRenderer(content);
            return renderer.tooltip(
                content,
                ExecutionOverlayContentHelper.currentVisualIndex(content),
                TooltipContext.create()
            );
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    private @Nullable RecipeContent contentAt(double localX, double localY) {
        int baseX = frameOffsetX();
        int baseY = frameOffsetY();
        RecipeContent input = contentAt(layout.inputs(), localX, localY, baseX, baseY);
        if (input != null) {
            return input;
        }
        return contentAt(layout.outputs(), localX, localY, baseX, baseY);
    }

    private @Nullable RecipeContent contentAt(
        List<ExecutionRecipePreviewLayout.SlotEntry> entries,
        double localX,
        double localY,
        int baseX,
        int baseY
    ) {
        for (ExecutionRecipePreviewLayout.SlotEntry entry : entries) {
            int x = baseX + entry.x();
            int y = baseY + entry.y();
            if (localX >= x
                    && localX < x + ExecutionRecipePreviewLayout.slotSize
                    && localY >= y
                    && localY < y + ExecutionRecipePreviewLayout.slotSize) {
                RecipeContent content = ExecutionRecipePreviewLayout.slotContent(entry.slot());
                return content.isEmpty() ? null : content;
            }
        }
        return null;
    }

    private int frameOffsetX() {
        return Math.max(framePadding, (width() - layout.width()) / 2);
    }

    private int frameOffsetY() {
        return Math.max(framePadding, (height() - layout.height()) / 2);
    }
}

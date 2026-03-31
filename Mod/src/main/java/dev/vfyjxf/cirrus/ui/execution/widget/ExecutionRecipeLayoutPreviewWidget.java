package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionRecipePreviewLayout;
import dev.vfyjxf.cirrus.ui.execution.tooltip.ExecutionRecipeTooltipPreview;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
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

import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public final class ExecutionRecipeLayoutPreviewWidget extends Widget {
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

    public boolean framed() {
        return true;
    }

    public boolean normalRecipeLayout() {
        return layout.showArrow();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        canvas.texture(CirrusTextures.Background.Outlined.flat, 0, 0, width(), height());
        int baseX = frameOffsetX();
        int baseY = frameOffsetY();

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
        renderSlotContents(canvas, baseX, baseY, partialTicks);
    }

    private void renderSlotBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        for (ExecutionRecipePreviewLayout.Section section : layout.inputSections()) {
            renderSectionBackground(canvas, section, baseX, baseY);
        }
        for (ExecutionRecipePreviewLayout.Section section : layout.outputSections()) {
            renderSectionBackground(canvas, section, baseX, baseY);
        }
    }

    private void renderSectionBackground(SceneCanvas canvas, ExecutionRecipePreviewLayout.Section section, int baseX, int baseY) {
        CirrusSlotGroupRenderer.renderCells(
            canvas,
            baseX + section.x(),
            baseY + section.y(),
            ExecutionRecipePreviewLayout.slotSize,
            section.cells()
        );
    }

    private void renderSlotContents(SceneCanvas canvas, int baseX, int baseY, float partialTicks) {
        BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch.create(canvas.layeredGraphics(), partialTicks);
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

    private void submitSlot(BatchableRecipeContentRenderer.Batch batch, ExecutionRecipePreviewLayout.SlotEntry entry, int baseX, int baseY) {
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

    private void renderFallbackMarker(Font font, GuiGraphics graphics, ExecutionRecipePreviewLayout.SlotEntry entry, int baseX, int baseY) {
        MaterialSlot slot = entry.slot();
        if (slot == null || ExecutionOverlayContentHelper.hasRenderableVisual(ExecutionRecipePreviewLayout.slotContent(slot))) {
            return;
        }
        var icon = slot.isCatalyst()
            ? CirrusTextures.Icon.Sign.Amount.catalyst
            : CirrusTextures.Icon.Sign.Amount.available;
        int slotX = baseX + entry.x();
        int slotY = baseY + entry.y();
        int iconX = slotX + (ExecutionRecipePreviewLayout.slotSize - icon.width()) / 2;
        int iconY = slotY + (ExecutionRecipePreviewLayout.slotSize - icon.height()) / 2;
        icon.render(graphics, iconX, iconY, icon.width(), icon.height());
    }

    private void renderAmount(Font font, GuiGraphics graphics, ExecutionRecipePreviewLayout.SlotEntry entry, int baseX, int baseY) {
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

    private @Nullable Tooltip tooltipAt(double localX, double localY) {
        RecipeContent content = contentAt(localX, localY);
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer.getRenderer(content);
            return renderer.tooltip(content, ExecutionOverlayContentHelper.currentVisualIndex(content), TooltipContext.create());
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

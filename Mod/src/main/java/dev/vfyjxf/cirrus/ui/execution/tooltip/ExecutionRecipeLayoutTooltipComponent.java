package dev.vfyjxf.cirrus.ui.execution.tooltip;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionRecipePreviewLayout;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cirrus.util.NumberUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ExecutionRecipeLayoutTooltipComponent(
    ExecutionRecipePreviewLayout.Layout layout,
    int leftPadding,
    int topPadding,
    int rightPadding,
    int bottomPadding
) implements TooltipComponent {
    private static final int padding = 3;

    public ExecutionRecipeLayoutTooltipComponent(ExecutionRecipePreviewLayout.Layout layout) {
        this(layout, padding, padding, padding, padding);
    }

    public ExecutionRecipeLayoutTooltipComponent(List<@Nullable MaterialSlot> inputs, List<MaterialSlot> outputs) {
        this(ExecutionRecipePreviewLayout.build(inputs, outputs));
    }

    public static ExecutionRecipeLayoutTooltipComponent leftAligned(
        List<@Nullable MaterialSlot> inputs,
        List<MaterialSlot> outputs
    ) {
        return new ExecutionRecipeLayoutTooltipComponent(
            ExecutionRecipePreviewLayout.build(inputs, outputs),
            4,
            padding,
            padding,
            padding
        );
    }

    public static ClientTooltipComponent toClient(ExecutionRecipeLayoutTooltipComponent data) {
        return new Client(data);
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

    private static final class Client implements ClientTooltipComponent {
        private final ExecutionRecipeLayoutTooltipComponent data;

        private Client(ExecutionRecipeLayoutTooltipComponent data) {
            this.data = data;
        }

        @Override
        public int getWidth(Font font) {
            return data.layout().width() + data.leftPadding() + data.rightPadding();
        }

        @Override
        public int getHeight() {
            return data.layout().height() + data.topPadding() + data.bottomPadding();
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            ExecutionRecipePreviewLayout.Layout layout = data.layout();
            CirrusTextures.Background.Outlined.flat.render(graphics, x, y, getWidth(font), getHeight());
            int baseX = x + data.leftPadding();
            int baseY = y + data.topPadding();
            for (ExecutionRecipePreviewLayout.Section section : layout.inputSections()) {
                renderSection(graphics, section, baseX, baseY);
            }
            for (ExecutionRecipePreviewLayout.Section section : layout.outputSections()) {
                renderSection(graphics, section, baseX, baseY);
            }

            for (ExecutionRecipePreviewLayout.SlotEntry entry : layout.inputs()) {
                renderSlot(font, graphics, entry.slot(), baseX + entry.x(), baseY + entry.y());
            }

            if (layout.showArrow()) {
                CirrusTextures.Icon.Middle.arrowRight.render(
                    graphics,
                    baseX + layout.arrowX(),
                    baseY + layout.arrowY(),
                    CirrusTextures.Icon.Middle.arrowRight.width(),
                    CirrusTextures.Icon.Middle.arrowRight.height()
                );
            }

            for (ExecutionRecipePreviewLayout.SlotEntry entry : layout.outputs()) {
                renderSlot(font, graphics, entry.slot(), baseX + entry.x(), baseY + entry.y());
            }
        }

        private static void renderSection(
            GuiGraphics graphics,
            ExecutionRecipePreviewLayout.Section section,
            int baseX,
            int baseY
        ) {
            int slotSize = ExecutionRecipePreviewLayout.slotSize;
            for (var cell : section.cells()) {
                CirrusTextures.Background.Slot.base.render(
                    graphics,
                    baseX + section.x() + cell.column() * slotSize,
                    baseY + section.y() + cell.row() * slotSize,
                    slotSize,
                    slotSize
                );
            }
            for (var border : CirrusSlotGroupRenderer.borderSegments(section.cells(), slotSize)) {
                graphics.fill(
                    baseX + section.x() + border.x(),
                    baseY + section.y() + border.y(),
                    baseX + section.x() + border.x() + border.width(),
                    baseY + section.y() + border.y() + border.height(),
                    0xFFFFFFFF
                );
            }
        }

        private static void renderSlot(Font font, GuiGraphics graphics, @Nullable MaterialSlot slot, int x, int y) {
            if (slot == null) {
                return;
            }
            RecipeContent content = ExecutionRecipePreviewLayout.slotContent(slot);
            if (!ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
                renderFallbackMarker(graphics, slot, x, y);
            } else {
                renderRecipeContent(graphics, content, x + 1, y + 1);
            }
            renderAmount(font, graphics, slot.amount(), x, y);
        }

        private static void renderFallbackMarker(GuiGraphics graphics, MaterialSlot slot, int x, int y) {
            int color = slot.isCatalyst() ? 0xFFE5B567 : 0xFF7FA6C4;
            int cx = x + ExecutionRecipePreviewLayout.slotSize / 2;
            int cy = y + ExecutionRecipePreviewLayout.slotSize / 2;
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, color);
            graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF1F242D);
        }

        private static void renderAmount(Font font, GuiGraphics graphics, int amount, int x, int y) {
            if (amount <= 1) {
                return;
            }
            String text = NumberUtils.toSuffix(amount);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(
                font,
                text,
                x + ExecutionRecipePreviewLayout.slotSize - 1 - font.width(text),
                y + ExecutionRecipePreviewLayout.slotSize - 9,
                0xFFFFFFFF,
                true
            );
            graphics.pose().popPose();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void renderRecipeContent(GuiGraphics graphics, RecipeContent content, int x, int y) {
            RecipeContentRenderer renderer = RecipeContentRenderer.getRenderer(content);
            renderer.renderAt(
                graphics,
                content,
                ExecutionOverlayContentHelper.currentVisualIndex(content),
                x,
                y,
                ExecutionRecipePreviewLayout.slotInnerSize,
                ExecutionRecipePreviewLayout.slotInnerSize,
                0
            );
        }

    }
}

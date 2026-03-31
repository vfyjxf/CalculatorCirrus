package dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Sign;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MachineInfo;
import dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialSlot;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textWhite;

final class OmniCalcSlotRenderer {

    private OmniCalcSlotRenderer() {
    }

    static boolean usesRecipeContentRenderer() {
        return true;
    }

    static boolean usesFallbackBarrierIcon() {
        return false;
    }

    static boolean rendersMachinePlaceholder() {
        return false;
    }

    static void renderMaterialSlot(
            SceneCanvas canvas,
            Font font,
            @Nullable MaterialSlot slot,
            int x,
            int y,
            int size,
            float partialTicks,
            boolean amountOverlay
    ) {
        CirrusSlotGroupRenderer.renderSingle(canvas, x, y, size);
        if (slot == null) {
            renderTypePlaceholder(canvas, null, x, y, size);
            return;
        }

        int contentSize = Math.max(1, size - 2);
        int contentX = x + 1;
        int contentY = y + 1;
        if (slot.content().notEmpty() && visualCount(slot.content()) > 0) {
            renderContent(canvas, slot.content(), currentVisualIndex(slot.content()),
                    contentX, contentY, contentSize, partialTicks);
        } else if (slot.itemStack() != null && !slot.itemStack().isEmpty()) {
            renderItem(canvas, slot.itemStack(), x, y, size);
        } else if (slot.fluidStack() != null && !slot.fluidStack().isEmpty()) {
            renderFluid(canvas, slot.fluidStack(), contentX, contentY, contentSize, partialTicks);
        } else {
            renderTypePlaceholder(canvas, slot, x, y, size);
        }

        if (amountOverlay && slot.amount() > 1) {
            String amount = compactNumber(slot.amount());
            int amountW = font.width(amount);
            canvas.drawString(amount, x + size - amountW - 1, y + size - 7, textWhite, true);
        }
    }

    static void renderMachineSlot(
            SceneCanvas canvas,
            @Nullable MachineInfo machine,
            int x,
            int y,
            int size,
            int fallbackColor
    ) {
        ItemStack icon = machine == null ? ItemStack.EMPTY : machine.icon();
        if (icon != null && !icon.isEmpty()) {
            CirrusSlotGroupRenderer.renderSingle(canvas, x, y, size);
            renderItem(canvas, icon, x, y, size);
        }
    }

    private static void renderItem(SceneCanvas canvas, ItemStack stack, int x, int y, int size) {
        int itemX = x + Math.max(0, (size - 16) / 2);
        int itemY = y + Math.max(0, (size - 16) / 2);
        canvas.renderItem(stack, itemX, itemY);
    }

    private static void renderFluid(SceneCanvas canvas, FluidStack stack, int x, int y, int size, float partialTicks) {
        var graphics = canvas.layeredGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float scale = size / 16.0f;
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().last().normal().identity();
        FluidStackRenderer.instance.renderBase(graphics, stack, 16, 16, partialTicks);
        graphics.pose().popPose();
    }

    private static void renderTypePlaceholder(SceneCanvas canvas, @Nullable MaterialSlot slot, int x, int y, int size) {
        var icon = slot != null && slot.isCatalyst()
                ? Sign.Group.catalyst
                : slot != null && slot.type() == dev.vfyjxf.cirrus.ui.process.ProcessNode.MaterialType.FLUID
                ? Sign.Group.fluid
                : Sign.Group.item;
        int iconSize = Math.min(10, Math.max(6, size - 8));
        canvas.texture(icon, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, iconSize);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int visualCount(RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        RecipeContentRenderer renderer = RecipeContentRenderer.getRenderer(content);
        return Math.max(0, renderer.visualCount(content));
    }

    private static int currentVisualIndex(RecipeContent content) {
        int count = visualCount(content);
        return count <= 1 ? 0 : (int) ((System.currentTimeMillis() / 1000L) % count);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderContent(
            SceneCanvas canvas,
            RecipeContent content,
            int visualIndex,
            int x,
            int y,
            int size,
            float partialTicks
    ) {
        RecipeContentRenderer renderer = RecipeContentRenderer.getRenderer(content);
        renderer.renderAt(canvas.layeredGraphics(), content, visualIndex, x, y, size, size, partialTicks);
    }

    static String compactNumber(long value) {
        if (value < 1_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fk", value / 1_000.0d);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0d);
    }
}

package dev.vfyjxf.calculatorcirrus.content.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.calculatorcirrus.api.content.render.StackRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import dev.vfyjxf.calculatorcirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.text.TooltipEntry;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class ItemStackRenderer implements StackRenderer<Item, ItemStack> {

    @Override
    public void render(GuiGraphics graphics, ItemStack stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void render(GuiGraphics graphics, ContentStack<Item, ItemStack> stack, int width, int height, float delta) {
        renderBase(graphics, stack.value(), width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    @Override
    public void renderBase(GuiGraphics graphics, ItemStack stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        RenderSystem.enableDepthTest();
        graphics.pose().scale(width / 16f, height / 16f, 0f);
        graphics.renderFakeItem(stack, 0, 0);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }

    @Override
    public void renderBase(GuiGraphics graphics, ContentStack<Item, ItemStack> stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        renderBase(graphics, stack.value(), width, height, delta);
    }

    @Override
    public void decorate(GuiGraphics graphics, ItemStack stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        //TODO:support custom number formater
        graphics.pose().pushPose();
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        graphics.renderItemDecorations(getFont(stack, Minecraft.getInstance()), stack, 0, 0, NumberUtils.toSuffix(stack.getCount()));
        graphics.pose().popPose();
    }


    @Override
    public void decorate(GuiGraphics graphics, ContentStack<Item, ItemStack> stack, int width, int height, float delta) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        var content = stack.value();
        graphics.renderItemDecorations(getFont(content, Minecraft.getInstance()), content, 0, 0, NumberUtils.toSuffix(stack.amount()));
        graphics.pose().popPose();
    }

    @Override
    public Font getFont(ItemStack stack, Minecraft minecraft) {
        IClientItemExtensions extensions = IClientItemExtensions.of(stack);
        Font font = extensions.getFont(stack, IClientItemExtensions.FontContext.TOOLTIP);
        return font == null ? minecraft.font : font;
    }

    @Override
    public RichTooltip getTooltip(ItemStack stack, TooltipContext context) {
        if (stack.isEmpty()) return RichTooltip.empty();
        var tooltip = RichTooltip.create();
        var lines = stack.getTooltipLines(context, context.player(), context.flag());
        for (var line : lines) tooltip.add(line);
        var tooltipImage = stack.getTooltipImage();
        //noinspection OptionalIsPresent
        if (tooltipImage.isPresent()) {
            tooltip.add(1, TooltipEntry.of(tooltipImage.get()));
        }
        return tooltip;
    }

    @Override
    public Component getName(ItemStack stack) {
        return stack.getDisplayName();
    }

    @Override
    public String getDisplayName(ItemStack stack) {
        return stack.getDisplayName().getString();
    }
}

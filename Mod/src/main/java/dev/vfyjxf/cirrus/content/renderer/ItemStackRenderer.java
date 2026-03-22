package dev.vfyjxf.cirrus.content.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipLocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class ItemStackRenderer implements StackRenderer<Item, ItemStack> {

    public static final ItemStackRenderer instance = new ItemStackRenderer();

    private ItemStackRenderer() {}

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
        graphics.pose().scale(width / 16f, height / 16f, 1f);
        graphics.renderFakeItem(stack, 0, 0);
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
        //TODO:support custom number formater,also see FluidStackRenderer
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
    public Tooltip getTooltip(ItemStack stack, TooltipContext context) {
        if (stack.isEmpty()) return new Tooltip();
        var tooltip = new Tooltip();
        var lines = stack.getTooltipLines(context, context.player(), context.flag());
        for (var line : lines) tooltip.add(line);
        var tooltipImage = stack.getTooltipImage();
        tooltipImage.ifPresent(tooltipComponent -> tooltip.insert(TooltipEntry.component(tooltipComponent), Tooltip.body, TooltipLocator.after(Tooltip.title)));
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

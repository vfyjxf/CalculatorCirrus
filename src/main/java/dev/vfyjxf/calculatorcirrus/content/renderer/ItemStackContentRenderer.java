package dev.vfyjxf.calculatorcirrus.content.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.vfyjxf.calculatorcirrus.api.content.IContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import dev.vfyjxf.calculatorcirrus.helper.LocalizationHelper;
import dev.vfyjxf.calculatorcirrus.utils.ErrorUtils;
import dev.vfyjxf.calculatorcirrus.utils.NumberUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ItemStackContentRenderer implements IContentRenderer<ItemStack> {

    private static final Logger LOGGER = LogManager.getLogger();

    private void renderItemStack(GuiGraphics graphics, ItemStack content, int width, int height, @Nullable String text) {
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        {
            modelViewStack.mul(graphics.pose().last().pose());

            RenderSystem.enableDepthTest();
            graphics.pose().scale(width / 16f, height / 16f, 0f);
            Minecraft minecraft = Minecraft.getInstance();
            graphics.renderFakeItem(content, 0, 0);
            graphics.renderItemDecorations(minecraft.font, content, 0, 0, text);
            RenderSystem.disableBlend();
        }
        modelViewStack.popMatrix();
        // Restore model-view matrix now that the item has been rendered
        RenderSystem.applyModelViewMatrix();
    }

    @Override
    public void render(IContentStack<ItemStack> content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {
        ItemStack itemStack = content.value();
        if (itemStack.isEmpty())
            return;
        int count = itemStack.getCount();
        String amount = count == 1 ? null : NumberUtils.toSuffix(count);
        renderItemStack(graphics, itemStack, width, height, amount);
    }

    @Override
    public void render(ItemStack content, GuiGraphics graphics, int width, int height, int mouseX, int mouseY, float delta) {
        if (content.isEmpty())
            return;
        int count = content.getCount();
        String amount = count == 1 ? null : NumberUtils.toSuffix(count);
        renderItemStack(graphics, content, width, height, amount);
    }

    @Override
    public void render(ItemStack content, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (content.isEmpty())
            return;
        int count = content.getCount();
        String amount = count == 1 ? null : NumberUtils.toSuffix(count);
        renderItemStack(graphics, content, 16, 16, amount);
    }

    @Override
    public @Nullable ITooltip getTooltip(ItemStack content, TooltipContext context) {
        if (content.isEmpty())
            return null;
        ITooltip tooltip = ITooltip.create();
        Optional<TooltipComponent> component = content.getTooltipImage();
        List<Component> components = tooltipHelper(content, context.flag());
        if (!components.isEmpty()) {
            tooltip.add(components.get(0));
        }
        component.ifPresent(tooltip::add);
        for (int i = 1; i < components.size(); i++) {
            tooltip.add(components.get(i));
        }
        return tooltip;
    }

    private static List<Component> tooltipHelper(ItemStack itemStack, TooltipFlag tooltipFlag) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        try {
            return itemStack.getTooltipLines(Item.TooltipContext.of(minecraft.level), player, tooltipFlag);
        } catch (RuntimeException | LinkageError e) {
            String itemStackInfo = ErrorUtils.getItemStackInfo(itemStack);
            LOGGER.error("Failed to get tooltip: {}", itemStackInfo, e);
            List<Component> list = new ArrayList<>();
            MutableComponent crash = LocalizationHelper.translatable("tooltip.error.crash");
            list.add(crash.withStyle(ChatFormatting.RED));
            return list;
        }
    }


}

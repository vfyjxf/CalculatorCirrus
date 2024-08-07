package dev.vfyjxf.calculatorcirrus.utils;

import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.math.Rectangle;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ScreenUtil {

    public static final Rectangle POINT = new Rectangle(0, 0, 0, 0);

    public static double getMouseX() {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouseHelper = minecraft.mouseHandler;
        double scale = (double) minecraft.getWindow().getGuiScaledWidth() / (double) minecraft.getWindow().getScreenWidth();
        return mouseHelper.xpos() * scale;
    }

    public static double getMouseY() {
        Minecraft minecraft = Minecraft.getInstance();
        MouseHandler mouseHelper = minecraft.mouseHandler;
        double scale = (double) minecraft.getWindow().getGuiScaledHeight() / (double) minecraft.getWindow().getScreenHeight();
        return mouseHelper.ypos() * scale;
    }

    public static Point ofMouse() {
        return new Point((int) getMouseX(), (int) getMouseY());
    }

    public static void renderTooltip(Screen screen, GuiGraphics graphics, ITooltip tooltip, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        {
            TooltipStack<?> stack = tooltip.stack();
            ItemStack itemStack = (stack != null && stack.type() == ItemStack.class) ? stack.castValue() : ItemStack.EMPTY;
            Font font = Minecraft.getInstance().font;
            if (!itemStack.isEmpty()) {
                font = ClientHooks.getTooltipFont(itemStack, font);
            }
            graphics.renderComponentTooltipFromElements(font, tooltip.guiEntries(), mouseX, mouseY, itemStack);
        }
        graphics.pose().popPose();
    }

}

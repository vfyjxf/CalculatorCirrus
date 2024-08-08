package dev.vfyjxf.calculatorcirrus.utils;

import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.math.Rectangle;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;

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

    public static void renderTooltip(Screen screen, GuiGraphics graphics, Tooltip tooltip, int mouseX, int mouseY) {
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

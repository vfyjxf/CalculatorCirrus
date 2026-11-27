package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

@SuppressWarnings({"rawtypes", "unchecked"})
class EmptyStackRenderer implements StackRenderer {

    private static final EmptyStackRenderer instance = new EmptyStackRenderer();

    static <T, S> StackRenderer<T, S> castStackRenderer() {
        return instance;
    }

    private EmptyStackRenderer() {}

    @Override
    public void renderBase(GuiGraphics graphics, Object stack, int width, int height, float delta) {

    }

    @Override
    public void decorate(GuiGraphics graphics, Object stack, int width, int height, float delta) {

    }

    @Override
    public Font getFont(Object stack, Minecraft minecraft) {
        return minecraft.font;
    }

    @Override
    public Tooltip getTooltip(Object stack, TooltipContext context) {
        return Tooltip.create();
    }

    @Override
    public Component getName(Object stack) {
        return Component.empty();
    }

}

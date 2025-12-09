package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface StackRenderer<T, S> {

    static <T, S> StackRenderer<T, S> getStackRender(StackType<T, S> stackType) {
        return ClientContentApiImpl.instance().getStackRenderer(stackType);
    }

    default void render(GuiGraphics graphics, S stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    default void render(GuiGraphics graphics, ContentStack<T, S> stack, int width, int height, float delta) {
        this.render(graphics, stack.value(), width, height, delta);
    }

    //region base

    void renderBase(GuiGraphics graphics, S stack, int width, int height, float delta);

    default void renderBase(GuiGraphics graphics, ContentStack<T, S> stack, int width, int height, float delta) {
        renderBase(graphics, stack.value(), width, height, delta);
    }

    void decorate(GuiGraphics graphics, S stack, int width, int height, float delta);

    default void decorate(GuiGraphics graphics, ContentStack<T, S> stack, int width, int height, float delta) {
        this.decorate(graphics, stack.value(), width, height, delta);
    }

    //endregion


    Font getFont(S stack, Minecraft minecraft);

    RichTooltip getTooltip(S stack, TooltipContext context);

    Component getName(S stack);

    String getDisplayName(S stack);

}

package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface StackRenderer<T, S> {

    static <T, S> StackRenderer<T, S> getStackRenderer(StackType<T, S> stackType) {
        return ClientContentApiImpl.instance().getStackRenderer(stackType);
    }

    static <T, S> StackRenderer<T, S> emptyRenderer() {
        return EmptyStackRenderer.castStackRenderer();
    }

    static <T, S> StackRenderer<T, S> getStackRenderer(ContentStack<T, S> stack) {
        return stack.isEmpty() ? emptyRenderer() : getStackRenderer(stack.type());
    }

    default void render(GuiGraphics graphics, S stack, int width, int height, float delta) {
        renderBase(graphics, stack, width, height, delta);
        decorate(graphics, stack, width, height, delta);
    }

    default void renderAt(GuiGraphics graphics, S stack, int x, int y, int width, int height, float delta) {
        renderBaseAt(graphics, stack, x, y, width, height, delta);
        decorateAt(graphics, stack, x, y, width, height, delta);
    }

    default void render(GuiGraphics graphics, ContentStack<T, S> stack, int width, int height, float delta) {
        this.render(graphics, stack.value(), width, height, delta);
    }

    default void renderAt(
        GuiGraphics graphics,
        ContentStack<T, S> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        this.renderAt(graphics, stack.value(), x, y, width, height, delta);
    }

    //region base

    void renderBase(GuiGraphics graphics, S stack, int width, int height, float delta);

    default void renderBaseAt(GuiGraphics graphics, S stack, int x, int y, int width, int height, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        renderBase(graphics, stack, width, height, delta);
        graphics.pose().popPose();
    }

    default void renderBase(GuiGraphics graphics, ContentStack<T, S> stack, int width, int height, float delta) {
        renderBase(graphics, stack.value(), width, height, delta);
    }

    default void renderBaseAt(
        GuiGraphics graphics,
        ContentStack<T, S> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        renderBaseAt(graphics, stack.value(), x, y, width, height, delta);
    }

    void decorate(GuiGraphics graphics, S stack, int width, int height, float delta);

    default void decorateAt(GuiGraphics graphics, S stack, int x, int y, int width, int height, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        decorate(graphics, stack, width, height, delta);
        graphics.pose().popPose();
    }

    default void decorate(GuiGraphics graphics, ContentStack<T, S> stack, int width, int height, float delta) {
        this.decorate(graphics, stack.value(), width, height, delta);
    }

    default void decorateAt(
        GuiGraphics graphics,
        ContentStack<T, S> stack,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        this.decorateAt(graphics, stack.value(), x, y, width, height, delta);
    }

    //endregion

    Font getFont(S stack, Minecraft minecraft);

    Tooltip getTooltip(S stack, TooltipContext context);

    Component getName(S stack);

}

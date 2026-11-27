package dev.vfyjxf.cirrus.ui.widget.content;

import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;

public class StackWidget<T, S> extends Widget {

    private ContentStack<T, S> stack;
    private StackRenderer<T, S> renderer;

    public StackWidget(ContentStack<T, S> stack) {
        this.stack = stack;
        this.renderer = StackRenderer.getStackRenderer(stack);
    }

    public ContentStack<T, S> stack() {
        return stack;
    }

    public StackRenderer<T, S> renderer() {
        return renderer;
    }

    public void setStack(ContentStack<T, S> stack) {
        this.stack = stack;
        this.renderer = StackRenderer.getStackRenderer(stack);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        if (stack.isEmpty()) return;
        renderer.render(canvas.layeredGraphics(), stack, width(), height(), partialTicks);
    }
}

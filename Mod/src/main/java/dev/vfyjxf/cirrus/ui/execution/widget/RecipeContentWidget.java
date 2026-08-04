package dev.vfyjxf.cirrus.ui.execution.widget;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import org.jetbrains.annotations.Nullable;

public class RecipeContentWidget extends Widget {

    private RecipeContent content = RecipeContent.empty();
    private int visualIndex;

    public RecipeContentWidget() {
    }

    public void setContent(@Nullable RecipeContent content, int visualIndex) {
        this.content = content == null ? RecipeContent.empty() : content;
        this.visualIndex = Math.max(0, visualIndex);
    }

    public RecipeContent content() {
        return content;
    }

    public int visualIndex() {
        return visualIndex;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        if (content.isEmpty()) {
            return;
        }
        RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer.getRenderer(content);
        if (renderer.visualCount(content) <= 0) {
            return;
        }
        renderer.render(canvas.layeredGraphics(), content, visualIndex, width(), height(), partialTicks);
    }
}

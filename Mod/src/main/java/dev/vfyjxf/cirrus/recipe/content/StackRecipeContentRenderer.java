package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class StackRecipeContentRenderer implements BatchableRecipeContentRenderer<StackContent<?, ?>> {

    public static final StackRecipeContentRenderer instance = new StackRecipeContentRenderer();

    private StackRecipeContentRenderer() {
    }

    @Override
    public int visualCount(StackContent<?, ?> content) {
        return content.notEmpty() ? 1 : 0;
    }

    @Override
    public void render(GuiGraphics graphics, StackContent<?, ?> content, int visualIndex, int width, int height, float delta) {
        renderAt(graphics, content, visualIndex, 0, 0, width, height, delta);
    }

    @Override
    public void renderAt(GuiGraphics graphics, StackContent<?, ?> content, int visualIndex, int x, int y, int width, int height, float delta) {
        RecipeContentRenderSupport.renderBaseStackAt(graphics, content.content(), x, y, width, height, delta);
    }

    @Override
    public void renderDecorated(GuiGraphics graphics, StackContent<?, ?> content, int visualIndex, int width, int height, float delta) {
        renderDecoratedAt(graphics, content, visualIndex, 0, 0, width, height, delta);
    }

    @Override
    public void renderDecoratedAt(GuiGraphics graphics, StackContent<?, ?> content, int visualIndex, int x, int y, int width, int height, float delta) {
        RecipeContentRenderSupport.renderStackAt(
            graphics,
            RecipeContentRenderSupport.stackForTooltipDisplay(content, content.content()),
            x,
            y,
            width,
            height,
            delta
        );
    }

    @Override
    public void submit(Batch batch, StackContent<?, ?> content, int visualIndex, int x, int y, int width, int height) {
        RecipeContentRenderSupport.submitBaseStack(batch, content.content(), x, y, width, height);
    }

    @Override
    public void submitDecorated(Batch batch, StackContent<?, ?> content, int visualIndex, int x, int y, int width, int height) {
        RecipeContentRenderSupport.submitStack(
            batch,
            RecipeContentRenderSupport.stackForTooltipDisplay(content, content.content()),
            x,
            y,
            width,
            height
        );
    }

    @Override
    public Tooltip tooltip(StackContent<?, ?> content, int visualIndex, TooltipContext context) {
        return RecipeContentRenderSupport.tooltipForStack(
            RecipeContentRenderSupport.stackForTooltipDisplay(content, content.content()),
            context
        );
    }

    @Override
    public TooltipStack<?> tooltipStack(StackContent<?, ?> content, int visualIndex) {
        return RecipeContentRenderSupport.tooltipStackForStack(
            RecipeContentRenderSupport.stackForTooltipDisplay(content, content.content())
        );
    }

    @Override
    public Component getName(StackContent<?, ?> content) {
        ContentStack<?, ?> stack = content.content();
        return Component.literal(RecipeContentRenderSupport.stackLabel(stack));
    }
}

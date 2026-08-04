package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class TagStackRecipeContentRenderer implements BatchableRecipeContentRenderer<TagStackContent<?>> {

    public static final TagStackRecipeContentRenderer instance = new TagStackRecipeContentRenderer();

    private TagStackRecipeContentRenderer() {
    }

    @Override
    public int visualCount(TagStackContent<?> content) {
        return RecipeContentRenderSupport.enumeratedVisuals(content).size();
    }

    @Override
    public void render(GuiGraphics graphics, TagStackContent<?> content, int visualIndex, int width, int height, float delta) {
        renderAt(graphics, content, visualIndex, 0, 0, width, height, delta);
    }

    @Override
    public void renderAt(GuiGraphics graphics, TagStackContent<?> content, int visualIndex, int x, int y, int width, int height, float delta) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport.visualStack(
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        if (stack.notEmpty()) {
            RecipeContentRenderSupport.renderBaseStackAt(graphics, stack, x, y, width, height, delta);
        }
    }

    @Override
    public void renderDecorated(GuiGraphics graphics, TagStackContent<?> content, int visualIndex, int width, int height, float delta) {
        renderDecoratedAt(graphics, content, visualIndex, 0, 0, width, height, delta);
    }

    @Override
    public void renderDecoratedAt(GuiGraphics graphics, TagStackContent<?> content, int visualIndex, int x, int y, int width, int height, float delta) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport.stackForTooltipDisplay(
            content,
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        RecipeContentRenderSupport.renderStackAt(graphics, stack, x, y, width, height, delta);
    }

    @Override
    public void submit(Batch batch, TagStackContent<?> content, int visualIndex, int x, int y, int width, int height) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport.visualStack(
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        if (stack.notEmpty()) {
            RecipeContentRenderSupport.submitBaseStack(batch, stack, x, y, width, height);
        }
    }

    @Override
    public void submitDecorated(Batch batch, TagStackContent<?> content, int visualIndex, int x, int y, int width, int height) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport.stackForTooltipDisplay(
            content,
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        RecipeContentRenderSupport.submitStack(batch, stack, x, y, width, height);
    }

    @Override
    public Tooltip tooltip(TagStackContent<?> content, int visualIndex, TooltipContext context) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport.stackForTooltipDisplay(
            content,
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        return stack.isEmpty() ? null : RecipeContentRenderSupport.tooltipForStack(stack, context);
    }

    @Override
    public TooltipStack<?> tooltipStack(TagStackContent<?> content, int visualIndex) {
        return RecipeContentRenderSupport.tooltipStackForStack(
            RecipeContentRenderSupport.stackForTooltipDisplay(
                content,
                RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
            )
        );
    }

    @Override
    public Component getName(TagStackContent<?> content) {
        return Component.literal("#" + content.tagKey().location());
    }
}

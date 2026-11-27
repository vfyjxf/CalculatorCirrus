package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class TypedStackListRecipeContentRenderer
        implements
            BatchableRecipeContentRenderer<TypedStackListContent<?, ?>> {

    public static final TypedStackListRecipeContentRenderer instance = new TypedStackListRecipeContentRenderer();

    private TypedStackListRecipeContentRenderer() {}

    @Override
    public int visualCount(TypedStackListContent<?, ?> content) {
        return content.stacks().size();
    }

    @Override
    public void render(
        GuiGraphics graphics,
        TypedStackListContent<?, ?> content,
        int visualIndex,
        int width,
        int height,
        float delta
    ) {
        renderAt(graphics, content, visualIndex, 0, 0, width, height, delta);
    }

    @Override
    public void renderAt(
        GuiGraphics graphics,
        TypedStackListContent<?, ?> content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport
                .visualStack(RecipeContentRenderSupport.visualAt(content.stacks(), visualIndex));
        if (stack.notEmpty()) {
            RecipeContentRenderSupport.renderBaseStackAt(graphics, stack, x, y, width, height, delta);
        }
    }

    @Override
    public void renderDecorated(
        GuiGraphics graphics,
        TypedStackListContent<?, ?> content,
        int visualIndex,
        int width,
        int height,
        float delta
    ) {
        renderDecoratedAt(graphics, content, visualIndex, 0, 0, width, height, delta);
    }

    @Override
    public void renderDecoratedAt(
        GuiGraphics graphics,
        TypedStackListContent<?, ?> content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport
                .stackForTooltipDisplay(content, RecipeContentRenderSupport.visualAt(content.stacks(), visualIndex));
        RecipeContentRenderSupport.renderStackAt(graphics, stack, x, y, width, height, delta);
    }

    @Override
    public void submit(
        Batch batch,
        TypedStackListContent<?, ?> content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height
    ) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport
                .visualStack(RecipeContentRenderSupport.visualAt(content.stacks(), visualIndex));
        if (stack.notEmpty()) {
            RecipeContentRenderSupport.submitBaseStack(batch, stack, x, y, width, height);
        }
    }

    @Override
    public void submitDecorated(
        Batch batch,
        TypedStackListContent<?, ?> content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height
    ) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport
                .stackForTooltipDisplay(content, RecipeContentRenderSupport.visualAt(content.stacks(), visualIndex));
        RecipeContentRenderSupport.submitStack(batch, stack, x, y, width, height);
    }

    @Override
    public @Nullable Tooltip tooltip(TypedStackListContent<?, ?> content, int visualIndex, TooltipContext context) {
        ContentStack<?, ?> stack = RecipeContentRenderSupport
                .stackForTooltipDisplay(content, RecipeContentRenderSupport.visualAt(content.stacks(), visualIndex));
        return stack.isEmpty() ? null : RecipeContentRenderSupport.tooltipForStack(stack, context);
    }

    @Override
    public @Nullable TooltipStack<?> tooltipStack(TypedStackListContent<?, ?> content, int visualIndex) {
        return RecipeContentRenderSupport.tooltipStackForStack(
            RecipeContentRenderSupport
                    .stackForTooltipDisplay(content, RecipeContentRenderSupport.visualAt(content.stacks(), visualIndex))
        );
    }

    @Override
    public Component getName(TypedStackListContent<?, ?> content) {
        return Component.literal(RecipeContentRenderSupport.labelForStacks(content.stacks()));
    }
}

package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.FluidContainerContent;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ComplexRecipeContentRenderer implements BatchableRecipeContentRenderer<ComplexContent> {

    public static final ComplexRecipeContentRenderer instance = new ComplexRecipeContentRenderer();

    private ComplexRecipeContentRenderer() {}

    @Override
    public int visualCount(ComplexContent content) {
        if (content instanceof FluidContainerContent fluidContainer && fluidContainer.sampleContainer().notEmpty()) {
            return 1;
        }
        return RecipeContentRenderSupport.enumeratedVisuals(content).size();
    }

    @Override
    public void render(
        GuiGraphics graphics,
        ComplexContent content,
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
        ComplexContent content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        if (content instanceof FluidContainerContent fluidContainer) {
            RecipeContentRenderSupport
                    .renderBaseStackAt(graphics, fluidContainer.sampleContainer(), x, y, width, height, delta);
            return;
        }
        ContentStack<?, ?> stack = RecipeContentRenderSupport.visualStack(
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        if (stack.notEmpty()) {
            RecipeContentRenderSupport.renderBaseStackAt(graphics, stack, x, y, width, height, delta);
        }
    }

    @Override
    public void renderDecorated(
        GuiGraphics graphics,
        ComplexContent content,
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
        ComplexContent content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height,
        float delta
    ) {
        if (content instanceof FluidContainerContent fluidContainer) {
            RecipeContentRenderSupport.renderStackAt(
                graphics,
                fluidContainer.sampleContainer().copyWithAmount(content.amount()),
                x,
                y,
                width,
                height,
                delta
            );
            return;
        }
        ContentStack<?, ?> stack = RecipeContentRenderSupport.stackForTooltipDisplay(
            content,
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        RecipeContentRenderSupport.renderStackAt(graphics, stack, x, y, width, height, delta);
    }

    @Override
    public void submit(Batch batch, ComplexContent content, int visualIndex, int x, int y, int width, int height) {
        if (content instanceof FluidContainerContent fluidContainer) {
            RecipeContentRenderSupport.submitBaseStack(batch, fluidContainer.sampleContainer(), x, y, width, height);
            return;
        }
        ContentStack<?, ?> stack = RecipeContentRenderSupport.visualStack(
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        if (stack.notEmpty()) {
            RecipeContentRenderSupport.submitBaseStack(batch, stack, x, y, width, height);
        }
    }

    @Override
    public void submitDecorated(
        Batch batch,
        ComplexContent content,
        int visualIndex,
        int x,
        int y,
        int width,
        int height
    ) {
        if (content instanceof FluidContainerContent fluidContainer) {
            RecipeContentRenderSupport.submitStack(
                batch,
                fluidContainer.sampleContainer().copyWithAmount(content.amount()),
                x,
                y,
                width,
                height
            );
            return;
        }
        ContentStack<?, ?> stack = RecipeContentRenderSupport.stackForTooltipDisplay(
            content,
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        RecipeContentRenderSupport.submitStack(batch, stack, x, y, width, height);
    }

    @Override
    public @Nullable Tooltip tooltip(ComplexContent content, int visualIndex, TooltipContext context) {
        if (content instanceof FluidContainerContent fluidContainer) {
            Tooltip tooltip = RecipeContentRenderSupport
                    .tooltipForStack(fluidContainer.sampleContainer().copyWithAmount(content.amount()), context);
            if (tooltip == null) {
                tooltip = Tooltip.create();
            }
            tooltip.add(CirrusLangs.Content.fluidContainer.component(), Tooltip.title);
            tooltip.add(CirrusLangs.Content.fluidContainerDesc.component());
            for (ContentStack<?, ?> required : fluidContainer.requiredFluids()) {
                tooltip.add(
                    Component.literal(
                        RecipeContentRenderSupport.stackLabel(required.copyWithAmount(Math.max(1L, required.amount())))
                    )
                );
            }
            return tooltip;
        }
        ContentStack<?, ?> stack = RecipeContentRenderSupport.stackForTooltipDisplay(
            content,
            RecipeContentRenderSupport.visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
        );
        return stack.isEmpty() ? null : RecipeContentRenderSupport.tooltipForStack(stack, context);
    }

    @Override
    public @Nullable TooltipStack<?> tooltipStack(ComplexContent content, int visualIndex) {
        if (content instanceof FluidContainerContent fluidContainer) {
            return RecipeContentRenderSupport
                    .tooltipStackForStack(fluidContainer.sampleContainer().copyWithAmount(content.amount()));
        }
        return RecipeContentRenderSupport
                .tooltipStackForStack(
                    RecipeContentRenderSupport.stackForTooltipDisplay(
                        content,
                        RecipeContentRenderSupport
                                .visualAt(RecipeContentRenderSupport.enumeratedVisuals(content), visualIndex)
                    )
                );
    }

    @Override
    public Component getName(ComplexContent content) {
        if (content instanceof FluidContainerContent) {
            return CirrusLangs.Content.fluidContainer.component();
        }
        return Component.literal(RecipeContentRenderSupport.labelForEnumeratedInput(content));
    }
}

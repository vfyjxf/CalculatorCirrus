package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererApi;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public interface RecipeContentRenderer<C extends RecipeContent> {

    static <C extends RecipeContent> RecipeContentRenderer<C> getRenderer(RecipeContentType<C> type) {
        Checks.checkNotNull(type, "type");
        return RecipeContentRendererApi.instance().getRenderer(type);
    }

    @SuppressWarnings("unchecked")
    static <C extends RecipeContent> RecipeContentRenderer<C> getRenderer(C content) {
        Checks.checkNotNull(content, "content");
        return (RecipeContentRenderer<C>) getRenderer((RecipeContentType<? extends RecipeContent>) content.recipeType());
    }

    default int visualCount(C content) {
        return 0;
    }

    void render(GuiGraphics graphics, C content, int visualIndex, int width, int height, float delta);

    default void render(GuiGraphics graphics, C content, int width, int height, float delta) {
        render(graphics, content, 0, width, height, delta);
    }

    default void renderAt(GuiGraphics graphics, C content, int visualIndex, int x, int y, int width, int height, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        render(graphics, content, visualIndex, width, height, delta);
        graphics.pose().popPose();
    }

    default void renderDecorated(GuiGraphics graphics, C content, int visualIndex, int width, int height, float delta) {
        render(graphics, content, visualIndex, width, height, delta);
    }

    default void renderDecoratedAt(GuiGraphics graphics, C content, int visualIndex, int x, int y, int width, int height, float delta) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        renderDecorated(graphics, content, visualIndex, width, height, delta);
        graphics.pose().popPose();
    }

    default @Nullable Tooltip tooltip(C content, int visualIndex, TooltipContext context) {
        return null;
    }

    default @Nullable Tooltip tooltip(C content, TooltipContext context) {
        return tooltip(content, 0, context);
    }

    default @Nullable TooltipStack<?> tooltipStack(C content, int visualIndex) {
        return null;
    }

    default @Nullable TooltipStack<?> tooltipStack(C content) {
        return tooltipStack(content, 0);
    }

    Component getName(C content);
}

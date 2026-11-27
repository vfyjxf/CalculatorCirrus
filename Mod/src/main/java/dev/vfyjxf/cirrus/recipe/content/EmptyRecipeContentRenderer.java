package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class EmptyRecipeContentRenderer implements BatchableRecipeContentRenderer<EmptyRecipeContent> {

    public static final EmptyRecipeContentRenderer instance = new EmptyRecipeContentRenderer();

    private EmptyRecipeContentRenderer() {}

    @Override
    public void render(
        GuiGraphics graphics,
        EmptyRecipeContent content,
        int visualIndex,
        int width,
        int height,
        float delta
    ) {}

    @Override
    public Component getName(EmptyRecipeContent content) {
        return Component.literal("-");
    }
}

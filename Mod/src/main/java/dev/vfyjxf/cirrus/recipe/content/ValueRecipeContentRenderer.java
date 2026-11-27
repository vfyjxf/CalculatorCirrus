package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class ValueRecipeContentRenderer implements BatchableRecipeContentRenderer<ValueContent<?>> {

    public static final ValueRecipeContentRenderer instance = new ValueRecipeContentRenderer();

    private ValueRecipeContentRenderer() {}

    @Override
    public void render(
        GuiGraphics graphics,
        ValueContent<?> content,
        int visualIndex,
        int width,
        int height,
        float delta
    ) {}

    @Override
    public Component getName(ValueContent<?> content) {
        return Component.literal(RecipeContentRenderSupport.describeValue(content.content()));
    }
}

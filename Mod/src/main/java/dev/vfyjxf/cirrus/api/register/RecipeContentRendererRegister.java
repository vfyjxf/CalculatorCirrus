package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;

public interface RecipeContentRendererRegister {

    <C extends RecipeContent> void registerRenderer(RecipeContentType<C> type, RecipeContentRenderer<C> renderer);

}

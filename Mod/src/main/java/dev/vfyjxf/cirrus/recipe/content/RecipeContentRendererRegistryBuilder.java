package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.register.RecipeContentRendererRegister;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.concurrent.ConcurrentHashMap;

public final class RecipeContentRendererRegistryBuilder implements RecipeContentRendererRegister {

    final ConcurrentHashMap<RecipeContentType<?>, RecipeContentRenderer<?>> renderers = new ConcurrentHashMap<>();

    @Override
    public <C extends RecipeContent> void registerRenderer(RecipeContentType<C> type, RecipeContentRenderer<C> renderer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(renderer, "renderer");
        if (renderers.putIfAbsent(type, renderer) != null) {
            throw new IllegalStateException("Recipe content renderer already registered for type: " + type.typeId());
        }
    }
}

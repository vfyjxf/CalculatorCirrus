package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class RecipeContentRendererApi {

    private static @Nullable RecipeContentRendererApi instance;

    public static RecipeContentRendererApi instance() {
        RecipeContentRendererApi current = instance;
        if (current == null) {
            throw new IllegalStateException("RecipeContentRendererApi is not initialized");
        }
        return current;
    }

    public static void attach(RecipeContentRendererApi api) {
        instance = Checks.checkNotNull(api, "api");
    }

    private final Map<RecipeContentType<?>, RecipeContentRenderer<?>> renderers;

    public RecipeContentRendererApi(RecipeContentRendererRegistryBuilder builder) {
        this.renderers = new HashMap<>(builder.renderers);
    }

    @SuppressWarnings("unchecked")
    public <C extends RecipeContent> RecipeContentRenderer<C> getRenderer(RecipeContentType<C> type) {
        Checks.checkNotNull(type, "type");
        RecipeContentRenderer<C> renderer = (RecipeContentRenderer<C>) renderers.get(type);
        if (renderer == null) {
            throw new IllegalStateException("No recipe content renderer found for type: " + type.typeId());
        }
        return renderer;
    }
}

package dev.vfyjxf.cirrus.recipe.content;

import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;

public final class RecipeContentRendererTestBootstrap {

    private static volatile boolean initialized;

    private RecipeContentRendererTestBootstrap() {}

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (RecipeContentRendererTestBootstrap.class) {
            if (initialized) {
                return;
            }
            RecipeContentRendererRegistryBuilder builder = new RecipeContentRendererRegistryBuilder();
            registerBuiltins(builder);
            RecipeContentRendererApi.attach(new RecipeContentRendererApi(builder));
            initialized = true;
        }
    }

    public static void registerBuiltins(RecipeContentRendererRegistryBuilder builder) {
        builder.registerRenderer(RecipeContentType.empty, EmptyRecipeContentRenderer.instance);
        builder.registerRenderer(RecipeContentType.STACK, StackRecipeContentRenderer.instance);
        builder.registerRenderer(RecipeContentType.value, ValueRecipeContentRenderer.instance);
        builder.registerRenderer(RecipeContentType.TAG_STACK, TagStackRecipeContentRenderer.instance);
        builder.registerRenderer(RecipeContentType.STACK_LIST, StackListRecipeContentRenderer.instance);
        builder.registerRenderer(RecipeContentType.TYPED_STACK_LIST, TypedStackListRecipeContentRenderer.instance);
        builder.registerRenderer(RecipeContentType.COMPLEX, ComplexRecipeContentRenderer.instance);
    }
}

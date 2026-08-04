package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererApi;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererRegistryBuilder;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeContentRendererRegistryTest {

    @BeforeAll
    static void bootstrapDefaults() {
        RecipeContentRendererTestBootstrap.ensureInitialized();
    }

    @Test
    void duplicateRegistrationIsRejected() {
        RecipeContentRendererRegistryBuilder builder = new RecipeContentRendererRegistryBuilder();

        RecipeContentRendererTestBootstrap.registerBuiltins(builder);

        assertThrows(IllegalStateException.class, () -> RecipeContentRendererTestBootstrap.registerBuiltins(builder));
    }

    @Test
    void unregisteredTypeFailsLookup() {
        RecipeContentRendererRegistryBuilder builder = new RecipeContentRendererRegistryBuilder();
        RecipeContentRendererApi.attach(new RecipeContentRendererApi(builder));
        RecipeContentType<EmptyRecipeContent> customType = () -> dev.vfyjxf.cloudlib.api.util.Namespace.of("test", "missing");

        assertThrows(IllegalStateException.class, () -> RecipeContentRenderer.getRenderer(customType));

        RecipeContentRendererRegistryBuilder defaults = new RecipeContentRendererRegistryBuilder();
        RecipeContentRendererTestBootstrap.registerBuiltins(defaults);
        RecipeContentRendererApi.attach(new RecipeContentRendererApi(defaults));
    }

    @Test
    void registeredEmptyRendererProvidesFallbackName() {
        RecipeContentRenderer<EmptyRecipeContent> renderer = RecipeContentRenderer.getRenderer(RecipeContentType.EMPTY);

        assertEquals("-", renderer.getName(RecipeContent.empty()).getString());
    }
}

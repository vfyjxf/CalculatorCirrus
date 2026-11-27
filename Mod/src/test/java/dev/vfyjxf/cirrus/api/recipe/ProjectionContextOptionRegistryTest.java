package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.context.LongProjectionContextOption;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionContextOptionRegistryTest {

    @Test
    void recipeApiExposesProjectionContextOptionsForPreviewUi() {
        ExecutionContextKey<Long> key = ExecutionContextKey
                .create(ResourceLocation.fromNamespaceAndPath("test", "projection_context/overclock"), Long.class, 1L);
        LongProjectionContextOption option = new LongProjectionContextOption(
            Namespace.of("test", "projection_context/overclock"),
            ProjectionPlugin.builtin,
            key,
            "test.projection_context.overclock",
            "test.projection_context.overclock.desc",
            1L,
            16L,
            1L
        );

        RecipeApiRegistryBuilder builder = new RecipeApiRegistryBuilder();
        builder.registerProjectionContextOption(option);
        RecipeApi api = new RecipeApiImpl(builder);

        assertSame(ProjectionPlugin.builtin, api.findProjectionPlugin(ProjectionPlugin.builtin.id()));
        assertSame(option, api.findProjectionContextOption(option.id()));
        assertTrue(api.projectionContextOptions().anySatisfy(option::equals));
        assertEquals(16L, option.normalize(99L));
        assertEquals(1L, option.normalize(-4L));
    }
}

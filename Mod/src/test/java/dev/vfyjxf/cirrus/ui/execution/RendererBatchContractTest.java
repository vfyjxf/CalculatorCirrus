package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.content.render.BatchableStackRenderer;
import dev.vfyjxf.cirrus.api.recipe.RecipeContentType;
import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.content.renderer.FluidStackRenderer;
import dev.vfyjxf.cirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RendererBatchContractTest {

    @BeforeAll
    static void bootstrapDefaults() {
        RecipeContentRendererTestBootstrap.ensureInitialized();
    }

    @Test
    void builtinStackRenderersExposeBatchableApi() {
        assertTrue(ItemStackRenderer.instance instanceof BatchableStackRenderer);
        assertTrue(FluidStackRenderer.instance instanceof BatchableStackRenderer);
    }

    @Test
    void itemStackAmountDecorationOmitsSingleCounts() {
        assertEquals("", ItemStackRenderer.countDecorationText(0));
        assertEquals("", ItemStackRenderer.countDecorationText(1));
        assertEquals("2", ItemStackRenderer.countDecorationText(2));
    }

    @Test
    void builtinRecipeContentRenderersExposeBatchableApi() {
        assertBatchable(RecipeContentType.EMPTY);
        assertBatchable(RecipeContentType.STACK);
        assertBatchable(RecipeContentType.VALUE);
        assertBatchable(RecipeContentType.TAG_STACK);
        assertBatchable(RecipeContentType.STACK_LIST);
        assertBatchable(RecipeContentType.TYPED_STACK_LIST);
        assertBatchable(RecipeContentType.COMPLEX);
    }

    private static void assertBatchable(RecipeContentType<?> type) {
        assertTrue(RecipeContentRenderer.getRenderer(type) instanceof BatchableRecipeContentRenderer);
    }
}

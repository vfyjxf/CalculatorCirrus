package dev.vfyjxf.cirrus.ui.execution;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.recipe.content.RecipeContentRendererTestBootstrap;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeContentRendererTest {

    @BeforeAll
    static void bootstrapRenderers() {
        RecipeContentRendererTestBootstrap.ensureInitialized();
    }

    @Test
    void tagInputKeepsTagSemanticsInsteadOfPickingRandomItem(MinecraftServer server) {
        InputContent input = InputContent.fromItemTag(ItemTags.PLANKS, 2);
        RecipeContentRenderer<InputContent> renderer = RecipeContentRenderer.getRenderer(input);

        assertTrue(input.preserveOriginalShortageSemantics());
        assertTrue(renderer.getName(input).getString().startsWith("#"));
        assertTrue(renderer.visualCount(input) > 1);
        assertTrue(ExecutionOverlayContentHelper.stackFromInputContent(input).isEmpty());
    }

    @Test
    void stackListInputKeepsAlternativesInsteadOfPickingFirstItem(MinecraftServer server) {
        InputContent input = InputContent.fromTypedStackList(
            CirrusTypes.Contents.ITEM_STACK,
            List.of(Items.OAK_PLANKS.getDefaultInstance(), Items.BIRCH_PLANKS.getDefaultInstance()),
            1
        );
        RecipeContentRenderer<InputContent> renderer = RecipeContentRenderer.getRenderer(input);

        assertTrue(input.preserveOriginalShortageSemantics());
        assertEquals(2, renderer.visualCount(input));
        assertTrue(ExecutionOverlayContentHelper.stackFromInputContent(input).isEmpty());
    }

    @Test
    void rendererLookupUsesRegisteredDefaults(MinecraftServer server) {
        InputContent input = InputContent.fromItemStack(Items.STICK.getDefaultInstance(), 1);

        RecipeContentRenderer<InputContent> renderer = RecipeContentRenderer.getRenderer(input);

        assertEquals("Stick", renderer.getName(input).getString());
        assertEquals(1, renderer.visualCount(input));
    }
}

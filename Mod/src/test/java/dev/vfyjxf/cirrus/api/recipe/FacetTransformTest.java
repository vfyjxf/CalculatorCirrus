package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetedContent;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.TransformedView;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformPipeline;
import dev.vfyjxf.cirrus.recipe.facet.UnwrapFluidHandlerFacet;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.list.MutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(EphemeralTestServerProvider.class)
public class FacetTransformTest {

    @Test
    void testFluidHandlerUnwrap(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        RecipeView raw = new TestRecipeBuilder()
            .input(InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)), UnwrapFluidHandlerFacet.instance)
            .build();

        var pipeline = new ViewTransformPipeline();
        pipeline.detectFacet(CirrusTypes.Facets.UNWRAP_FLUID_HANDLER);
        TransformedView transformed = pipeline.transformView(new RecipeContext(), raw);
        System.out.println(transformed);
    }

    private static class TestRecipeBuilder {
        private final MutableList<FacetedContent<InputContent>> inputs = MutableLists.empty();
        private final MutableList<FacetedContent<OutputContent>> outputs = MutableLists.empty();

        public TestRecipeBuilder input(InputContent content) {
            inputs.add(FacetedContent.from(content));
            return this;
        }

        public TestRecipeBuilder output(OutputContent content) {
            outputs.add(FacetedContent.from(content));
            return this;
        }

        public TestRecipeBuilder input(InputContent content, RecipeFacet... facets) {
            FacetedContent<InputContent> from = FacetedContent.from(content);
            for (RecipeFacet facet : facets) from.facets().addFacet(facet);
            inputs.add(from);
            return this;
        }

        public TestRecipeBuilder output(OutputContent content, RecipeFacet... facets) {
            FacetedContent<OutputContent> from = FacetedContent.from(content);
            for (RecipeFacet facet : facets) from.facets().addFacet(facet);
            outputs.add(from);
            return this;
        }

        public RecipeView build() {
            return new RecipeView(null, inputs.toImmutable(), outputs.toImmutable());
        }

    }

}

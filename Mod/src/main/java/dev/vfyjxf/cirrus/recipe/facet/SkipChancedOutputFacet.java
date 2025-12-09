package dev.vfyjxf.cirrus.recipe.facet;

import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public class SkipChancedOutputFacet implements RecipeFacet {

    public static final Namespace id = CirrusNamespaces.ofMod("content/skip_chanced_output");
    public static final RecipeFacetType type = new RecipeFacetType(id, (recipe, content) -> !content.constantly());
    public static final SkipChancedOutputFacet instance = new SkipChancedOutputFacet();
    public static final ViewTransformer<SkipChancedOutputFacet> viewTransformer = ViewTransformer.of(
        (context, view) -> view.outputs().anySatisfy(content -> !content.constantly()),
        (context, view, facet, increment, decrement) -> {
            for (var output : view.outputs()) {
                if (!output.constantly()) {
                    decrement.rejectOutput(output.content());
                }
            }
        }
    );

    private SkipChancedOutputFacet() {}

    @Override
    public Namespace id() {
        return id;
    }
}

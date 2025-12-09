package dev.vfyjxf.cirrus.api.recipe.view;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetMap;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetedContent;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collector.Collectors2;

import java.util.Collection;

public record RecipeView(
    RecipeEntry<?> recipe,
    ImmutableList<FacetedContent<InputContent>> inputs,
    ImmutableList<FacetedContent<OutputContent>> outputs,
    FacetMap facets
) {

    //region factory

    public static <R> RecipeView create(
        RecipeSystem<R> system, R recipe,
        Collection<? extends InputContent> inputs,
        Collection<? extends OutputContent> outputs
    ) {
        Checks.checkNotNull(system, "system");
        Checks.checkNotNull(recipe, "recipe");
        return create(RecipeEntry.of(system, recipe), inputs, outputs);
    }

    public static <R> RecipeView create(
        RecipeEntry<R> entry,
        Collection<? extends InputContent> inputs,
        Collection<? extends OutputContent> outputs
    ) {
        Checks.checkNotNull(entry, "entry");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
        Checks.checkArgument(!inputs.isEmpty(), "inputs cannot be empty");
        Checks.checkArgument(!outputs.isEmpty(), "outputs cannot be empty");

        return new RecipeView(
            entry,
            inputs.stream().<FacetedContent<InputContent>>map(FacetedContent::from).collect(Collectors2.toImmutableList()),
            outputs.stream().<FacetedContent<OutputContent>>map(FacetedContent::from).collect(Collectors2.toImmutableList()),
            FacetMap.create()
        );
    }

    //endregion

    public RecipeView {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
        Checks.checkNotNull(facets, "facets");
    }

    public RecipeView(
        RecipeEntry<?> recipe,
        ImmutableList<FacetedContent<InputContent>> inputs,
        ImmutableList<FacetedContent<OutputContent>> outputs
    ) {
        this(recipe, inputs, outputs, FacetMap.create());
    }

    //region facet

    public RecipeFacet getFacet(Namespace facetId) {
        return facets.getFacet(facetId);
    }

    public FacetMap addFacet(RecipeFacet facet) {
        return facets.addFacet(facet);
    }

    public FacetMap removeFacet(RecipeFacetType facetType) {
        return facets.removeFacet(facetType);
    }

    public FacetMap removeFacet(Namespace facetId) {
        return facets.removeFacet(facetId);
    }

    public boolean hasFacet(Namespace facetId) {
        return facets.hasFacet(facetId);
    }

    public boolean hasFacet(RecipeFacetType facetType) {
        return facets.hasFacet(facetType);
    }

    public boolean hasFacet(RecipeFacet facet) {
        return facets.hasFacet(facet);
    }

    public RecipeFacet getFacet(RecipeFacetType facetType) {
        return facets.getFacet(facetType);
    }

    public void resetFacets() {
        facets.reset();
    }

    public RichIterable<RecipeFacet> allFacets() {
        return facets.allFacets();
    }

    //endregion

    @Override
    public String toString() {
        return "RecipeView{" +
               "recipe=" + recipe +
               ", inputs=" + inputs +
               ", outputs=" + outputs +
               ", facets=" + facets +
               '}';
    }
}

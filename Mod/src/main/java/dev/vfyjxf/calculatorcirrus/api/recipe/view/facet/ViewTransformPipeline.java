package dev.vfyjxf.calculatorcirrus.api.recipe.view.facet;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.InputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.OutputContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.api.list.MutableList;

import java.util.function.Predicate;

//TODO:ordering
public final class ViewTransformPipeline {
    private final MutableList<Pair<RecipeFacetType, ViewTransformer<?>>> viewTransformers = MutableLists.empty();
    private final MutableList<Pair<RecipeFacetType, FacetTransformer<?, ?>>> facetTransformers = MutableLists.empty();

    public void detectFacet(RecipeFacetType facetType) {
        var api = RecipeApi.getInstance();
        ViewTransformer<?> viewTransformer = api.getViewTransformer(facetType);
        if (viewTransformer != null)
            addTransformer(facetType, viewTransformer);
        FacetTransformer<?, ?> facetTransformer = api.getFacetTransformer(facetType);
        if (facetTransformer != null)
            addTransformer(facetType, facetTransformer);
    }

    public void addTransformer(RecipeFacetType type, ViewTransformer<?> transformer) {
        this.viewTransformers.add(Pair.of(type, transformer));
    }

    public void addTransformer(RecipeFacetType type, FacetTransformer<?, ?> transformer) {
        this.facetTransformers.add(Pair.of(type, transformer));
    }

    public TransformedRecipeView transformView(RecipeContext context, RecipeView view) {
        var api = RecipeApi.getInstance();
        var viewFacets = view.facets();
        var increment = new ViewIncrementOps();
        var decrement = new ViewDecrementOps();
        for (var viewFacet : viewFacets.allFacets()) {
            RecipeFacetType type = api.getFacetType(viewFacet.id());
            ViewTransformer<RecipeFacet> viewTransformer = api.getViewTransformer(type);
            if (viewTransformer != null && viewTransformers.anySatisfy(pair -> pair.getKey().equals(type))) {
                viewTransformer.transformView(context, view, viewFacet, increment, decrement);
            }
        }
        for (var entry : facetTransformers) {
            RecipeFacetType type = entry.getKey();
            FacetTransformer<?, ?> facetTransformer = entry.getRight();
            for (var input : view.inputs()) {
                boolean rejected = decrement.inputDecrements.anySatisfy(reject -> reject.test(input.content()));
                if (rejected) continue;
                boolean marked = input.facets().hasFacet(type);
                boolean selected = facetTransformer.select(context, view, input.content(), true);
                if (!marked || !selected) continue;
                var facet = input.facets().getFacet(type);
                applyTransformer(facetTransformer, context, view.recipe(), input.content(), facet, true, increment, decrement);
                decrement.rejectInput(input.content());
            }
            for (var output : view.outputs()) {
                boolean rejected = decrement.outputDecrements.anySatisfy(reject -> reject.test(output.content()));
                if (rejected) continue;
                boolean marked = output.facets().hasFacet(type);
                boolean selected = facetTransformer.select(context, view, output.content(), false);
                if (!marked || !selected) continue;
                var facet = output.facets().getFacet(type);
                applyTransformer(facetTransformer, context, view.recipe(), output.content(), facet, false, increment, decrement);
                decrement.rejectOutput(output.content());
            }
        }

        MutableList<InputContent> transformedInputs = view.inputs()
            .collect(FacetedContent::content)
            .reject(decrement::rejected)
            .newWithAll(increment.inputIncrements)
            .toList();

        MutableList<OutputContent> transformedOutputs = view.outputs()
            .collect(FacetedContent::content)
            .reject(decrement::rejected)
            .newWithAll(increment.outputIncrements)
            .toList();

        return new TransformedRecipeView(view.recipe(), transformedInputs.toImmutable(), transformedOutputs.toImmutable());
    }

    @SuppressWarnings("unchecked")
    private static <T extends RecipeContent, F extends RecipeFacet> void applyTransformer(
        FacetTransformer<?, ?> transformer,
        RecipeContext context, RecipeEntry<?> recipe,
        T content, F facet, boolean asInput,
        ViewIncrementOps increment, ViewDecrementOps decrement
    ) {
        FacetTransformer<T, F> typedTransformer = (FacetTransformer<T, F>) transformer;
        typedTransformer.transform(context, recipe, content, facet, asInput, increment, decrement);
    }

    private static class ViewIncrementOps implements FacetTransformer.IncrementOps {

        private final MutableList<InputContent> inputIncrements = MutableLists.empty();
        private final MutableList<OutputContent> outputIncrements = MutableLists.empty();

        @Override
        public FacetTransformer.IncrementOps injectInput(InputContent content) {
            inputIncrements.add(content);
            return this;
        }

        @Override
        public FacetTransformer.IncrementOps injectOutput(OutputContent content) {
            outputIncrements.add(content);
            return this;
        }


    }

    private static class ViewDecrementOps implements FacetTransformer.DecrementOps {

        private final MutableList<Predicate<InputContent>> inputDecrements = MutableLists.empty();
        private final MutableList<Predicate<OutputContent>> outputDecrements = MutableLists.empty();

        boolean rejected(InputContent input) {
            return inputDecrements.anySatisfy(predicate -> predicate.test(input));
        }

        boolean rejected(OutputContent output) {
            return outputDecrements.anySatisfy(predicate -> predicate.test(output));
        }

        @Override
        public FacetTransformer.DecrementOps rejectInput(Predicate<InputContent> predicate) {
            inputDecrements.add(predicate);
            return this;
        }

        @Override
        public FacetTransformer.DecrementOps rejectInput(InputContent content) {
            inputDecrements.add(input -> input == content);
            return this;
        }

        @Override
        public FacetTransformer.DecrementOps rejectOutput(Predicate<OutputContent> predicate) {
            outputDecrements.add(predicate);
            return this;
        }

        @Override
        public FacetTransformer.DecrementOps rejectOutput(OutputContent content) {
            outputDecrements.add(output -> output == content);
            return this;
        }
    }

}

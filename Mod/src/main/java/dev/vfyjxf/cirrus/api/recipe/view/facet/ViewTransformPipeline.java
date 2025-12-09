package dev.vfyjxf.cirrus.api.recipe.view.facet;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer.ActionResult;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

//TODO:ordering
// disable transformer
public final class ViewTransformPipeline {

    private final Map<Namespace, ViewTransformer<?>> viewTransformers = new LinkedHashMap<>();
    private final Map<Namespace, FacetTransformer<?, ?>> facetTransformers = new LinkedHashMap<>();

    public void detectFacet(RecipeFacetType facetType) {
        var api = RecipeApi.instance();
        ViewTransformer<?> viewTransformer = api.findViewTransformer(facetType);
        if (viewTransformer != null) addViewTransformer(facetType, viewTransformer);

        FacetTransformer<?, ?> facetTransformer = api.findFacetTransformer(facetType);
        if (facetTransformer != null) addContentTransformer(facetType, facetTransformer);
    }

    public void addViewTransformer(RecipeFacetType type, ViewTransformer<?> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        this.viewTransformers.put(type.id(), transformer);
    }

    public void addContentTransformer(RecipeFacetType type, FacetTransformer<?, ?> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        this.facetTransformers.put(type.id(), transformer);
    }

    @SuppressWarnings("unchecked")
    public TransformedView transformView(RecipeContext context, RecipeView view) {
        var viewFacets = view.facets();
        var increment = new ViewIncrementOps();
        var decrement = new ViewDecrementOps();
        for (var viewFacet : viewFacets.allFacets()) {
            ViewTransformer<RecipeFacet> viewTransformer = (ViewTransformer<RecipeFacet>) viewTransformers.get(viewFacet.id());
            if (viewTransformer == null) continue;
            if (viewTransformer.selectView(context, view)) {
                viewTransformer.transformView(context, view, viewFacet, increment, decrement);
            }
        }
        for (var entry : facetTransformers.entrySet()) {
            Namespace typeId = entry.getKey();
            FacetTransformer<?, ?> facetTransformer = entry.getValue();
            for (var input : view.inputs()) {
                boolean rejected = decrement.inputDecrements.anySatisfy(reject -> reject.test(input.content()));
                if (rejected) continue;
                boolean marked = input.facets().hasFacet(typeId);
                boolean selected = facetTransformer.select(context, view, input.content(), true);
                if (!marked || !selected) continue;
                var facet = input.facets().getFacet(typeId);
                ActionResult action = applyTransformer(facetTransformer, context, view.recipe(), input.content(), facet, true, increment, decrement);
                if (action == ActionResult.REMOVE) decrement.rejectInput(input.content());
            }
            for (var output : view.outputs()) {
                boolean rejected = decrement.outputDecrements.anySatisfy(reject -> reject.test(output.content()));
                if (rejected) continue;
                boolean marked = output.facets().hasFacet(typeId);
                boolean selected = facetTransformer.select(context, view, output.content(), false);
                if (!marked || !selected) continue;
                var facet = output.facets().getFacet(typeId);
                ActionResult action = applyTransformer(facetTransformer, context, view.recipe(), output.content(), facet, false, increment, decrement);
                if (action == ActionResult.REMOVE) decrement.rejectOutput(output.content());
            }
        }

        ImmutableList<InputContent> transformedInputs = view.inputs()
                                                            .collect(FacetedContent::content)
                                                            .reject(decrement::rejected)
                                                            .newWithAll(increment.inputIncrements)
                                                            .toImmutable();

        ImmutableList<OutputContent> transformedOutputs = view.outputs()
                                                              .collect(FacetedContent::content)
                                                              .reject(decrement::rejected)
                                                              .newWithAll(increment.outputIncrements)
                                                              .toImmutable();

        return new TransformedView(view.recipe(), transformedInputs, transformedOutputs);
    }

    @SuppressWarnings("unchecked")
    private static <T extends RecipeContent, F extends RecipeFacet> ActionResult applyTransformer(
        FacetTransformer<?, ?> transformer,
        RecipeContext context, RecipeEntry<?> recipe,
        T content, F facet, boolean asInput,
        ViewIncrementOps increment, ViewDecrementOps decrement
    ) {
        FacetTransformer<T, F> typedTransformer = (FacetTransformer<T, F>) transformer;
        return typedTransformer.transform(context, recipe, content, facet, asInput, increment, decrement);
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

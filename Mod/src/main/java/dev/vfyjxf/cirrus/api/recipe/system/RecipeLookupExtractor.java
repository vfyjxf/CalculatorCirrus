package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionSlot;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts a lightweight {@link RoutingRecipeView} for building a {@link RecipeLookup} index.
 */
@FunctionalInterface
public interface RecipeLookupExtractor<R> {

    @Nullable
    RoutingRecipeView extract(R recipe);

    /**
     * Default extractor for any {@link RecipeSystem}: uses {@link RecipeSystem#routingView} first,
     * then falls back to {@link RecipeApi#resolve} / {@link RecipeSystem#resolveFallback}.
     */
    static <R> RecipeLookupExtractor<R> forSystem(RecipeSystem<R> system) {
        Checks.checkNotNull(system, "system");
        return recipe -> {
            if (recipe == null) {
                return null;
            }
            try {
                RoutingRecipeView direct = system.routingView(ResolveContext.empty(), recipe);
                if (direct != null) {
                    return direct;
                }
            } catch (RuntimeException | LinkageError ignored) {}

            ResolveResult result;
            try {
                RecipeApi api = dev.vfyjxf.cirrus.recipe.RecipeApiImpl.instance();
                result = api != null
                        ? api.resolve(ResolveContext.empty(), system, recipe)
                        : system.resolveFallback(ResolveContext.empty(), recipe);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
            if (result == null || !result.resolved()) {
                return null;
            }
            RecipeProjection projection = result.projection();
            if (projection == null) {
                return null;
            }
            List<InputContent> inputs = new ArrayList<>(projection.inputs().size());
            for (ProjectionSlot<InputContent> in : projection.inputs()) {
                inputs.add(in.content());
            }
            List<OutputContent> outputs = new ArrayList<>(projection.outputs().size());
            for (ProjectionSlot<OutputContent> out : projection.outputs()) {
                outputs.add(out.content());
            }
            return new RoutingRecipeView(inputs, outputs);
        };
    }
}

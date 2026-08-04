package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseSet;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.list.MutableList;

import java.util.List;
import java.util.function.IntFunction;

final class VanillaRecipeProjectionSupport {

    private VanillaRecipeProjectionSupport() {
    }

    static <T extends Recipe<?>> ResolveResult resolve(
        RecipeSystem<RecipeHolder<T>> system,
        RecipeHolder<T> holder,
        List<Ingredient> ingredients,
        ItemStack result
    ) {
        return resolve(system, holder, ingredients, result, ProjectionSchema.empty(), ignored -> ProjectionSchema.empty());
    }

    static <T extends Recipe<?>> ResolveResult resolve(
        RecipeSystem<RecipeHolder<T>> system,
        RecipeHolder<T> holder,
        List<Ingredient> ingredients,
        ItemStack result,
        ProjectionSchema projectionSchema,
        IntFunction<ProjectionSchema> inputSchema
    ) {
        try {
            MutableList<ClausedContent<InputContent>> inputs = MutableLists.empty();
            for (int index = 0; index < ingredients.size(); index++) {
                InputContent input = InputContent.fromItemIngredient(ingredients.get(index));
                if (!input.isEmpty()) {
                    ProjectionSchema schema = inputSchema.apply(index);
                    inputs.add(ClausedContent.from(
                        input,
                        schema == null ? ProjectionSchema.empty() : schema,
                        ClauseSet.create()
                    ));
                }
            }

            OutputContent output = OutputContent.fromItemStack(result);
            if (inputs.isEmpty() || output.isEmpty()) {
                return ResolveResult.failed(ResolveError.invalidRecipe);
            }

            MutableList<ClausedContent<OutputContent>> outputs = MutableLists.empty();
            outputs.add(ClausedContent.from(output));
            return ResolveResult.resolved(new RecipeProjection(
                RecipeEntry.of(system, holder),
                inputs.toImmutable(),
                outputs.toImmutable(),
                projectionSchema,
                ClauseSet.create()
            ));
        } catch (RuntimeException ignored) {
            return ResolveResult.failed(ResolveError.invalidRecipe);
        }
    }

    static @org.jetbrains.annotations.Nullable RoutingRecipeView routingView(
        List<Ingredient> ingredients,
        ItemStack result
    ) {
        try {
            MutableList<InputContent> inputs = MutableLists.empty();
            for (Ingredient ingredient : ingredients) {
                InputContent input = InputContent.fromItemIngredient(ingredient);
                if (!input.isEmpty()) {
                    inputs.add(input);
                }
            }

            OutputContent output = OutputContent.fromItemStack(result);
            if (inputs.isEmpty() || output.isEmpty()) {
                return null;
            }
            return new RoutingRecipeView(inputs.toImmutable(), List.of(output));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

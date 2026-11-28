package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
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
import org.jspecify.annotations.Nullable;

import java.util.List;

final class VanillaRecipeProjectionSupport {

    private VanillaRecipeProjectionSupport() {}

    static <T extends Recipe<?>> ResolveResult resolve(
        RecipeSystem<RecipeHolder<T>> system,
        RecipeHolder<T> holder,
        List<Ingredient> ingredients,
        ItemStack result
    ) {
        return resolve(system, holder, ingredients, result, null, List.of(), List.of());
    }

    static <T extends Recipe<?>> ResolveResult resolve(
        RecipeSystem<RecipeHolder<T>> system,
        RecipeHolder<T> holder,
        List<Ingredient> ingredients,
        ItemStack result,
        @Nullable GridSpec inputGrid,
        List<InputContent> extraInputs,
        List<OutputContent> extraOutputs
    ) {
        try {
            RecipeProjection.Builder builder = RecipeProjection.builder(RecipeEntry.of(system, holder));
            if (inputGrid != null) {
                builder.inputGrid(inputGrid.width(), inputGrid.height());
            }
            int inputCount = 0;
            for (int index = 0; index < ingredients.size(); index++) {
                InputContent input = InputContent.fromItemIngredient(ingredients.get(index));
                if (input.isEmpty()) {
                    continue;
                }
                if (inputGrid != null && index < inputGrid.cellCount()) {
                    builder.inputCellIndex(index, input);
                } else {
                    builder.input(input);
                }
                inputCount++;
            }
            for (InputContent extra : extraInputs) {
                if (extra != null && !extra.isEmpty()) {
                    builder.input(extra);
                    inputCount++;
                }
            }

            OutputContent output = OutputContent.fromItemStack(result);
            if (inputCount == 0 || output.isEmpty()) {
                return ResolveResult.failed(ResolveError.invalidRecipe);
            }
            builder.output(output);
            for (OutputContent extra : extraOutputs) {
                if (extra != null && !extra.isEmpty()) {
                    builder.output(extra);
                }
            }
            return ResolveResult.resolved(builder.build());
        } catch (RuntimeException ignored) {
            return ResolveResult.failed(ResolveError.invalidRecipe);
        }
    }

    static @Nullable RoutingRecipeView routingView(List<Ingredient> ingredients, ItemStack result) {
        return routingView(ingredients, result, List.of(), List.of());
    }

    static @Nullable RoutingRecipeView routingView(
        List<Ingredient> ingredients,
        ItemStack result,
        List<InputContent> extraInputs,
        List<OutputContent> extraOutputs
    ) {
        try {
            MutableList<InputContent> inputs = MutableLists.empty();
            for (Ingredient ingredient : ingredients) {
                InputContent input = InputContent.fromItemIngredient(ingredient);
                if (!input.isEmpty()) {
                    inputs.add(input);
                }
            }
            for (InputContent extra : extraInputs) {
                if (extra != null && !extra.isEmpty()) {
                    inputs.add(extra);
                }
            }

            OutputContent output = OutputContent.fromItemStack(result);
            if (inputs.isEmpty() || output.isEmpty()) {
                return null;
            }
            MutableList<OutputContent> outputs = MutableLists.empty();
            outputs.add(output);
            for (OutputContent extra : extraOutputs) {
                if (extra != null && !extra.isEmpty()) {
                    outputs.add(extra);
                }
            }
            return new RoutingRecipeView(inputs.toImmutable(), outputs.toImmutable());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.GridSpec;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

import java.util.List;

public abstract class VanillaIngredientRecipeSystem<T extends Recipe<?>> extends BasicVanillaSystem<T> {

    protected VanillaIngredientRecipeSystem(Namespace systemId, RecipeType<T> recipeType) {
        super(systemId, recipeType);
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, RecipeHolder<T> recipeHolder) {
        T recipe = recipeHolder.value();
        return VanillaRecipeProjectionSupport.resolve(
            this,
            recipeHolder,
            ingredients(recipe),
            result(recipe),
            inputGrid(recipe),
            extraInputs(recipe),
            extraOutputs(recipe)
        );
    }

    @Override
    public @Nullable RoutingRecipeView routingView(ResolveContext context, RecipeHolder<T> recipeHolder) {
        T recipe = recipeHolder.value();
        return VanillaRecipeProjectionSupport.routingView(
            ingredients(recipe),
            result(recipe),
            extraInputs(recipe),
            extraOutputs(recipe)
        );
    }

    protected List<Ingredient> ingredients(T recipe) {
        return recipe.getIngredients();
    }

    protected ItemStack result(T recipe) {
        return RecipeHelper.getOutput(recipe);
    }

    protected @Nullable GridSpec inputGrid(T recipe) {
        return null;
    }

    protected List<InputContent> extraInputs(T recipe) {
        return List.of();
    }

    protected List<OutputContent> extraOutputs(T recipe) {
        return List.of();
    }
}

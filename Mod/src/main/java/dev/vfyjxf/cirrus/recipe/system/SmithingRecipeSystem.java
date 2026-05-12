package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridSize;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemas;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class SmithingRecipeSystem extends BasicVanillaSystem<SmithingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("smithing");

    public SmithingRecipeSystem() {
        super(systemId, RecipeType.SMITHING);
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, RecipeHolder<SmithingRecipe> recipeHolder) {
        List<Ingredient> ingredients = smithingIngredients(recipeHolder.value());
        return VanillaRecipeProjectionSupport.resolve(
            this,
            recipeHolder,
            ingredients,
            RecipeHelper.getOutput(recipeHolder.value()),
            ProjectionSchema.empty().with(ProjectionSchemas.gridSize, new GridSize(3, 1)),
            index -> ProjectionSchema.empty().with(ProjectionSchemas.gridIndex, new GridIndex(index))
        );
    }

    @Override
    public RoutingRecipeView routingView(ResolveContext context, RecipeHolder<SmithingRecipe> recipeHolder) {
        return VanillaRecipeProjectionSupport.routingView(
            smithingIngredients(recipeHolder.value()),
            RecipeHelper.getOutput(recipeHolder.value())
        );
    }

    private static List<Ingredient> smithingIngredients(SmithingRecipe recipe) {
        List<Ingredient> publicIngredients = recipe.getIngredients();
        if (!publicIngredients.isEmpty()) {
            return publicIngredients;
        }

        ArrayList<Ingredient> reflected = new ArrayList<>(3);
        addIngredient(reflected, field(recipe, "template"));
        addIngredient(reflected, field(recipe, "base"));
        addIngredient(reflected, field(recipe, "addition"));
        return reflected;
    }

    private static void addIngredient(List<Ingredient> out, @Nullable Ingredient ingredient) {
        if (ingredient != null) {
            out.add(ingredient);
        }
    }

    private static @Nullable Ingredient field(SmithingRecipe recipe, String name) {
        try {
            Field field = recipe.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "SmithingRecipeSystem";
    }
}

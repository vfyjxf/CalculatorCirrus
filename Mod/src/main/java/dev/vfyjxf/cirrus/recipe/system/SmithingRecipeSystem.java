package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridIndex;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.GridSize;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchemas;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.helper.RecipeHelper;
import dev.vfyjxf.cirrus.mixin.SmithingTransformRecipeAccessor;
import dev.vfyjxf.cirrus.mixin.SmithingTrimRecipeAccessor;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class SmithingRecipeSystem extends BasicVanillaSystem<SmithingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("smithing");

    private static final RecipeScreeningHints trimHints = RecipeScreeningHints.builder()
            .targetOnly()
            .penalty(500)
            .reasonKey("cirrus.screening.cosmetic_trim")
            .build();

    public SmithingRecipeSystem() {
        super(systemId, RecipeType.SMITHING);
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, RecipeHolder<SmithingRecipe> recipeHolder) {
        SmithingRecipe recipe = recipeHolder.value();
        return VanillaRecipeProjectionSupport.resolve(
            this,
            recipeHolder,
            smithingIngredients(recipe),
            smithingResult(recipe),
            ProjectionSchema.empty().with(ProjectionSchemas.gridSize, new GridSize(3, 1)),
            index -> ProjectionSchema.empty().with(ProjectionSchemas.gridIndex, new GridIndex(index))
        );
    }

    @Override
    public @Nullable RoutingRecipeView routingView(ResolveContext context, RecipeHolder<SmithingRecipe> recipeHolder) {
        SmithingRecipe recipe = recipeHolder.value();
        return VanillaRecipeProjectionSupport.routingView(smithingIngredients(recipe), smithingResult(recipe));
    }

    @Override
    public RecipeScreeningHints routingHints(RecipeHolder<SmithingRecipe> recipeHolder) {
        if (recipeHolder.value() instanceof SmithingTrimRecipe) {
            return trimHints;
        }
        return RecipeScreeningHints.empty();
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.SMITHING_TABLE));
    }

    private static List<Ingredient> smithingIngredients(SmithingRecipe recipe) {
        if (recipe instanceof SmithingTransformRecipe transform) {
            SmithingTransformRecipeAccessor accessor = (SmithingTransformRecipeAccessor) transform;
            return List.of(
                nonNull(accessor.cirrusGetTemplate()),
                nonNull(accessor.cirrusGetBase()),
                nonNull(accessor.cirrusGetAddition())
            );
        }
        if (recipe instanceof SmithingTrimRecipe trim) {
            SmithingTrimRecipeAccessor accessor = (SmithingTrimRecipeAccessor) trim;
            return List.of(
                nonNull(accessor.cirrusGetTemplate()),
                nonNull(accessor.cirrusGetBase()),
                nonNull(accessor.cirrusGetAddition())
            );
        }
        return recipe.getIngredients();
    }

    private static ItemStack smithingResult(SmithingRecipe recipe) {
        if (recipe instanceof SmithingTransformRecipe transform) {
            ItemStack direct = ((SmithingTransformRecipeAccessor) transform).cirrusGetResult();
            if (!direct.isEmpty()) {
                return direct.copy();
            }
        }
        return RecipeHelper.getOutput(recipe);
    }

    private static Ingredient nonNull(@Nullable Ingredient ingredient) {
        return ingredient == null ? Ingredient.EMPTY : ingredient;
    }

    @Override
    public String toString() {
        return "SmithingRecipeSystem";
    }
}

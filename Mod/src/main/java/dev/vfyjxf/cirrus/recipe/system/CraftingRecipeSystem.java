package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
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
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class CraftingRecipeSystem extends BasicVanillaSystem<CraftingRecipe> {

    public static final Namespace systemId = Namespace.ofMc("crafting");

    public CraftingRecipeSystem() {
        super(systemId, RecipeType.CRAFTING);
    }

    @Override
    public ResolveResult resolveFallback(ResolveContext context, RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        VanillaSpecialCraftingSupport.SpecialSpec special = VanillaSpecialCraftingSupport.inspect(recipe);
        if (special != null) {
            return VanillaSpecialCraftingSupport.resolveSpecial(this, recipeHolder, special);
        }

        ProjectionSchema projectionSchema = ProjectionSchema.empty();
        if (recipe instanceof ShapedRecipe shaped) {
            projectionSchema = projectionSchema
                    .with(ProjectionSchemas.gridSize, new GridSize(shaped.getWidth(), shaped.getHeight()));
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        List<OutputContent> remainders = VanillaSpecialCraftingSupport.remainingOutputs(ingredients);
        return VanillaRecipeProjectionSupport.resolve(
            this,
            recipeHolder,
            ingredients,
            RecipeHelper.getOutput(recipe),
            projectionSchema,
            recipe instanceof ShapedRecipe
                    ? index -> ProjectionSchema.empty().with(ProjectionSchemas.gridIndex, new GridIndex(index))
                    : ignored -> ProjectionSchema.empty(),
            List.of(),
            remainders
        );
    }

    @Override
    public @Nullable RoutingRecipeView routingView(ResolveContext context, RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        VanillaSpecialCraftingSupport.SpecialSpec special = VanillaSpecialCraftingSupport.inspect(recipe);
        if (special != null) {
            return VanillaSpecialCraftingSupport.routingViewSpecial(special);
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        List<OutputContent> remainders = VanillaSpecialCraftingSupport.remainingOutputs(ingredients);
        return VanillaRecipeProjectionSupport
                .routingView(ingredients, RecipeHelper.getOutput(recipe), List.of(), remainders);
    }

    @Override
    public RecipeScreeningHints routingHints(RecipeHolder<CraftingRecipe> recipeHolder) {
        VanillaSpecialCraftingSupport.SpecialSpec special = VanillaSpecialCraftingSupport.inspect(recipeHolder.value());
        return special != null ? special.hints() : RecipeScreeningHints.empty();
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(Items.CRAFTING_TABLE));
    }

    @Override
    public String toString() {
        return "CraftingRecipeSystem";
    }
}

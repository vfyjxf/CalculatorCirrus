package dev.vfyjxf.calculatorcirrus.integration.gtm.systems;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeData;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeLookup;
import dev.vfyjxf.calculatorcirrus.api.recipes.VanillaRecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.helper.RecipeHelper;
import dev.vfyjxf.calculatorcirrus.integration.gtm.ContentConverter;
import dev.vfyjxf.calculatorcirrus.utils.Locations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class GTMRecipeSystem implements VanillaRecipeSystem<GTRecipe> {

    private static final MutableMap<RecipeCapability<?>, ContentConverter> CONVERTERS = Maps.mutable.empty();

    public static void registerConverter(RecipeCapability<?> capability, ContentConverter converter) {
        CONVERTERS.put(capability, converter);
    }

    private final GTRecipeType recipeType;
    private final ResourceLocation systemId;
    private final RecipeManager recipeManager;
    private final MutableList<ContentStack<ItemStack>> machines;

    public GTMRecipeSystem(GTRecipeType recipeType) {
        this.recipeType = recipeType;
        this.systemId = Locations.of(recipeType.registryName.getNamespace(), recipeType.group + "/" + recipeType.registryName.getPath());
        this.recipeManager = RecipeHelper.getRecipeManager();
        this.machines = LazyIterate.adapt(GTRegistries.MACHINES)
                .select(machine -> {
                    @Nullable GTRecipeType[] recipeTypes = machine.getRecipeTypes();
                    if (recipeTypes == null) return false;
                    return ArrayAdapter.adapt(recipeTypes).contains(recipeType);
                })
                .collect(machine -> ContentStack.create(machine.asStack()))
                .toList();
    }

    @Override
    public RecipeType<GTRecipe> recipeType() {
        return recipeType;
    }

    @Override
    public ResourceLocation systemId() {
        return systemId;
    }

    @Override
    public RecipeData parseRecipe(RecipeHolder<GTRecipe> recipe) {
        MutableListMultimap<IngredientType, IngredientStack> recipeData = Multimaps.mutable.list.empty();
        GTRecipe gtRecipe = recipe.value();

        gtRecipe.inputs.forEach((key, value) -> addIngredients(IngredientType.INPUT, key, value, recipeData));
        gtRecipe.outputs.forEach((key, value) -> addIngredients(IngredientType.OUTPUT, key, value, recipeData));
        //TODO:tick ingredients support
        return RecipeData.create(recipeData, recipe.id().toString());
    }

    private static <T> void addIngredients(
            IngredientType ingredientType,
            RecipeCapability<T> capability,
            List<Content> contents,
            MutableListMultimap<IngredientType, IngredientStack> recipeData
    ) {
        ContentConverter converter = CONVERTERS.get(capability);
        if (converter == null) return;
        for (Content content : contents) {
            IngredientStack converted = converter.convert(content);
            if (converted == null || converted.isEmpty()) continue;
            recipeData.put(ingredientType, converted);
        }
    }

    @Override
    public MutableList<ContentStack<ItemStack>> getMachines() {
        return machines.asUnmodifiable();
    }

    @Override
    public RecipeLookup<RecipeHolder<GTRecipe>> createLookup() {
        return RecipeLookup.ofSystem(this);
    }

    @Override
    public @Unmodifiable OrderedIterable<RecipeHolder<GTRecipe>> allRecipes() {
        return Lists.immutable.ofAll(recipeManager.getAllRecipesFor(recipeType));
    }
}

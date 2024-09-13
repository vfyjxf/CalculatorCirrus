package dev.vfyjxf.calculatorcirrus.integration.gtm;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.data.recipe.GTRecipeTypes;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterPlugin;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.IngredientType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.RecipeApi;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import dev.vfyjxf.calculatorcirrus.calculator.SimpleCalculator;
import dev.vfyjxf.calculatorcirrus.integration.CirrusAdapter;
import dev.vfyjxf.calculatorcirrus.integration.gtm.converter.FluidConverter;
import dev.vfyjxf.calculatorcirrus.integration.gtm.converter.ItemConverter;
import dev.vfyjxf.calculatorcirrus.integration.gtm.systems.GTMRecipeSystem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;

public class PluginRegister {

    @RegisterPlugin
    public static class Adapter implements CirrusAdapter {

        @Override
        public void registerContent(RegisterContentEvent event) {

        }

        @Override
        public void registerRecipeSystem(RegisterRecipeSystemEvent event) {
            for (GTRecipeType recipeType : GTRegistries.RECIPE_TYPES) {
                event.registerSystem(new GTMRecipeSystem(recipeType));
            }
            GTMRecipeSystem.registerConverter(ItemRecipeCapability.CAP, new ItemConverter());
            GTMRecipeSystem.registerConverter(FluidRecipeCapability.CAP, new FluidConverter());

            test();
        }
    }


    @SuppressWarnings("ConstantConditions")
    private static void test() {
        SimpleCalculator calculator = new SimpleCalculator();
        RecipeApi recipeApi = RecipeApi.getInstance();
        GTMRecipeSystem recipeSystem = (GTMRecipeSystem) recipeApi.allSystems().detect(system -> {
            if (system instanceof GTMRecipeSystem gtSystem) {
                return gtSystem.recipeType() == GTRecipeTypes.CENTRIFUGE_RECIPES;
            }
            return false;
        });
        RecipeHolder<GTRecipe> target = recipeSystem.getRecipe("gtceu:centrifuge/mycelium_separation");
        ContentStack<ItemStack> targetStack = ContentStack.create(new ItemStack(Items.RED_MUSHROOM));
        calculator.setRecipe(recipeSystem, target);
        calculator.setTarget(recipeSystem.parseRecipe(target).getOutputs().getLast().getAny());
        calculator.setAmount(100);
        calculator.calculate();
        MutableListMultimap<IngredientType, ? extends IngredientStack> calculations = calculator.getCalculations();
    }
}

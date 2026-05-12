package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiRecipeLayoutResolver;
import dev.vfyjxf.cirrus.ui.execution.tooltip.JeiRecipeTooltipComponent;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeManager;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

@JeiPlugin
public class CirrusJeiPlugin implements IModPlugin {

    private static final JeiSystemCatalog systemCatalog = new JeiSystemCatalog();
    private static IRecipeManager recipeManager;
    private static IIngredientManager ingredientManager;
    private static volatile List<RecipeType<?>> knownRecipeTypes = List.of();

    public static IFocusFactory focusFactory;

    private static ISubtypeManager subtypeManager;

    public static IRecipeManager recipeManager() {
        Checks.checkNotNull(recipeManager, "recipeManager");
        return recipeManager;
    }

    public static @Nullable IRecipeManager recipeManagerOrNull() {
        return recipeManager;
    }

    public static IFocusFactory focusFactory() {
        Checks.checkNotNull(focusFactory, "focusFactory");
        return focusFactory;
    }

    public static @Nullable IFocusFactory focusFactoryOrNull() {
        return focusFactory;
    }

    public static @Nullable IIngredientManager ingredientManagerOrNull() {
        return ingredientManager;
    }

    public static List<RecipeSystem<?>> recipeSystems() {
        return systemCatalog.systems();
    }

    public static long recipeSystemCatalogVersion() {
        return systemCatalog.version();
    }

    public static @Nullable RecipeSystem<?> findRecipeSystem(RecipeType<?> recipeType) {
        return systemCatalog.findByRecipeType(recipeType);
    }

    public static List<RecipeType<?>> knownRecipeTypes() {
        return knownRecipeTypes;
    }

    public static ISubtypeManager subtypeManager() {
        return subtypeManager;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.modId, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        CirrusJeiPlugin.recipeManager = jeiRuntime.getRecipeManager();
        CirrusJeiPlugin.ingredientManager = jeiRuntime.getIngredientManager();
        IJeiHelpers helpers = jeiRuntime.getJeiHelpers();
        CirrusJeiPlugin.focusFactory = helpers == null ? null : helpers.getFocusFactory();
        updateKnownRecipeTypes(recipeTypesOrEmpty(helpers));
        JeiRecipeTooltipComponent.clearLayoutCache();
        JeiRecipeLayoutResolver.clearIgnoredLayouts();
        systemCatalog.rebuild(CirrusJeiPlugin.recipeManager);
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        updateKnownRecipeTypes(recipeTypesOrEmpty(registration.getJeiHelpers()));
        registration.addRecipeButtonFactory(JeiRecipeCalculatorTargetButtonHelper.buttonFactory());
        registration.addRecipeButtonFactory(JeiRecipeDatabaseButtonHelper.buttonFactory());
        registration.addRecipeButtonFactory(JeiTargetButtonHelper.buttonFactory());
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        subtypeManager = registration.getSubtypeManager();
    }

    private static List<RecipeType<?>> recipeTypesOrEmpty(@Nullable IJeiHelpers helpers) {
        if (helpers == null) {
            return List.of();
        }
        Stream<RecipeType<?>> recipeTypes = helpers.getAllRecipeTypes();
        return recipeTypes == null ? List.of() : recipeTypes.toList();
    }

    private static void updateKnownRecipeTypes(List<RecipeType<?>> recipeTypes) {
        LinkedHashMap<ResourceLocation, RecipeType<?>> out = new LinkedHashMap<>();
        for (RecipeType<?> recipeType : knownRecipeTypes) {
            out.put(recipeType.getUid(), recipeType);
        }
        for (RecipeType<?> recipeType : recipeTypes) {
            if (JeiRecipeCategoryFilter.include(recipeType)) {
                out.putIfAbsent(recipeType.getUid(), recipeType);
            }
        }
        knownRecipeTypes = List.copyOf(out.values());
    }
}
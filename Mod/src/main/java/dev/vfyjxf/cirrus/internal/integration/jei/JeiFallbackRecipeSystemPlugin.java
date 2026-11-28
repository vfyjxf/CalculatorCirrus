package dev.vfyjxf.cirrus.internal.integration.jei;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cirrus.api.plugin.CirrusPlugin;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.api.register.JeiRecipeTypeBridgeRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemTagRegister;
import dev.vfyjxf.cirrus.recipe.system.jei.JeiCategoryRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class JeiFallbackRecipeSystemPlugin implements CirrusPlugin {
    public static final Namespace id = Namespace.of(CirrusConstants.modId, "jei_fallback_recipe_systems");
    private static final List<RecipeType<?>> defaultRecipeTypes = List.of(
        RecipeTypes.CRAFTING,
        RecipeTypes.SMELTING,
        RecipeTypes.BLASTING,
        RecipeTypes.SMOKING,
        RecipeTypes.CAMPFIRE_COOKING,
        RecipeTypes.FUELING,
        RecipeTypes.STONECUTTING,
        RecipeTypes.SMITHING,
        RecipeTypes.BREWING,
        RecipeTypes.ANVIL,
        RecipeTypes.GRINDSTONE,
        RecipeTypes.COMPOSTING
    );

    private static final Set<RecipeType<?>> nativeVanillaMirrors = Set.copyOf(defaultRecipeTypes);

    @Override
    public Namespace pluginId() {
        return id;
    }

    @Override
    public void registerRecipeSystem(RecipeSystemRegister register) {
        recipeTypes().stream().map(JeiCategoryRecipeSystem::create).forEach(register::registerSystem);
    }

    @Override
    public void registerRecipeSystemTag(RecipeSystemTagRegister register) {
        for (RecipeType<?> recipeType : recipeTypes()) {
            Namespace systemId = JeiCategoryRecipeSystem.systemId(recipeType);
            register.registerSystemTagEntry(RecipeSystemTags.jeiFallback, systemId);
            register.registerSystemTagEntry(
                RecipeSystemTags.folder("jei", recipeType.getUid().getNamespace()),
                systemId
            );
            if (nativeVanillaMirrors.contains(recipeType)) {
                register.registerSystemTagEntry(RecipeSystemTags.mirrorDuplicate, systemId);
            }
            if (RecipeTypes.FUELING.equals(recipeType)) {
                register.registerSystemTagEntry(RecipeSystemTags.displayOnly, systemId);
            }
        }
    }

    @Override
    public void registerJeiRecipeTypeBridge(JeiRecipeTypeBridgeRegister register) {
        recipeTypes().forEach(
            recipeType -> register.registerRecipeTypeForSystem(JeiCategoryRecipeSystem.systemId(recipeType), recipeType)
        );
    }

    private static Collection<RecipeType<?>> recipeTypes() {
        LinkedHashMap<Namespace, RecipeType<?>> out = new LinkedHashMap<>();
        for (RecipeType<?> recipeType : defaultRecipeTypes) {
            if (JeiRecipeCategoryFilter.include(recipeType)) {
                out.put(JeiCategoryRecipeSystem.systemId(recipeType), recipeType);
            }
        }
        for (RecipeType<?> recipeType : CirrusJeiPlugin.knownRecipeTypes()) {
            if (JeiRecipeCategoryFilter.include(recipeType)) {
                out.putIfAbsent(JeiCategoryRecipeSystem.systemId(recipeType), recipeType);
            }
        }
        return out.values();
    }
}

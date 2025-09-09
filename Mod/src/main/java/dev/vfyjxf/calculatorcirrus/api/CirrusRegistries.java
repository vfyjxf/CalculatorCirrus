package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import dev.vfyjxf.calculatorcirrus.api.recipe.transform.IngredientModifier;
import dev.vfyjxf.calculatorcirrus.api.recipe.transform.RecipeModifier;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.registry.util.RegistryRegister;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

@EventBusSubscriber(modid = CirrusConstants.MOD_ID)
public final class CirrusRegistries {

    private static final RegistryRegister registryRegister = new RegistryRegister();

    public static final Registry<RecipeModifier> recipeModifier = registryRegister.register(
            new RegistryBuilder<>(Keys.recipeModifier)
                    .sync(false)
                    .create()
    );

    public static final Registry<IngredientModifier> ingredientModifier = registryRegister.register(
            new RegistryBuilder<>(Keys.ingredientModifier)
                    .sync(false)
                    .create()
    );

    public interface Keys {
        ResourceKey<Registry<RecipeModifier>> recipeModifier = CirrusLocations.createKey(CirrusLocations.create("recipe_modifier"));
        ResourceKey<Registry<IngredientModifier>> ingredientModifier = CirrusLocations.createKey(CirrusLocations.create("ingredient_modifier"));
    }

    @SubscribeEvent
    private static void onRegister(NewRegistryEvent event) {
        registryRegister.register(event);
    }

    private CirrusRegistries() {}
}

package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.Constants;
import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientGroup;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.jetbrains.annotations.ApiStatus;

public final class CirrusRegistries {

    public static final Registry<IngredientGroup> ingredientGroup = new RegistryBuilder<>(Keys.ingredientGroup)
            .sync(true)
            .create();

    public interface Keys {
        ResourceKey<Registry<IngredientGroup>> ingredientGroup = ResourceKey.createRegistryKey(CirrusLocations.create("ingredient_group"));
    }


    public static <T> ResourceKey<Registry<T>> createKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path));
    }

    public static <T> ResourceKey<Registry<T>> createKey(String namespace, String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    @ApiStatus.Internal
    public static void register(NewRegistryEvent event) {
        event.register(ingredientGroup);
    }

    private CirrusRegistries() {}
}

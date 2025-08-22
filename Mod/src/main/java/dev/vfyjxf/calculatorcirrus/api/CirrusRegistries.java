package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientGroup;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;
import dev.vfyjxf.cloudlib.api.event.RegisterListener;
import dev.vfyjxf.cloudlib.api.registry.util.RegistryRegister;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;

public final class CirrusRegistries {

    @RegisterListener
    private static final RegistryRegister registryRegister = new RegistryRegister();

    public static final Registry<IngredientGroup> ingredientGroup = registryRegister.register(
            new RegistryBuilder<>(Keys.ingredientGroup)
                    .sync(true)
                    .create()
    );

    public interface Keys {
        ResourceKey<Registry<IngredientGroup>> ingredientGroup = CirrusLocations.createKey(CirrusLocations.create("ingredient_group"));
    }

    private CirrusRegistries() {}
}

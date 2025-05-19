package dev.vfyjxf.calculatorcirrus.recipes;

import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeApi;
import dev.vfyjxf.calculatorcirrus.api.recipes.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipes.VanillaRecipeSystem;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public class RecipeApiImpl implements RecipeApi {

    private final MutableMap<ResourceLocation, RecipeSystem<?>> systems = Maps.mutable.empty();

    public RecipeApiImpl() {
    }

    @ApiStatus.Internal
    public void registerSystem(RecipeSystem<?> system) {
        if (systems.containsKey(system.systemId())) {
            throw new IllegalArgumentException("Recipe system with id: " + system.systemId() + " already registered");
        }
        systems.put(system.systemId(), system);
    }

    @ApiStatus.Internal
    public void replaceSystem(RecipeSystem<?> system) {
        if (!systems.containsKey(system.systemId())) {
            throw new IllegalArgumentException("Recipe system with id: " + system.systemId() + " not registered");
        }
        systems.put(system.systemId(), system);
    }

    @ApiStatus.Internal
    public void registerVanillaSystem(VanillaRecipeSystem<?> system) {
        registerSystem(system);
    }

    @Override
    public boolean hasSystem(ResourceLocation systemId) {
        return systems.containsKey(systemId);
    }

    @Override
    public RecipeSystem<?> getSystem(ResourceLocation systemId) {
        return systems.getIfAbsent(systemId, () -> {
            throw new IllegalArgumentException("No recipe system found with id: " + systemId);
        });
    }


    @Override
    public @Nullable RecipeSystem<?> getSystemOrNull(ResourceLocation systemId) {
        return systems.get(systemId);
    }

    @Override
    public RichIterable<? extends RecipeSystem<?>> allSystems() {
        return systems.valuesView();
    }
}

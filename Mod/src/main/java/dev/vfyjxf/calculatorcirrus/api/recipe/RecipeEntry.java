package dev.vfyjxf.calculatorcirrus.api.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RecipeEntry<R>(
        RecipeSystem<R> system,
        R recipe
) {

    @Nullable
    public ResourceLocation id() {
        return system().getId(recipe());
    }

}

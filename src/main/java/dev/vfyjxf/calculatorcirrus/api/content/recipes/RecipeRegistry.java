package dev.vfyjxf.calculatorcirrus.api.content.recipes;

import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface RecipeRegistry {

    static RecipeRegistry getInstance() {
        return Singletons.get(RecipeRegistry.class);
    }

    boolean hasSystem(ResourceLocation systemId);

    RecipeSystem<?> getSystem(ResourceLocation systemId);

    @Nullable
    RecipeSystem<?> getSystemOrNull(ResourceLocation systemId);

    default RecipeSystem<?> getSystem(String uniqueId) {
        return getSystem(ResourceLocation.parse(uniqueId));
    }

    /**
     * @return an unmodifiable view of all registered recipe systems
     */
    RichIterable<? extends RecipeSystem<?>> allSystems();


}

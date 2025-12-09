package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Unmodifiable;

public abstract class BasicVanillaSystem<T extends Recipe<?>> implements VanillaRecipeSystem<T> {

    private final Namespace systemId;
    private final RecipeType<T> recipeType;
    protected final RecipeManager recipeManager;

    protected BasicVanillaSystem(
        Namespace systemId,
        RecipeType<T> recipeType
    ) {
        this.systemId = systemId;
        this.recipeType = recipeType;
        this.recipeManager = VanillaRecipeSystem.recipeManager();
    }


    @Override
    public Namespace systemId() {
        return systemId;
    }

    @Override
    public RecipeType<T> recipeType() {
        return recipeType;
    }

    @Override
    public boolean contains(RecipeHolder<T> recipe) {
        return recipeManager.byKey(recipe.id()).isPresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public @Unmodifiable RichIterable<RecipeHolder<T>> allRecipes() {
        //fuck type infer
        return CollectionAdapter.adapt(recipeManager.byType(((RecipeType) recipeType)))
                                .asUnmodifiable();
    }
}

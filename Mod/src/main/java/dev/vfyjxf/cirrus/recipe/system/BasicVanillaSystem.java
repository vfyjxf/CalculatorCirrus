package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.impl.collection.mutable.CollectionAdapter;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public abstract class BasicVanillaSystem<T extends Recipe<?>> implements VanillaRecipeSystem<T> {

    private final Namespace systemId;
    private final RecipeType<T> recipeType;

    protected BasicVanillaSystem(Namespace systemId, RecipeType<T> recipeType) {
        this.systemId = systemId;
        this.recipeType = recipeType;
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
        if (recipe.value().getType() != recipeType) {
            return false;
        }
        RecipeManager manager = VanillaRecipeSystem.recipeManagerOrNull();
        return manager != null && manager.byKey(recipe.id()).isPresent();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public @Unmodifiable RichIterable<RecipeHolder<T>> allRecipes() {
        RecipeManager manager = VanillaRecipeSystem.recipeManagerOrNull();
        if (manager == null) {
            return Lists.immutable.empty();
        }
        Collection<RecipeHolder<T>> recipes = manager.byType((RecipeType) recipeType);
        return CollectionAdapter.adapt(recipes).asUnmodifiable();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public long lookupVersion() {
        RecipeManager manager = VanillaRecipeSystem.recipeManagerOrNull();
        if (manager == null) {
            return 0L;
        }
        int size = manager.byType((RecipeType) recipeType).size();
        return (((long) System.identityHashCode(manager)) << 32) ^ (size & 0xFFFFFFFFL);
    }
}

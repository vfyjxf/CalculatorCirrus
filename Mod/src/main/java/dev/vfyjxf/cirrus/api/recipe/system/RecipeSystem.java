package dev.vfyjxf.cirrus.api.recipe.system;


import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Unmodifiable;

import javax.annotation.Nullable;

/**
 * <li>
 * Convention Machine/Constraint: Just as we look at a crafting recipe,
 * we usually assume that the recipe is handled by a default machine/constraint, which we call convention machine/constraint.
 * </li>
 *
 * @param <R> the type of the recipe
 */
//TODO:给RecipeSystem引入Tag类似的功能，允许用户对RecipeSystem进行分类，用于配方筛选
public interface RecipeSystem<R> {

    //region system info

    Namespace systemId();

    Class<?> basicType();

    /**
     *
     * @return the codec of the recipe
     */
    @Nullable
    default Codec<R> codec() {return null;}

    //endregion

    //region recipe info

    default boolean supports(R recipe) {
        return contains(recipe);
    }

    default boolean contains(R recipe) {
        return basicType().isInstance(recipe);
    }

    @Nullable
    ResourceLocation getId(R recipe);

    @Nullable
    R findRecipe(String uniqueId);

    @Nullable
    R findRecipe(ResourceLocation registryName);

    int hash(R recipe);

    boolean sameRecipe(R r1, R r2);

    //endregion

    //region resolve

    RecipeView resolveRecipe(R recipe);

    //endregion

    //region system data

    default boolean hasData(SystemDataType<?> type) {
        return false;
    }


    default <T> @Nullable T getData(SystemDataType<T> type) throws IllegalStateException {
        throw new IllegalStateException("No data for " + type.id());
    }

    default RecipeConstraint<R> getRecipeConstraint(R recipe) {
        return RecipeConstraint.ingredientConstraint();
    }

    /**
     *
     * @return the convention machine with convention {@link dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint} for this system
     */
    default MachineEntry conventionMachine() {return MachineEntry.empty;}

    @Unmodifiable
    RichIterable<R> allRecipes();

    //endregion

    //region util

    static MutableList<ContentStack<Item, ItemStack>> getMachinesFromJei(RecipeType<?> recipeType) {
        return CirrusJeiPlugin.recipeManager()
                              .createRecipeCatalystLookup(recipeType)
                              .get()
                              .flatMap(i -> i.getItemStack().stream())
                              .map(ContentStack::fromStack)
                              .collect(Collectors2.toList())
                              .collect(ContentStack::normalize);
    }

    default RecipeLookup<R> createLookup() {
        return RecipeLookup.empty();
    }

    //endregion

}

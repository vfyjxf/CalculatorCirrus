package dev.vfyjxf.cirrus.api.recipe.system;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.internal.integration.jei.CirrusJeiPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

/**
 * <li>
 * Convention Machine/Constraint: Just as we look at a crafting recipe,
 * we usually assume that the recipe is handled by a default machine/constraint, which we call convention machine/constraint.
 * </li>
 *
 * @param <R> the type of the recipe
 */
//TODO: give RecipeSystem a Tag-like classification so users can filter recipes by system.
public interface RecipeSystem<R> {

    //region system info

    Namespace systemId();

    Class<?> basicType();

    /**
     *
     * @return the codec of the recipe
     */
    @Nullable
    default Codec<R> codec() {
        return null;
    }

    //endregion

    //region recipe info

    default boolean contains(R recipe) {
        return basicType().isInstance(recipe);
    }

    default RecipeClaim claim(R recipe) {
        return contains(recipe) ? RecipeClaim.normal : RecipeClaim.none;
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

    default ResolveResult resolveFallback(ResolveContext context, R recipe) {
        return ResolveResult.failed(ResolveError.unsupported);
    }

    /**
     * Returns a projection-free deterministic content view for routing graph construction.
     *
     * <p>Implementations should return {@code null} when routing requires projection clauses,
     * schemas, context-sensitive resolution, or any registered subsystem. The graph builder will
     * then use the regular projection pipeline.
     */
    default @Nullable RoutingRecipeView routingView(ResolveContext context, R recipe) {
        return null;
    }

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

    default RecipeScreeningHints routingHints(R recipe) {
        return RecipeScreeningHints.empty();
    }

    /**
     *
     * @return the convention machine with convention {@link dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint} for this system
     */
    default MachineEntry conventionMachine() {
        return MachineEntry.empty;
    }

    @Unmodifiable
    RichIterable<R> allRecipes();

    //endregion

    //region util

    static MutableList<ContentStack<Item, ItemStack>> getMachinesFromJei(RecipeType<?> recipeType) {
        var manager = CirrusJeiPlugin.recipeManagerOrNull();
        if (manager == null) {
            return Lists.mutable.empty();
        }
        return manager.createRecipeCatalystLookup(recipeType).get().flatMap(i -> i.getItemStack().stream())
                .map(ContentStack::fromStack).collect(Collectors2.toList()).collect(ContentStack::normalize);
    }

    /**
     * Monotonic or content-sensitive version token used by {@link RecipeLookup} to detect when
     * cached per-system indexes must be rebuilt.
     */
    default long lookupVersion() {
        return 0L;
    }

    default RecipeLookup<R> createLookup() {
        return RecipeLookup.ofSystem(this);
    }

    //endregion

}

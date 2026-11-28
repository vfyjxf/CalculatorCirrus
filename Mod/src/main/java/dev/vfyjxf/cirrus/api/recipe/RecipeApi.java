package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionPipeline;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

@ApiStatus.NonExtendable
public interface RecipeApi {

    /**
     * Get an instance of RecipeApi
     *
     * @return an instance of RecipeApi
     * @throws IllegalStateException if RecipeApi is not initialized
     */
    static RecipeApi instance() {
        RecipeApi instance = RecipeApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("RecipeApi not initialized");
        }
        return instance;
    }

    //region system

    /**
     * Check if a recipe system is registered
     *
     * @param systemId the id of the system
     * @return true if the system is registered, false otherwise
     */
    boolean hasSystem(Namespace systemId);

    /**
     * Get a recipe system
     *
     * @param systemId the id of the system
     * @return the recipe system
     * @throws IllegalArgumentException if the system is not registered
     */
    RecipeSystem<?> getSystem(Namespace systemId);

    /**
     * Find a recipe system
     *
     * @param systemId the id of the system
     * @return the recipe system if found, null otherwise
     */
    @Nullable
    RecipeSystem<?> findSystem(Namespace systemId);

    /**
     * Find a vanilla recipe system
     *
     * @param recipeType the type of the recipe
     * @param <T>        the type of the recipe
     * @return the vanilla recipe system if found, null otherwise
     */
    @Nullable
    <T extends Recipe<?>> VanillaRecipeSystem<T> findVanillaSystem(
        net.minecraft.world.item.crafting.RecipeType<T> recipeType
    );

    /**
     * Get all recipe systems tagged with the given tag
     *
     * @param tag the tag of the system
     * @return an unmodifiable view of all recipe systems tagged with the given tag
     */
    RichIterable<? extends RecipeSystem<?>> getTaggedSystems(Namespace tag);

    /**
     * Get all tags assigned to the given recipe system.
     *
     * @param systemId the id of the system
     * @return an unmodifiable view of all tags assigned to the system
     */
    RichIterable<Namespace> getSystemTags(Namespace systemId);

    /**
     * Resolves a recipe through the system's registered subsystems and finally its fallback parser.
     */
    <R> ResolveResult resolve(ResolveContext context, RecipeSystem<R> system, R recipe);

    /**
     * @return an unmodifiable view of all registered recipe systems
     */
    @UnmodifiableView
    RichIterable<? extends RecipeSystem<?>> allSystems();

    //endregion

    //region projection

    RichIterable<ProjectionEffect> projectionEffects();

    @Nullable
    ProjectionEffect findProjectionEffect(Namespace effectId);

    default ProjectionPipeline defaultProjectionPipeline() {
        return ProjectionPipeline.fromRegistry();
    }

    //endregion

    //region projection context

    RichIterable<ProjectionPlugin> projectionPlugins();

    @Nullable
    ProjectionPlugin findProjectionPlugin(Namespace id);

    RichIterable<ProjectionContextOption<?>> projectionContextOptions();

    @Nullable
    ProjectionContextOption<?> findProjectionContextOption(Namespace id);

    //endregion

    //region handler

    /**
     * @param systemId        the id of the system
     * @param withJeiFallback whether to include handlers from JEI's {@link mezz.jei.api.recipe.IRecipeManager#createRecipeCatalystLookup(RecipeType)} when {@link RecipeSystem} bind to a {@link RecipeType}
     * @return an unmodifiable view of all handlers for the given system
     * @see RecipeApi#getJeiRecipeTypeForSystem(Namespace)
     */
    RichIterable<MachineEntry> getMachines(Namespace systemId, boolean withJeiFallback);

    //endregion

    //region ingredient

    /**
     * Get all ingredient resolvers for the given type
     *
     * @param type the type of the resolver
     * @param <T>  the type of the ingredient
     * @return an unmodifiable view of all ingredient resolvers for the given type
     */
    <T> @UnmodifiableView RichIterable<IngredientResolver<T>> getIngredientResolver(IngredientResolverType<T> type);

    /**
     * Find an ingredient resolver for the given ingredient
     *
     * @param type       the type of the resolver
     * @param ingredient the ingredient
     * @param <T>        the type of the ingredient
     * @return the ingredient resolver if found, null otherwise
     */
    <T> @Nullable IngredientResolver<T> findIngredientResolver(IngredientResolverType<T> type, T ingredient);

    //endregion

    //region filter

    /**
     * Get all recipe filters for the given id and system
     *
     * @param id     the id of the filter
     * @param system the recipe system
     * @param <R>    the type of the recipe
     * @return an unmodifiable view of all recipe filters for the given id and system
     */
    <R> RichIterable<? extends RecipeFilter<R>> getFilter(Namespace id, RecipeSystem<R> system);

    //endregion

    //region jei bridge

    /**
     * Get all JEI recipe types for the given system
     *
     * @param systemId the id of the system
     * @return an unmodifiable view of all JEI recipe types for the given system
     */
    RichIterable<? extends RecipeType<?>> getJeiRecipeTypeForSystem(Namespace systemId);

    //endregion

}

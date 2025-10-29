package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.api.recipe.RecipeApi;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.calculatorcirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.calculatorcirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.collector.Collectors2;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Map;

public class RecipeApiImpl implements RecipeApi {

    private static RecipeApiImpl instance = null;

    public static @Nullable RecipeApi instance() {
        return instance;
    }

    public static void attach(RecipeApiImpl recipeApi) {
        Checks.checkNotNull(recipeApi, "recipeApi");
        instance = recipeApi;
    }


    private final ImmutableMap<ResourceLocation, RecipeSystem<?>> systems;
    private final ImmutableMap<ResourceLocation, RecipeFacetType> facets;
    private final ImmutableMap<RecipeFacetType, FacetTransformer<?, ?>> facetTransformers;
    private final ImmutableMap<RecipeFacetType, ViewTransformer<?>> viewTransformers;
    private final ImmutableMap<ResourceLocation, ImmutableSet<MachineEntry>> handlerEntries;
    private final ImmutableMap<ResourceLocation, ImmutableSet<RecipeType<?>>> jeiRecipeTypes;
    private final ImmutableMap<IngredientResolverType<?>, ImmutableSet<IngredientResolver<?>>> ingredientResolvers;

    public RecipeApiImpl(RecipeApiRegistryBuilder builder) {
        this.systems = Maps.immutable.ofMap(builder.recipeSystems);
        this.facets = Maps.immutable.ofMap(builder.facets);
        this.facetTransformers = Maps.immutable.ofMap(builder.facetTransformers);
        this.viewTransformers = Maps.immutable.ofMap(builder.viewTransformers);
        this.handlerEntries = builder.handlerEntries.entrySet().stream().collect(
            Collectors2.toImmutableMap(
                Map.Entry::getKey,
                entry -> Sets.immutable.withAll(entry.getValue())
            ));
        this.jeiRecipeTypes = builder.jeiRecipeTypes.entrySet().stream().collect(
            Collectors2.toImmutableMap(
                Map.Entry::getKey,
                entry -> Sets.immutable.withAll(entry.getValue())
            ));
        this.ingredientResolvers = builder.ingredientResolvers.entrySet().stream().collect(
            Collectors2.toImmutableMap(
                Map.Entry::getKey,
                entry -> Sets.immutable.withAll(entry.getValue())
            ));
    }

    @Override
    public boolean hasSystem(ResourceLocation systemId) {
        return systems.containsKey(systemId);
    }

    @Override
    public RecipeSystem<?> getSystem(ResourceLocation systemId) {
        var ret = systems.get(systemId);
        if (ret == null) {
            throw new IllegalArgumentException("No recipe system found with id: " + systemId);
        }
        return ret;
    }

    @Override
    public @Nullable RecipeSystem<?> getSystemOrNull(ResourceLocation systemId) {
        return systems.get(systemId);
    }

    @Override
    public @UnmodifiableView RichIterable<? extends RecipeSystem<?>> allSystems() {
        return systems.valuesView();
    }

    @Override
    public boolean hasFacet(ResourceLocation facetId) {
        return facets.containsKey(facetId);
    }

    @Override
    public RecipeFacetType getFacetType(ResourceLocation facetId) throws IllegalArgumentException {
        var ret = facets.get(facetId);
        if (ret == null) {
            throw new IllegalArgumentException("No facet found with id: " + facetId);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends RecipeContent, F extends RecipeFacet> FacetTransformer<T, F> getFacetTransformer(RecipeFacetType type) {
        return (FacetTransformer<T, F>) facetTransformers.get(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <F extends RecipeFacet> ViewTransformer<F> getViewTransformer(RecipeFacetType type) {
        return (ViewTransformer<F>) viewTransformers.get(type);
    }

    @Override
    public RichIterable<MachineEntry> getMachines(ResourceLocation systemId, boolean withJeiFallback) {
        Checks.checkNotNull(systemId, "systemId");
        ImmutableSet<MachineEntry> entries = handlerEntries.get(systemId);
        var jeiRecipeTypes = getJeiRecipeTypeForSystem(systemId);
        var machinesFromJei =
            jeiRecipeTypes.flatCollect(RecipeSystem::getMachinesFromJei)
                          .collect(content -> MachineEntry.fromItemStack(content.value()))
                          .toSet();
        if (entries == null && machinesFromJei.isEmpty()) return Sets.immutable.empty();
        MutableSet<MachineEntry> ret = Sets.mutable.empty();
        if (entries != null) entries.forEach(ret::add);
        ret.addAll(machinesFromJei);
        return ret;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public @UnmodifiableView <T> RichIterable<IngredientResolver<T>> getIngredientResolver(IngredientResolverType<T> type) {
        RichIterable resolvers = ingredientResolvers.get(type);
        return resolvers == null ? Sets.immutable.empty() : resolvers;
    }

    @Override
    public @Nullable <T> IngredientResolver<T> findIngredientResolver(IngredientResolverType<T> type, T ingredient) {
        var resolvers = getIngredientResolver(type);
        for (var resolver : resolvers) {
            if (resolver.select(ingredient)) {
                return resolver;
            }
        }
        return null;
    }

    @Override
    public RichIterable<RecipeType<?>> getJeiRecipeTypeForSystem(ResourceLocation systemId) {
        var recipeTypes = jeiRecipeTypes.get(systemId);
        return recipeTypes == null ? Sets.immutable.empty() : recipeTypes;
    }

}

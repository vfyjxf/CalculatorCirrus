package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.recipe.RecipeType;
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


    private final ImmutableMap<Namespace, RecipeSystem<?>> systems;
    private final ImmutableMap<Namespace, RecipeFacetType> facets;
    private final ImmutableMap<RecipeFacetType, FacetTransformer<?, ?>> facetTransformers;
    private final ImmutableMap<RecipeFacetType, ViewTransformer<?>> viewTransformers;
    private final ImmutableMap<Namespace, ImmutableSet<MachineEntry>> handlerEntries;
    private final ImmutableMap<Namespace, ImmutableSet<RecipeType<?>>> jeiRecipeTypes;
    private final ImmutableMap<IngredientResolverType<?>, ImmutableSet<IngredientResolver<?>>> ingredientResolvers;
    private final ImmutableMap<RecipeApiRegistryBuilder.FilterEntryType, ImmutableSet<RecipeFilter<?>>> recipeFilters;
    private final ImmutableMap<Namespace, ImmutableSet<RecipeSystem<?>>> recipeSystemTags;

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
        this.recipeFilters = builder.recipeFilters.entrySet().stream().collect(
            Collectors2.toImmutableMap(
                Map.Entry::getKey,
                entry -> Sets.immutable.withAll(entry.getValue())
            ));
        this.recipeSystemTags = builder.recipeSystemTags.entrySet().stream().collect(
            Collectors2.toImmutableMap(
                Map.Entry::getKey,
                entry -> {
                    MutableSet<RecipeSystem<?>> tagValues = Sets.mutable.empty();
                    for (Namespace namespace : entry.getValue()) {
                        var system = findSystem(namespace);
                        if (system != null) tagValues.add(system);
                    }
                    return tagValues.toImmutable();
                }
            )
        );
    }

    @Override
    public boolean hasSystem(Namespace systemId) {
        return systems.containsKey(systemId);
    }

    @Override
    public RecipeSystem<?> getSystem(Namespace systemId) {
        var ret = systems.get(systemId);
        if (ret == null) {
            throw new IllegalArgumentException("No recipe system found with id: " + systemId);
        }
        return ret;
    }

    @Override
    public @Nullable RecipeSystem<?> findSystem(Namespace systemId) {
        return systems.get(systemId);
    }


    @Override
    public RichIterable<? extends RecipeSystem<?>> getTaggedSystems(Namespace tag) {
        ImmutableSet<RecipeSystem<?>> tagValues = recipeSystemTags.get(tag);
        return tagValues == null ? Sets.immutable.empty() : tagValues;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends RecipeSystem<?>> allSystems() {
        return systems.valuesView();
    }

    @Override
    public boolean hasFacet(Namespace facetId) {
        return facets.containsKey(facetId);
    }

    @Override
    public RecipeFacetType getFacetType(Namespace facetId) throws IllegalArgumentException {
        var ret = facets.get(facetId);
        if (ret == null) {
            throw new IllegalArgumentException("No facet found with id: " + facetId);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends RecipeContent, F extends RecipeFacet> FacetTransformer<T, F> findFacetTransformer(RecipeFacetType type) {
        return (FacetTransformer<T, F>) facetTransformers.get(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <F extends RecipeFacet> ViewTransformer<F> findViewTransformer(RecipeFacetType type) {
        return (ViewTransformer<F>) viewTransformers.get(type);
    }

    @Override
    public RichIterable<MachineEntry> getMachines(Namespace systemId, boolean withJeiFallback) {
        Checks.checkNotNull(systemId, "systemId");
        ImmutableSet<MachineEntry> entries = handlerEntries.get(systemId);
        MutableSet<? extends MachineEntry> machinesFromJei = Sets.mutable.empty();
        if (withJeiFallback) {
            var jeiRecipeTypes = getJeiRecipeTypeForSystem(systemId);
            machinesFromJei = jeiRecipeTypes.flatCollect(RecipeSystem::getMachinesFromJei)
                                            .collect(content -> MachineEntry.fromItemStack(content.value()))
                                            .toSet();
        }
        if (entries == null && machinesFromJei.isEmpty()) return Sets.immutable.empty();
        MutableSet<MachineEntry> ret = Sets.mutable.empty();
        if (entries != null) entries.forEach(ret::add);
        if (withJeiFallback) ret.addAll(machinesFromJei);
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

    @SuppressWarnings("unchecked")
    @Override
    public <R> RichIterable<? extends RecipeFilter<R>> getFilter(Namespace id, RecipeSystem<R> system) {
        var filterType = new RecipeApiRegistryBuilder.FilterEntryType(id, system.systemId());
        ImmutableSet<? extends RecipeFilter<?>> filters = recipeFilters.get(filterType);
        return filters == null ? Sets.immutable.empty() : (RichIterable<? extends RecipeFilter<R>>) filters;
    }

    @Override
    public RichIterable<RecipeType<?>> getJeiRecipeTypeForSystem(Namespace systemId) {
        var recipeTypes = jeiRecipeTypes.get(systemId);
        return recipeTypes == null ? Sets.immutable.empty() : recipeTypes;
    }

}

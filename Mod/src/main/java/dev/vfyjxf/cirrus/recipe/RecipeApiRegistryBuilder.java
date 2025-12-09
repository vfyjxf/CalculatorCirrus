package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeSystemExtension;
import dev.vfyjxf.cirrus.api.recipe.view.facet.FacetTransformer;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacet;
import dev.vfyjxf.cirrus.api.recipe.view.facet.RecipeFacetType;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformer;
import dev.vfyjxf.cirrus.api.register.IngredientResolverRegister;
import dev.vfyjxf.cirrus.api.register.JeiRecipeTypeBridgeRegister;
import dev.vfyjxf.cirrus.api.register.MachineEntryRegister;
import dev.vfyjxf.cirrus.api.register.RecipeFacetRegister;
import dev.vfyjxf.cirrus.api.register.RecipeFilterRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemRegister;
import dev.vfyjxf.cirrus.api.register.RecipeSystemTagRegister;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.recipe.RecipeType;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public class RecipeApiRegistryBuilder
    implements RecipeSystemRegister,
               RecipeFacetRegister,
               MachineEntryRegister,
               IngredientResolverRegister,
               JeiRecipeTypeBridgeRegister,
               RecipeSystemTagRegister,
               RecipeFilterRegister {

    final ConcurrentHashMap<Namespace, RecipeSystem<?>> recipeSystems = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, RecipeFacetType> facets = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RecipeFacetType, FacetTransformer<?, ?>> facetTransformers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<RecipeFacetType, ViewTransformer<?>> viewTransformers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, Set<MachineEntry>> handlerEntries = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, Set<RecipeType<?>>> jeiRecipeTypes = new ConcurrentHashMap<>();
    final ConcurrentHashMap<IngredientResolverType<?>, Set<IngredientResolver<?>>> ingredientResolvers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<FilterEntryType, Set<RecipeFilter<?>>> recipeFilters = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, Set<Namespace>> recipeSystemTags = new ConcurrentHashMap<>();

    public void registerSystem(RecipeSystem<?> system) {
        if (recipeSystems.containsKey(system.systemId())) {
            throw new IllegalArgumentException("Recipe system with id: " + system.systemId() + " already registered");
        }
        recipeSystems.put(system.systemId(), system);
    }

    @Override
    public void registerExtension(Namespace systemId, RecipeSystemExtension extension) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    public <T extends RecipeContent, F extends RecipeFacet> void registerFacetTransformer(RecipeFacetType type, FacetTransformer<T, F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        if (facets.containsKey(type.id())) {
            throw new IllegalArgumentException("Recipe facet with id: " + type.id() + " already registered");
        }
        facets.put(type.id(), type);
        facetTransformers.put(type, transformer);
    }

    public void registerFacetType(RecipeFacetType type) {
        Checks.checkNotNull(type, "type");
        if (facets.containsKey(type.id())) {
            throw new IllegalArgumentException("Recipe facet with id: " + type.id() + " already registered");
        }
        facets.put(type.id(), type);
    }


    public <F extends RecipeFacet> void registerViewTransformer(RecipeFacetType type, ViewTransformer<F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        if (viewTransformers.containsKey(type)) {
            throw new IllegalArgumentException("View transformer for facet with id: " + type.id() + " already registered");
        }
        viewTransformers.put(type, transformer);
    }

    @Override
    public void registerMachineForSystem(Namespace systemId, MachineEntry... entries) {
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(entries, "entries");
        handlerEntries.computeIfAbsent(systemId, k -> ConcurrentHashMap.newKeySet())
                      .addAll(Set.of(entries));
    }

    @Override
    public void registerMachineForSystem(Namespace systemId, Collection<? extends MachineEntry> entries) {
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(entries, "entries");
        handlerEntries.computeIfAbsent(systemId, k -> ConcurrentHashMap.newKeySet())
                      .addAll(entries);
    }

    @Override
    public void registerRecipeTypeForSystem(Namespace systemId, RecipeType<?> recipeType) {
        Checks.checkNotNull(systemId, "systemId");
        Checks.checkNotNull(recipeType, "recipeType");
        jeiRecipeTypes.computeIfAbsent(systemId, k -> ConcurrentHashMap.newKeySet())
                      .add(recipeType);
    }

    @Override
    public <T> void registerIngredientResolver(IngredientResolverType<T> type, IngredientResolver<T> resolver) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(resolver, "resolver");
        ingredientResolvers.computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet())
                           .add(resolver);
    }

    @Override
    public <R> void registerFilter(Namespace filterId, Namespace systemId, RecipeFilter<R> filter) {
        Checks.checkNotNull(filterId, "filterId");
        Checks.checkNotNull(systemId, "systemId");
        recipeFilters.computeIfAbsent(new FilterEntryType(filterId, systemId), k -> ConcurrentHashMap.newKeySet())
                     .add(filter);
    }

    @Override
    public void registerSystemTagEntry(Namespace tag, Namespace system) {
        Checks.checkNotNull(tag, "tag");
        Checks.checkNotNull(system, "system");
        recipeSystemTags.computeIfAbsent(tag, k -> ConcurrentHashMap.newKeySet())
                        .add(system);
    }

    record FilterEntryType(
        Namespace filterId,
        Namespace systemId
    ) {}
}

package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeSystemExtension;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionTransformer;
import dev.vfyjxf.cirrus.api.register.IngredientResolverRegister;
import dev.vfyjxf.cirrus.api.register.JeiRecipeTypeBridgeRegister;
import dev.vfyjxf.cirrus.api.register.MachineEntryRegister;
import dev.vfyjxf.cirrus.api.register.RecipeClauseRegister;
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
                   RecipeClauseRegister,
                   MachineEntryRegister,
                   IngredientResolverRegister,
                   JeiRecipeTypeBridgeRegister,
                   RecipeSystemTagRegister,
                   RecipeFilterRegister {

    final ConcurrentHashMap<Namespace, RecipeSystem<?>> recipeSystems = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Namespace, ClauseType> clauses = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ClauseType, ContentTransformer<?, ?>> contentTransformers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<ClauseType, ProjectionTransformer<?>> projectionTransformers = new ConcurrentHashMap<>();
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

    public <T extends RecipeContent, F extends RecipeClause> void registerContentTransformer(ClauseType type, ContentTransformer<T, F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        registerClauseType(type);
        contentTransformers.put(type, transformer);
    }

    public void registerClauseType(ClauseType type) {
        Checks.checkNotNull(type, "type");
        ClauseType existing = clauses.putIfAbsent(type.id(), type);
        if (existing != null && !existing.equals(type)) {
            throw new IllegalArgumentException("Recipe clause type with id: " + type.id() + " already registered");
        }
    }


    public <F extends RecipeClause> void registerProjectionTransformer(ClauseType type, ProjectionTransformer<F> transformer) {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(transformer, "transformer");
        registerClauseType(type);
        if (projectionTransformers.containsKey(type)) {
            throw new IllegalArgumentException("Projection transformer for clause with id: " + type.id() + " already registered");
        }
        projectionTransformers.put(type, transformer);
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

package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolver;
import dev.vfyjxf.cirrus.api.recipe.content.resolver.IngredientResolverType;
import dev.vfyjxf.cirrus.api.recipe.filter.RecipeFilter;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextOption;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeResolverChain;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionTransformer;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.Recipe;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.collector.Collectors2;
import org.eclipse.collections.impl.map.mutable.MapAdapter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class RecipeApiImpl implements RecipeApi {

    private static RecipeApiImpl instance = null;
    private static RecipeApiImpl canonicalInstance = null;

    public static @Nullable RecipeApiImpl canonicalInstance() {
        return canonicalInstance;
    }

    public static @Nullable RecipeApi instance() {
        return instance;
    }

    public static void attach(RecipeApiImpl recipeApi) {
        if (canonicalInstance == null && recipeApi.systems.containsKey(Namespace.ofMc("crafting"))) {
            canonicalInstance = recipeApi;
        }
        Checks.checkNotNull(recipeApi, "recipeApi");
        instance = recipeApi;
    }

    private final ImmutableMap<Namespace, RecipeSystem<?>> systems;
    private final ImmutableMap<Namespace, ClauseType> clauses;
    private final ImmutableMap<ClauseType, ContentTransformer<?, ?>> contentTransformers;
    private final ImmutableMap<ClauseType, ProjectionTransformer<?>> projectionTransformers;
    private final ImmutableMap<Namespace, ImmutableSet<MachineEntry>> handlerEntries;
    private final ImmutableMap<Namespace, ImmutableSet<RecipeType<?>>> jeiRecipeTypes;
    private final ImmutableMap<IngredientResolverType<?>, ImmutableSet<IngredientResolver<?>>> ingredientResolvers;
    private final ImmutableMap<RecipeApiRegistryBuilder.FilterEntryType, ImmutableSet<RecipeFilter<?>>> recipeFilters;
    private final ImmutableMap<Namespace, ImmutableSet<RecipeSystem<?>>> recipeSystemTags;
    private final ImmutableMap<Namespace, ImmutableSet<Namespace>> tagsByRecipeSystem;
    private final ImmutableMap<net.minecraft.world.item.crafting.RecipeType<?>, VanillaRecipeSystem<?>> vanillaRecipeSystemByType;
    private final ImmutableMap<Namespace, ProjectionPlugin> projectionPlugins;
    private final ImmutableMap<Namespace, ProjectionContextOption<?>> projectionContextOptions;
    private final Map<Namespace, List<RecipeApiRegistryBuilder.SubsystemEntry<?>>> recipeSubsystems;

    public RecipeApiImpl(RecipeApiRegistryBuilder builder) {
        this.systems = Maps.immutable.ofMap(builder.recipeSystems);
        this.clauses = Maps.immutable.ofMap(builder.clauses);
        this.contentTransformers = Maps.immutable.ofMap(builder.contentTransformers);
        this.projectionTransformers = Maps.immutable.ofMap(builder.projectionTransformers);
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
        this.projectionPlugins = Maps.immutable.ofMap(builder.projectionPlugins);
        this.projectionContextOptions = Maps.immutable.ofMap(builder.projectionContextOptions);
        this.recipeSubsystems = sortSubsystems(builder);
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
        Map<Namespace, MutableSet<Namespace>> reverseTags = new LinkedHashMap<>();
        builder.recipeSystemTags.forEach((tag, systemIds) -> {
            for (Namespace systemId : systemIds) {
                if (findSystem(systemId) == null) {
                    continue;
                }
                reverseTags.computeIfAbsent(systemId, ignored -> Sets.mutable.empty()).add(tag);
            }
        });
        this.tagsByRecipeSystem = reverseTags.entrySet().stream()
                .collect(Collectors2.toImmutableMap(Map.Entry::getKey, entry -> entry.getValue().toImmutable()));
        this.vanillaRecipeSystemByType = MapAdapter.adapt(builder.recipeSystems)
                                                   .select((k, v) -> v instanceof VanillaRecipeSystem)
                                                   .toImmutableMap(v -> ((VanillaRecipeSystem<?>) v).recipeType(), v -> (VanillaRecipeSystem<?>) v);
    }

    private static Map<Namespace, List<RecipeApiRegistryBuilder.SubsystemEntry<?>>> sortSubsystems(RecipeApiRegistryBuilder builder) {
        Map<Namespace, List<RecipeApiRegistryBuilder.SubsystemEntry<?>>> grouped = new LinkedHashMap<>();
        for (RecipeApiRegistryBuilder.SubsystemEntry<?> entry : builder.recipeSubsystems.values()) {
            if (!builder.recipeSystems.containsKey(entry.systemId())) {
                throw new IllegalArgumentException("Recipe subsystem " + entry.subsystemId() + " targets missing system: " + entry.systemId());
            }
            grouped.computeIfAbsent(entry.systemId(), ignored -> new ArrayList<>()).add(entry);
        }
        Map<Namespace, List<RecipeApiRegistryBuilder.SubsystemEntry<?>>> sorted = new LinkedHashMap<>();
        grouped.forEach((systemId, entries) -> sorted.put(systemId, List.copyOf(sortSubsystemGroup(systemId, entries))));
        return Map.copyOf(sorted);
    }

    private static List<RecipeApiRegistryBuilder.SubsystemEntry<?>> sortSubsystemGroup(
        Namespace systemId,
        List<RecipeApiRegistryBuilder.SubsystemEntry<?>> entries
    ) {
        Map<Namespace, RecipeApiRegistryBuilder.SubsystemEntry<?>> byId = new LinkedHashMap<>();
        for (RecipeApiRegistryBuilder.SubsystemEntry<?> entry : entries) {
            RecipeApiRegistryBuilder.SubsystemEntry<?> previous = byId.putIfAbsent(entry.subsystemId(), entry);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate recipe subsystem id " + entry.subsystemId() + " for system " + systemId);
            }
        }

        Map<Namespace, Set<Namespace>> edges = new HashMap<>();
        Map<Namespace, Integer> indegree = new HashMap<>();
        for (Namespace id : byId.keySet()) {
            edges.put(id, new HashSet<>());
            indegree.put(id, 0);
        }

        for (RecipeApiRegistryBuilder.SubsystemEntry<?> entry : entries) {
            Namespace id = entry.subsystemId();
            for (Namespace dependency : entry.order().after()) {
                requireSubsystemDependency(systemId, id, dependency, byId);
                addSubsystemEdge(dependency, id, edges, indegree);
            }
            for (Namespace dependent : entry.order().before()) {
                requireSubsystemDependency(systemId, id, dependent, byId);
                addSubsystemEdge(id, dependent, edges, indegree);
            }
        }

        Comparator<RecipeApiRegistryBuilder.SubsystemEntry<?>> comparator = Comparator
            .comparingInt((RecipeApiRegistryBuilder.SubsystemEntry<?> entry) -> entry.order().order())
            .thenComparing(RecipeApiRegistryBuilder.SubsystemEntry::subsystemId);
        PriorityQueue<RecipeApiRegistryBuilder.SubsystemEntry<?>> queue = new PriorityQueue<>(comparator);
        for (RecipeApiRegistryBuilder.SubsystemEntry<?> entry : entries) {
            if (indegree.get(entry.subsystemId()) == 0) {
                queue.add(entry);
            }
        }

        List<RecipeApiRegistryBuilder.SubsystemEntry<?>> out = new ArrayList<>(entries.size());
        while (!queue.isEmpty()) {
            RecipeApiRegistryBuilder.SubsystemEntry<?> current = queue.remove();
            out.add(current);
            for (Namespace target : edges.get(current.subsystemId())) {
                int next = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
                if (next == 0) {
                    queue.add(byId.get(target));
                }
            }
        }

        if (out.size() != entries.size()) {
            throw new IllegalArgumentException("Recipe subsystem dependency cycle for system: " + systemId);
        }
        return out;
    }

    private static void requireSubsystemDependency(
        Namespace systemId,
        Namespace subsystemId,
        Namespace dependency,
        Map<Namespace, RecipeApiRegistryBuilder.SubsystemEntry<?>> byId
    ) {
        if (!byId.containsKey(dependency)) {
            throw new IllegalArgumentException("Recipe subsystem " + subsystemId + " for system " + systemId + " references missing subsystem: " + dependency);
        }
    }

    private static void addSubsystemEdge(
        Namespace from,
        Namespace to,
        Map<Namespace, Set<Namespace>> edges,
        Map<Namespace, Integer> indegree
    ) {
        if (edges.get(from).add(to)) {
            indegree.computeIfPresent(to, (ignored, value) -> value + 1);
        }
    }

    @Override
    public boolean hasSystem(Namespace systemId) {
        return systems.containsKey(systemId);
    }

    @Override
    public RecipeSystem<?> getSystem(Namespace systemId) {
        var ret = findSystem(systemId);
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
    public <R> ResolveResult resolve(ResolveContext context, RecipeSystem<R> system, R recipe) {
        Checks.checkNotNull(context, "context");
        Checks.checkNotNull(system, "system");
        Checks.checkNotNull(recipe, "recipe");
        if (!system.claim(recipe).claimed()) {
            return ResolveResult.failed(ResolveError.unsupported);
        }
        List<RecipeApiRegistryBuilder.SubsystemEntry<?>> chain = recipeSubsystems.get(system.systemId());
        if (chain == null || chain.isEmpty()) {
            return system.resolveFallback(context, recipe);
        }
        return new SubsystemChain<>(system, chain, 0).next(context, recipe);
    }

    private record SubsystemChain<R>(
        RecipeSystem<R> system,
        List<RecipeApiRegistryBuilder.SubsystemEntry<?>> entries,
        int index
    ) implements RecipeResolverChain<R> {

        @SuppressWarnings("unchecked")
        @Override
        public ResolveResult next(ResolveContext context, R recipe) {
            if (index >= entries.size()) {
                return system.resolveFallback(context, recipe);
            }
            RecipeApiRegistryBuilder.SubsystemEntry<R> entry = (RecipeApiRegistryBuilder.SubsystemEntry<R>) entries.get(index);
            return entry.subsystem().resolve(context, recipe, new SubsystemChain<>(system, entries, index + 1));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends Recipe<?>> VanillaRecipeSystem<T> findVanillaSystem(net.minecraft.world.item.crafting.RecipeType<T> recipeType) {
        return (VanillaRecipeSystem<T>) vanillaRecipeSystemByType.get(recipeType);
    }

    @Override
    public RichIterable<? extends RecipeSystem<?>> getTaggedSystems(Namespace tag) {
        ImmutableSet<RecipeSystem<?>> tagValues = recipeSystemTags.get(tag);
        return tagValues == null ? Sets.immutable.empty() : tagValues;
    }

    @Override
    public RichIterable<Namespace> getSystemTags(Namespace systemId) {
        Checks.checkNotNull(systemId, "systemId");
        ImmutableSet<Namespace> tagValues = tagsByRecipeSystem.get(systemId);
        return tagValues == null ? Sets.immutable.empty() : tagValues;
    }

    @Override
    public @UnmodifiableView RichIterable<? extends RecipeSystem<?>> allSystems() {
        return systems.valuesView();
    }

    @Override
    public boolean hasClause(Namespace clauseId) {
        return clauses.containsKey(clauseId);
    }

    @Override
    public ClauseType getClauseType(Namespace clauseId) throws IllegalArgumentException {
        var ret = clauses.get(clauseId);
        if (ret == null) {
            throw new IllegalArgumentException("No clause type found with id: " + clauseId);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends RecipeContent, F extends RecipeClause> ContentTransformer<T, F> findContentTransformer(ClauseType type) {
        return (ContentTransformer<T, F>) contentTransformers.get(type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <F extends RecipeClause> ProjectionTransformer<F> findProjectionTransformer(ClauseType type) {
        return (ProjectionTransformer<F>) projectionTransformers.get(type);
    }

    @Override
    public RichIterable<ProjectionPlugin> projectionPlugins() {
        return projectionPlugins.valuesView();
    }

    @Override
    public @Nullable ProjectionPlugin findProjectionPlugin(Namespace id) {
        Checks.checkNotNull(id, "id");
        return projectionPlugins.get(id);
    }

    @Override
    public RichIterable<ProjectionContextOption<?>> projectionContextOptions() {
        return projectionContextOptions.valuesView();
    }

    @Override
    public @Nullable ProjectionContextOption<?> findProjectionContextOption(Namespace id) {
        Checks.checkNotNull(id, "id");
        return projectionContextOptions.get(id);
    }

    @Override
    public RichIterable<MachineEntry> getMachines(Namespace systemId, boolean withJeiFallback) {
        Checks.checkNotNull(systemId, "systemId");
        ImmutableSet<MachineEntry> entries = handlerEntries.get(systemId);
        MutableSet<MachineEntry> machinesFromJei = Sets.mutable.empty();
        if (withJeiFallback) {
            var jeiRecipeTypes = getJeiRecipeTypeForSystem(systemId);
            for (RecipeType<?> recipeType : jeiRecipeTypes) {
                try {
                    RecipeSystem.getMachinesFromJei(recipeType)
                                .collect(content -> MachineEntry.fromItemStack(content.value()))
                                .forEach(machinesFromJei::add);
                } catch (RuntimeException ignored) {
                    // JEI is a client/runtime integration. Missing runtime data must not make
                    // server-side routing or broad recipe graph construction reject recipes.
                }
            }
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
    public RichIterable<? extends RecipeType<?>> getJeiRecipeTypeForSystem(Namespace systemId) {
        var recipeTypes = jeiRecipeTypes.get(systemId);
        return recipeTypes == null ? Sets.immutable.empty() : recipeTypes;
    }

}

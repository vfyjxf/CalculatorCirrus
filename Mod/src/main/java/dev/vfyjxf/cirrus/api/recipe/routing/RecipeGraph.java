package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.MachineConstraint;
import dev.vfyjxf.cirrus.api.recipe.content.constraint.RecipeConstraint;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeHeuristicContext;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Immutable recipe dependency graph used by the routing engine.
 *
 * <p>A {@code RecipeGraph} is built once from a collection of {@link RecipeEntry} instances
 * via {@link #buildGraph(RichIterable)}. The construction process projects each recipe
 * through a {@link ProjectionEngine}, merges identical content slots, and then builds
 * two global cross-reference indices:
 * <ul>
 *     <li><b>producersByInput</b> — for a given {@link InputContent}, which
 *         {@link OutputRef output refs} from other recipes can produce it.</li>
 *     <li><b>consumersByOutput</b> — for a given {@link OutputContent}, which
 *         {@link InputRef input refs} from other recipes consume it.</li>
 * </ul>
 *
 * <p>All public accessors return unmodifiable views; the graph itself is externally
 * immutable after construction, while exact match lookup caches are filled lazily.
 *
 * <p>Responsibilities are intentionally separated:
 * <ul>
 *     <li>{@link Node} — immutable recipe projection snapshot holding merged
 *         inputs/outputs and associated machine constraints.</li>
 *     <li>Global indices — content-to-producer and content-to-consumer cross-references
 *         enabling O(1) neighbor lookups during planning.</li>
 *     <li>Builder logic — one-shot construction from raw recipe entries; not reusable.</li>
 * </ul>
 *
 * @see Node
 * @see OutputRef
 * @see InputRef
 */
public final class RecipeGraph {

    private static final int minInputIndexCapacity = 16;
    private static final int minOutputIndexCapacity = 16;
    private static final int minNodeMapCapacity = 16;
    private static final int parallelBuildThreshold = 256;
    private static final int minRecipesPerBuildWorker = 96;
    private static final int graphBuildChunk = 64;

    private static final Comparator<Node> stableNodeOrder = Comparator
            .comparing((Node node) -> node.recipe().systemId().toString())
            .thenComparing(node -> Objects.toString(node.recipe().id(), "null"))
            .thenComparingInt(node -> node.recipe().hashCode());

    private static final Strategy<RecipeContent> contentHashStrategy = new Strategy<>() {

        @Override
        public int hashCode(RecipeContent content) {
            return switch (content) {
                case EmptyRecipeContent empty -> empty.hashCode();
                case StackContent<?, ?> stack -> hashStackIdentity(stack.content());
                case ValueContent<?> value -> value.type().hashCode();
                case StackListContent list -> {
                    int hash = 0;
                    for (var stack : list.stacks()) {
                        hash = 31 * hash + hashStackIdentity(stack);
                    }
                    yield hash;
                }
                case TypedStackListContent<?, ?> list -> {
                    int hash = list.type().hashCode();
                    for (var stack : list.stacks()) {
                        hash = 31 * hash + hashStackIdentity(stack);
                    }
                    yield hash;
                }
                case TagStackContent<?> tag -> 31 * tag.type().hashCode() + tag.tagKey().hashCode();
                default -> content.getClass().hashCode();
            };
        }

        @Override
        public boolean equals(RecipeContent left, RecipeContent right) {
            return switch (left) {
                case EmptyRecipeContent empty -> empty == right;
                case StackContent<?, ?> stack when right instanceof StackContent<?, ?> stack2 ->
                        stack.satisfy(stack2.content());
                case ValueContent<?> value when right instanceof ValueContent<?> value2 ->
                        value.satisfy(value2.content());
                case StackListContent list when right instanceof StackListContent list2 -> list.mergeable(list2);
                case TypedStackListContent<?, ?> list when right instanceof TypedStackListContent<?, ?> list2 -> {
                    if (list.type() != list2.type() || list.size() != list2.size()) {
                        yield false;
                    }
                    for (int i = 0; i < list.size(); i++) {
                        if (!list.stacks().get(i).matches(list2.stacks().get(i))) {
                            yield false;
                        }
                    }
                    yield true;
                }
                case TagStackContent<?> tag when right instanceof TagStackContent<?> tag2 ->
                        tag.type() == tag2.type() && tag.tagKey() == tag2.tagKey();
                default -> {
                    if (left instanceof ComplexContent l && right instanceof ComplexContent r) {
                        yield l.mergeable(r);
                    }
                    yield left == right;
                }
            };
        }
    };

    private static final Strategy<InputContent> inputHashStrategy = new Strategy<>() {
        @Override
        public int hashCode(InputContent object) {
            return contentHashStrategy.hashCode(object);
        }

        @Override
        public boolean equals(InputContent left, InputContent right) {
            return contentHashStrategy.equals(left, right);
        }
    };

    private static final Strategy<OutputContent> outputHashStrategy = new Strategy<>() {
        @Override
        public int hashCode(OutputContent object) {
            return switch (object) {
                case EmptyRecipeContent empty -> empty.hashCode();
                case StackContent<?, ?> stack -> hashStackIdentity(stack.content());
                case ValueContent<?> value -> value.type().hashCode();
            };
        }

        @Override
        public boolean equals(OutputContent left, OutputContent right) {
            return switch (left) {
                case StackContent<?, ?> stack when right instanceof StackContent<?, ?> stack2 ->
                        stack.content().matches(stack2.content());
                case ValueContent<?> value when right instanceof ValueContent<?> value2 ->
                        value.type() == value2.type();
                default -> left == right;
            };
        }
    };

    private final ObjectSet<Node> nodes;
    private final ImmutableList<Node> stableNodes;
    private final Object2ObjectMap<RecipeEntry<?>, Node> nodeByRecipe;
    private final Object2ObjectMap<ContentMatchKey, ObjectSet<OutputRef>> outputCandidatesByKey;
    private final Object2ObjectMap<ContentMatchKey, ObjectSet<InputRef>> inputCandidatesByKey;
    private final Object2ObjectMap<OutputContent, ObjectSet<OutputRef>> producersByOutput;
    private final Object2ObjectMap<ContentKind<?>, ObjectSet<OutputRef>> producersByKind;
    private final Object2ObjectMap<InputContent, ObjectSet<OutputRef>> producersByInput;
    private final Object2ObjectMap<OutputContent, ObjectSet<InputRef>> consumersByOutput;
    private final Object2ObjectMap<ContentKind<?>, ObjectSet<InputRef>> consumersByKind;

    private RecipeGraph(
            ObjectSet<Node> nodes,
            ImmutableList<Node> stableNodes,
            Object2ObjectMap<RecipeEntry<?>, Node> nodeByRecipe,
            Object2ObjectMap<ContentMatchKey, ObjectSet<OutputRef>> outputCandidatesByKey,
            Object2ObjectMap<ContentMatchKey, ObjectSet<InputRef>> inputCandidatesByKey,
            Object2ObjectMap<OutputContent, ObjectSet<OutputRef>> producersByOutput,
            Object2ObjectMap<ContentKind<?>, ObjectSet<OutputRef>> producersByKind,
            Object2ObjectMap<InputContent, ObjectSet<OutputRef>> producersByInput,
            Object2ObjectMap<OutputContent, ObjectSet<InputRef>> consumersByOutput,
            Object2ObjectMap<ContentKind<?>, ObjectSet<InputRef>> consumersByKind
    ) {
        this.nodes = nodes;
        this.stableNodes = stableNodes;
        this.nodeByRecipe = nodeByRecipe;
        this.outputCandidatesByKey = outputCandidatesByKey;
        this.inputCandidatesByKey = inputCandidatesByKey;
        this.producersByOutput = producersByOutput;
        this.producersByKind = producersByKind;
        this.producersByInput = producersByInput;
        this.consumersByOutput = consumersByOutput;
        this.consumersByKind = consumersByKind;
    }

    /**
     * Returns an unmodifiable view of all recipe nodes in this graph.
     *
     * @return every {@link Node} in the graph
     */
    public @UnmodifiableView ObjectSet<Node> nodes() {
        return ObjectSets.unmodifiable(nodes);
    }

    /**
     * Returns all recipe nodes in the stable order used by routing catalogs.
     *
     * <p>The order is fixed when the graph is built so services can be created
     * repeatedly without re-sorting immutable graph nodes.
     */
    public ImmutableList<Node> stableNodes() {
        return stableNodes;
    }

    /**
     * Looks up the node corresponding to a specific recipe entry.
     *
     * @param recipe the recipe entry to look up
     * @return the corresponding node, or {@code null} if not present in this graph
     */
    public @Nullable Node findNode(RecipeEntry<?> recipe) {
        return nodeByRecipe.get(recipe);
    }

    /**
     * Returns all output refs whose output content can satisfy the given input.
     *
     * <p>This is the primary lookup used by planners to find candidate recipes
     * that can produce a demanded input.
     *
     * @param input the demanded input content
     * @return unmodifiable set of producers; empty set if none match
     */
    public @UnmodifiableView ObjectSet<OutputRef> producersFor(InputContent input) {
        ObjectSet<OutputRef> refs = cachedProducersForInput(input);
        return readonly(refs);
    }

    /**
     * Returns all output refs whose output resource matches the given target resource.
     *
     * <p>This is the output-target adapter counterpart to {@link #producersFor(InputContent)}.
     * Amount is intentionally ignored by the index; callers still use each matched output's
     * actual amount to calculate craft times.
     *
     * @param output the requested output resource
     * @return unmodifiable set of producers; empty set if none match
     */
    public @UnmodifiableView ObjectSet<OutputRef> producersFor(ContentResource output) {
        ObjectSet<OutputRef> refs = cachedProducersForResource(output);
        return readonly(refs);
    }

    /**
     * Returns producer refs whose output can match a concrete variant from an enumerable input.
     *
     * <p>Resource variants use the direct output index. Kind variants use an output-kind index
     * so tag/list inputs can avoid scanning the full recipe set in large catalogs.
     */
    public @UnmodifiableView ObjectSet<OutputRef> producersFor(ContentVariant variant) {
        return switch (variant) {
            case ContentResource resource -> producersFor(resource);
            case ContentKind<?> kind -> producersForKind(kind);
        };
    }

    private @UnmodifiableView ObjectSet<OutputRef> producersForKind(ContentKind<?> kind) {
        if (kind.isEmpty()) {
            return ObjectSets.emptySet();
        }
        ObjectSet<OutputRef> refs = producersByKind.get(kind);
        return refs == null ? ObjectSets.emptySet() : ObjectSets.unmodifiable(refs);
    }

    /**
     * Returns all input refs whose input content is satisfied by the given output.
     *
     * <p>This is the reverse lookup — which recipes consume the output of another recipe.
     *
     * @param output the produced output content
     * @return unmodifiable set of consumers; empty set if none match
     */
    public @UnmodifiableView ObjectSet<InputRef> consumersFor(OutputContent output) {
        ObjectSet<InputRef> refs = cachedConsumersForOutput(output);
        return readonly(refs);
    }

    private ObjectSet<OutputRef> cachedProducersForInput(InputContent input) {
        synchronized (producersByInput) {
            ObjectSet<OutputRef> refs = producersByInput.get(input);
            if (refs != null) {
                return refs;
            }
            refs = collectProducersForInput(input);
            producersByInput.put(input, refs);
            return refs;
        }
    }

    private ObjectSet<OutputRef> cachedProducersForResource(ContentResource output) {
        OutputContent key = outputContent(output);
        synchronized (producersByOutput) {
            ObjectSet<OutputRef> refs = producersByOutput.get(key);
            if (refs != null) {
                return refs;
            }
            refs = collectProducersForResource(output);
            producersByOutput.put(key, refs);
            return refs;
        }
    }

    private ObjectSet<InputRef> cachedConsumersForOutput(OutputContent output) {
        synchronized (consumersByOutput) {
            ObjectSet<InputRef> refs = consumersByOutput.get(output);
            if (refs != null) {
                return refs;
            }
            refs = collectConsumersForOutput(output);
            consumersByOutput.put(output, refs);
            return refs;
        }
    }

    private ObjectSet<OutputRef> collectProducersForInput(InputContent input) {
        if (input.isEmpty()) {
            return ObjectSets.emptySet();
        }
        ContentResource direct = directInputResource(input);
        if (direct != null) {
            return filterProducersForInput(outputCandidates(direct), input);
        }
        if (!input.isEnumerable()) {
            return ObjectSets.emptySet();
        }

        ObjectOpenHashSet<OutputRef> candidates = new ObjectOpenHashSet<>();
        for (ContentVariant variant : input.enumerate()) {
            switch (variant) {
                case ContentResource resource -> addAll(candidates, outputCandidates(resource));
                case ContentKind<?> kind when kind.notEmpty() -> addAll(candidates, producersByKind.get(kind));
                default -> {
                }
            }
        }
        return filterProducersForInput(candidates, input);
    }

    private ObjectSet<OutputRef> collectProducersForResource(ContentResource output) {
        ObjectSet<OutputRef> candidates = outputCandidates(output);
        if (candidates == null || candidates.isEmpty()) {
            return ObjectSets.emptySet();
        }
        ObjectOpenHashSet<OutputRef> refs = new ObjectOpenHashSet<>();
        for (OutputRef candidate : candidates) {
            if (outputMatchesResource(candidate.output(), output)) {
                refs.add(candidate);
            }
        }
        return compact(refs);
    }

    private ObjectSet<InputRef> collectConsumersForOutput(OutputContent output) {
        ContentResource resource = output.asResource();
        ObjectSet<InputRef> directCandidates = inputCandidates(resource);
        ContentKind<?> kind = outputKind(output);
        ObjectSet<InputRef> kindCandidates = kind == null ? null : consumersByKind.get(kind);
        if ((directCandidates == null || directCandidates.isEmpty()) &&
                (kindCandidates == null || kindCandidates.isEmpty())) {
            return ObjectSets.emptySet();
        }

        ObjectOpenHashSet<InputRef> refs = new ObjectOpenHashSet<>();
        addMatchingConsumers(refs, directCandidates, resource);
        addMatchingConsumers(refs, kindCandidates, resource);
        return compact(refs);
    }

    private ObjectSet<OutputRef> outputCandidates(ContentResource resource) {
        ContentMatchKey key = ContentMatchKey.from(resource);
        return key == null ? null : outputCandidatesByKey.get(key);
    }

    private @Nullable ObjectSet<InputRef> inputCandidates(ContentResource resource) {
        ContentMatchKey key = ContentMatchKey.from(resource);
        return key == null ? null : inputCandidatesByKey.get(key);
    }

    private static ObjectSet<OutputRef> filterProducersForInput(ObjectSet<OutputRef> candidates, InputContent input) {
        if (candidates == null || candidates.isEmpty()) {
            return ObjectSets.emptySet();
        }
        ObjectOpenHashSet<OutputRef> refs = new ObjectOpenHashSet<>();
        for (OutputRef candidate : candidates) {
            if (input.satisfy(candidate.output().asResource())) {
                refs.add(candidate);
            }
        }
        return compact(refs);
    }

    private static void addMatchingConsumers(
            ObjectOpenHashSet<InputRef> refs,
            @Nullable ObjectSet<InputRef> candidates,
            ContentResource output
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (InputRef candidate : candidates) {
            if (candidate.input().satisfy(output)) {
                refs.add(candidate);
            }
        }
    }

    private static boolean outputMatchesResource(OutputContent output, ContentResource resource) {
        return output.asResource().matches(resource, MatchStrategy.identityAndComponent);
    }

    private static <T> void addAll(ObjectOpenHashSet<T> target, @Nullable ObjectSet<T> source) {
        if (source != null && !source.isEmpty()) {
            target.addAll(source);
        }
    }

    private static <T> ObjectSet<T> compact(ObjectOpenHashSet<T> refs) {
        return refs.isEmpty() ? ObjectSets.emptySet() : refs;
    }

    private static <T> @UnmodifiableView ObjectSet<T> readonly(ObjectSet<T> refs) {
        return refs == null || refs.isEmpty() ? ObjectSets.emptySet() : ObjectSets.unmodifiable(refs);
    }

    /**
     * Builds an immutable graph snapshot from raw recipe entries.
     *
     * <p>Each routeable recipe is projected through a default {@link ProjectionEngine},
     * its inputs/outputs are merged by content identity, and the global
     * producer/consumer indices are constructed.
     * Entries that cannot produce a deterministic projection are skipped so
     * one plugin/category recipe cannot invalidate the entire graph.
     *
     * @param recipes the recipe entries to include in the graph
     * @return a fully constructed, immutable {@code RecipeGraph}
     */
    public static RecipeGraph buildGraph(RichIterable<RecipeEntry<?>> recipes) {
        Checks.checkNotNull(recipes, "recipes");

        List<RecipeEntry<?>> recipeList = Lists.mutable.ofAll(recipes);
        if (recipeList.isEmpty()) {
            return buildIndices(Sets.immutable.empty());
        }
        GraphBuildContext buildContext = GraphBuildContext.preload(recipeList);
        if (recipeList.size() < parallelBuildThreshold) {
            return buildGraphSerial(recipeList, buildContext);
        }

        int workerCount = graphBuildWorkerCount(recipeList.size());
        if (workerCount <= 1) {
            return buildGraphSerial(recipeList, buildContext);
        }
        return buildGraphParallel(recipeList, buildContext, workerCount);
    }

    private static RecipeGraph buildGraphSerial(List<RecipeEntry<?>> recipes, GraphBuildContext buildContext) {
        ProjectionEngine projectionEngine = createDefaultProjectionEngine();
        ExecutionContext executionContext = ProjectionContexts.global();
        var nodes = Sets.mutable.<Node>empty();
        for (RecipeEntry<?> entry : recipes) {
            Node node = tryResolveNode(entry, projectionEngine, executionContext, buildContext);
            if (node != null) {
                nodes.add(node);
            }
        }
        return buildIndices(nodes.toImmutable());
    }

    private static RecipeGraph buildGraphParallel(
            List<RecipeEntry<?>> recipes,
            GraphBuildContext buildContext,
            int workerCount
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(workerCount, new GraphBuildThreadFactory());
        try {
            int chunkSize = Math.max(1, Math.min(graphBuildChunk, (recipes.size() + workerCount * 4 - 1) / (workerCount * 4)));
            AtomicInteger next = new AtomicInteger();
            @SuppressWarnings("unchecked")
            Future<List<Node>>[] futures = new Future[workerCount];
            for (int worker = 0; worker < workerCount; worker++) {
                futures[worker] = executor.submit(() -> resolveNodeChunks(recipes, next, chunkSize, buildContext));
            }

            var nodes = Sets.mutable.<Node>empty();
            for (int i = 0; i < workerCount; i++) {
                for (Node node : futures[i].get()) {
                    nodes.add(node);
                }
            }
            return buildIndices(nodes.toImmutable());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return buildGraphSerial(recipes, buildContext);
        } catch (ExecutionException exception) {
            return buildGraphSerial(recipes, buildContext);
        } finally {
            executor.shutdown();
        }
    }

    private static List<Node> resolveNodeChunks(
            List<RecipeEntry<?>> recipes,
            AtomicInteger next,
            int chunkSize,
            GraphBuildContext buildContext
    ) {
        ProjectionEngine projectionEngine = createDefaultProjectionEngine();
        ExecutionContext executionContext = ProjectionContexts.global();
        ArrayList<Node> nodes = new ArrayList<>(chunkSize);
        while (true) {
            int from = next.getAndAdd(chunkSize);
            if (from >= recipes.size()) {
                return nodes;
            }
            int to = Math.min(recipes.size(), from + chunkSize);
            for (int i = from; i < to; i++) {
                Node node = tryResolveNode(recipes.get(i), projectionEngine, executionContext, buildContext);
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
    }

    private static int graphBuildWorkerCount(int recipeCount) {
        int processors = Runtime.getRuntime().availableProcessors();
        if (processors <= 1) {
            return 1;
        }
        int byWork = Math.max(1, recipeCount / minRecipesPerBuildWorker);
        return Math.max(1, Math.min(Math.max(1, processors - 1), byWork));
    }

    private static @Nullable Node tryResolveNode(
            RecipeEntry<?> recipeEntry,
            ProjectionEngine projectionEngine,
            ExecutionContext executionContext,
            GraphBuildContext buildContext
    ) {
        try {
            return resolveNode(recipeEntry, projectionEngine, executionContext, buildContext);
        } catch (RuntimeException ignored) {
            // Broad recipe pools can contain display-only or probabilistic recipes.
            // Those do not belong in deterministic routing, but other recipes still do.
            return null;
        }
    }

    private static @Nullable Node resolveNode(
            RecipeEntry<?> recipeEntry,
            ProjectionEngine projectionEngine,
            ExecutionContext executionContext,
            GraphBuildContext buildContext
    ) {
        MachineEntry machine = buildContext.machineForSystem(recipeEntry.system());
        RecipeProjection projection = recipeEntry.resolve();
        if (projection == null) {
            return null;
        }
        return Node.resolve(recipeEntry, projection, recipeEntry.constraint(), machine, projectionEngine, executionContext);
    }

    private static RecipeGraph buildIndices(ImmutableSet<Node> resolvedNodes) {
        ObjectSet<Node> nodeSet = new ObjectOpenHashSet<>(resolvedNodes.size());
        Object2ObjectMap<RecipeEntry<?>, Node> nodeMap = new Object2ObjectOpenHashMap<>(Math.max(minNodeMapCapacity, resolvedNodes.size() * 2));
        for (Node node : resolvedNodes) {
            nodeSet.add(node);
            nodeMap.put(node.recipe(), node);
        }
        var stableNodes = Lists.mutable.ofAll(resolvedNodes);
        stableNodes.sortThis(stableNodeOrder);
        ImmutableList<Node> stableNodeList = stableNodes.toImmutable();

        int inputCount = Math.toIntExact(resolvedNodes.sumOfInt(node -> node.mergedInputs().size()));
        int outputCount = Math.toIntExact(resolvedNodes.sumOfInt(node -> node.mergedOutputs().size()));
        Object2ObjectMap<ContentMatchKey, ObjectSet<OutputRef>> outputCandidatesByKey =
                new Object2ObjectOpenHashMap<>(Math.max(minOutputIndexCapacity, outputCount * 2));
        Object2ObjectMap<ContentMatchKey, ObjectSet<InputRef>> inputCandidatesByKey =
                new Object2ObjectOpenHashMap<>(Math.max(minInputIndexCapacity, inputCount * 2));
        Object2ObjectMap<InputContent, ObjectSet<OutputRef>> producersByInput =
                new Object2ObjectOpenCustomHashMap<>(Math.max(minInputIndexCapacity, inputCount), inputHashStrategy);
        Object2ObjectMap<OutputContent, ObjectSet<OutputRef>> producersByOutput =
                new Object2ObjectOpenCustomHashMap<>(Math.max(minOutputIndexCapacity, outputCount), outputHashStrategy);
        Object2ObjectMap<ContentKind<?>, ObjectSet<OutputRef>> producersByKind =
                new Object2ObjectOpenHashMap<>(Math.max(minOutputIndexCapacity, outputCount * 2));
        Object2ObjectMap<OutputContent, ObjectSet<InputRef>> consumersByOutput =
                new Object2ObjectOpenCustomHashMap<>(Math.max(minOutputIndexCapacity, outputCount), outputHashStrategy);
        Object2ObjectMap<ContentKind<?>, ObjectSet<InputRef>> consumersByKind =
                new Object2ObjectOpenHashMap<>(Math.max(minOutputIndexCapacity, inputCount * 2));

        for (Node node : resolvedNodes) {
            for (OutputContent output : node.mergedOutputs()) {
                OutputRef ref = new OutputRef(node, output);
                addOutputCandidate(outputCandidatesByKey, output, ref);
                ContentKind<?> outputKind = outputKind(output);
                if (outputKind != null && outputKind.notEmpty()) {
                    producersByKind.computeIfAbsent(outputKind, ignored -> new ObjectOpenHashSet<>()).add(ref);
                }
            }
            for (InputContent input : node.mergedInputs()) {
                addInputConsumerIndices(inputCandidatesByKey, consumersByKind, node, input);
            }
        }

        return new RecipeGraph(
                nodeSet,
                stableNodeList,
                nodeMap,
                outputCandidatesByKey,
                inputCandidatesByKey,
                producersByOutput,
                producersByKind,
                producersByInput,
                consumersByOutput,
                consumersByKind
        );
    }

    private static void addOutputCandidate(
            Object2ObjectMap<ContentMatchKey, ObjectSet<OutputRef>> outputCandidatesByKey,
            OutputContent output,
            OutputRef ref
    ) {
        ContentMatchKey key = ContentMatchKey.from(output.asResource());
        if (key != null) {
            outputCandidatesByKey.computeIfAbsent(key, ignored -> new ObjectOpenHashSet<>()).add(ref);
        }
    }

    private static void addInputConsumerIndices(
            Object2ObjectMap<ContentMatchKey, ObjectSet<InputRef>> inputCandidatesByKey,
            Object2ObjectMap<ContentKind<?>, ObjectSet<InputRef>> consumersByKind,
            Node node,
            InputContent input
    ) {
        InputRef ref = new InputRef(node, input);
        ContentResource direct = directInputResource(input);
        if (direct != null) {
            addInputCandidate(inputCandidatesByKey, direct, ref);
            return;
        }
        if (!input.isEnumerable()) {
            return;
        }
        for (ContentVariant variant : input.enumerate()) {
            switch (variant) {
                case ContentResource resource -> addInputCandidate(inputCandidatesByKey, resource, ref);
                case ContentKind<?> kind when kind.notEmpty() ->
                        consumersByKind.computeIfAbsent(kind, ignored -> new ObjectOpenHashSet<>()).add(ref);
                default -> {
                }
            }
        }
    }

    private static void addInputCandidate(
            Object2ObjectMap<ContentMatchKey, ObjectSet<InputRef>> inputCandidatesByKey,
            ContentResource resource,
            InputRef ref
    ) {
        ContentMatchKey key = ContentMatchKey.from(resource);
        if (key != null) {
            inputCandidatesByKey.computeIfAbsent(key, ignored -> new ObjectOpenHashSet<>()).add(ref);
        }
    }

    private static final class GraphBuildContext {
        private final Map<RecipeSystem<?>, MachineEntry> machineBySystem;

        private GraphBuildContext(Map<RecipeSystem<?>, MachineEntry> machineBySystem) {
            this.machineBySystem = machineBySystem;
        }

        private static GraphBuildContext preload(Iterable<RecipeEntry<?>> recipes) {
            Map<RecipeSystem<?>, MachineEntry> machines = new HashMap<>();
            for (RecipeEntry<?> recipe : recipes) {
                RecipeSystem<?> system = recipe.system();
                machines.computeIfAbsent(system, GraphBuildContext::resolveMachine);
            }
            return new GraphBuildContext(machines);
        }

        private MachineEntry machineForSystem(RecipeSystem<?> system) {
            MachineEntry machine = machineBySystem.get(system);
            return machine == null ? MachineEntry.empty : machine;
        }

        private static MachineEntry resolveMachine(RecipeSystem<?> system) {
            MachineEntry machine = system.conventionMachine();
            if (!machine.isEmpty()) {
                return machine;
            }
            var machines = RecipeApi.instance().getMachines(system.systemId(), true);
            return machines.isEmpty() ? MachineEntry.empty : machines.getAny();
        }
    }

    private static final class GraphBuildThreadFactory implements ThreadFactory {
        private int sequence;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "cirrus-recipe-graph-build-" + sequence++);
            thread.setDaemon(true);
            return thread;
        }
    }

    private static OutputContent outputContent(ContentResource resource) {
        return switch (resource) {
            case ContentStack<?, ?> stack -> OutputContent.fromStack(stack);
            case ContentValue<?> value -> OutputContent.fromValue(value);
        };
    }

    private static @Nullable ContentResource directInputResource(InputContent input) {
        return switch (input) {
            case StackContent<?, ?> stack -> stack.content();
            case ValueContent<?> value -> value.content();
            default -> null;
        };
    }

    private static @Nullable ContentKind<?> outputKind(OutputContent output) {
        return switch (output.asResource()) {
            case ContentStack<?, ?> stack -> stack.kind();
            case ContentValue<?> ignored -> null;
        };
    }

    private static ProjectionEngine createDefaultProjectionEngine() {
        return new ProjectionEngine();
    }

    private static <T extends RecipeContent> ImmutableList<T> mergeContents(
            ImmutableCollection<T> contents,
            Strategy<T> strategy
    ) {
        Object2ObjectMap<T, T> merged = new Object2ObjectOpenCustomHashMap<>(strategy);
        for (T content : contents) {
            T existing = merged.putIfAbsent(content, content);
            if (existing == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            T next = (T) existing.copyWithAmount(existing.amount() + content.amount());
            merged.put(content, next);
        }
        var sorted = Lists.mutable.ofAll(merged.values());
        sorted.sortThis(RecipeGraph::compareMergedContent);
        return sorted.toImmutable();
    }

    private static int compareMergedContent(RecipeContent left, RecipeContent right) {
        if (left == right) {
            return 0;
        }
        int cmp = Long.compare(left.amount(), right.amount());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(contentKindRank(left), contentKindRank(right));
        if (cmp != 0) {
            return cmp;
        }
        cmp = compareContentIdentity(left, right);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(System.identityHashCode(left), System.identityHashCode(right));
    }

    private static int contentKindRank(RecipeContent content) {
        return switch (content) {
            case EmptyRecipeContent ignored -> 0;
            case StackContent<?, ?> ignored -> 1;
            case ValueContent<?> ignored -> 2;
            case StackListContent ignored -> 3;
            case TypedStackListContent<?, ?> ignored -> 4;
            case TagStackContent<?> ignored -> 5;
            default -> 6;
        };
    }

    private static int compareContentIdentity(RecipeContent left, RecipeContent right) {
        return switch (left) {
            case EmptyRecipeContent ignored -> 0;
            case StackContent<?, ?> stack when right instanceof StackContent<?, ?> other ->
                    compareStackIdentity(stack.content(), other.content());
            case ValueContent<?> value when right instanceof ValueContent<?> other -> {
                int cmp = value.type().typeId().toString().compareTo(other.type().typeId().toString());
                if (cmp != 0) {
                    yield cmp;
                }
                yield Integer.compare(value.type().hashCode(), other.type().hashCode());
            }
            case StackListContent list when right instanceof StackListContent other ->
                    compareStackLists(list.stacks(), other.stacks());
            case TypedStackListContent<?, ?> list when right instanceof TypedStackListContent<?, ?> other -> {
                int cmp = list.type().typeId().toString().compareTo(other.type().typeId().toString());
                if (cmp != 0) {
                    yield cmp;
                }
                yield compareStackLists(list.stacks(), other.stacks());
            }
            case TagStackContent<?> tag when right instanceof TagStackContent<?> other -> {
                int cmp = tag.type().typeId().toString().compareTo(other.type().typeId().toString());
                if (cmp != 0) {
                    yield cmp;
                }
                yield tag.tagKey().location().compareTo(other.tagKey().location());
            }
            default -> {
                int cmp = left.getClass().getName().compareTo(right.getClass().getName());
                if (cmp != 0) {
                    yield cmp;
                }
                yield Integer.compare(contentHashStrategy.hashCode(left), contentHashStrategy.hashCode(right));
            }
        };
    }

    private static int compareStackLists(
            ImmutableList<? extends ContentStack<?, ?>> left,
            ImmutableList<? extends ContentStack<?, ?>> right
    ) {
        int cmp = Integer.compare(left.size(), right.size());
        if (cmp != 0) {
            return cmp;
        }
        for (int i = 0; i < left.size(); i++) {
            cmp = compareStackIdentity(left.get(i), right.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static int compareStackIdentity(ContentStack<?, ?> left, ContentStack<?, ?> right) {
        if (left == right) {
            return 0;
        }
        if (left == null || left.isEmpty()) {
            return right == null || right.isEmpty() ? 0 : -1;
        }
        if (right == null || right.isEmpty()) {
            return 1;
        }
        return compareStackIdentityTyped(left.cast(), right.cast());
    }

    private static <T, S> int compareStackIdentityTyped(ContentStack<T, S> left, ContentStack<?, ?> rightRaw) {
        ContentStack<?, ?> right = rightRaw;
        int cmp = left.type().typeId().toString().compareTo(right.type().typeId().toString());
        if (cmp != 0) {
            return cmp;
        }
        cmp = left.contentType().typeId().toString().compareTo(right.contentType().typeId().toString());
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(stackContentHash(left), stackContentHash(right));
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(stackVariantHash(left), stackVariantHash(right));
    }

    private static int stackContentHash(ContentStack<?, ?> stack) {
        return stackContentHashTyped(stack.cast());
    }

    private static <T, S> int stackContentHashTyped(ContentStack<T, S> stack) {
        return stack.contentType().hash(stack.content());
    }

    private static int stackVariantHash(ContentStack<?, ?> stack) {
        return stackVariantHashTyped(stack.cast());
    }

    private static <T, S> int stackVariantHashTyped(ContentStack<T, S> stack) {
        return stack.variantType().hashIdentity(stack.value());
    }

    private static <T, S> int hashStackIdentity(ContentStack<T, S> stack) {
        if (stack.isEmpty()) {
            return ContentStack.empty().hashCode();
        }
        var type = stack.type();
        var variantType = stack.variantType();
        int hash = type.hashCode();
        hash = 31 * hash + type.contentType().hash(stack.content());
        hash = 31 * hash + variantType.hashIdentity(stack.value());
        return hash;
    }

    /**
     * Immutable snapshot of a single recipe after projection and content merging.
     *
     * <p>A {@code Node} holds the projected recipe view, the resolved machine entry,
     * and the merged input/output content sets. Content merging combines slots that
     * share the same identity (via the graph's hash strategies) and sums their amounts.
     *
     * <p>Node identity is defined solely by the underlying {@link RecipeEntry} —
     * two nodes are equal if and only if they wrap the same recipe.
     *
     * @see RecipeGraph#buildGraph(RichIterable)
     */
    public static final class Node {
        private final RecipeProjection view;
        private final RecipeConstraint<?> recipeConstraint;
        private final MachineEntry machineEntry;
        private final ProjectedRecipe projectedRecipe;
        private final ImmutableList<InputContent> mergedInputs;
        private final ImmutableList<OutputContent> mergedOutputs;
        private final RecipeRoutingHints routingHints;

        private Node(
                RecipeProjection view,
                RecipeConstraint<?> recipeConstraint,
                MachineEntry machineEntry,
                ProjectedRecipe projectedRecipe,
                ImmutableList<InputContent> mergedInputs,
                ImmutableList<OutputContent> mergedOutputs,
                RecipeRoutingHints routingHints
        ) {
            this.view = view;
            this.recipeConstraint = recipeConstraint;
            this.machineEntry = machineEntry;
            this.projectedRecipe = projectedRecipe;
            this.mergedInputs = mergedInputs;
            this.mergedOutputs = mergedOutputs;
            this.routingHints = routingHints;
        }

        private static Node resolve(
                RecipeEntry<?> recipeEntry,
                RecipeProjection view,
                RecipeConstraint<?> recipeConstraint,
                MachineEntry machineEntry,
                ProjectionEngine projectionEngine,
                ExecutionContext executionContext
        ) {
            Checks.checkNotNull(recipeEntry, "recipeEntry");
            Checks.checkNotNull(view, "view");
            Checks.checkNotNull(recipeConstraint, "recipeConstraint");
            Checks.checkNotNull(machineEntry, "machineEntry");

            ProjectedRecipe projected = projectionEngine.project(executionContext, view);
            Checks.checkArgument(projected.isConstantInputs(), "inputs must be constant");
            Checks.checkArgument(projected.isConstantOutputs(), "outputs must be constant");

            ImmutableList<InputContent> mergedInputs = mergeContents(
                    projected.inputs().reject(RecipeContent::isEmpty),
                    inputHashStrategy
            );
            ImmutableList<OutputContent> mergedOutputs = mergeContents(
                    projected.outputs().reject(RecipeContent::isEmpty),
                    outputHashStrategy
            );
            RecipeHeuristicContext heuristicContext = new RecipeHeuristicContext(
                    recipeEntry,
                    recipeEntry.id(),
                    recipeEntry.systemId(),
                    machineEntry,
                    projected,
                    mergedInputs,
                    mergedOutputs
            );
            RecipeRoutingHints hints = systemRoutingHints(recipeEntry)
                    .merge(RecipeRoutingHeuristics.matchingHints(heuristicContext));
            return new Node(view, recipeConstraint, machineEntry, projected, mergedInputs, mergedOutputs, hints);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static RecipeRoutingHints systemRoutingHints(RecipeEntry<?> recipeEntry) {
            RecipeSystem system = recipeEntry.system();
            return system.routingHints(recipeEntry.recipe());
        }

        /**
         * Returns the original recipe entry this node was built from.
         */
        public RecipeEntry<?> recipe() {
            return view.recipe();
        }

        /**
         * Returns the machine entry resolved for this recipe during graph construction.
         */
        public MachineEntry machineEntry() {
            return machineEntry;
        }

        /**
         * Returns the projected recipe after clause expansion.
         */
        public ProjectedRecipe current() {
            return projectedRecipe;
        }

        /**
         * Returns the machine constraint associated with the resolved machine entry.
         */
        public MachineConstraint machineConstraint() {
            return machineEntry.constraint();
        }

        /**
         * Returns the merged input contents sorted by amount (ascending).
         *
         * <p>Slots with identical content identity are merged and their amounts summed.
         */
        public ImmutableList<InputContent> mergedInputs() {
            return mergedInputs;
        }

        /**
         * Returns the merged output contents sorted by amount (ascending).
         *
         * <p>Slots with identical content identity are merged and their amounts summed.
         */
        public ImmutableList<OutputContent> mergedOutputs() {
            return mergedOutputs;
        }

        public RecipeRoutingHints routingHints() {
            return routingHints;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Node other)) {
                return false;
            }
            return view.recipe().equals(other.view.recipe());
        }

        @Override
        public int hashCode() {
            return view.recipe().hashCode();
        }

        @Override
        public String toString() {
            return "Node{" +
                    "recipe=" + view.recipe() +
                    ", machine=" + machineEntry +
                    ", mergedInputs=" + mergedInputs +
                    ", mergedOutputs=" + mergedOutputs +
                    '}';
        }
    }

    /**
     * A reference to a specific output slot of a {@link Node}, used as an element in
     * the producer index returned by {@link #producersFor(InputContent)}.
     *
     * @param node   the producer node
     * @param output the specific output content that matches the demanded input
     */
    public record OutputRef(
            Node node,
            OutputContent output
    ) {}

    /**
     * A reference to a specific input slot of a {@link Node}, used as an element in
     * the consumer index returned by {@link #consumersFor(OutputContent)}.
     *
     * @param node  the consumer node
     * @param input the specific input content that consumes the given output
     */
    public record InputRef(
            Node node,
            InputContent input
    ) {}

    /**
     * Cheap candidate key used only to keep graph construction away from stack component matching.
     */
    private record ContentMatchKey(
            Object type,
            @Nullable Object content
    ) {
        private static @Nullable ContentMatchKey from(ContentResource resource) {
            return switch (resource) {
                case ContentStack<?, ?> stack when stack.notEmpty() -> new ContentMatchKey(stack.type(), stack.kind());
                case ContentValue<?> value -> new ContentMatchKey(value.type(), null);
                default -> null;
            };
        }
    }
}

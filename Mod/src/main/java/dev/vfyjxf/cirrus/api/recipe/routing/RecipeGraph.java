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
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ContentTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectedRecipe;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionEngine;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ProjectionTransformer;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeHeuristicContext;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHeuristics;
import dev.vfyjxf.cirrus.api.recipe.routing.heuristic.RecipeRoutingHints;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.recipe.clause.ResolvePolicyClause;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Immutable recipe dependency graph used by the routing engine.
 *
 * <p>A {@code RecipeGraph} is built once from a collection of {@link RecipeEntry} instances
 * via {@link #buildGraph(RichIterable)}. The construction process uses routing views when
 * available and only falls back to projection for recipes that need it. Each routeable recipe
 * is normalized into merged content slots, then the graph builds a compact output-producer index:
 * <ul>
 *     <li><b>outputCandidatesByKey</b> — direct producer buckets by resource identity.</li>
 *     <li><b>producersByKind</b> — coarse producer buckets used by tag/list inputs.</li>
 * </ul>
 *
 * <p>All public accessors return unmodifiable views; the graph itself is externally
 * immutable after construction, while exact input/output lookup caches are filled lazily
 * and kept bounded.
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
    private static final int parallelBuildThreshold = 256;
    private static final int minRecipesPerBuildWorker = 96;
    private static final int graphBuildChunk = 64;
    private static final int minLookupCacheCapacity = 64;
    private static final int maxLookupCacheCapacity = 4096;
    private static final int maxInitialOutputCandidateIndexCapacity = 1_048_576;
    private static final int maxInitialKindIndexCapacity = 16_384;
    private static final int smallRawMergeThreshold = 8;
    private static final String noStableRecipeId = "";
    private static final OutputRefCursor emptyOutputRefCursor = new OutputRefCursor() {
        @Override
        public boolean next() {
            return false;
        }

        @Override
        public int recipeIndex() {
            throw new NoSuchElementException();
        }

        @Override
        public int outputIndex() {
            throw new NoSuchElementException();
        }
    };

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
    private final Object2ObjectMap<Object, RefSet> outputCandidatesByKey;
    private final BoundedLookupCache<OutputContent, RefSet> producersByOutput;
    private final Object2ObjectMap<ContentKind<?>, RefSet> producersByKind;
    private final BoundedLookupCache<InputContent, RefSet> producersByInput;
    private final BoundedLookupCache<OutputContent, RefSet> consumersByOutput;

    private RecipeGraph(
            ObjectSet<Node> nodes,
            ImmutableList<Node> stableNodes,
            Object2ObjectMap<Object, RefSet> outputCandidatesByKey,
            BoundedLookupCache<OutputContent, RefSet> producersByOutput,
            Object2ObjectMap<ContentKind<?>, RefSet> producersByKind,
            BoundedLookupCache<InputContent, RefSet> producersByInput,
            BoundedLookupCache<OutputContent, RefSet> consumersByOutput
    ) {
        this.nodes = nodes;
        this.stableNodes = stableNodes;
        this.outputCandidatesByKey = outputCandidatesByKey;
        this.producersByOutput = producersByOutput;
        this.producersByKind = producersByKind;
        this.producersByInput = producersByInput;
        this.consumersByOutput = consumersByOutput;
    }

    private Node nodeAt(int recipeIndex) {
        return stableNodes.get(recipeIndex);
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
     * Returns all recipe nodes in deterministic graph order used by routing catalogs.
     *
     * <p>The order is fixed when the graph is built. It follows source recipe order
     * after unresolved and duplicate entries have been removed. Candidate tie-breaking
     * still uses {@link dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog.StableRecipeKey}
     * rather than this list position.
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
        for (Node node : stableNodes) {
            if (node.recipe().equals(recipe)) {
                return node;
            }
        }
        return null;
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
        return readonlyOutputRefs(cachedProducersForInput(input));
    }

    /**
     * Returns a low-allocation cursor over producer refs for planner hot paths.
     *
     * <p>The public set-returning APIs intentionally preserve the object based
     * view for callers that need collection semantics. Planners that only scan
     * candidates should use this cursor so packed graph refs do not have to be
     * materialized as {@link OutputRef} records for every route.
     */
    public OutputRefCursor producerCursorFor(InputContent input) {
        return outputRefCursor(cachedProducersForInput(input));
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
        return readonlyOutputRefs(cachedProducersForResource(output));
    }

    public OutputRefCursor producerCursorFor(ContentResource output) {
        return outputRefCursor(cachedProducersForResource(output));
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

    public OutputRefCursor producerCursorFor(ContentVariant variant) {
        return switch (variant) {
            case ContentResource resource -> producerCursorFor(resource);
            case ContentKind<?> kind -> outputRefCursor(kind.isEmpty() ? null : producersByKind.get(kind));
        };
    }

    private @UnmodifiableView ObjectSet<OutputRef> producersForKind(ContentKind<?> kind) {
        if (kind.isEmpty()) {
            return ObjectSets.emptySet();
        }
        return readonlyOutputRefs(producersByKind.get(kind));
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
        return readonlyInputRefs(cachedConsumersForOutput(output));
    }

    private RefSet cachedProducersForInput(InputContent input) {
        return producersByInput.getOrCompute(input, this::collectProducersForInput);
    }

    private RefSet cachedProducersForResource(ContentResource output) {
        OutputContent key = outputContent(output);
        return producersByOutput.getOrCompute(key, ignored -> collectProducersForResource(output));
    }

    private RefSet cachedConsumersForOutput(OutputContent output) {
        return consumersByOutput.getOrCompute(output, this::collectConsumersForOutput);
    }

    private RefSet collectProducersForInput(InputContent input) {
        if (input.isEmpty()) {
            return RefSet.empty();
        }
        ContentResource direct = directInputResource(input);
        if (direct != null) {
            return filterProducersForInput(outputCandidates(direct), input);
        }
        if (!input.isEnumerable()) {
            return RefSet.empty();
        }

        RefSet candidates = RefSet.mutable();
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

    private RefSet collectProducersForResource(ContentResource output) {
        RefSet candidates = outputCandidates(output);
        if (candidates == null || candidates.isEmpty()) {
            return RefSet.empty();
        }
        RefSet refs = RefSet.mutable();
        for (RefCursor cursor = candidates.cursor(); cursor.hasNext(); ) {
            long candidate = cursor.nextLong();
            if (outputMatchesResource(outputAt(candidate), output)) {
                refs.append(candidate);
            }
        }
        return refs;
    }

    private RefSet collectConsumersForOutput(OutputContent output) {
        ContentResource resource = output.asResource();
        RefSet refs = RefSet.mutable();
        for (int recipeIndex = 0; recipeIndex < stableNodes.size(); recipeIndex++) {
            Node node = stableNodes.get(recipeIndex);
            for (int inputIndex = 0; inputIndex < node.inputCount(); inputIndex++) {
                if (node.inputAt(inputIndex).satisfy(resource)) {
                    refs.append(packRef(recipeIndex, inputIndex));
                }
            }
        }
        return refs.isEmpty() ? RefSet.empty() : refs;
    }

    private RefSet outputCandidates(ContentResource resource) {
        Object key = contentMatchKey(resource);
        return key == null ? null : outputCandidatesByKey.get(key);
    }

    private RefSet filterProducersForInput(RefSet candidates, InputContent input) {
        if (candidates == null || candidates.isEmpty()) {
            return RefSet.empty();
        }
        RefSet refs = RefSet.mutable();
        for (RefCursor cursor = candidates.cursor(); cursor.hasNext(); ) {
            long candidate = cursor.nextLong();
            if (input.satisfy(outputAt(candidate).asResource())) {
                refs.append(candidate);
            }
        }
        return refs;
    }

    private static boolean outputMatchesResource(OutputContent output, ContentResource resource) {
        return output.asResource().matches(resource, MatchStrategy.identityAndComponent);
    }

    private static void addAll(RefSet target, @Nullable RefSet source) {
        if (source != null && !source.isEmpty()) {
            target.addAll(source);
        }
    }

    private OutputContent outputAt(long ref) {
        return nodeAt(refRecipeIndex(ref)).outputAt(refSlotIndex(ref));
    }

    private InputContent inputAt(long ref) {
        return nodeAt(refRecipeIndex(ref)).inputAt(refSlotIndex(ref));
    }

    private @UnmodifiableView ObjectSet<OutputRef> readonlyOutputRefs(@Nullable RefSet refs) {
        return refs == null || refs.isEmpty() ? ObjectSets.emptySet() : new OutputRefSet(this, refs);
    }

    private @UnmodifiableView ObjectSet<InputRef> readonlyInputRefs(@Nullable RefSet refs) {
        return refs == null || refs.isEmpty() ? ObjectSets.emptySet() : new InputRefSet(this, refs);
    }

    private static OutputRefCursor outputRefCursor(@Nullable RefSet refs) {
        if (refs == null || refs.isEmpty()) {
            return emptyOutputRefCursor;
        }
        RefCursor cursor = refs.cursor();
        return new OutputRefCursor() {
            private long current = RefSet.noRef;

            @Override
            public boolean next() {
                if (!cursor.hasNext()) {
                    current = RefSet.noRef;
                    return false;
                }
                current = cursor.nextLong();
                return true;
            }

            @Override
            public int recipeIndex() {
                if (current == RefSet.noRef) {
                    throw new NoSuchElementException();
                }
                return refRecipeIndex(current);
            }

            @Override
            public int outputIndex() {
                if (current == RefSet.noRef) {
                    throw new NoSuchElementException();
                }
                return refSlotIndex(current);
            }
        };
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
            return buildIndices(List.of());
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
        ExecutionContext executionContext = ProjectionContexts.global();
        ProjectionUsePlan projectionUsePlan = ProjectionUsePlan.create(executionContext);
        ArrayList<Node> nodes = new ArrayList<>(recipes.size());
        for (int i = 0; i < recipes.size(); i++) {
            RecipeEntry<?> entry = recipes.get(i);
            Node node = tryResolveNode(i, entry, projectionUsePlan, buildContext);
            if (node != null) {
                nodes.add(node);
            }
        }
        return buildIndices(nodes);
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
            Node[] resolved = new Node[recipes.size()];
            @SuppressWarnings("unchecked")
            Future<?>[] futures = new Future[workerCount];
            for (int worker = 0; worker < workerCount; worker++) {
                futures[worker] = executor.submit(() -> resolveNodeChunks(recipes, resolved, next, chunkSize, buildContext));
            }

            for (int i = 0; i < workerCount; i++) {
                futures[i].get();
            }
            return buildGraphFromStableNodes(stableNodeListFromArray(resolved));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return buildGraphSerial(recipes, buildContext);
        } catch (ExecutionException exception) {
            return buildGraphSerial(recipes, buildContext);
        } finally {
            executor.shutdown();
        }
    }

    private static void resolveNodeChunks(
            List<RecipeEntry<?>> recipes,
            Node[] resolved,
            AtomicInteger next,
            int chunkSize,
            GraphBuildContext buildContext
    ) {
        ExecutionContext executionContext = ProjectionContexts.global();
        ProjectionUsePlan projectionUsePlan = ProjectionUsePlan.create(executionContext);
        while (true) {
            int from = next.getAndAdd(chunkSize);
            if (from >= recipes.size()) {
                return;
            }
            int to = Math.min(recipes.size(), from + chunkSize);
            for (int i = from; i < to; i++) {
                resolved[i] = tryResolveNode(i, recipes.get(i), projectionUsePlan, buildContext);
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
            int sourceOrder,
            RecipeEntry<?> recipeEntry,
            ProjectionUsePlan projectionUsePlan,
            GraphBuildContext buildContext
    ) {
        try {
            return resolveNode(sourceOrder, recipeEntry, projectionUsePlan, buildContext);
        } catch (RuntimeException ignored) {
            // Broad recipe pools can contain display-only or probabilistic recipes.
            // Those do not belong in deterministic routing, but other recipes still do.
            return null;
        }
    }

    private static @Nullable Node resolveNode(
            int sourceOrder,
            RecipeEntry<?> recipeEntry,
            ProjectionUsePlan projectionUsePlan,
            GraphBuildContext buildContext
    ) {
        RecipeSystem<?> system = recipeEntry.system();
        MachineEntry machine = buildContext.machineForSystem(system);
        String stableSystemId = buildContext.stableSystemId(system);
        RoutingRecipeView routingView = routingView(recipeEntry);
        if (routingView != null) {
            return Node.fromRoutingView(sourceOrder, recipeEntry, machine, stableSystemId, routingView);
        }
        RecipeProjection projection = recipeEntry.resolve();
        if (projection == null) {
            return null;
        }
        return Node.resolve(sourceOrder, recipeEntry, projection, machine, stableSystemId, projectionUsePlan);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable RoutingRecipeView routingView(RecipeEntry<?> recipeEntry) {
        RecipeSystem system = recipeEntry.system();
        return system.routingView(ResolveContext.empty(), recipeEntry.recipe());
    }

    private static RecipeGraph buildIndices(Collection<Node> resolvedNodes) {
        return buildGraphFromStableNodes(sortedUniqueNodes(resolvedNodes));
    }

    private static RecipeGraph buildGraphFromStableNodes(ImmutableList<Node> stableNodeList) {
        ObjectSet<Node> nodeSet = new ImmutableListBackedNodeSet(stableNodeList);

        int inputCount = 0;
        int outputCount = 0;
        for (Node node : stableNodeList) {
            inputCount += node.inputCount();
            outputCount += node.outputCount();
        }
        Object2ObjectMap<Object, RefSet> outputCandidatesByKey =
                new Object2ObjectOpenHashMap<>(outputCandidateIndexCapacity(outputCount));
        BoundedLookupCache<InputContent, RefSet> producersByInput =
                new BoundedLookupCache<>(lookupCacheCapacity(inputCount), inputHashStrategy);
        BoundedLookupCache<OutputContent, RefSet> producersByOutput =
                new BoundedLookupCache<>(lookupCacheCapacity(outputCount), outputHashStrategy);
        Object2ObjectMap<ContentKind<?>, RefSet> producersByKind =
                new Object2ObjectOpenHashMap<>(kindIndexCapacity(outputCount));
        BoundedLookupCache<OutputContent, RefSet> consumersByOutput =
                new BoundedLookupCache<>(lookupCacheCapacity(outputCount), outputHashStrategy);

        for (int recipeIndex = 0; recipeIndex < stableNodeList.size(); recipeIndex++) {
            Node node = stableNodeList.get(recipeIndex);
            for (int outputIndex = 0; outputIndex < node.outputCount(); outputIndex++) {
                OutputContent output = node.outputAt(outputIndex);
                long ref = packRef(recipeIndex, outputIndex);
                addOutputCandidate(outputCandidatesByKey, output, ref);
                ContentKind<?> outputKind = outputKind(output);
                if (outputKind != null && outputKind.notEmpty()) {
                    putRef(producersByKind, outputKind, ref);
                }
            }
        }
        return new RecipeGraph(
                nodeSet,
                stableNodeList,
                outputCandidatesByKey,
                producersByOutput,
                producersByKind,
                producersByInput,
                consumersByOutput
        );
    }

    private static ImmutableList<Node> sortedUniqueNodes(Collection<Node> resolvedNodes) {
        var nodes = Lists.mutable.<Node>ofInitialCapacity(resolvedNodes.size());
        for (Node node : resolvedNodes) {
            nodes.add(node);
        }
        return uniqueInSourceOrder(nodes);
    }

    private static ImmutableList<Node> stableNodeListFromArray(Node[] resolvedNodes) {
        var nodes = Lists.mutable.<Node>ofInitialCapacity(resolvedNodes.length);
        for (Node node : resolvedNodes) {
            if (node != null) {
                nodes.add(node);
            }
        }
        return uniqueInSourceOrder(nodes);
    }

    private static ImmutableList<Node> uniqueInSourceOrder(org.eclipse.collections.api.list.MutableList<Node> nodes) {
        if (nodes.size() < 2) {
            return nodes.toImmutable();
        }

        Map<RecipeEntry<?>, Boolean> seen = new HashMap<>(Math.min(nodes.size(), 16_384));
        var unique = Lists.mutable.<Node>ofInitialCapacity(nodes.size());
        boolean removedDuplicate = false;
        for (Node node : nodes) {
            if (seen.putIfAbsent(node.recipe(), Boolean.TRUE) == null) {
                unique.add(node);
            } else {
                removedDuplicate = true;
            }
        }
        return removedDuplicate ? unique.toImmutable() : nodes.toImmutable();
    }

    private static int lookupCacheCapacity(int slotCount) {
        return Math.max(minLookupCacheCapacity, Math.min(maxLookupCacheCapacity, slotCount * 2));
    }

    private static int outputCandidateIndexCapacity(int slotCount) {
        return Math.max(minOutputIndexCapacity, Math.min(maxInitialOutputCandidateIndexCapacity, slotCount));
    }

    private static int kindIndexCapacity(int slotCount) {
        return Math.max(minOutputIndexCapacity, Math.min(maxInitialKindIndexCapacity, slotCount));
    }

    int producersByInputCacheSize() {
        return producersByInput.size();
    }

    int producersByOutputCacheSize() {
        return producersByOutput.size();
    }

    int consumersByOutputCacheSize() {
        return consumersByOutput.size();
    }

    private static void addOutputCandidate(
            Object2ObjectMap<Object, RefSet> outputCandidatesByKey,
            OutputContent output,
            long ref
    ) {
        Object key = contentMatchKey(output.asResource());
        if (key != null) {
            putRef(outputCandidatesByKey, key, ref);
        }
    }

    private static <K> void putRef(Object2ObjectMap<K, RefSet> map, K key, long ref) {
        RefSet existing = map.get(key);
        if (existing == null) {
            map.put(key, RefSet.of(ref));
            return;
        }
        existing.append(ref);
    }

    private static final class GraphBuildContext {
        private final Map<RecipeSystem<?>, MachineEntry> machineBySystem;
        private final Map<RecipeSystem<?>, String> stableSystemIdBySystem;

        private GraphBuildContext(
                Map<RecipeSystem<?>, MachineEntry> machineBySystem,
                Map<RecipeSystem<?>, String> stableSystemIdBySystem
        ) {
            this.machineBySystem = machineBySystem;
            this.stableSystemIdBySystem = stableSystemIdBySystem;
        }

        private static GraphBuildContext preload(Iterable<RecipeEntry<?>> recipes) {
            Map<RecipeSystem<?>, MachineEntry> machines = new HashMap<>();
            Map<RecipeSystem<?>, String> stableSystemIds = new HashMap<>();
            for (RecipeEntry<?> recipe : recipes) {
                RecipeSystem<?> system = recipe.system();
                machines.computeIfAbsent(system, GraphBuildContext::resolveMachine);
                stableSystemIds.computeIfAbsent(system, ignored -> system.systemId().toString());
            }
            return new GraphBuildContext(machines, stableSystemIds);
        }

        private MachineEntry machineForSystem(RecipeSystem<?> system) {
            MachineEntry machine = machineBySystem.get(system);
            return machine == null ? MachineEntry.empty : machine;
        }

        private String stableSystemId(RecipeSystem<?> system) {
            String stableSystemId = stableSystemIdBySystem.get(system);
            return stableSystemId == null ? system.systemId().toString() : stableSystemId;
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

    private static final class ProjectionUsePlan {
        private final ExecutionContext executionContext;
        private @Nullable ProjectionEngine projectionEngine;

        private ProjectionUsePlan(ExecutionContext executionContext) {
            this.executionContext = executionContext;
        }

        private static ProjectionUsePlan create(ExecutionContext executionContext) {
            return new ProjectionUsePlan(executionContext);
        }

        private Node resolve(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                RecipeProjection projection,
                MachineEntry machineEntry,
                String stableSystemId
        ) {
            if (requiresProjectionEngine(projection)) {
                ProjectedRecipe projected = projectionEngine().project(executionContext, projection);
                Checks.checkArgument(projected.isConstantInputs(), "inputs must be constant");
                Checks.checkArgument(projected.isConstantOutputs(), "outputs must be constant");
                return Node.fromProjected(sourceOrder, recipeEntry, machineEntry, stableSystemId, projected);
            }
            return Node.fromProjection(sourceOrder, recipeEntry, machineEntry, stableSystemId, projection);
        }

        private boolean requiresProjectionEngine(RecipeProjection projection) {
            if (projection.hasClause(ResolvePolicyClause.type)) {
                return true;
            }
            if (hasEnabledProjectionTransformer(projection)) {
                return true;
            }
            return hasEnabledContentTransformer(projection);
        }

        private ProjectionEngine projectionEngine() {
            ProjectionEngine engine = projectionEngine;
            if (engine == null) {
                engine = createDefaultProjectionEngine();
                projectionEngine = engine;
            }
            return engine;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean hasEnabledProjectionTransformer(RecipeProjection projection) {
            if (projection.clauses().allClauses().isEmpty()) {
                return false;
            }
            RecipeApi api = RecipeApi.instance();
            for (RecipeClause clause : projection.clauses().allClauses()) {
                ProjectionTransformer transformer = projectionTransformer(api, clause);
                if (transformer == null || !transformer.plugin().enabled(executionContext)) {
                    continue;
                }
                if (transformer.selectProjection(executionContext, projection)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean hasEnabledContentTransformer(RecipeProjection projection) {
            RecipeApi api = RecipeApi.instance();
            for (ClausedContent<InputContent> input : projection.inputs()) {
                if (hasEnabledContentTransformer(api, projection, input, true)) {
                    return true;
                }
            }
            for (ClausedContent<OutputContent> output : projection.outputs()) {
                if (hasEnabledContentTransformer(api, projection, output, false)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean hasEnabledContentTransformer(
                RecipeApi api,
                RecipeProjection projection,
                ClausedContent<? extends RecipeContent> content,
                boolean asInput
        ) {
            if (content.clauses().allClauses().isEmpty()) {
                return false;
            }
            for (RecipeClause clause : content.clauses().allClauses()) {
                ContentTransformer transformer = contentTransformer(api, clause);
                if (transformer == null || !transformer.plugin().enabled(executionContext)) {
                    continue;
                }
                if (transformer.select(executionContext, projection, content.content(), asInput)) {
                    return true;
                }
            }
            return false;
        }

        private static @Nullable ProjectionTransformer<?> projectionTransformer(RecipeApi api, RecipeClause clause) {
            if (!api.hasClause(clause.id())) {
                return null;
            }
            return api.findProjectionTransformer(api.getClauseType(clause.id()));
        }

        private static @Nullable ContentTransformer<?, ?> contentTransformer(RecipeApi api, RecipeClause clause) {
            if (!api.hasClause(clause.id())) {
                return null;
            }
            return api.findContentTransformer(api.getClauseType(clause.id()));
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

    private static <T extends RecipeContent> Iterable<T> rejectEmpty(ImmutableList<T> contents) {
        return () -> new ObjectIterator<>() {
            private int index;
            private @Nullable T next;
            private boolean loaded;

            @Override
            public boolean hasNext() {
                load();
                return next != null;
            }

            @Override
            public T next() {
                load();
                if (next == null) {
                    throw new NoSuchElementException();
                }
                T value = next;
                next = null;
                loaded = false;
                return value;
            }

            private void load() {
                if (loaded) {
                    return;
                }
                loaded = true;
                while (index < contents.size()) {
                    T candidate = contents.get(index++);
                    if (candidate.notEmpty()) {
                        next = candidate;
                        return;
                    }
                }
                next = null;
            }
        };
    }

    private static <T extends RecipeContent> ImmutableList<T> mergeContents(
            Iterable<T> contents,
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

    private static ImmutableList<InputContent> mergeInputs(ImmutableList<ClausedContent<InputContent>> contents) {
        if (contents.size() == 1) {
            InputContent content = contents.getFirst().content();
            Checks.checkArgument(content.constantly(), "inputs must be constant");
            return content.isEmpty() ? Lists.immutable.empty() : Lists.immutable.of(content);
        }
        Object2ObjectMap<InputContent, InputContent> merged = new Object2ObjectOpenCustomHashMap<>(inputHashStrategy);
        for (ClausedContent<InputContent> input : contents) {
            InputContent content = input.content();
            Checks.checkArgument(content.constantly(), "inputs must be constant");
            if (content.isEmpty()) {
                continue;
            }
            InputContent existing = merged.putIfAbsent(content, content);
            if (existing == null) {
                continue;
            }
            InputContent next = (InputContent) existing.copyWithAmount(existing.amount() + content.amount());
            merged.put(content, next);
        }
        var sorted = Lists.mutable.ofAll(merged.values());
        sorted.sortThis(RecipeGraph::compareMergedContent);
        return sorted.toImmutable();
    }

    private static ImmutableList<OutputContent> mergeOutputs(ImmutableList<ClausedContent<OutputContent>> contents) {
        if (contents.size() == 1) {
            OutputContent content = contents.getFirst().content();
            Checks.checkArgument(content.constantly(), "outputs must be constant");
            return content.isEmpty() ? Lists.immutable.empty() : Lists.immutable.of(content);
        }
        Object2ObjectMap<OutputContent, OutputContent> merged = new Object2ObjectOpenCustomHashMap<>(outputHashStrategy);
        for (ClausedContent<OutputContent> output : contents) {
            OutputContent content = output.content();
            Checks.checkArgument(content.constantly(), "outputs must be constant");
            if (content.isEmpty()) {
                continue;
            }
            OutputContent existing = merged.putIfAbsent(content, content);
            if (existing == null) {
                continue;
            }
            OutputContent next = (OutputContent) existing.copyWithAmount(existing.amount() + content.amount());
            merged.put(content, next);
        }
        var sorted = Lists.mutable.ofAll(merged.values());
        sorted.sortThis(RecipeGraph::compareMergedContent);
        return sorted.toImmutable();
    }

    private static ImmutableList<InputContent> mergeRawInputs(Iterable<? extends InputContent> contents) {
        if (contents instanceof Collection<? extends InputContent> collection && collection.size() == 1) {
            InputContent content = collection.iterator().next();
            Checks.checkArgument(content.constantly(), "inputs must be constant");
            return content.isEmpty() ? Lists.immutable.empty() : Lists.immutable.of(content);
        }
        Object2ObjectMap<InputContent, InputContent> merged = new Object2ObjectOpenCustomHashMap<>(inputHashStrategy);
        for (InputContent content : contents) {
            Checks.checkArgument(content.constantly(), "inputs must be constant");
            if (content.isEmpty()) {
                continue;
            }
            InputContent existing = merged.putIfAbsent(content, content);
            if (existing == null) {
                continue;
            }
            InputContent next = (InputContent) existing.copyWithAmount(existing.amount() + content.amount());
            merged.put(content, next);
        }
        var sorted = Lists.mutable.ofAll(merged.values());
        sorted.sortThis(RecipeGraph::compareMergedContent);
        return sorted.toImmutable();
    }

    private static ImmutableList<OutputContent> mergeRawOutputs(Iterable<? extends OutputContent> contents) {
        if (contents instanceof Collection<? extends OutputContent> collection && collection.size() == 1) {
            OutputContent content = collection.iterator().next();
            Checks.checkArgument(content.constantly(), "outputs must be constant");
            return content.isEmpty() ? Lists.immutable.empty() : Lists.immutable.of(content);
        }
        Object2ObjectMap<OutputContent, OutputContent> merged = new Object2ObjectOpenCustomHashMap<>(outputHashStrategy);
        for (OutputContent content : contents) {
            Checks.checkArgument(content.constantly(), "outputs must be constant");
            if (content.isEmpty()) {
                continue;
            }
            OutputContent existing = merged.putIfAbsent(content, content);
            if (existing == null) {
                continue;
            }
            OutputContent next = (OutputContent) existing.copyWithAmount(existing.amount() + content.amount());
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
     * <p>The graph keeps only routing data here. The full projection/clause tree is
     * intentionally not retained because large catalogs otherwise keep millions of
     * clause/list/map objects alive after construction.
     *
     * @see RecipeGraph#buildGraph(RichIterable)
     */
    public static final class Node {
        private final RecipeEntry<?> recipe;
        private final MachineEntry machineEntry;
        private final @Nullable Object mergedInputs;
        private final @Nullable Object mergedOutputs;
        private final RecipeRoutingHints routingHints;
        private final String stableSystemId;
        private final String stableRecipeId;
        private final int stableRecipeHash;
        private final int sourceOrder;

        private Node(
                RecipeEntry<?> recipe,
                MachineEntry machineEntry,
                @Nullable Object mergedInputs,
                @Nullable Object mergedOutputs,
                RecipeRoutingHints routingHints,
                String stableSystemId,
                String stableRecipeId,
                int stableRecipeHash,
                int sourceOrder
        ) {
            this.recipe = recipe;
            this.machineEntry = machineEntry;
            this.mergedInputs = mergedInputs;
            this.mergedOutputs = mergedOutputs;
            this.routingHints = routingHints;
            this.stableSystemId = stableSystemId;
            this.stableRecipeId = stableRecipeId;
            this.stableRecipeHash = stableRecipeHash;
            this.sourceOrder = sourceOrder;
        }

        private static Node resolve(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                RecipeProjection view,
                MachineEntry machineEntry,
                String stableSystemId,
                ProjectionUsePlan projectionUsePlan
        ) {
            Checks.checkNotNull(recipeEntry, "recipeEntry");
            Checks.checkNotNull(view, "view");
            Checks.checkNotNull(machineEntry, "machineEntry");
            Checks.checkNotNull(stableSystemId, "stableSystemId");

            return projectionUsePlan.resolve(sourceOrder, recipeEntry, view, machineEntry, stableSystemId);
        }

        private static Node fromProjected(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                MachineEntry machineEntry,
                String stableSystemId,
                ProjectedRecipe projected
        ) {
            Checks.checkArgument(projected.isConstantInputs(), "inputs must be constant");
            Checks.checkArgument(projected.isConstantOutputs(), "outputs must be constant");

            ImmutableList<InputContent> mergedInputs = mergeContents(
                    rejectEmpty(projected.inputs()),
                    inputHashStrategy
            );
            ImmutableList<OutputContent> mergedOutputs = mergeContents(
                    rejectEmpty(projected.outputs()),
                    outputHashStrategy
            );
            return create(sourceOrder, recipeEntry, machineEntry, stableSystemId, mergedInputs, mergedOutputs);
        }

        private static Node fromProjection(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                MachineEntry machineEntry,
                String stableSystemId,
                RecipeProjection projection
        ) {
            ImmutableList<InputContent> mergedInputs = mergeInputs(projection.inputs());
            ImmutableList<OutputContent> mergedOutputs = mergeOutputs(projection.outputs());
            return create(sourceOrder, recipeEntry, machineEntry, stableSystemId, mergedInputs, mergedOutputs);
        }

        private static Node fromRoutingView(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                MachineEntry machineEntry,
                String stableSystemId,
                RoutingRecipeView view
        ) {
            if (view.inputs() instanceof Collection<? extends InputContent> inputs
                    && view.outputs() instanceof Collection<? extends OutputContent> outputs) {
                return createCompact(
                        sourceOrder,
                        recipeEntry,
                        machineEntry,
                        stableSystemId,
                        compactRawInputSlots(inputs),
                        compactRawOutputSlots(outputs)
                );
            }
            ImmutableList<InputContent> mergedInputs = mergeRawInputs(view.inputs());
            ImmutableList<OutputContent> mergedOutputs = mergeRawOutputs(view.outputs());
            return create(sourceOrder, recipeEntry, machineEntry, stableSystemId, mergedInputs, mergedOutputs);
        }

        private static Node create(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                MachineEntry machineEntry,
                String stableSystemId,
                ImmutableList<InputContent> mergedInputs,
                ImmutableList<OutputContent> mergedOutputs
        ) {
            return createCompact(
                    sourceOrder,
                    recipeEntry,
                    machineEntry,
                    stableSystemId,
                    compactSlots(mergedInputs),
                    compactSlots(mergedOutputs)
            );
        }

        private static Node createCompact(
                int sourceOrder,
                RecipeEntry<?> recipeEntry,
                MachineEntry machineEntry,
                String stableSystemId,
                @Nullable Object mergedInputs,
                @Nullable Object mergedOutputs
        ) {
            RecipeRoutingHints hints = systemRoutingHints(recipeEntry);
            if (RecipeRoutingHeuristics.hasRules()) {
                RecipeHeuristicContext heuristicContext = new RecipeHeuristicContext(
                        recipeEntry,
                        recipeEntry.id(),
                        recipeEntry.systemId(),
                        machineEntry,
                        slotsAsImmutableList(mergedInputs),
                        slotsAsImmutableList(mergedOutputs)
                );
                hints = hints.merge(RecipeRoutingHeuristics.matchingHints(heuristicContext));
            }
            hints = RecipeRoutingHints.canonical(hints);
            return new Node(
                    recipeEntry,
                    machineEntry,
                    mergedInputs,
                    mergedOutputs,
                    hints,
                    stableSystemId,
                    lazyStableRecipeId(recipeEntry),
                    recipeEntry.hashCode(),
                    sourceOrder
            );
        }

        private static @Nullable Object compactRawInputSlots(Collection<? extends InputContent> contents) {
            if (contents.isEmpty()) {
                return null;
            }
            if (contents.size() > smallRawMergeThreshold) {
                return compactSlots(mergeRawInputs(contents));
            }
            if (contents.size() > 1) {
                return compactSmallRawInputs(contents);
            }
            InputContent content = firstElement(contents);
            Checks.checkArgument(content.constantly(), "inputs must be constant");
            return content.isEmpty() ? null : content;
        }

        private static @Nullable Object compactRawOutputSlots(Collection<? extends OutputContent> contents) {
            if (contents.isEmpty()) {
                return null;
            }
            if (contents.size() > smallRawMergeThreshold) {
                return compactSlots(mergeRawOutputs(contents));
            }
            if (contents.size() > 1) {
                return compactSmallRawOutputs(contents);
            }
            OutputContent content = firstElement(contents);
            Checks.checkArgument(content.constantly(), "outputs must be constant");
            return content.isEmpty() ? null : content;
        }

        private static @Nullable Object compactSmallRawInputs(Collection<? extends InputContent> contents) {
            InputContent[] merged = new InputContent[contents.size()];
            int count = 0;
            boolean mergedDuplicate = false;
            nextContent:
            for (InputContent content : contents) {
                Checks.checkArgument(content.constantly(), "inputs must be constant");
                if (content.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    InputContent existing = merged[i];
                    if (inputHashStrategy.equals(existing, content)) {
                        merged[i] = (InputContent) existing.copyWithAmount(existing.amount() + content.amount());
                        mergedDuplicate = true;
                        continue nextContent;
                    }
                }
                merged[count++] = content;
            }
            return compactSortedArray(merged, count, !mergedDuplicate && count == merged.length);
        }

        private static @Nullable Object compactSmallRawOutputs(Collection<? extends OutputContent> contents) {
            OutputContent[] merged = new OutputContent[contents.size()];
            int count = 0;
            boolean mergedDuplicate = false;
            nextContent:
            for (OutputContent content : contents) {
                Checks.checkArgument(content.constantly(), "outputs must be constant");
                if (content.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    OutputContent existing = merged[i];
                    if (outputHashStrategy.equals(existing, content)) {
                        merged[i] = (OutputContent) existing.copyWithAmount(existing.amount() + content.amount());
                        mergedDuplicate = true;
                        continue nextContent;
                    }
                }
                merged[count++] = content;
            }
            return compactSortedArray(merged, count, !mergedDuplicate && count == merged.length);
        }

        private static <T extends RecipeContent> @Nullable Object compactSortedArray(
                T[] contents,
                int count,
                boolean fullArray
        ) {
            if (count == 0) {
                return null;
            }
            sortContentArray(contents, count);
            if (count == 1) {
                return contents[0];
            }
            return fullArray ? contents : Arrays.copyOf(contents, count);
        }

        private static void sortContentArray(RecipeContent[] contents, int count) {
            for (int i = 1; i < count; i++) {
                RecipeContent value = contents[i];
                int j = i - 1;
                while (j >= 0 && compareMergedContent(contents[j], value) > 0) {
                    contents[j + 1] = contents[j];
                    j--;
                }
                contents[j + 1] = value;
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> T firstElement(Collection<? extends T> contents) {
            if (contents instanceof List<?> list) {
                return (T) list.get(0);
            }
            return contents.iterator().next();
        }

        private static String lazyStableRecipeId(RecipeEntry<?> recipeEntry) {
            if (RecipeRoutingHeuristics.hasRules()) {
                return Objects.toString(recipeEntry.id(), "null");
            }
            return noStableRecipeId;
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
            return recipe;
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
            return new ProjectedRecipe(recipe, mergedInputs(), mergedOutputs());
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
            return slotsAsImmutableList(mergedInputs);
        }

        /**
         * Returns the merged output contents sorted by amount (ascending).
         *
         * <p>Slots with identical content identity are merged and their amounts summed.
         */
        public ImmutableList<OutputContent> mergedOutputs() {
            return slotsAsImmutableList(mergedOutputs);
        }

        public int inputCount() {
            return slotCount(mergedInputs);
        }

        public InputContent inputAt(int index) {
            return slotAt(mergedInputs, index);
        }

        public int outputCount() {
            return slotCount(mergedOutputs);
        }

        public OutputContent outputAt(int index) {
            return slotAt(mergedOutputs, index);
        }

        private static <T extends RecipeContent> @Nullable Object compactSlots(ImmutableList<T> contents) {
            return switch (contents.size()) {
                case 0 -> null;
                case 1 -> contents.getFirst();
                default -> contentArray(contents);
            };
        }

        private static RecipeContent[] contentArray(ImmutableList<? extends RecipeContent> contents) {
            RecipeContent[] array = new RecipeContent[contents.size()];
            for (int i = 0; i < contents.size(); i++) {
                array[i] = contents.get(i);
            }
            return array;
        }

        private static int slotCount(@Nullable Object slots) {
            if (slots == null) {
                return 0;
            }
            if (slots instanceof ImmutableList<?> list) {
                return list.size();
            }
            if (slots instanceof RecipeContent[] array) {
                return array.length;
            }
            return 1;
        }

        @SuppressWarnings("unchecked")
        private static <T extends RecipeContent> T slotAt(@Nullable Object slots, int index) {
            if (slots == null) {
                throw new IndexOutOfBoundsException(index);
            }
            if (slots instanceof ImmutableList<?> list) {
                return (T) list.get(index);
            }
            if (slots instanceof RecipeContent[] array) {
                return (T) array[index];
            }
            if (index == 0) {
                return (T) slots;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @SuppressWarnings("unchecked")
        private static <T extends RecipeContent> ImmutableList<T> slotsAsImmutableList(@Nullable Object slots) {
            if (slots == null) {
                return Lists.immutable.empty();
            }
            if (slots instanceof ImmutableList<?> list) {
                return (ImmutableList<T>) list;
            }
            if (slots instanceof RecipeContent[] array) {
                var list = Lists.mutable.<T>ofInitialCapacity(array.length);
                for (RecipeContent content : array) {
                    list.add((T) content);
                }
                return list.toImmutable();
            }
            return Lists.immutable.of((T) slots);
        }

        public RecipeRoutingHints routingHints() {
            return routingHints;
        }

        public String stableSystemId() {
            return stableSystemId;
        }

        public String stableRecipeId() {
            return stableRecipeId;
        }

        public int stableRecipeHash() {
            return stableRecipeHash;
        }

        public int sourceOrder() {
            return sourceOrder;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Node other)) {
                return false;
            }
            return recipe.equals(other.recipe);
        }

        @Override
        public int hashCode() {
            return stableRecipeHash;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "recipe=" + recipe +
                    ", machine=" + machineEntry +
                    ", mergedInputs=" + mergedInputs() +
                    ", mergedOutputs=" + mergedOutputs() +
                    '}';
        }
    }

    /**
     * A reference to a specific output slot of a {@link Node}, used as an element in
     * the producer index returned by {@link #producersFor(InputContent)}.
     */
    public record OutputRef(
            int recipeIndex,
            int outputIndex
    ) {
        public OutputRef {
            Checks.checkArgument(recipeIndex >= 0, "recipeIndex must be non-negative");
            Checks.checkArgument(outputIndex >= 0, "outputIndex must be non-negative");
        }

        private OutputRef(int recipeIndex, int outputIndex, Node ignoredNode, OutputContent ignoredOutput) {
            this(recipeIndex, outputIndex);
        }

        public Node node(RecipeGraph graph) {
            return graph.nodeAt(recipeIndex);
        }

        public OutputContent output(RecipeGraph graph) {
            return node(graph).outputAt(outputIndex);
        }
    }

    public interface OutputRefCursor {
        boolean next();

        int recipeIndex();

        int outputIndex();
    }

    /**
     * A reference to a specific input slot of a {@link Node}, used as an element in
     * the consumer index returned by {@link #consumersFor(OutputContent)}.
     */
    public record InputRef(
            int recipeIndex,
            int inputIndex
    ) {
        public InputRef {
            Checks.checkArgument(recipeIndex >= 0, "recipeIndex must be non-negative");
            Checks.checkArgument(inputIndex >= 0, "inputIndex must be non-negative");
        }

        private InputRef(int recipeIndex, int inputIndex, Node ignoredNode, InputContent ignoredInput) {
            this(recipeIndex, inputIndex);
        }

        public Node node(RecipeGraph graph) {
            return graph.nodeAt(recipeIndex);
        }

        public InputContent input(RecipeGraph graph) {
            return node(graph).inputAt(inputIndex);
        }
    }

    private static long packRef(int recipeIndex, int slotIndex) {
        Checks.checkArgument(recipeIndex >= 0, "recipeIndex must be non-negative");
        Checks.checkArgument(slotIndex >= 0, "slotIndex must be non-negative");
        return ((long) recipeIndex << 32) | (slotIndex & 0xFFFF_FFFFL);
    }

    private static int refRecipeIndex(long ref) {
        return (int) (ref >>> 32);
    }

    private static int refSlotIndex(long ref) {
        return (int) ref;
    }

    private static final class RefSet {
        private static final long noRef = -1L;
        private static final RefSet empty = new RefSet(noRef);
        private long singleton;
        private @Nullable long[] refs;
        private int refCount;

        private RefSet(long singleton) {
            this.singleton = singleton;
        }

        private static RefSet empty() {
            return empty;
        }

        private static RefSet mutable() {
            return new RefSet(noRef);
        }

        private static RefSet of(long ref) {
            return new RefSet(ref);
        }

        private boolean isEmpty() {
            return this == empty || (refs == null && singleton == noRef);
        }

        private int size() {
            if (isEmpty()) {
                return 0;
            }
            return refs == null ? 1 : refCount;
        }

        private void add(long ref) {
            if (contains(ref)) {
                return;
            }
            append(ref);
        }

        private void append(long ref) {
            if (this == empty) {
                throw new UnsupportedOperationException("empty ref set is immutable");
            }
            if (refs == null) {
                if (singleton == noRef) {
                    singleton = ref;
                    return;
                }
                if (singleton == ref) {
                    return;
                }
                long[] expanded = new long[4];
                expanded[0] = singleton;
                expanded[1] = ref;
                refs = expanded;
                refCount = 2;
                singleton = noRef;
                return;
            }
            ensureCapacity(refCount + 1);
            refs[refCount++] = ref;
        }

        private void addAll(RefSet source) {
            if (source == null || source.isEmpty()) {
                return;
            }
            if (isEmpty()) {
                appendAll(source);
                return;
            }
            for (RefCursor cursor = source.cursor(); cursor.hasNext(); ) {
                add(cursor.nextLong());
            }
        }

        private boolean contains(long ref) {
            if (isEmpty()) {
                return false;
            }
            if (refs == null) {
                return singleton == ref;
            }
            for (int i = 0; i < refCount; i++) {
                if (refs[i] == ref) {
                    return true;
                }
            }
            return false;
        }

        private RefCursor cursor() {
            if (isEmpty()) {
                return RefCursor.empty();
            }
            if (refs == null) {
                return RefCursor.singleton(singleton);
            }
            return RefCursor.array(refs, refCount);
        }

        private void appendAll(RefSet source) {
            for (RefCursor cursor = source.cursor(); cursor.hasNext(); ) {
                append(cursor.nextLong());
            }
        }

        private void ensureCapacity(int minCapacity) {
            if (refs == null || refs.length >= minCapacity) {
                return;
            }
            int nextCapacity = refs.length + (refs.length >> 1);
            if (nextCapacity < minCapacity) {
                nextCapacity = minCapacity;
            }
            refs = Arrays.copyOf(refs, nextCapacity);
        }
    }

    private interface RefCursor {
        boolean hasNext();

        long nextLong();

        static RefCursor empty() {
            return new RefCursor() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public long nextLong() {
                    throw new NoSuchElementException();
                }
            };
        }

        static RefCursor singleton(long ref) {
            return new RefCursor() {
                private boolean consumed;

                @Override
                public boolean hasNext() {
                    return !consumed;
                }

                @Override
                public long nextLong() {
                    if (consumed) {
                        throw new NoSuchElementException();
                    }
                    consumed = true;
                    return ref;
                }
            };
        }

        static RefCursor array(long[] refs, int size) {
            return new RefCursor() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public long nextLong() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return refs[index++];
                }
            };
        }
    }

    private abstract static class AbstractRefView<T> extends AbstractObjectSet<T> {
        protected final RecipeGraph graph;
        protected final RefSet refs;

        private AbstractRefView(RecipeGraph graph, RefSet refs) {
            this.graph = graph;
            this.refs = refs;
        }

        @Override
        public int size() {
            return refs.size();
        }

        @Override
        public boolean add(T value) {
            throw new UnsupportedOperationException("recipe graph refs are immutable");
        }

        @Override
        public boolean remove(Object value) {
            throw new UnsupportedOperationException("recipe graph refs are immutable");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("recipe graph refs are immutable");
        }
    }

    private static final class OutputRefSet extends AbstractRefView<OutputRef> {
        private OutputRefSet(RecipeGraph graph, RefSet refs) {
            super(graph, refs);
        }

        @Override
        public ObjectIterator<OutputRef> iterator() {
            RefCursor cursor = refs.cursor();
            return new ObjectIterator<>() {
                @Override
                public boolean hasNext() {
                    return cursor.hasNext();
                }

                @Override
                public OutputRef next() {
                    long ref = cursor.nextLong();
                    return new OutputRef(refRecipeIndex(ref), refSlotIndex(ref));
                }
            };
        }

        @Override
        public boolean contains(Object value) {
            return value instanceof OutputRef ref && refs.contains(packRef(ref.recipeIndex(), ref.outputIndex()));
        }
    }

    private static final class InputRefSet extends AbstractRefView<InputRef> {
        private InputRefSet(RecipeGraph graph, RefSet refs) {
            super(graph, refs);
        }

        @Override
        public ObjectIterator<InputRef> iterator() {
            RefCursor cursor = refs.cursor();
            return new ObjectIterator<>() {
                @Override
                public boolean hasNext() {
                    return cursor.hasNext();
                }

                @Override
                public InputRef next() {
                    long ref = cursor.nextLong();
                    return new InputRef(refRecipeIndex(ref), refSlotIndex(ref));
                }
            };
        }

        @Override
        public boolean contains(Object value) {
            return value instanceof InputRef ref && refs.contains(packRef(ref.recipeIndex(), ref.inputIndex()));
        }
    }

    private static final class ImmutableListBackedNodeSet extends AbstractObjectSet<Node> {
        private final ImmutableList<Node> nodes;

        private ImmutableListBackedNodeSet(ImmutableList<Node> nodes) {
            this.nodes = nodes;
        }

        @Override
        public ObjectIterator<Node> iterator() {
            return new ObjectIterator<>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return index < nodes.size();
                }

                @Override
                public Node next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return nodes.get(index++);
                }
            };
        }

        @Override
        public int size() {
            return nodes.size();
        }

        @Override
        public boolean contains(Object value) {
            if (!(value instanceof Node node)) {
                return false;
            }
            for (Node candidate : nodes) {
                if (candidate.equals(node)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean add(Node node) {
            throw new UnsupportedOperationException("recipe graph nodes are immutable");
        }

        @Override
        public boolean remove(Object value) {
            throw new UnsupportedOperationException("recipe graph nodes are immutable");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("recipe graph nodes are immutable");
        }
    }

    private static final class BoundedLookupCache<K, V> {
        private final int capacity;
        private final Object2ObjectMap<K, CacheEntry<K, V>> entries;
        private CacheEntry<K, V> head;
        private CacheEntry<K, V> tail;

        private BoundedLookupCache(int capacity, Strategy<K> strategy) {
            Checks.checkArgument(capacity > 0, "capacity must be positive");
            this.capacity = capacity;
            this.entries = new Object2ObjectOpenCustomHashMap<>(capacity, strategy);
        }

        private synchronized V getOrCompute(K key, Function<K, V> loader) {
            CacheEntry<K, V> entry = entries.get(key);
            if (entry != null) {
                moveToHead(entry);
                return entry.value;
            }

            V value = loader.apply(key);
            CacheEntry<K, V> next = new CacheEntry<>(key, value);
            entries.put(key, next);
            linkHead(next);
            if (entries.size() > capacity) {
                evictTail();
            }
            return value;
        }

        private synchronized int size() {
            return entries.size();
        }

        private void moveToHead(CacheEntry<K, V> entry) {
            if (entry == head) {
                return;
            }
            unlink(entry);
            linkHead(entry);
        }

        private void linkHead(CacheEntry<K, V> entry) {
            entry.previous = null;
            entry.next = head;
            if (head != null) {
                head.previous = entry;
            }
            head = entry;
            if (tail == null) {
                tail = entry;
            }
        }

        private void unlink(CacheEntry<K, V> entry) {
            CacheEntry<K, V> previous = entry.previous;
            CacheEntry<K, V> next = entry.next;
            if (previous != null) {
                previous.next = next;
            } else {
                head = next;
            }
            if (next != null) {
                next.previous = previous;
            } else {
                tail = previous;
            }
            entry.previous = null;
            entry.next = null;
        }

        private void evictTail() {
            CacheEntry<K, V> entry = tail;
            if (entry == null) {
                return;
            }
            unlink(entry);
            entries.remove(entry.key);
        }

        private static final class CacheEntry<K, V> {
            private final K key;
            private final V value;
            private CacheEntry<K, V> previous;
            private CacheEntry<K, V> next;

            private CacheEntry(K key, V value) {
                this.key = key;
                this.value = value;
            }
        }
    }

    private static @Nullable Object contentMatchKey(ContentResource resource) {
        return switch (resource) {
            case ContentStack<?, ?> stack when stack.notEmpty() -> new StackContentMatchKey(stack.type(), stack.kind());
            case ContentValue<?> value -> value.type();
            default -> null;
        };
    }

    /**
     * Stack candidate key used only to keep graph construction away from stack component matching.
     */
    private record StackContentMatchKey(
            Object type,
            @Nullable Object content
    ) {
    }
}

package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
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
import org.eclipse.collections.api.factory.SortedSets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Comparator;

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
 * <p>All public accessors return unmodifiable views; the graph itself is fully immutable
 * after construction.
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

    private static final int inputIndexCapacity = 10_000;
    private static final int outputIndexCapacity = 5_000;
    private static final int nodeMapCapacity = 1_000;

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
                case StackListContent list when right instanceof StackListContent list2 ->
                    list.mergeable(list2);
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
    private final Object2ObjectMap<RecipeEntry<?>, Node> nodeByRecipe;
    private final Object2ObjectMap<InputContent, ObjectSet<OutputRef>> producersByInput;
    private final Object2ObjectMap<OutputContent, ObjectSet<InputRef>> consumersByOutput;

    private RecipeGraph(
        ObjectSet<Node> nodes,
        Object2ObjectMap<RecipeEntry<?>, Node> nodeByRecipe,
        Object2ObjectMap<InputContent, ObjectSet<OutputRef>> producersByInput,
        Object2ObjectMap<OutputContent, ObjectSet<InputRef>> consumersByOutput
    ) {
        this.nodes = nodes;
        this.nodeByRecipe = nodeByRecipe;
        this.producersByInput = producersByInput;
        this.consumersByOutput = consumersByOutput;
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
        ObjectSet<OutputRef> refs = producersByInput.get(input);
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
        ObjectSet<InputRef> refs = consumersByOutput.get(output);
        return refs == null ? ObjectSets.emptySet() : ObjectSets.unmodifiable(refs);
    }

    /**
     * Builds an immutable graph snapshot from raw recipe entries.
     *
     * <p>Each recipe is projected through a default {@link ProjectionEngine},
     * its inputs/outputs are merged by content identity, and the global
     * producer/consumer indices are constructed.
     *
     * @param recipes the recipe entries to include in the graph
     * @return a fully constructed, immutable {@code RecipeGraph}
     */
    public static RecipeGraph buildGraph(RichIterable<RecipeEntry<?>> recipes) {
        Checks.checkNotNull(recipes, "recipes");

        ProjectionEngine projectionEngine = createDefaultProjectionEngine();
        ExecutionContext executionContext = new ExecutionContext();
        ImmutableSet<Node> nodes = recipes.collect(entry -> resolveNode(entry, projectionEngine, executionContext)).toImmutableSet();
        return buildIndices(nodes);
    }

    private static Node resolveNode(
        RecipeEntry<?> recipeEntry,
        ProjectionEngine projectionEngine,
        ExecutionContext executionContext
    ) {
        MachineEntry machine = recipeEntry.system().conventionMachine();
        if (machine.isEmpty()) {
            var machines = RecipeApi.instance().getMachines(recipeEntry.system().systemId(), true);
            if (machines.notEmpty()) {
                machine = machines.getAny();
            }
        }
        return Node.resolve(recipeEntry.resolve(), recipeEntry.constraint(), machine, projectionEngine, executionContext);
    }

    private static RecipeGraph buildIndices(ImmutableSet<Node> resolvedNodes) {
        ObjectSet<Node> nodeSet = new ObjectOpenHashSet<>(resolvedNodes.size());
        Object2ObjectMap<RecipeEntry<?>, Node> nodeMap = new Object2ObjectOpenHashMap<>(Math.max(nodeMapCapacity, resolvedNodes.size() * 2));
        for (Node node : resolvedNodes) {
            nodeSet.add(node);
            nodeMap.put(node.recipe(), node);
        }

        int inputCount = Math.toIntExact(resolvedNodes.sumOfInt(node -> node.mergedInputs().size()));
        int outputCount = Math.toIntExact(resolvedNodes.sumOfInt(node -> node.mergedOutputs().size()));
        Object2ObjectMap<RecipeContent, ObjectSet<ContentRef<InputContent>>> inputDemands =
            new Object2ObjectOpenCustomHashMap<>(Math.max(inputIndexCapacity, inputCount * 2), contentHashStrategy);
        Object2ObjectMap<RecipeContent, ObjectSet<ContentRef<OutputContent>>> outputSupplies =
            new Object2ObjectOpenCustomHashMap<>(Math.max(outputIndexCapacity, outputCount * 2), contentHashStrategy);

        for (Node node : resolvedNodes) {
            for (InputContent input : node.mergedInputs()) {
                inputDemands.computeIfAbsent(input, ignored -> new ObjectOpenHashSet<>()).add(new ContentRef<>(node, input));
            }
            for (OutputContent output : node.mergedOutputs()) {
                outputSupplies.computeIfAbsent(output, ignored -> new ObjectOpenHashSet<>()).add(new ContentRef<>(node, output));
            }
        }

        Object2ObjectMap<InputContent, ObjectSet<OutputRef>> producersByInput =
            new Object2ObjectOpenCustomHashMap<>(Math.max(inputIndexCapacity, inputCount * 2), inputHashStrategy);
        Object2ObjectMap<OutputContent, ObjectSet<InputRef>> consumersByOutput =
            new Object2ObjectOpenCustomHashMap<>(Math.max(outputIndexCapacity, outputCount * 2), outputHashStrategy);

        for (Node node : resolvedNodes) {
            for (InputContent input : node.mergedInputs()) {
                ObjectSet<ContentRef<OutputContent>> producers = outputSupplies.get(input);
                if (producers == null || producers.isEmpty()) {
                    continue;
                }
                ObjectSet<OutputRef> refs = producersByInput.computeIfAbsent(input, ignored -> new ObjectOpenHashSet<>());
                for (ContentRef<OutputContent> producer : producers) {
                    refs.add(new OutputRef(producer.node(), producer.content()));
                }
            }
            for (OutputContent output : node.mergedOutputs()) {
                ObjectSet<ContentRef<InputContent>> consumers = inputDemands.get(output);
                if (consumers == null || consumers.isEmpty()) {
                    continue;
                }
                ObjectSet<InputRef> refs = consumersByOutput.computeIfAbsent(output, ignored -> new ObjectOpenHashSet<>());
                for (ContentRef<InputContent> consumer : consumers) {
                    refs.add(new InputRef(consumer.node(), consumer.content()));
                }
            }
        }

        return new RecipeGraph(nodeSet, nodeMap, producersByInput, consumersByOutput);
    }

    private static ProjectionEngine createDefaultProjectionEngine() {
        ProjectionEngine engine = new ProjectionEngine();
        engine.detectClause(CirrusTypes.Clauses.fluidContainerUnpack);
        return engine;
    }

    private static <T extends RecipeContent> ImmutableSortedSet<T> mergeContents(
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
        return SortedSets.immutable.ofAll(Comparator.comparingLong(RecipeContent::amount), merged.values());
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
        private final ImmutableSortedSet<InputContent> mergedInputs;
        private final ImmutableSortedSet<OutputContent> mergedOutputs;

        private Node(
            RecipeProjection view,
            RecipeConstraint<?> recipeConstraint,
            MachineEntry machineEntry,
            ProjectedRecipe projectedRecipe,
            ImmutableSortedSet<InputContent> mergedInputs,
            ImmutableSortedSet<OutputContent> mergedOutputs
        ) {
            this.view = view;
            this.recipeConstraint = recipeConstraint;
            this.machineEntry = machineEntry;
            this.projectedRecipe = projectedRecipe;
            this.mergedInputs = mergedInputs;
            this.mergedOutputs = mergedOutputs;
        }

        private static Node resolve(
            RecipeProjection view,
            RecipeConstraint<?> recipeConstraint,
            MachineEntry machineEntry,
            ProjectionEngine projectionEngine,
            ExecutionContext executionContext
        ) {
            Checks.checkNotNull(view, "view");
            Checks.checkNotNull(recipeConstraint, "recipeConstraint");
            Checks.checkNotNull(machineEntry, "machineEntry");

            ProjectedRecipe projected = projectionEngine.project(executionContext, view);
            Checks.checkArgument(projected.isConstantInputs(), "inputs must be constant");
            Checks.checkArgument(projected.isConstantOutputs(), "outputs must be constant");

            ImmutableSortedSet<InputContent> mergedInputs = mergeContents(
                projected.inputs().reject(RecipeContent::isEmpty),
                inputHashStrategy
            );
            ImmutableSortedSet<OutputContent> mergedOutputs = mergeContents(
                projected.outputs().reject(RecipeContent::isEmpty),
                outputHashStrategy
            );
            return new Node(view, recipeConstraint, machineEntry, projected, mergedInputs, mergedOutputs);
        }

        /** Returns the original recipe entry this node was built from. */
        public RecipeEntry<?> recipe() {
            return view.recipe();
        }

        /** Returns the machine entry resolved for this recipe during graph construction. */
        public MachineEntry machineEntry() {
            return machineEntry;
        }

        /** Returns the projected recipe after clause expansion. */
        public ProjectedRecipe current() {
            return projectedRecipe;
        }

        /** Returns the machine constraint associated with the resolved machine entry. */
        public MachineConstraint machineConstraint() {
            return machineEntry.constraint();
        }

        /**
         * Returns the merged input contents sorted by amount (ascending).
         *
         * <p>Slots with identical content identity are merged and their amounts summed.
         */
        public ImmutableSortedSet<InputContent> mergedInputs() {
            return mergedInputs;
        }

        /**
         * Returns the merged output contents sorted by amount (ascending).
         *
         * <p>Slots with identical content identity are merged and their amounts summed.
         */
        public ImmutableSortedSet<OutputContent> mergedOutputs() {
            return mergedOutputs;
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
    ) {
    }

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
    ) {
    }

    /**
     * Internal reference pairing a node with one of its content slots.
     * Used only during index construction.
     */
    private record ContentRef<T extends RecipeContent>(
        Node node,
        T content
    ) {
    }
}

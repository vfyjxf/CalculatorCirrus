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
import dev.vfyjxf.cirrus.api.recipe.routing.evaluator.RoutingEvaluator;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeContext;
import dev.vfyjxf.cirrus.api.recipe.view.RecipeView;
import dev.vfyjxf.cirrus.api.recipe.view.facet.TransformedView;
import dev.vfyjxf.cirrus.api.recipe.view.facet.ViewTransformPipeline;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.factory.SortedSets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class RecipeGraph {

    static final int inputMapCapacity = 10_000;
    static final int outputMapCapacity = 5_000;

    //region hash
    static final Strategy<RecipeContent> contentHashStrategy = new Strategy<>() {

        @Override
        public int hashCode(RecipeContent input) {
            return switch (input) {
                case EmptyRecipeContent e -> e.hashCode();
                case StackContent<?, ?> stack -> hashContent(stack.content());
                case ValueContent<?> value -> value.type().hashCode();
                case StackListContent list -> {
                    int hash = 0;
                    for (var stack : list.stacks()) hash = 31 * hash + hashContent(stack);
                    yield hash;
                }
                case TypedStackListContent<?, ?> list -> {
                    int hash = list.type().hashCode();
                    for (var stack : list.stacks()) hash = 31 * hash + hashContent(stack);
                    yield hash;
                }
                case TagStackContent<?> tag -> {
                    int hash = tag.type().hashCode();
                    hash = 31 * hash + tag.tagKey().hashCode();
                    yield hash;
                }
                default -> input.getClass().hashCode();
            };
        }

        @Override
        public boolean equals(RecipeContent content1, RecipeContent content2) {
            return switch (content1) {
                case EmptyRecipeContent e -> e == content2;
                case StackContent<?, ?> stack when content2 instanceof StackContent<?, ?> stack2 ->
                    stack.satisfy(stack2.content());
                case ValueContent<?> value when content2 instanceof ValueContent<?> value2 ->
                    value.satisfy(value2.content());
                case StackListContent list when content2 instanceof StackListContent list2 -> list.mergeable(list2);
                case TypedStackListContent<?, ?> list when content2 instanceof TypedStackListContent<?, ?> list2 -> {
                    if (list.type() != list2.type() || list.size() != list2.size()) yield false;
                    for (int i = 0; i < list.size(); i++) {
                        if (!list.stacks().get(i).matches(list2.stacks().get(i))) yield false;
                    }
                    yield true;
                }
                case TagStackContent<?> tag when content2 instanceof TagStackContent<?> tag2 ->
                    tag.type() == tag2.type() &&
                    tag.tagKey() == tag2.tagKey();
                default -> {
                    if (content1 instanceof ComplexContent complexContent1 &&
                        content2 instanceof ComplexContent complexContent2
                    ) {
                        //TODO:试图去回想起来为什么我之前会写成sameConstraint，但是我忘了:P
                        yield complexContent1.mergeable(complexContent2);
                    }
                    yield content1 == content2;
                }
            };
        }
    };

    static final Strategy<InputContent> inputHashStrategy = new Strategy<>() {
        @Override
        public int hashCode(InputContent o) {
            return contentHashStrategy.hashCode(o);
        }

        @Override
        public boolean equals(InputContent a, InputContent b) {
            return contentHashStrategy.equals(a, b);
        }
    };

    static final Strategy<OutputContent> outputHashStrategy = new Strategy<>() {
        @Override
        public int hashCode(OutputContent object) {
            return switch (object) {
                case EmptyRecipeContent e -> e.hashCode();
                case StackContent<?, ?> stack -> hashContent(stack.content());
                case ValueContent<?> value -> value.type().hashCode();
            };
        }

        @Override
        public boolean equals(OutputContent content, OutputContent content2) {
            return switch (content) {
                case StackContent<?, ?> stack when content2 instanceof StackContent<?, ?> stack2 ->
                    stack.content().matches(stack2.content());
                case ValueContent<?> value when content2 instanceof ValueContent<?> value2 ->
                    value.type() == value2.type();
                default -> content == content2;
            };
        }
    };

    private static <T, S> int hashContent(ContentStack<T, S> contentStack) {
        if (contentStack.isEmpty()) return ContentStack.empty().hashCode();
        var type = contentStack.type();
        var variantType = contentStack.variantType();
        int hash = type.hashCode();
        hash = 31 * hash + type.contentType().hash(contentStack.content());
        hash = 31 * hash + variantType.hashIdentity(contentStack.value());
        return hash;
    }

    //endregion

    //region basic data
    private final RecipeContext calculationContext = new RecipeContext();
    private final ViewTransformPipeline pipeline = new ViewTransformPipeline();
    private final ObjectSet<Node> nodes = new ObjectOpenHashSet<>();
    //endregion

    //region path link

    //input source from  output
    Object2ObjectMap<InputContent, ObjectSet<ContentNode<OutputContent>>> inputFrom = new Object2ObjectOpenCustomHashMap<>(inputMapCapacity, contentHashStrategy);
    //output target to input
    Object2ObjectMap<OutputContent, ObjectSet<ContentNode<InputContent>>> outputGoto = new Object2ObjectOpenCustomHashMap<>(outputMapCapacity, outputHashStrategy);

    //endregion

    @SuppressWarnings("unchecked")
    private static <T extends RecipeContent> ImmutableSortedSet<T> mergeContents(ImmutableCollection<T> contents, Strategy<T> strategy) {
        Object2ObjectMap<T, T> map = new Object2ObjectOpenCustomHashMap<>(strategy);
        for (T content : contents) {
            T merged = map.putIfAbsent(content, content);
            if (merged == null) continue;
            map.put(content, (T) merged.copyWithAmount(merged.amount() + content.amount()));
        }
        return SortedSets.immutable.ofAll(Comparator.comparingLong(RecipeContent::amount), map.values());
    }

    //region nodes

    void addNode(Node node) {
        Checks.checkArgument(node.current.isConstantInputs(), "inputs must be constant");
        Checks.checkArgument(node.current.isConstantOutputs(), "outputs must be constant");
        nodes.add(node);
    }

    public static final class Node {
        final RecipeView view;
        final RecipeConstraint<?> recipeConstraint;
        MachineEntry machineEntry;
        TransformedView current;
        ImmutableSortedSet<InputContent> mergedInputs;
        ImmutableSortedSet<OutputContent> mergedOutputs;
        Links links;

        Node(
            RecipeView view,
            RecipeConstraint<?> recipeConstraint
        ) {
            this(view, recipeConstraint, MachineEntry.empty);
        }

        Node(
            RecipeView view,
            RecipeConstraint<?> recipeConstraint,
            MachineEntry machineEntry
        ) {
            Checks.checkNotNull(view, "view");
            Checks.checkNotNull(recipeConstraint, "recipeConstraint");

            this.view = view;
            this.recipeConstraint = recipeConstraint;
            this.machineEntry = machineEntry;
        }

        void apply(ViewTransformPipeline pipeline, RecipeContext context) {
            this.current = pipeline.transformView(context, view);
            this.mergedInputs = mergeContents(current.inputs().reject(RecipeContent::isEmpty), inputHashStrategy);
            this.mergedOutputs = mergeContents(current.outputs().reject(RecipeContent::isEmpty), outputHashStrategy);
        }

        RecipeEntry<?> recipe() {return view.recipe();}

        MachineConstraint machineConstraint() {return machineEntry.constraint();}

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Node node = (Node) o;
            return view.recipe().equals(node.view.recipe());
        }

        @Override
        public int hashCode() {
            return view.recipe().hashCode();
        }

        @Override
        public String toString() {
            return "Node{" +
                   "view=" + view +
                   ", recipeConstraint=" + recipeConstraint +
                   ", machineEntry=" + machineEntry +
                   ", current=" + current +
                   ", mergedInputs=" + mergedInputs +
                   ", mergedOutputs=" + mergedOutputs +
                   ", links=" + links +
                   '}';
        }
    }

    static class Links {
        TransformedView sourceRecipe;
        Object2ObjectMap<InputContent, ObjectSet<ContentNode<OutputContent>>> inputFromOutput =
            new Object2ObjectOpenCustomHashMap<>(16, contentHashStrategy);
        Object2ObjectMap<OutputContent, ObjectSet<ContentNode<InputContent>>> outputGotoInput =
            new Object2ObjectOpenCustomHashMap<>(8, outputHashStrategy);
    }

    private record ContentNode<T extends RecipeContent>(
        Node node,
        T content
    ) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            ContentNode<?> that = (ContentNode<?>) o;
            if (node != that.node) return false;
            if (content instanceof InputContent input1 && that.content instanceof InputContent input2) {
                return contentHashStrategy.equals(input1, input2);
            }
            if (content instanceof OutputContent output1 && that.content instanceof OutputContent output2) {
                return outputHashStrategy.equals(output1, output2);
            }
            return content == that.content;
        }

        @Override
        public int hashCode() {
            int result = node.hashCode();
            if (content instanceof InputContent input) {
                result = 31 * result + contentHashStrategy.hashCode(input);
            }
            if (content instanceof OutputContent output) {
                result = 31 * result + outputHashStrategy.hashCode(output);
            }
            return result;
        }
    }

    //endregion

    public static RecipeGraph buildGraph(RichIterable<RecipeEntry<?>> recipes) {
        RecipeGraph graph = createDefaultGraph();
        ImmutableSet<Node> nodes = recipes.collect(recipe -> {
            var machine = recipe.system().conventionMachine();
            if (machine.isEmpty()) {
                var api = RecipeApi.instance();
                var machines = api.getMachines(recipe.system().systemId(), true);
                if (machines.notEmpty()) machine = machines.getAny();
            }
            Node node = new Node(recipe.resolve(), recipe.constraint(), machine);
            node.apply(graph.pipeline, graph.calculationContext);
            return node;
        }).toImmutableSet();
        nodes.forEach(graph::addNode);
        //what we need?
        //find where that input can from
        //find where that output can goto
        int inputCount = nodes.stream().mapToInt(node -> node.mergedInputs.size()).sum();
        int outputCount = nodes.stream().mapToInt(node -> node.mergedOutputs.size()).sum();
        Object2ObjectMap<RecipeContent, ObjectSet<ContentNode<InputContent>>> inputFrom = new Object2ObjectOpenCustomHashMap<>(inputCount, contentHashStrategy);
        Object2ObjectMap<RecipeContent, ObjectSet<ContentNode<OutputContent>>> outputFrom = new Object2ObjectOpenCustomHashMap<>(outputCount, contentHashStrategy);
        for (Node node : nodes) {
            for (var input : node.mergedInputs) {
                var nodeInput = new ContentNode<>(node, input);
                inputFrom.computeIfAbsent(input, k -> new ObjectOpenHashSet<>()).add(nodeInput);
            }
            for (var output : node.mergedOutputs) {
                var nodeOutput = new ContentNode<>(node, output);
                outputFrom.computeIfAbsent(output, k -> new ObjectOpenHashSet<>()).add(nodeOutput);
            }
        }
        for (Node node : nodes) {
            node.links = new Links();
            node.links.sourceRecipe = node.current;
            for (var input : node.mergedInputs) {
                for (var from : outputFrom.getOrDefault(input, ObjectSet.of())) {
                    graph.inputFrom.computeIfAbsent(input, k -> new ObjectOpenHashSet<>()).add(from);
                    node.links.inputFromOutput.computeIfAbsent(input, k -> new ObjectOpenHashSet<>()).add(from);
                }
            }
            for (var output : node.mergedOutputs) {
                for (var to : inputFrom.getOrDefault(output, ObjectSet.of())) {
                    graph.outputGoto.computeIfAbsent(output, k -> new ObjectOpenHashSet<>()).add(to);
                    node.links.outputGotoInput.computeIfAbsent(output, k -> new ObjectOpenHashSet<>()).add(to);
                }
            }
        }
        return graph;
    }

    private static RecipeGraph createDefaultGraph() {
        RecipeGraph graph = new RecipeGraph();
        graph.pipeline.detectFacet(CirrusTypes.Facets.UNWRAP_FLUID_HANDLER);
        return graph;
    }

    public record RoutingResult(

    ) {}


    public @Nullable RoutingResult findRouting(
        RecipeEntry<?> recipe,
        int craftTimes,
        RoutingEvaluator evaluator,
        Stock stock
    ) {
        Node node = nodes.stream()
                         .filter(n -> n.recipe().equals(recipe))
                         .findAny()
                         .orElseGet(() -> new Node(recipe.resolve(), recipe.constraint()));
        if (node.current == null) node.apply(pipeline, calculationContext);
        if (craftTimes == 0) return null;
        var lookupContext = new RoutingContext(stock);
        return null;
    }

}

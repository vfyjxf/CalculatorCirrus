package dev.vfyjxf.cirrus.api.recipe.routing.state;

import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRecipeCatalog;
import dev.vfyjxf.cloudlib.util.Checks;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.BitSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Strongly Connected Component (SCC) analysis of the recipe dependency graph.
 *
 * <p>Uses Tarjan's algorithm to decompose the recipe graph into SCCs. An SCC
 * containing more than one recipe (or a self-loop) is marked as <i>cyclic</i>,
 * which informs the {@link CycleGuard} and the planner's amplification-risk
 * estimation.
 *
 * <p>Each recipe is assigned its SCC identifier via {@link #sccOf(int)}, and
 * {@link #isCyclicScc(int)} reports whether a given SCC is cyclic.
 *
 * @param recipeToScc maps each recipe index to its SCC identifier
 * @param cyclicScc   for each SCC ID, whether it is cyclic
 * @see CycleGuard
 */
public record SccAnalysis(
    int[] recipeToScc,
    boolean[] cyclicScc
) {
    public SccAnalysis {
        Checks.checkNotNull(recipeToScc, "recipeToScc");
        Checks.checkNotNull(cyclicScc, "cyclicScc");
    }

    /**
     * Returns the SCC identifier for the given recipe index.
     *
     * @param recipeIndex the recipe's catalog index
     * @return the SCC ID
     */
    public int sccOf(int recipeIndex) {
        return recipeToScc[recipeIndex];
    }

    /**
     * Returns whether the given SCC is cyclic (contains a cycle).
     *
     * @param sccId the SCC identifier
     * @return {@code true} if the SCC has more than one member or a self-loop
     */
    public boolean isCyclicScc(int sccId) {
        return sccId >= 0 && sccId < cyclicScc.length && cyclicScc[sccId];
    }

    /**
     * Builds an SCC analysis for the subset of recipes allowed by the source policy.
     *
     * @param graph          the recipe dependency graph
     * @param catalog        the stable recipe catalog for index mapping
     * @param allowedRecipes bitset of recipe indices permitted by the source policy
     * @return the computed SCC analysis
     */
    public static SccAnalysis build(RecipeGraph graph, RoutingRecipeCatalog catalog, BitSet allowedRecipes) {
        Checks.checkNotNull(graph, "graph");
        Checks.checkNotNull(catalog, "catalog");
        Checks.checkNotNull(allowedRecipes, "allowedRecipes");
        int n = catalog.size();
        RecipeGraph.Node[] nodes = new RecipeGraph.Node[n];
        List<FallbackConsumer> fallbackConsumers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RecipeGraph.Node node = catalog.nodeAt(i);
            nodes[i] = node;
            if (!allowedRecipes.get(i)) {
                continue;
            }
            for (InputContent input : node.mergedInputs()) {
                if (input.notConsumed() || !requiresScanFallback(input)) {
                    continue;
                }
                fallbackConsumers.add(new FallbackConsumer(i, input));
            }
        }

        BitSet[] adjacency = new BitSet[n];
        for (int i = allowedRecipes.nextSetBit(0); i >= 0; i = allowedRecipes.nextSetBit(i + 1)) {
            RecipeGraph.Node producer = nodes[i];
            for (OutputContent output : producer.mergedOutputs()) {
                addIndexedConsumers(graph, catalog, allowedRecipes, adjacency, i, output);
                addFallbackConsumers(allowedRecipes, adjacency, fallbackConsumers, i, output);
            }
        }

        IntArrayList[] edges = new IntArrayList[n];
        for (int i = 0; i < n; i++) {
            edges[i] = toIntList(adjacency[i]);
        }

        Tarjan tarjan = new Tarjan(edges, allowedRecipes);
        tarjan.run();
        return new SccAnalysis(tarjan.recipeToScc, tarjan.cyclicScc);
    }

    private static void addIndexedConsumers(
        RecipeGraph graph,
        RoutingRecipeCatalog catalog,
        BitSet allowedRecipes,
        BitSet[] adjacency,
        int producerIndex,
        OutputContent output
    ) {
        for (RecipeGraph.InputRef consumer : graph.consumersFor(output)) {
            if (consumer.input().notConsumed()) {
                continue;
            }
            int consumerIndex = catalog.indexOf(consumer.node().recipe());
            if (consumerIndex < 0 || !allowedRecipes.get(consumerIndex)) {
                continue;
            }
            edgeSet(adjacency, producerIndex).set(consumerIndex);
        }
    }

    private static void addFallbackConsumers(
        BitSet allowedRecipes,
        BitSet[] adjacency,
        List<FallbackConsumer> fallbackConsumers,
        int producerIndex,
        OutputContent output
    ) {
        if (fallbackConsumers.isEmpty()) {
            return;
        }
        var resource = output.asResource();
        for (FallbackConsumer consumer : fallbackConsumers) {
            if (!allowedRecipes.get(consumer.recipeIndex())) {
                continue;
            }
            if (consumer.input().satisfy(resource)) {
                edgeSet(adjacency, producerIndex).set(consumer.recipeIndex());
            }
        }
    }

    private static boolean requiresScanFallback(InputContent input) {
        return input instanceof ComplexContent && !input.isEnumerable();
    }

    private static BitSet edgeSet(BitSet[] adjacency, int producerIndex) {
        BitSet edges = adjacency[producerIndex];
        if (edges == null) {
            edges = new BitSet();
            adjacency[producerIndex] = edges;
        }
        return edges;
    }

    private static IntArrayList toIntList(BitSet bits) {
        IntArrayList edges = new IntArrayList();
        if (bits == null) {
            return edges;
        }
        for (int edge = bits.nextSetBit(0); edge >= 0; edge = bits.nextSetBit(edge + 1)) {
            edges.add(edge);
        }
        return edges;
    }

    private record FallbackConsumer(int recipeIndex, InputContent input) {
    }

    private static final class Tarjan {
        private final IntArrayList[] edges;
        private final BitSet allowed;
        private final int[] index;
        private final int[] low;
        private final boolean[] onStack;
        private final int[] recipeToScc;
        private final IntArrayList stack = new IntArrayList();
        private int time;
        private int sccCount;
        private boolean[] cyclicScc = new boolean[16];

        private Tarjan(IntArrayList[] edges, BitSet allowed) {
            this.edges = edges;
            this.allowed = allowed;
            int n = edges.length;
            this.index = new int[n];
            this.low = new int[n];
            this.onStack = new boolean[n];
            this.recipeToScc = new int[n];
            java.util.Arrays.fill(index, -1);
            java.util.Arrays.fill(recipeToScc, -1);
        }

        private void run() {
            for (int i = 0; i < edges.length; i++) {
                if (allowed.get(i) && index[i] < 0) {
                    dfs(i);
                }
            }
            cyclicScc = java.util.Arrays.copyOf(cyclicScc, sccCount);
        }

        private void dfs(int v) {
            index[v] = time;
            low[v] = time;
            time++;
            stack.push(v);
            onStack[v] = true;

            IntArrayList next = edges[v];
            for (int i = 0; i < next.size(); i++) {
                int to = next.getInt(i);
                if (!allowed.get(to)) {
                    continue;
                }
                if (index[to] < 0) {
                    dfs(to);
                    low[v] = Math.min(low[v], low[to]);
                } else if (onStack[to]) {
                    low[v] = Math.min(low[v], index[to]);
                }
            }

            if (low[v] != index[v]) {
                return;
            }

            ensureSccCapacity(sccCount + 1);
            int size = 0;
            boolean selfLoop = false;
            while (!stack.isEmpty()) {
                int node = stack.popInt();
                onStack[node] = false;
                recipeToScc[node] = sccCount;
                size++;
                if (node == v) {
                    for (int i = 0; i < edges[node].size(); i++) {
                        if (edges[node].getInt(i) == node) {
                            selfLoop = true;
                            break;
                        }
                    }
                    break;
                }
            }
            cyclicScc[sccCount] = size > 1 || selfLoop;
            sccCount++;
        }

        private void ensureSccCapacity(int required) {
            if (required <= cyclicScc.length) {
                return;
            }
            int next = Math.max(required, cyclicScc.length * 2);
            cyclicScc = java.util.Arrays.copyOf(cyclicScc, next);
        }
    }
}


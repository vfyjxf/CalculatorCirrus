package dev.vfyjxf.cirrus.recipe.routing.topology;

import dev.vfyjxf.cirrus.api.recipe.classify.ClassifyContent;
import dev.vfyjxf.cirrus.api.recipe.classify.RecipeClassifyView;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.recipe.classify.ClassifyViewFactory;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resource-level topology of a {@link RecipeGraph}: which resources (concrete
 * item/fluid ids) participate in strongly connected components, and whether
 * the request cone reachable from a target recipe contains any cycle.
 *
 * <p>The graph is projected onto resources: every recipe contributes edges
 * from each input resource id to each output resource id. SCCs of this
 * projection are computed once per graph (iterative Kosaraju) and cached.
 * A non-trivial SCC (size &gt; 1, or a self-loop like amplification recipes)
 * marks a resource family where planning can loop — e.g. the sixteen
 * concrete colors that dye recipes convert into each other.
 *
 * <p>Tag/list slots contribute all their member ids, so the projection
 * over-approximates the planner's true matching. Cone analysis therefore may
 * report "cyclic" for a cone that is acyclic under a specific source policy,
 * but never the reverse — the DAG planner only runs when the cone is
 * provably acyclic, and its own visiting set aborts to the greedy planner if
 * a cycle still slips through.
 */
public final class GraphTopology {

    /**
     * Weak keys: a topology must never keep its graph alive (the routing
     * integration tests assert graphs are collectible after a route).
     * Entries vanish with the graph instead of being evicted by size.
     */
    private static final Map<RecipeGraph, GraphTopology> cache = Collections.synchronizedMap(new WeakHashMap<>());

    /** Returns the cached topology for the graph, computing it on first use. */
    public static GraphTopology of(RecipeGraph graph) {
        GraphTopology cached;
        synchronized (cache) {
            cached = cache.get(graph);
        }
        if (cached != null) {
            return cached;
        }
        GraphTopology built = compute(graph);
        synchronized (cache) {
            cache.put(graph, built);
        }
        return built;
    }

    /** Visible for tests. */
    public static void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }

    private final Object2IntOpenHashMap<String> resourceIndex;
    /** node index -> input resource indices (deduplicated). */
    private final int[][] nodeInputs;
    /** resource index -> node indices that produce it. */
    private final int[][] resourceProducers;
    /** resource index -> SCC id. */
    private final int[] resourceScc;
    /** SCC id -> member count. */
    private final int[] sccSizes;
    /** SCC id -> true when the SCC is non-trivial (size &gt; 1 or self-loop). */
    private final boolean[] sccNonTrivial;
    /**
     * node index -> node indices of its round-trip partners: a different recipe
     * that exchanges the same resources back in (near-)equal amounts (compress ↔
     * decompress, revert ↔ _revert, chiseling back), closed on resources so
     * byproduct-carrying pairs are excluded. Empty arrays mean "none".
     */
    private final int[][] roundTripPartners;
    private final int resourceCount;
    private final int nonTrivialSccCount;
    private final int largestNonTrivialSccSize;

    private GraphTopology(
        Object2IntOpenHashMap<String> resourceIndex,
        int[][] nodeInputs,
        int[][] resourceProducers,
        int[] resourceScc,
        int[] sccSizes,
        boolean[] sccNonTrivial,
        int[][] roundTripPartners
    ) {
        this.resourceIndex = resourceIndex;
        this.nodeInputs = nodeInputs;
        this.resourceProducers = resourceProducers;
        this.resourceScc = resourceScc;
        this.sccSizes = sccSizes;
        this.sccNonTrivial = sccNonTrivial;
        this.roundTripPartners = roundTripPartners;
        this.resourceCount = resourceIndex.size();
        int count = 0;
        int largest = 0;
        for (int scc = 0; scc < sccNonTrivial.length; scc++) {
            if (sccNonTrivial[scc]) {
                count++;
                largest = Math.max(largest, sccSizes[scc]);
            }
        }
        this.nonTrivialSccCount = count;
        this.largestNonTrivialSccSize = largest;
    }

    public int nodeCount() {
        return nodeInputs.length;
    }

    public int resourceCount() {
        return resourceCount;
    }

    public int nonTrivialSccCount() {
        return nonTrivialSccCount;
    }

    public int largestNonTrivialSccSize() {
        return largestNonTrivialSccSize;
    }

    /** The SCC id of the given resource id string, or -1 when unknown. */
    public int sccIdOf(String resourceId) {
        int resource = resourceIndex.getOrDefault(resourceId, -1);
        return resource < 0 ? -1 : resourceScc[resource];
    }

    /**
     * The SCC id of the resource when it belongs to a non-trivial (looping)
     * SCC, otherwise -1. Used by the planner to recognize family-internal
     * conversions such as dye recolors.
     */
    public int loopingSccIdOf(String resourceId) {
        int resource = resourceIndex.getOrDefault(resourceId, -1);
        if (resource < 0) {
            return -1;
        }
        int scc = resourceScc[resource];
        return sccNonTrivial[scc] ? scc : -1;
    }

    /** Whether the resource id belongs to a non-trivial SCC. */
    public boolean isLoopingResource(String resourceId) {
        int resource = resourceIndex.getOrDefault(resourceId, -1);
        return resource >= 0 && sccNonTrivial[resourceScc[resource]];
    }

    /**
     * Round-trip partners of the recipe node (different recipes exchanging the
     * same resources back in near-equal amounts). Returns the shared empty
     * array when the node has none.
     */
    public int[] roundTripPartnerOf(int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= roundTripPartners.length) {
            return EMPTY_PARTNERS;
        }
        return roundTripPartners[nodeIndex];
    }

    /** Whether two distinct nodes are round-trip partners of each other. */
    public boolean isRoundTripPartner(int a, int b) {
        if (a == b) {
            return false;
        }
        for (int partner : roundTripPartnerOf(a)) {
            if (partner == b) {
                return true;
            }
        }
        return false;
    }

    private static final int[] EMPTY_PARTNERS = new int[0];

    /**
     * Whether two resource ids belong to the same non-trivial SCC — i.e.
     * recipes can convert each into the other (dye recolor families, chiseling
     * exchanges). Two identical ids never count as "sharing a loop".
     */
    public boolean sharesLoopingScc(String first, String second) {
        int firstResource = resourceIndex.getOrDefault(first, -1);
        int secondResource = resourceIndex.getOrDefault(second, -1);
        if (firstResource < 0 || secondResource < 0 || firstResource == secondResource) {
            return false;
        }
        int scc = resourceScc[firstResource];
        return sccNonTrivial[scc] && resourceScc[secondResource] == scc;
    }

    /**
     * Backward reachability from the target recipe over allowed recipes only:
     * returns true as soon as a demanded input resource sits in a non-trivial
     * SCC (meaning the planner could loop while resolving it).
     *
     * <p>Input resources without any allowed producer cannot loop (their
     * demand dies as a pending input) and are skipped.
     */
    public boolean coneHasNontrivialCycle(int targetNodeIndex, BitSet allowedRecipes) {
        return coneStats(targetNodeIndex, allowedRecipes).hasNontrivialCycle();
    }

    /**
     * Single backward-reachability pass from the target recipe over allowed
     * recipes, collecting the cone shape used for budget sizing. Follows the
     * same traversal and "no allowed producer → dead demand" rule as the
     * historical cycle check, so {@link ConeStats#hasNontrivialCycle()} is
     * equivalent to it.
     */
    public ConeStats coneStats(int targetNodeIndex, BitSet allowedRecipes) {
        int nodeCount = nodeInputs.length;
        if (targetNodeIndex < 0 || targetNodeIndex >= nodeCount) {
            return ConeStats.empty;
        }
        BitSet visitedNodes = new BitSet(nodeCount);
        BitSet visitedResources = new BitSet(resourceCount);
        BitSet loopSccs = new BitSet(sccNonTrivial.length);
        long candidateEstimate = 0;
        IntArrayList stack = new IntArrayList();
        stack.push(targetNodeIndex);
        while (!stack.isEmpty()) {
            int node = stack.popInt();
            if (visitedNodes.get(node)) {
                continue;
            }
            visitedNodes.set(node);
            for (int resource : nodeInputs[node]) {
                if (visitedResources.get(resource)) {
                    continue;
                }
                visitedResources.set(resource);
                int[] producers = resourceProducers[resource];
                int allowedCount = 0;
                for (int producer : producers) {
                    if (allowedRecipes.get(producer)) {
                        allowedCount++;
                    }
                }
                if (allowedCount == 0) {
                    continue;
                }
                candidateEstimate += Math.min(allowedCount, 64);
                int scc = resourceScc[resource];
                if (sccNonTrivial[scc]) {
                    loopSccs.set(scc);
                }
                for (int producer : producers) {
                    if (allowedRecipes.get(producer) && !visitedNodes.get(producer)) {
                        stack.push(producer);
                    }
                }
            }
        }
        return new ConeStats(
            visitedNodes.cardinality(),
            visitedResources.cardinality(),
            loopSccs.cardinality(),
            candidateEstimate
        );
    }

    private static GraphTopology compute(RecipeGraph graph) {
        ImmutableList<RecipeGraph.Node> nodes = graph.stableNodes();
        int nodeCount = nodes.size();
        Object2IntOpenHashMap<String> resourceIndex = new Object2IntOpenHashMap<>();
        resourceIndex.defaultReturnValue(-1);

        int[][] nodeInputs = new int[nodeCount][];
        int[][] nodeOutputs = new int[nodeCount][];
        // per-node resource -> amount (first occurrence; used by round-trip equality)
        Int2IntOpenHashMap[] nodeInputAmounts = new Int2IntOpenHashMap[nodeCount];
        Int2IntOpenHashMap[] nodeOutputAmounts = new Int2IntOpenHashMap[nodeCount];
        IntArrayList[] producers = new IntArrayList[64];

        for (int index = 0; index < nodeCount; index++) {
            RecipeClassifyView view = ClassifyViewFactory.fromNode(nodes.get(index), index);
            nodeInputs[index] = internResources(view.inputs(), resourceIndex, null);
            nodeOutputs[index] = internResources(view.outputs(), resourceIndex, null);
            nodeInputAmounts[index] = resourceAmounts(view.inputs(), resourceIndex);
            nodeOutputAmounts[index] = resourceAmounts(view.outputs(), resourceIndex);
            for (int resource : nodeOutputs[index]) {
                if (resource >= producers.length) {
                    producers = Arrays.copyOf(producers, Math.max(resource + 1, producers.length * 2));
                }
                IntArrayList list = producers[resource];
                if (list == null) {
                    list = new IntArrayList(2);
                    producers[resource] = list;
                }
                list.add(index);
            }
        }

        int resourceCount = resourceIndex.size();
        int[][] resourceProducers = new int[resourceCount][];
        for (int resource = 0; resource < resourceCount; resource++) {
            IntArrayList list = resource < producers.length ? producers[resource] : null;
            resourceProducers[resource] = list == null ? new int[0] : list.toIntArray();
        }

        // resource projection edges: input resource -> output resource (flow direction)
        IntArrayList edgeSources = new IntArrayList();
        IntArrayList edgeTargets = new IntArrayList();
        boolean[] selfLoop = new boolean[resourceCount];
        for (int node = 0; node < nodeCount; node++) {
            int[] inputs = nodeInputs[node];
            int[] outputs = nodeOutputs[node];
            if (inputs.length == 0 || outputs.length == 0) {
                continue;
            }
            for (int input : inputs) {
                for (int output : outputs) {
                    if (input == output) {
                        selfLoop[input] = true;
                    } else {
                        edgeSources.add(input);
                        edgeTargets.add(output);
                    }
                }
            }
        }

        int[] resourceScc = kosaraju(resourceCount, edgeSources.toIntArray(), edgeTargets.toIntArray());
        int sccCount = 0;
        for (int scc : resourceScc) {
            sccCount = Math.max(sccCount, scc + 1);
        }
        int[] sccSizes = new int[sccCount];
        for (int scc : resourceScc) {
            sccSizes[scc]++;
        }
        boolean[] sccNonTrivial = new boolean[sccCount];
        for (int resource = 0; resource < resourceCount; resource++) {
            int scc = resourceScc[resource];
            if (sccSizes[scc] > 1 || selfLoop[resource]) {
                sccNonTrivial[scc] = true;
            }
        }

        int[][] roundTripPartners = computeRoundTripPartners(
            nodeCount,
            nodeInputs,
            nodeOutputs,
            nodeInputAmounts,
            nodeOutputAmounts,
            resourceProducers
        );
        return new GraphTopology(
            resourceIndex,
            nodeInputs,
            resourceProducers,
            resourceScc,
            sccSizes,
            sccNonTrivial,
            roundTripPartners
        );
    }

    /**
     * Detects round-trip pairs: two distinct recipes that exchange the same
     * resources back and forth in (near-)equal amounts without introducing any
     * net new resource — the compress↔decompress / revert↔_revert no-op loops.
     *
     * <p>Three gates keep the detection precise: (1) exchange — a produces
     * something b consumes and vice versa; (2) near-equal per-craft amounts
     * (ratio within {@value #ROUND_TRIP_TOLERANCE} of 1); (3) closure — the
     * union of both outputs equals the union of both inputs, so pairs carrying
     * byproducts (ore→ingot+slag) or legit conversions (ore→ingot vs
     * block→9×ingot) never match.
     */
    private static int[][] computeRoundTripPartners(
        int nodeCount,
        int[][] nodeInputs,
        int[][] nodeOutputs,
        Int2IntOpenHashMap[] nodeInputAmounts,
        Int2IntOpenHashMap[] nodeOutputAmounts,
        int[][] resourceProducers
    ) {
        // amount tolerance: amounts within 25% count as "equal" per craft
        final double tolerance = ROUND_TRIP_TOLERANCE;
        // consumers index: resource -> nodes that CONSUME it (the inverse recipe
        // of a is a consumer of a's output, not a producer of it)
        int resourceCount = resourceProducers.length;
        IntArrayList[] consumers = new IntArrayList[resourceCount];
        for (int node = 0; node < nodeCount; node++) {
            for (int resource : nodeInputs[node]) {
                if (resource >= consumers.length) {
                    consumers = Arrays.copyOf(consumers, Math.max(resource + 1, consumers.length * 2));
                }
                IntArrayList list = consumers[resource];
                if (list == null) {
                    list = new IntArrayList(2);
                    consumers[resource] = list;
                }
                list.add(node);
            }
        }
        IntArrayList[] partners = new IntArrayList[nodeCount];
        for (int a = 0; a < nodeCount; a++) {
            int[] outputs = nodeOutputs[a];
            if (outputs.length == 0 || nodeInputs[a].length == 0) {
                continue;
            }
            for (int exchanged : outputs) {
                int[] consumersOfExchanged = exchanged < consumers.length && consumers[exchanged] != null
                    ? consumers[exchanged].toIntArray()
                    : EMPTY_PARTNERS;
                for (int b : consumersOfExchanged) {
                    if (b <= a) {
                        // each unordered pair is visited once via a<b
                        continue;
                    }
                    if (nodeOutputs[b].length == 0 || nodeInputs[b].length == 0) {
                        continue;
                    }
                    // gate 1b: b must produce something a consumes (symmetric exchange)
                    int backFlow = -1;
                    for (int bOut : nodeOutputs[b]) {
                        for (int aIn : nodeInputs[a]) {
                            if (bOut == aIn) {
                                backFlow = bOut;
                                break;
                            }
                        }
                        if (backFlow >= 0) {
                            break;
                        }
                    }
                    if (backFlow < 0) {
                        continue;
                    }
                    // gate 2: near-equal amounts on both exchanged resources
                    if (!amountsNearEqual(nodeOutputAmounts[a], exchanged, nodeInputAmounts[b], exchanged, tolerance)) {
                        continue;
                    }
                    if (!amountsNearEqual(nodeOutputAmounts[b], backFlow, nodeInputAmounts[a], backFlow, tolerance)) {
                        continue;
                    }
                    // gate 3: closed pair — no net new resource either way
                    if (!closedPair(nodeInputs[a], nodeOutputs[a], nodeInputs[b], nodeOutputs[b])) {
                        continue;
                    }
                    if (partners[a] == null) {
                        partners[a] = new IntArrayList(2);
                    }
                    if (partners[b] == null) {
                        partners[b] = new IntArrayList(2);
                    }
                    partners[a].add(b);
                    partners[b].add(a);
                }
            }
        }
        int[][] result = new int[nodeCount][];
        for (int node = 0; node < nodeCount; node++) {
            result[node] = partners[node] == null ? EMPTY_PARTNERS : partners[node].toIntArray();
        }
        return result;
    }

    private static final double ROUND_TRIP_TOLERANCE = 0.25d;

    private static boolean amountsNearEqual(
        Int2IntOpenHashMap from,
        int fromResource,
        Int2IntOpenHashMap to,
        int toResource,
        double tolerance
    ) {
        if (from == null || to == null) {
            return false;
        }
        int fromAmount = from.get(fromResource);
        int toAmount = to.get(toResource);
        if (fromAmount <= 0 || toAmount <= 0) {
            return false;
        }
        double ratio = (double) fromAmount / toAmount;
        return ratio >= 1.0d - tolerance && ratio <= 1.0d + tolerance;
    }

    /**
     * Closed-pair gate: {@code outputs(a) ∪ outputs(b) == inputs(a) ∪ inputs(b)}
     * as resource id sets — the pair introduces and consumes exactly the same
     * resources, i.e. it is a pure no-op round trip with no byproducts.
     */
    private static boolean closedPair(int[] inputsA, int[] outputsA, int[] inputsB, int[] outputsB) {
        IntOpenHashSet combined = new IntOpenHashSet(inputsA.length + inputsB.length);
        for (int r : inputsA) {
            combined.add(r);
        }
        for (int r : inputsB) {
            combined.add(r);
        }
        int outputCount = 0;
        IntOpenHashSet outputs = new IntOpenHashSet(outputsA.length + outputsB.length);
        for (int r : outputsA) {
            if (outputs.add(r)) {
                outputCount++;
            }
        }
        for (int r : outputsB) {
            if (outputs.add(r)) {
                outputCount++;
            }
        }
        return outputCount == combined.size() && outputs.containsAll(combined);
    }

    /** Per-node resource id -> first amount seen, for round-trip amount equality. */
    private static Int2IntOpenHashMap resourceAmounts(List<ClassifyContent> contents, Object2IntOpenHashMap<String> resourceIndex) {
        Int2IntOpenHashMap amounts = new Int2IntOpenHashMap();
        amounts.defaultReturnValue(0);
        for (ClassifyContent content : contents) {
            if (content.ids().size() != 1) {
                continue; // tag/list slots contribute no single exchange amount
            }
            int index = resourceIndex.getInt(content.ids().iterator().next());
            if (index >= 0 && !amounts.containsKey(index)) {
                amounts.put(index, (int) Math.max(0, content.amount()));
            }
        }
        return amounts;
    }

    private static int[] internResources(
        List<ClassifyContent> contents,
        Object2IntOpenHashMap<String> resourceIndex,
        @Nullable Void unused
    ) {
        if (contents.isEmpty()) {
            return new int[0];
        }
        IntOpenHashSet deduped = new IntOpenHashSet();
        for (ClassifyContent content : contents) {
            for (String id : content.ids()) {
                int index = resourceIndex.getInt(id);
                if (index < 0) {
                    index = resourceIndex.size();
                    resourceIndex.put(id, index);
                }
                deduped.add(index);
            }
        }
        return deduped.toIntArray();
    }

    /**
     * Iterative Kosaraju over the resource projection. Returns the SCC id per
     * resource; ids are assigned in second-pass discovery order.
     */
    private static int[] kosaraju(int resourceCount, int[] edgeSources, int[] edgeTargets) {
        int[][] forward = csr(resourceCount, edgeSources, edgeTargets);
        int[][] reverse = csr(resourceCount, edgeTargets, edgeSources);
        int[] forwardOffsets = forward[0];
        int[] forwardAdjacency = forward[1];
        int[] reverseOffsets = reverse[0];
        int[] reverseAdjacency = reverse[1];

        boolean[] visited = new boolean[resourceCount];
        int[] order = new int[resourceCount];
        int orderLength = 0;
        IntArrayList nodeStack = new IntArrayList();
        IntArrayList edgeCursor = new IntArrayList();
        for (int start = 0; start < resourceCount; start++) {
            if (visited[start]) {
                continue;
            }
            visited[start] = true;
            nodeStack.push(start);
            edgeCursor.push(forwardOffsets[start]);
            while (!nodeStack.isEmpty()) {
                int node = nodeStack.topInt();
                int cursor = edgeCursor.topInt();
                int end = forwardOffsets[node + 1];
                if (cursor < end) {
                    edgeCursor.set(edgeCursor.size() - 1, cursor + 1);
                    int next = forwardAdjacency[cursor];
                    if (!visited[next]) {
                        visited[next] = true;
                        nodeStack.push(next);
                        edgeCursor.push(forwardOffsets[next]);
                    }
                } else {
                    nodeStack.popInt();
                    edgeCursor.popInt();
                    order[orderLength++] = node;
                }
            }
        }

        int[] scc = new int[resourceCount];
        Arrays.fill(scc, -1);
        int sccCount = 0;
        for (int i = resourceCount - 1; i >= 0; i--) {
            int start = order[i];
            if (scc[start] >= 0) {
                continue;
            }
            scc[start] = sccCount;
            nodeStack.push(start);
            while (!nodeStack.isEmpty()) {
                int node = nodeStack.popInt();
                int end = reverseOffsets[node + 1];
                for (int cursor = reverseOffsets[node]; cursor < end; cursor++) {
                    int next = reverseAdjacency[cursor];
                    if (scc[next] < 0) {
                        scc[next] = sccCount;
                        nodeStack.push(next);
                    }
                }
            }
            sccCount++;
        }
        return scc;
    }

    /**
     * Builds CSR adjacency grouped by {@code keys}: returns
     * {@code {offsets(resourceCount + 1), adjacency(keys.length)}} where the
     * outgoing edges of node {@code n} live at
     * {@code adjacency[offsets[n] .. offsets[n + 1])}.
     */
    private static int[][] csr(int resourceCount, int[] keys, int[] values) {
        int[] offsets = new int[resourceCount + 1];
        for (int key : keys) {
            offsets[key + 1]++;
        }
        for (int i = 0; i < resourceCount; i++) {
            offsets[i + 1] += offsets[i];
        }
        int[] adjacency = new int[values.length];
        int[] cursor = Arrays.copyOf(offsets, resourceCount + 1);
        for (int edge = 0; edge < keys.length; edge++) {
            adjacency[cursor[keys[edge]]++] = values[edge];
        }
        return new int[][]{offsets, adjacency};
    }
}

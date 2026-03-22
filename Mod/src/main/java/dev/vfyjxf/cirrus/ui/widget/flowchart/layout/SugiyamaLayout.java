package dev.vfyjxf.cirrus.ui.widget.flowchart.layout;

import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessEdge;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;

import java.util.*;

/**
 * Layered graph layout with A* orthogonal routing.
 * <p>
 * Produces a left-to-right column layout with grid-snapped Manhattan edge routing,
 * suitable for factory / main-bus style process flow diagrams.
 * <ol>
 *   <li><b>Cycle Removal</b> — DFS-based back-edge reversal to obtain a DAG</li>
 *   <li><b>Layer Assignment</b> — Longest-path layering + compaction</li>
 *   <li><b>Dummy Node Insertion</b> — Long edges split for crossing minimisation</li>
 *   <li><b>Crossing Minimisation</b> — Barycenter heuristic + transpose</li>
 *   <li><b>Grid-Snapped Positioning</b> — Columns left-to-right, all coords on grid</li>
 *   <li><b>A* Orthogonal Routing</b> — Manhattan paths with bend &amp; crossing penalties</li>
 * </ol>
 * Ports: inputs on the <b>left</b> edge, outputs on the <b>right</b> edge of each card.
 * All connections are strictly horizontal/vertical polylines.
 */
public class SugiyamaLayout {

    // ─────────────────── Configuration ───────────────────

    /** Pixel width of a process card (all cards share the same width). */
    private int cardWidth = 240;
    /** Vertical gap between adjacent layers (measured edge-to-edge). */
    private int layerGap = 120;
    /** Horizontal gap between adjacent nodes in the same layer. */
    private int nodeGap = 60;
    /** Maximum crossing-minimisation iterations (each iteration is one full up+down sweep). */
    private int maxSweeps = 24;

    /** Optional per-node size overrides (nodeId → [width, height] in pixels). */
    private Map<String, int[]> nodeSizeOverrides = Map.of();
    /** When true, phase 6 (A* edge routing) is skipped — positions only. */
    private boolean skipRouting = false;

    // ─────────────────── Grid & Routing ───────────────────

    /** Grid cell size in pixels for snapping and A* routing. */
    public static final int GRID_CELL = 10;
    /** A* penalty for each 90° bend in a route. */
    private static final int BEND_PENALTY = 40;
    /** A* penalty for entering a cell already used by another route. */
    private static final int CROSS_PENALTY = 200;
    /** Extra cells of node-margin when marking the routing grid as blocked. */
    private static final int NODE_MARGIN_CELLS = 1;
    /** Maximum tolerated crossings when using the relaxed (shorter) route. */
    private static final int MAX_TOLERATED_CROSSINGS = 3;
    /** Edges spanning more than this many layers are replaced with mirror-node satellites. */
    private static final int SATELLITE_THRESHOLD = 3;
    /** Width of a mirror-link badge in pixels. */
    public static final int MIRROR_BADGE_W = 160;
    /** Height of a mirror-link badge in pixels. */
    public static final int MIRROR_BADGE_H = 44;

    // ─────────────────── Card height computation ───────────────────

    /** Header row height. */
    public static final int CARD_HEADER_H = 18;
    /** Recipe name row height. */
    public static final int CARD_NAME_H = 14;
    /** I/O section label height. */
    public static final int CARD_SECTION_LABEL_H = 11;
    /** Single slot row height ( 18 = 16-px icon + 2-px gap). */
    public static final int CARD_SLOT_H = 18;
    /** Bottom stats bar height. */
    public static final int CARD_STATS_H = 13;
    /** Vertical padding + dividers. */
    public static final int CARD_VERT_PAD = 5;
    /** Fallback height when node data is unavailable. */
    private static final int FALLBACK_CARD_HEIGHT = 90;

    /**
     * Computes the pixel height of a card based on its input/output slot count.
     * Uses a default card width of 280 for name-line estimation.
     */
    public static int computeCardHeight(ProcessNode node) {
        return computeCardHeight(node, 280);
    }

    /**
     * Computes the pixel height of a card, accounting for wrapped recipe name lines.
     *
     * @param node      the process node
     * @param cardWidth the card width in pixels (used to estimate name wrapping)
     */
    public static int computeCardHeight(ProcessNode node, int cardWidth) {
        int slotRows = Math.max(node.inputs().size(), node.outputs().size());
        slotRows = Math.max(slotRows, 1);
        // Estimate how many lines the recipe name will need
        int usableWidth = cardWidth - 8;
        int approxCharsPerLine = Math.max(1, usableWidth / 6); // MC font avg ~6px/char
        int nameLines = Math.min(2, Math.max(1,
                (node.name().length() + approxCharsPerLine - 1) / approxCharsPerLine));
        return CARD_HEADER_H + CARD_NAME_H * nameLines + CARD_SECTION_LABEL_H
                + slotRows * CARD_SLOT_H + CARD_STATS_H + CARD_VERT_PAD;
    }

    // ─────────────────── Builder-style config ───────────────────

    public SugiyamaLayout cardWidth(int width) {
        this.cardWidth = width;
        return this;
    }

    /** @deprecated Use {@link #cardWidth(int)} — height is now computed dynamically per node. */
    @Deprecated
    public SugiyamaLayout cardSize(int width, int height) {
        this.cardWidth = width;
        return this;
    }

    public SugiyamaLayout layerGap(int gap) {
        this.layerGap = gap;
        return this;
    }

    public SugiyamaLayout nodeGap(int gap) {
        this.nodeGap = gap;
        return this;
    }

    public SugiyamaLayout maxSweeps(int n) {
        this.maxSweeps = n;
        return this;
    }

    public SugiyamaLayout nodeSizeOverrides(Map<String, int[]> overrides) {
        this.nodeSizeOverrides = overrides;
        return this;
    }

    public Map<String, int[]> getNodeSizeOverrides() {
        return nodeSizeOverrides;
    }

    public SugiyamaLayout skipRouting(boolean skip) {
        this.skipRouting = skip;
        return this;
    }

    // ═══════════════════ Public entry point ═══════════════════

    /**
     * Computes a layered layout for the given process graph.
     * Card heights are computed dynamically per node based on I/O slot counts.
     */
    public LayoutResult layout(ProcessGraph graph) {
        if (graph.nodeCount() == 0) {
            return new LayoutResult(Map.of(), List.of(), Map.of(), List.of(), Map.of(), 0, 0);
        }

        var nodeIds = new ArrayList<>(graph.nodes().stream().map(n -> n.id()).toList());
        var adj = new AdjacencyMatrix(nodeIds, graph);

        // Phase 1 — Cycle removal
        Set<int[]> reversedArcs = removeCycles(adj);

        // Phase 2 — Layer assignment
        int[] layerOf = assignLayers(adj);

        // Phase 2b — Layer compaction (pull nodes toward successors, reduce dummy count)
        compactLayers(layerOf, adj);

        // Phase 3 — Dummy node insertion
        DummyResult dummies = insertDummies(adj, layerOf, graph);

        // Build layer buckets
        int maxLayer = 0;
        for (int l : dummies.layerOf) maxLayer = Math.max(maxLayer, l);
        List<List<Integer>> layers = new ArrayList<>();
        for (int i = 0; i <= maxLayer; i++) layers.add(new ArrayList<>());
        for (int i = 0; i < dummies.nodeCount; i++) {
            layers.get(dummies.layerOf[i]).add(i);
        }

        // Phase 4 — Crossing minimisation (barycenter heuristic)
        minimiseCrossings(layers, dummies);

        // Extract real-node ordering from the crossing-minimized layers.
        // Each layer becomes a column positioned left-to-right.
        int numLayers = maxLayer + 1;
        List<List<String>> columnOrder = new ArrayList<>();
        for (int l = 0; l < numLayers; l++) columnOrder.add(new ArrayList<>());
        for (int l = 0; l < layers.size(); l++) {
            for (int nodeIdx : layers.get(l)) {
                if (!dummies.isDummy[nodeIdx]) {
                    columnOrder.get(dummies.layerOf[nodeIdx]).add(dummies.nodeId[nodeIdx]);
                }
            }
        }

        // Pre-compute satellite badge counts per node (for layout spacing)
        Map<String, Integer> nodeLayerMap = new HashMap<>();
        for (int l = 0; l < numLayers; l++)
            for (String nid : columnOrder.get(l)) nodeLayerMap.put(nid, l);
        Map<String, Integer> badgeCounts = new HashMap<>();
        for (ProcessEdge edge : graph.edges()) {
            Integer srcL = nodeLayerMap.get(edge.fromNodeId());
            Integer tgtL = nodeLayerMap.get(edge.toNodeId());
            if (srcL != null && tgtL != null && Math.abs(tgtL - srcL) > SATELLITE_THRESHOLD) {
                badgeCounts.merge(edge.fromNodeId(), 1, Integer::sum);
                badgeCounts.merge(edge.toNodeId(), 1, Integer::sum);
            }
        }

        // Phase 5 — Grid-snapped left-to-right position assignment
        Map<String, NodePosition> positions = assignGridPositions(graph, columnOrder, numLayers, badgeCounts);

        // Phase 5.5 — Port reordering to reduce crossings near nodes
        Map<String, int[][]> portOrder = computePortOrder(graph, positions);

        // Phase 5.75 — Satellite mirror links for ultra-long edges
        List<MirrorLink> mirrorLinks = new ArrayList<>();
        Set<ProcessEdge> satelliteEdges = identifySatelliteEdges(graph, positions, mirrorLinks);

        // Phase 6 — A* orthogonal edge routing (skip satellite edges)
        List<EdgeRoute> routes = skipRouting
                ? List.of()
                : routeAllEdges(graph, positions, portOrder, satelliteEdges, mirrorLinks);

        // Compute final bounds
        double totalWidth = 0, totalHeight = 0;
        for (NodePosition p : positions.values()) {
            totalWidth = Math.max(totalWidth, p.right());
            totalHeight = Math.max(totalHeight, p.bottom());
        }
        for (EdgeRoute r : routes) {
            for (double[] pt : r.points()) {
                totalWidth = Math.max(totalWidth, pt[0]);
                totalHeight = Math.max(totalHeight, pt[1]);
            }
        }
        for (MirrorLink ml : mirrorLinks) {
            totalWidth = Math.max(totalWidth, ml.badgeX() + MIRROR_BADGE_W);
            totalHeight = Math.max(totalHeight, ml.badgeY() + MIRROR_BADGE_H);
        }
        totalWidth += GRID_CELL * 5;
        totalHeight += GRID_CELL * 5;

        return new LayoutResult(positions, routes, portOrder, mirrorLinks, Map.of(), totalWidth, totalHeight);
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 1 — Cycle Removal (DFS-based back-edge reversal)
    // ═══════════════════════════════════════════════════════════

    private Set<int[]> removeCycles(AdjacencyMatrix adj) {
        int n = adj.size;
        int[] state = new int[n]; // 0=white, 1=grey, 2=black
        Set<int[]> backEdges = new HashSet<>();

        for (int u = 0; u < n; u++) {
            if (state[u] == 0) {
                dfsVisit(u, adj, state, backEdges);
            }
        }

        // Reverse back edges
        for (int[] be : backEdges) {
            adj.reverse(be[0], be[1]);
        }
        return backEdges;
    }

    private void dfsVisit(int u, AdjacencyMatrix adj, int[] state, Set<int[]> backEdges) {
        Deque<int[]> stack = new ArrayDeque<>(); // [node, childIndex]
        state[u] = 1;
        stack.push(new int[]{u, 0});

        while (!stack.isEmpty()) {
            int[] top = stack.peek();
            int node = top[0];
            List<Integer> succs = adj.successors(node);

            if (top[1] < succs.size()) {
                int child = succs.get(top[1]++);
                if (state[child] == 1) {
                    // Back edge found
                    backEdges.add(new int[]{node, child});
                } else if (state[child] == 0) {
                    state[child] = 1;
                    stack.push(new int[]{child, 0});
                }
            } else {
                state[node] = 2;
                stack.pop();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 2 — Layer Assignment (longest path from sinks)
    // ═══════════════════════════════════════════════════════════

    private int[] assignLayers(AdjacencyMatrix adj) {
        int n = adj.size;
        int[] layer = new int[n];
        Arrays.fill(layer, -1);

        // Find sinks (no outgoing edges in DAG)
        List<Integer> sinks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (adj.successors(i).isEmpty()) sinks.add(i);
        }

        // BFS backward from sinks
        // layer[sink] = 0, layer[pred] = max(layer[succ]) + 1
        // We use a reverse topological order approach
        Queue<Integer> queue = new ArrayDeque<>(sinks);
        for (int s : sinks) layer[s] = 0;

        // Iterative relaxation: for each node, layer = max layer of successors + 1
        boolean changed = true;
        int maxIter = n * n; // safety bound
        while (changed && maxIter-- > 0) {
            changed = false;
            for (int u = 0; u < n; u++) {
                for (int v : adj.successors(u)) {
                    if (layer[v] >= 0) {
                        int candidate = layer[v] + 1;
                        if (candidate > layer[u]) {
                            layer[u] = candidate;
                            changed = true;
                        }
                    }
                }
            }
        }

        // Any unreachable nodes get layer 0
        for (int i = 0; i < n; i++) {
            if (layer[i] < 0) layer[i] = 0;
        }

        // Invert so sources are at top (layer 0) and sinks at bottom
        int maxLayer = 0;
        for (int l : layer) maxLayer = Math.max(maxLayer, l);
        // layer values are already correct: sources have highest values
        // We want sources at layer 0, so invert
        // Actually, our assignment gives sinks layer=0 and sources highest.
        // No inversion needed if we render top=highest layer. But conventionally
        // top=layer 0 and bottom=maxLayer. Let's keep sources at top:
        // Just flip: layer[i] = maxLayer - layer[i]
        // Wait, sources already have the highest layer numbers. So flipping
        // puts them at layer 0 (top).
        // Actually no — we want sinks at the bottom. Currently sinks = 0.
        // For top-to-bottom rendering where layer 0 is top, we need sources at 0.
        // So we do NOT invert. Instead, redefine: the layer is already
        // "distance from sinks" which means sources have the highest layer.
        // But we want Y to increase downward with layer 0 at top.
        // So we invert:
        for (int i = 0; i < n; i++) {
            layer[i] = maxLayer - layer[i];
        }

        return layer;
    }

    /**
     * Compacts layers by pulling each node as close as possible to its successors,
     * reducing the number of dummy nodes needed for long edges.
     */
    private void compactLayers(int[] layer, AdjacencyMatrix adj) {
        int n = adj.size;
        boolean changed = true;
        for (int pass = 0; pass < n && changed; pass++) {
            changed = false;
            for (int u = 0; u < n; u++) {
                List<Integer> succs = adj.successors(u);
                if (succs.isEmpty()) continue;
                int minSuccLayer = Integer.MAX_VALUE;
                for (int v : succs) minSuccLayer = Math.min(minSuccLayer, layer[v]);
                int maxPredLayer = -1;
                for (int p : adj.predecessors(u)) maxPredLayer = Math.max(maxPredLayer, layer[p]);
                int ideal = minSuccLayer - 1;
                int clamped = Math.max(ideal, maxPredLayer + 1);
                if (clamped != layer[u] && clamped >= 0) {
                    layer[u] = clamped;
                    changed = true;
                }
            }
        }
        // Remove empty layers by renumbering
        int maxL = 0;
        for (int l : layer) maxL = Math.max(maxL, l);
        boolean[] used = new boolean[maxL + 1];
        for (int i = 0; i < n; i++) used[layer[i]] = true;
        int[] remap = new int[maxL + 1];
        int next = 0;
        for (int l = 0; l <= maxL; l++) {
            if (used[l]) remap[l] = next++;
        }
        for (int i = 0; i < n; i++) layer[i] = remap[layer[i]];
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 3 — Dummy Node Insertion
    // ═══════════════════════════════════════════════════════════

    private record DummyResult(
            int nodeCount,
            int[] layerOf,
            boolean[] isDummy,
            String[] nodeId,
            // adjacency for dummy-expanded graph
            List<List<Integer>> fwdAdj,
            List<List<Integer>> bwdAdj,
            // mapping from original edge index to chain of node indices (from→...→to)
            List<List<Integer>> edgeChains
    ) {}

    private DummyResult insertDummies(AdjacencyMatrix adj, int[] layerOf, ProcessGraph graph) {
        int origN = adj.size;

        // Pre-count how many dummy nodes we need
        int dummyCount = 0;
        for (int u = 0; u < origN; u++) {
            for (int v : adj.successors(u)) {
                int span = layerOf[v] - layerOf[u];
                if (span > 1) dummyCount += (span - 1);
            }
        }

        int totalN = origN + dummyCount;
        int[] newLayer = new int[totalN];
        boolean[] isDummy = new boolean[totalN];
        String[] nodeId = new String[totalN];
        List<List<Integer>> fwd = new ArrayList<>();
        List<List<Integer>> bwd = new ArrayList<>();
        List<List<Integer>> edgeChains = new ArrayList<>();

        for (int i = 0; i < totalN; i++) {
            fwd.add(new ArrayList<>());
            bwd.add(new ArrayList<>());
        }

        // Copy original nodes
        for (int i = 0; i < origN; i++) {
            newLayer[i] = layerOf[i];
            isDummy[i] = false;
            nodeId[i] = adj.nodeIds.get(i);
        }

        int nextDummy = origN;
        int edgeIndex = 0;
        for (int u = 0; u < origN; u++) {
            for (int v : adj.successors(u)) {
                int span = newLayer[v] - newLayer[u];
                List<Integer> chain = new ArrayList<>();
                chain.add(u);

                if (span <= 1) {
                    // Direct edge
                    fwd.get(u).add(v);
                    bwd.get(v).add(u);
                    chain.add(v);
                } else {
                    // Insert dummy nodes
                    int prev = u;
                    for (int layer = newLayer[u] + 1; layer < newLayer[v]; layer++) {
                        int d = nextDummy++;
                        newLayer[d] = layer;
                        isDummy[d] = true;
                        nodeId[d] = "_dummy_" + d;
                        fwd.get(prev).add(d);
                        bwd.get(d).add(prev);
                        chain.add(d);
                        prev = d;
                    }
                    fwd.get(prev).add(v);
                    bwd.get(v).add(prev);
                    chain.add(v);
                }
                edgeChains.add(chain);
                edgeIndex++;
            }
        }

        return new DummyResult(totalN, newLayer, isDummy, nodeId, fwd, bwd, edgeChains);
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 4 — Crossing Minimisation (barycenter heuristic)
    // ═══════════════════════════════════════════════════════════

    private void minimiseCrossings(List<List<Integer>> layers, DummyResult dm) {
        if (layers.size() <= 1) return;

        int bestCrossings = countTotalCrossings(layers, dm);
        // Save best ordering
        List<List<Integer>> bestOrder = deepCopyLayers(layers);

        for (int iter = 0; iter < maxSweeps; iter++) {
            // Down sweep (fix layer i, reorder layer i+1)
            for (int i = 0; i < layers.size() - 1; i++) {
                reorderByBarycenter(layers.get(i), layers.get(i + 1), dm, true);
            }
            // Up sweep (fix layer i, reorder layer i-1)
            for (int i = layers.size() - 1; i > 0; i--) {
                reorderByBarycenter(layers.get(i), layers.get(i - 1), dm, false);
            }

            // Transpose: greedily swap adjacent pairs to further reduce crossings
            transposeAdjacentPairs(layers, dm);

            int crossings = countTotalCrossings(layers, dm);
            if (crossings < bestCrossings) {
                bestCrossings = crossings;
                bestOrder = deepCopyLayers(layers);
            }

            // Early termination
            if (crossings == 0) break;
        }

        // Restore best ordering
        for (int i = 0; i < layers.size(); i++) {
            layers.set(i, bestOrder.get(i));
        }
    }

    /**
     * Reorders {@code movableLayer} based on barycenter positions
     * relative to {@code fixedLayer}.
     *
     * @param downward true if fixedLayer is above movableLayer
     */
    private void reorderByBarycenter(List<Integer> fixedLayer,
                                      List<Integer> movableLayer,
                                      DummyResult dm,
                                      boolean downward) {
        // Build position map for fixed layer
        Map<Integer, Integer> fixedPos = new HashMap<>();
        for (int i = 0; i < fixedLayer.size(); i++) {
            fixedPos.put(fixedLayer.get(i), i);
        }

        // Compute barycenter for each node in movable layer
        double[] bary = new double[movableLayer.size()];
        for (int i = 0; i < movableLayer.size(); i++) {
            int node = movableLayer.get(i);
            List<Integer> neighbors = downward ? dm.bwdAdj.get(node) : dm.fwdAdj.get(node);
            if (neighbors.isEmpty()) {
                bary[i] = i; // keep original position
            } else {
                double sum = 0;
                int count = 0;
                for (int nb : neighbors) {
                    Integer pos = fixedPos.get(nb);
                    if (pos != null) {
                        sum += pos;
                        count++;
                    }
                }
                bary[i] = count > 0 ? sum / count : i;
            }
        }

        // Sort movable layer by barycenter
        Integer[] indices = new Integer[movableLayer.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, Comparator.comparingDouble(a -> bary[a]));

        List<Integer> sorted = new ArrayList<>(movableLayer.size());
        for (int idx : indices) sorted.add(movableLayer.get(idx));

        movableLayer.clear();
        movableLayer.addAll(sorted);
    }

    /**
     * Counts total edge crossings across all adjacent layer pairs.
     */
    private int countTotalCrossings(List<List<Integer>> layers, DummyResult dm) {
        int total = 0;
        for (int i = 0; i < layers.size() - 1; i++) {
            total += countCrossings(layers.get(i), layers.get(i + 1), dm);
        }
        return total;
    }

    /**
     * Counts crossings between two adjacent layers using the inversion-count approach.
     */
    private int countCrossings(List<Integer> topLayer, List<Integer> botLayer, DummyResult dm) {
        // Position maps
        Map<Integer, Integer> topPos = new HashMap<>();
        for (int i = 0; i < topLayer.size(); i++) topPos.put(topLayer.get(i), i);
        Map<Integer, Integer> botPos = new HashMap<>();
        for (int i = 0; i < botLayer.size(); i++) botPos.put(botLayer.get(i), i);

        // Collect all edges between these two layers as (topPos, botPos) pairs
        List<int[]> edgePairs = new ArrayList<>();
        for (int topNode : topLayer) {
            for (int succ : dm.fwdAdj.get(topNode)) {
                Integer bp = botPos.get(succ);
                if (bp != null) {
                    edgePairs.add(new int[]{topPos.get(topNode), bp});
                }
            }
        }

        // Sort by top position, then count inversions in bottom positions
        edgePairs.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));

        // Simple O(n²) inversion count (fine for moderate sizes)
        int crossings = 0;
        for (int i = 0; i < edgePairs.size(); i++) {
            for (int j = i + 1; j < edgePairs.size(); j++) {
                if (edgePairs.get(i)[1] > edgePairs.get(j)[1]) {
                    crossings++;
                }
            }
        }
        return crossings;
    }

    private static List<List<Integer>> deepCopyLayers(List<List<Integer>> layers) {
        List<List<Integer>> copy = new ArrayList<>(layers.size());
        for (var layer : layers) copy.add(new ArrayList<>(layer));
        return copy;
    }

    /**
     * Transpose heuristic: for each layer, check adjacent node pairs and swap
     * them if doing so reduces the number of edge crossings with neighbouring layers.
     * Repeats until no further improvements can be found.
     */
    private void transposeAdjacentPairs(List<List<Integer>> layers, DummyResult dm) {
        boolean improved = true;
        int maxPasses = 8;
        while (improved && maxPasses-- > 0) {
            improved = false;
            for (int li = 0; li < layers.size(); li++) {
                List<Integer> layer = layers.get(li);
                for (int j = 0; j < layer.size() - 1; j++) {
                    int before = 0;
                    if (li > 0) before += countCrossings(layers.get(li - 1), layer, dm);
                    if (li < layers.size() - 1) before += countCrossings(layer, layers.get(li + 1), dm);
                    Collections.swap(layer, j, j + 1);
                    int after = 0;
                    if (li > 0) after += countCrossings(layers.get(li - 1), layer, dm);
                    if (li < layers.size() - 1) after += countCrossings(layer, layers.get(li + 1), dm);
                    if (after < before) {
                        improved = true;
                    } else {
                        Collections.swap(layer, j, j + 1); // undo
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 5 — Grid-Snapped Left-to-Right Position Assignment
    // ═══════════════════════════════════════════════════════════

    /**
     * Places nodes in columns (one per layer) going left-to-right.
     * All coordinates are snapped to the {@link #GRID_CELL} grid.
     */
    private Map<String, NodePosition> assignGridPositions(
            ProcessGraph graph, List<List<String>> columnOrder, int numLayers,
            Map<String, Integer> badgeCounts) {
        Map<String, NodePosition> positions = new HashMap<>();
        int padding = snapToGrid(nodeGap);
        int colX = padding;
        for (int col = 0; col < numLayers; col++) {
            List<String> nodesInCol = columnOrder.get(col);
            int y = padding;
            int maxWidthInCol = snapToGrid(cardWidth); // track widest node in column
            for (String nid : nodesInCol) {
                ProcessNode node = graph.node(nid);
                int w, h;
                int[] szOverride = nodeSizeOverrides.get(nid);
                if (szOverride != null) {
                    w = snapToGrid(szOverride[0]);
                    h = snapToGrid(szOverride[1]);
                } else {
                    w = snapToGrid(cardWidth);
                    h = snapToGrid(node != null ? computeCardHeight(node, cardWidth) : FALLBACK_CARD_HEIGHT);
                }
                maxWidthInCol = Math.max(maxWidthInCol, w);
                int sx = snapToGrid(colX);
                int sy = snapToGrid(y);
                positions.put(nid, new NodePosition(nid, col, sx, sy, w, h));
                // Account for satellite badges stacked below this node
                int badges = badgeCounts.getOrDefault(nid, 0);
                int badgeSpace = badges > 0 ? snapToGrid(badges * (MIRROR_BADGE_H + 4) + 8) : 0;
                y = sy + h + badgeSpace + snapToGrid(nodeGap);
            }
            colX += maxWidthInCol + snapToGrid(layerGap);
        }
        return positions;
    }

    private static int snapToGrid(double v) {
        return (int) (Math.round(v / GRID_CELL) * GRID_CELL);
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 5.5 — Port Reordering (minimize near-node crossings)
    // ═══════════════════════════════════════════════════════════

    /**
     * For each node, reorders its input and output ports so that edges
     * connecting to vertically-similar neighbours exit/arrive in sorted
     * order, dramatically reducing crossings near the card edges.
     * <p>
     * Output ports are sorted by the average Y of their target nodes;
     * input ports by the average Y of their source nodes.
     *
     * @return nodeId → int[2][maxSlots];  [0][origIdx]=newIdx (inputs), [1][origIdx]=newIdx (outputs)
     */
    private Map<String, int[][]> computePortOrder(ProcessGraph graph,
                                                   Map<String, NodePosition> positions) {
        Map<String, int[][]> result = new HashMap<>();
        for (ProcessNode node : graph.nodes()) {
            NodePosition pos = positions.get(node.id());
            if (pos == null) continue;

            int inCount = node.inputs().size();
            int outCount = node.outputs().size();
            int maxSlots = Math.max(inCount, outCount);
            if (maxSlots == 0) continue;

            int[][] order = new int[2][maxSlots];
            for (int i = 0; i < maxSlots; i++) { order[0][i] = i; order[1][i] = i; }

            // ── Reorder output ports by average target-node Y ──
            if (outCount > 1) {
                double[] avgY = new double[outCount];
                int[] cnt = new int[outCount];
                for (ProcessEdge e : graph.outgoingEdges(node.id())) {
                    int si = findSlotIndex(node.outputs(), e.materialName());
                    NodePosition tp = positions.get(e.toNodeId());
                    if (tp != null) { avgY[si] += tp.centerY(); cnt[si]++; }
                }
                Integer[] idx = new Integer[outCount];
                for (int i = 0; i < outCount; i++) {
                    idx[i] = i;
                    avgY[i] = cnt[i] > 0 ? avgY[i] / cnt[i] : pos.centerY();
                }
                Arrays.sort(idx, Comparator.comparingDouble(i -> avgY[i]));
                for (int newPos = 0; newPos < outCount; newPos++) {
                    order[1][idx[newPos]] = newPos;
                }
            }

            // ── Reorder input ports by average source-node Y ──
            if (inCount > 1) {
                double[] avgY = new double[inCount];
                int[] cnt = new int[inCount];
                for (ProcessEdge e : graph.incomingEdges(node.id())) {
                    int si = findSlotIndex(node.inputs(), e.materialName());
                    NodePosition sp = positions.get(e.fromNodeId());
                    if (sp != null) { avgY[si] += sp.centerY(); cnt[si]++; }
                }
                Integer[] idx = new Integer[inCount];
                for (int i = 0; i < inCount; i++) {
                    idx[i] = i;
                    avgY[i] = cnt[i] > 0 ? avgY[i] / cnt[i] : pos.centerY();
                }
                Arrays.sort(idx, Comparator.comparingDouble(i -> avgY[i]));
                for (int newPos = 0; newPos < inCount; newPos++) {
                    order[0][idx[newPos]] = newPos;
                }
            }

            result.put(node.id(), order);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 5.75 — Satellite Mirror Links for Ultra-Long Edges
    // ═══════════════════════════════════════════════════════════

    /**
     * Identifies edges that span more than {@link #SATELLITE_THRESHOLD} layers
     * and replaces them with satellite mirror-link badges.
     * <p>
     * For each such edge, two small badges are created:
     * <ul>
     *   <li><b>Source-side badge</b>: placed to the right of the source node,
     *       labelled "→ TargetName"</li>
     *   <li><b>Target-side badge</b>: placed to the left of the target node,
     *       labelled "← SourceName"</li>
     * </ul>
     * The badges carry matching {@code linkId} values so the renderer can
     * highlight pairs on hover.
     *
     * @param graph      the process graph
     * @param positions  node positions (may be modified to accommodate badges)
     * @param outLinks   list to append MirrorLink records to
     * @return set of edges that were converted to satellites (should be skipped by A* routing)
     */
    private Set<ProcessEdge> identifySatelliteEdges(ProcessGraph graph,
                                                     Map<String, NodePosition> positions,
                                                     List<MirrorLink> outLinks) {
        Set<ProcessEdge> satelliteEdges = new HashSet<>();

        // Track how many badges have been placed per node to stack them vertically
        Map<String, Integer> srcBadgeCount = new HashMap<>();
        Map<String, Integer> tgtBadgeCount = new HashMap<>();

        int linkId = 0;
        for (ProcessEdge edge : graph.edges()) {
            NodePosition srcPos = positions.get(edge.fromNodeId());
            NodePosition tgtPos = positions.get(edge.toNodeId());
            if (srcPos == null || tgtPos == null) continue;

            int layerSpan = Math.abs(tgtPos.layer() - srcPos.layer());
            if (layerSpan <= SATELLITE_THRESHOLD) continue;

            satelliteEdges.add(edge);

            ProcessNode srcNode = graph.node(edge.fromNodeId());
            ProcessNode tgtNode = graph.node(edge.toNodeId());
            String srcName = srcNode != null ? srcNode.name() : edge.fromNodeId();
            String tgtName = tgtNode != null ? tgtNode.name() : edge.toNodeId();
            String material = edge.materialName();
            int amount = edge.amount();

            // Badge positions: stack vertically below the node
            int srcIdx = srcBadgeCount.getOrDefault(edge.fromNodeId(), 0);
            srcBadgeCount.put(edge.fromNodeId(), srcIdx + 1);
            int tgtIdx = tgtBadgeCount.getOrDefault(edge.toNodeId(), 0);
            tgtBadgeCount.put(edge.toNodeId(), tgtIdx + 1);

            double badgeGap = 6;
            // Source-side badge: below the source node, shifted right
            double srcBadgeX = snapToGrid(srcPos.right() - MIRROR_BADGE_W);
            double srcBadgeY = snapToGrid(srcPos.bottom() + badgeGap + srcIdx * (MIRROR_BADGE_H + 4));
            // Target-side badge: below the target node, shifted left
            double tgtBadgeX = snapToGrid(tgtPos.x());
            double tgtBadgeY = snapToGrid(tgtPos.bottom() + badgeGap + tgtIdx * (MIRROR_BADGE_H + 4));

            outLinks.add(new MirrorLink(linkId, edge, edge.fromNodeId(), edge.toNodeId(),
                    "\u2192 " + tgtName, material, amount,
                    srcBadgeX, srcBadgeY, true));
            outLinks.add(new MirrorLink(linkId, edge, edge.toNodeId(), edge.fromNodeId(),
                    "\u2190 " + srcName, material, amount,
                    tgtBadgeX, tgtBadgeY, false));
            linkId++;
        }
        return satelliteEdges;
    }

    // ═══════════════════════════════════════════════════════════
    //  Phase 6 — A* Orthogonal (Manhattan) Edge Routing
    // ═══════════════════════════════════════════════════════════

    /**
     * Routes all edges using A* pathfinding on a grid.
     * Produces strictly orthogonal (horizontal/vertical) polyline routes.
     * <p>
     * Each route exits horizontally from the source node's right edge
     * and arrives horizontally at the target node's left edge, matching the
     * factory / main-bus aesthetic.
     * <p>
     * <b>Two-pass relaxed crossing</b>: when the strict (high-penalty) route is
     * excessively long compared to the Manhattan distance, a second A* pass
     * runs with a much lower crossing penalty. If the relaxed route is
     * significantly shorter and has few crossings, it is preferred.
     */
    private List<EdgeRoute> routeAllEdges(ProcessGraph graph,
                                           Map<String, NodePosition> positions,
                                           Map<String, int[][]> portOrder,
                                           Set<ProcessEdge> satelliteEdges,
                                           List<MirrorLink> mirrorLinks) {
        // Determine grid dimensions (include mirror link badges)
        double maxPx = 0, maxPy = 0;
        for (NodePosition p : positions.values()) {
            maxPx = Math.max(maxPx, p.right());
            maxPy = Math.max(maxPy, p.bottom());
        }
        for (MirrorLink ml : mirrorLinks) {
            maxPx = Math.max(maxPx, ml.badgeX() + MIRROR_BADGE_W);
            maxPy = Math.max(maxPy, ml.badgeY() + MIRROR_BADGE_H);
        }
        int gridW = (int) (maxPx / GRID_CELL) + 30;
        int gridH = (int) (maxPy / GRID_CELL) + 30;

        // Mark cells occupied by nodes (with margin) as blocked
        boolean[][] blocked = new boolean[gridW][gridH];
        for (NodePosition p : positions.values()) {
            int gx0 = Math.max(0, (int) (p.x() / GRID_CELL) - NODE_MARGIN_CELLS);
            int gy0 = Math.max(0, (int) (p.y() / GRID_CELL) - NODE_MARGIN_CELLS);
            int gx1 = Math.min(gridW - 1, (int) Math.ceil(p.right() / GRID_CELL) + NODE_MARGIN_CELLS);
            int gy1 = Math.min(gridH - 1, (int) Math.ceil(p.bottom() / GRID_CELL) + NODE_MARGIN_CELLS);
            for (int gx = gx0; gx <= gx1; gx++)
                for (int gy = gy0; gy <= gy1; gy++)
                    blocked[gx][gy] = true;
        }
        // Mark cells occupied by mirror-link badges as blocked
        for (MirrorLink ml : mirrorLinks) {
            int gx0 = Math.max(0, (int) (ml.badgeX() / GRID_CELL) - 1);
            int gy0 = Math.max(0, (int) (ml.badgeY() / GRID_CELL) - 1);
            int gx1 = Math.min(gridW - 1, (int) Math.ceil((ml.badgeX() + MIRROR_BADGE_W) / GRID_CELL) + 1);
            int gy1 = Math.min(gridH - 1, (int) Math.ceil((ml.badgeY() + MIRROR_BADGE_H) / GRID_CELL) + 1);
            for (int gx = gx0; gx <= gx1; gx++)
                for (int gy = gy0; gy <= gy1; gy++)
                    blocked[gx][gy] = true;
        }

        int[][] edgeUsage = new int[gridW][gridH];
        List<EdgeRoute> routes = new ArrayList<>();

        // Sort edges by Manhattan distance descending — longer routes get priority
        List<ProcessEdge> sorted = new ArrayList<>(graph.edges());
        sorted.sort((a, b) -> {
            NodePosition pa = positions.get(a.fromNodeId());
            NodePosition qa = positions.get(a.toNodeId());
            NodePosition pb = positions.get(b.fromNodeId());
            NodePosition qb = positions.get(b.toNodeId());
            double dA = (pa != null && qa != null)
                    ? Math.abs(qa.x() - pa.right()) + Math.abs(qa.centerY() - pa.centerY()) : 0;
            double dB = (pb != null && qb != null)
                    ? Math.abs(qb.x() - pb.right()) + Math.abs(qb.centerY() - pb.centerY()) : 0;
            return Double.compare(dB, dA);
        });

        for (ProcessEdge edge : sorted) {
            if (satelliteEdges.contains(edge)) continue; // handled as mirror link
            NodePosition srcPos = positions.get(edge.fromNodeId());
            NodePosition tgtPos = positions.get(edge.toNodeId());
            if (srcPos == null || tgtPos == null) continue;

            ProcessNode srcNode = graph.node(edge.fromNodeId());
            ProcessNode tgtNode = graph.node(edge.toNodeId());
            if (srcNode == null || tgtNode == null) continue;

            int origOutIdx = findSlotIndex(srcNode.outputs(), edge.materialName());
            int origInIdx = findSlotIndex(tgtNode.inputs(), edge.materialName());
            int outIdx = reorderedPort(portOrder, edge.fromNodeId(), false, origOutIdx);
            int inIdx = reorderedPort(portOrder, edge.toNodeId(), true, origInIdx);
            int outCount = Math.max(srcNode.outputs().size(), 1);
            int inCount = Math.max(tgtNode.inputs().size(), 1);

            // Output port on RIGHT edge, input port on LEFT edge
            double srcPortX = snapToGrid(srcPos.right());
            double srcPortY = snapToGrid(srcPos.y() + portYOffset(outIdx, outCount, (int) srcPos.height()));
            double tgtPortX = snapToGrid(tgtPos.x());
            double tgtPortY = snapToGrid(tgtPos.y() + portYOffset(inIdx, inCount, (int) tgtPos.height()));

            // A* routes between cells just outside the node margin
            int startGX = (int) (srcPortX / GRID_CELL) + 2;
            int startGY = (int) (srcPortY / GRID_CELL);
            int endGX = (int) (tgtPortX / GRID_CELL) - 2;
            int endGY = (int) (tgtPortY / GRID_CELL);

            // -- Pass 1: strict (full crossing penalty)
            List<int[]> gridPath = aStarFind(startGX, startGY, endGX, endGY,
                    blocked, edgeUsage, gridW, gridH, CROSS_PENALTY);

            // -- Pass 2 (conditional): relaxed crossing penalty
            // If the strict route is excessively long (> 2.5× Manhattan distance),
            // try a second pass with a much lower crossing penalty.
            // Accept the shorter route if it has ≤ MAX_TOLERATED_CROSSINGS.
            int straightDist = Math.max(1, manhattan(startGX, startGY, endGX, endGY));
            if (gridPath != null && gridPath.size() > straightDist * 2.5) {
                List<int[]> relaxedPath = aStarFind(startGX, startGY, endGX, endGY,
                        blocked, edgeUsage, gridW, gridH, CROSS_PENALTY / 5);
                if (relaxedPath != null) {
                    int crossings = countPathCrossings(relaxedPath, edgeUsage);
                    if (crossings <= MAX_TOLERATED_CROSSINGS
                            && relaxedPath.size() < gridPath.size() * 0.65) {
                        gridPath = relaxedPath;
                    }
                }
            }

            // Build pixel path: [source port] -> [routed path] -> [target port]
            List<double[]> pixelPath = new ArrayList<>();
            pixelPath.add(new double[]{srcPortX, srcPortY});
            if (gridPath != null && !gridPath.isEmpty()) {
                pixelPath.addAll(simplifyGridPath(gridPath));
            } else {
                // Fallback: L-shaped route
                double midX = snapToGrid((srcPortX + tgtPortX) / 2.0);
                pixelPath.add(new double[]{midX, srcPortY});
                pixelPath.add(new double[]{midX, tgtPortY});
            }
            pixelPath.add(new double[]{tgtPortX, tgtPortY});

            markPathCells(pixelPath, edgeUsage, gridW, gridH);

            PortInfo srcPort = new PortInfo(edge.fromNodeId(), outIdx, false, srcPortX, srcPortY);
            PortInfo tgtPort = new PortInfo(edge.toNodeId(), inIdx, true, tgtPortX, tgtPortY);
            routes.add(new EdgeRoute(edge, pixelPath, srcPort, tgtPort));
        }

        return routes;
    }

    // ─────────────────── A* Core ───────────────────

    /** Direction deltas: RIGHT=0, DOWN=1, LEFT=2, UP=3. */
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    /**
     * A* pathfinding on the routing grid.
     * State = (gridX, gridY, lastDirection).
     * Cost per cell = 1 + {@link #BEND_PENALTY} per 90-degree turn
     *                   + crossPenalty per existing edge in cell occupancy.
     *
     * @param crossPenalty penalty multiplied by cell's existing edge count
     * @return list of grid-cell coordinates, or {@code null} if unreachable
     */
    private List<int[]> aStarFind(int sx, int sy, int tx, int ty,
                                   boolean[][] blocked, int[][] edgeUsage,
                                   int gridW, int gridH, int crossPenalty) {
        if (sx < 0 || sy < 0 || sx >= gridW || sy >= gridH) return null;
        if (tx < 0 || ty < 0 || tx >= gridW || ty >= gridH) return null;

        int stateCount = gridW * gridH * 4;
        int[] gCost = new int[stateCount];
        int[] cameFrom = new int[stateCount];
        Arrays.fill(gCost, Integer.MAX_VALUE);
        Arrays.fill(cameFrom, -1);

        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));

        // Seed with initial direction = RIGHT (direction 0); penalize other starts
        for (int d = 0; d < 4; d++) {
            int s = encState(sx, sy, d, gridH);
            int g = (d == 0) ? 0 : BEND_PENALTY;
            gCost[s] = g;
            open.add(new long[]{g + manhattan(sx, sy, tx, ty), s});
        }

        int goalState = -1;
        while (!open.isEmpty()) {
            long[] top = open.poll();
            int s = (int) top[1];
            int f = (int) top[0];
            int cx = s / (gridH * 4);
            int cy = (s / 4) % gridH;
            int cd = s % 4;

            if (gCost[s] > f - manhattan(cx, cy, tx, ty)) continue; // stale entry
            if (cx == tx && cy == ty) {
                goalState = s;
                break;
            }

            for (int d = 0; d < 4; d++) {
                if (d == ((cd + 2) & 3)) continue; // skip 180-degree turns
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (nx < 0 || ny < 0 || nx >= gridW || ny >= gridH) continue;
                if (blocked[nx][ny]) continue;

                int step = 1;
                if (d != cd) step += BEND_PENALTY;
                if (edgeUsage[nx][ny] > 0) step += crossPenalty * edgeUsage[nx][ny];

                int ng = gCost[s] + step;
                int ns = encState(nx, ny, d, gridH);
                if (ng < gCost[ns]) {
                    gCost[ns] = ng;
                    cameFrom[ns] = s;
                    open.add(new long[]{ng + manhattan(nx, ny, tx, ty), ns});
                }
            }
        }

        if (goalState < 0) return null;

        // Reconstruct grid path
        List<int[]> path = new ArrayList<>();
        for (int s = goalState; s >= 0; s = cameFrom[s]) {
            path.add(new int[]{s / (gridH * 4), (s / 4) % gridH});
        }
        Collections.reverse(path);
        return path;
    }

    private static int encState(int gx, int gy, int dir, int gridH) {
        return (gx * gridH + gy) * 4 + dir;
    }

    private static int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x2 - x1) + Math.abs(y2 - y1);
    }

    /**
     * Counts how many cells along a grid path already have edge traffic.
     */
    private static int countPathCrossings(List<int[]> gridPath, int[][] edgeUsage) {
        int crossings = 0;
        for (int[] cell : gridPath) {
            if (cell[0] >= 0 && cell[0] < edgeUsage.length
                    && cell[1] >= 0 && cell[1] < edgeUsage[0].length
                    && edgeUsage[cell[0]][cell[1]] > 0) {
                crossings++;
            }
        }
        return crossings;
    }

    // ─────────────────── Path Simplification ───────────────────

    /**
     * Converts a grid-cell path to pixel coordinates and merges collinear
     * segments so only corner (bend) points remain.
     */
    private List<double[]> simplifyGridPath(List<int[]> gridPath) {
        if (gridPath.size() <= 1) {
            List<double[]> r = new ArrayList<>();
            for (int[] c : gridPath)
                r.add(new double[]{c[0] * (double) GRID_CELL, c[1] * (double) GRID_CELL});
            return r;
        }
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{gridPath.get(0)[0] * (double) GRID_CELL,
                gridPath.get(0)[1] * (double) GRID_CELL});
        for (int i = 1; i < gridPath.size() - 1; i++) {
            int[] prev = gridPath.get(i - 1), curr = gridPath.get(i), next = gridPath.get(i + 1);
            if ((curr[0] - prev[0]) != (next[0] - curr[0])
                    || (curr[1] - prev[1]) != (next[1] - curr[1])) {
                pts.add(new double[]{curr[0] * (double) GRID_CELL,
                        curr[1] * (double) GRID_CELL});
            }
        }
        int[] last = gridPath.get(gridPath.size() - 1);
        pts.add(new double[]{last[0] * (double) GRID_CELL, last[1] * (double) GRID_CELL});
        return pts;
    }

    /**
     * Marks grid cells along a pixel-coordinate path as used (for crossing penalties).
     */
    private static void markPathCells(List<double[]> path,
                                       int[][] edgeUsage, int gridW, int gridH) {
        for (int i = 0; i < path.size() - 1; i++) {
            int gx0 = (int) (path.get(i)[0] / GRID_CELL);
            int gy0 = (int) (path.get(i)[1] / GRID_CELL);
            int gx1 = (int) (path.get(i + 1)[0] / GRID_CELL);
            int gy1 = (int) (path.get(i + 1)[1] / GRID_CELL);
            if (gy0 == gy1) {
                for (int gx = Math.min(gx0, gx1); gx <= Math.max(gx0, gx1); gx++)
                    if (gx >= 0 && gx < gridW && gy0 >= 0 && gy0 < gridH) edgeUsage[gx][gy0]++;
            } else {
                for (int gy = Math.min(gy0, gy1); gy <= Math.max(gy0, gy1); gy++)
                    if (gx0 >= 0 && gx0 < gridW && gy >= 0 && gy < gridH) edgeUsage[gx0][gy]++;
            }
        }
    }

    // ─────────────────── Port Position Helpers ───────────────────

    /**
     * Computes the Y offset from card-top for a port on the left/right edge.
     * Ports are vertically distributed within the card's usable area
     * (below header + name, above stats bar), snapped to the grid.
     */
    public static double portYOffset(int slotIndex, int slotCount, int cardHeight) {
        int topPad = CARD_HEADER_H + CARD_NAME_H + 2;
        int bottomPad = CARD_STATS_H + 2;
        double usable = cardHeight - topPad - bottomPad;
        double off = topPad + (slotIndex + 0.5) / Math.max(slotCount, 1) * usable;
        return Math.round(off / GRID_CELL) * GRID_CELL;
    }

    /**
     * Finds the index of the first slot matching the given material name.
     */
    private static int findSlotIndex(List<ProcessNode.MaterialSlot> slots, String materialName) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).materialName().equals(materialName)) return i;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  Internal adjacency helper
    // ═══════════════════════════════════════════════════════════

    private static class AdjacencyMatrix {
        final int size;
        final List<String> nodeIds;
        final Map<String, Integer> idToIndex;
        final List<List<Integer>> fwd; // successors
        final List<List<Integer>> bwd; // predecessors

        AdjacencyMatrix(List<String> nodeIds, ProcessGraph graph) {
            this.size = nodeIds.size();
            this.nodeIds = nodeIds;
            this.idToIndex = new HashMap<>();
            for (int i = 0; i < size; i++) idToIndex.put(nodeIds.get(i), i);

            fwd = new ArrayList<>();
            bwd = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                fwd.add(new ArrayList<>());
                bwd.add(new ArrayList<>());
            }

            for (var edge : graph.edges()) {
                Integer u = idToIndex.get(edge.fromNodeId());
                Integer v = idToIndex.get(edge.toNodeId());
                if (u != null && v != null) {
                    if (!fwd.get(u).contains(v)) {
                        fwd.get(u).add(v);
                        bwd.get(v).add(u);
                    }
                }
            }
        }

        List<Integer> successors(int u) {
            return fwd.get(u);
        }

        List<Integer> predecessors(int u) {
            return bwd.get(u);
        }

        void reverse(int u, int v) {
            fwd.get(u).remove(Integer.valueOf(v));
            bwd.get(v).remove(Integer.valueOf(u));
            fwd.get(v).add(u);
            bwd.get(u).add(v);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Result types
    // ═══════════════════════════════════════════════════════════

    /**
     * @param positions   node positions keyed by node id
     * @param edgeRoutes  routed edges
     * @param portOrder   per-node port permutation
     * @param mirrorLinks satellite mirror-link badges for ultra-long edges
     * @param groupBoxes  compound-group bounding boxes (groupId → rect)
     * @param totalWidth  bounding-box width
     * @param totalHeight bounding-box height
     */
    public record LayoutResult(
            Map<String, NodePosition> positions,
            List<EdgeRoute> edgeRoutes,
            Map<String, int[][]> portOrder,
            List<MirrorLink> mirrorLinks,
            Map<String, GroupBox> groupBoxes,
            double totalWidth,
            double totalHeight
    ) {}

    /**
     * Bounding box of a compound group, after bottom-up size propagation.
     */
    public record GroupBox(
            String groupId,
            double x, double y,
            double width, double height,
            int color,
            String label
    ) {
        public double right()  { return x + width; }
        public double bottom() { return y + height; }
    }

    /** Look up the reordered port index, returning {@code origIdx} if no mapping exists. */
    public static int reorderedPort(Map<String, int[][]> portOrder,
                                     String nodeId, boolean isInput, int origIdx) {
        int[][] order = portOrder == null ? null : portOrder.get(nodeId);
        if (order == null) return origIdx;
        int[] perm = isInput ? order[0] : order[1];
        return origIdx < perm.length ? perm[origIdx] : origIdx;
    }

    public record NodePosition(
            String nodeId,
            int layer,
            double x, double y,
            double width, double height
    ) {
        public double centerX() { return x + width / 2; }
        public double centerY() { return y + height / 2; }
        public double right()   { return x + width; }
        public double bottom()  { return y + height; }
    }

    /**
     * Port position on a card edge for edge connection.
     *
     * @param nodeId    the process node id
     * @param slotIndex index into the node's input or output slot list
     * @param isInput   true for input port, false for output port
     * @param x         port X pixel position
     * @param y         port Y pixel position
     */
    public record PortInfo(
            String nodeId,
            int slotIndex,
            boolean isInput,
            double x,
            double y
    ) {}

    /**
     * Routing path for a single edge with port connection info.
     *
     * @param edge       the original process edge
     * @param points     ordered control points for the spline
     * @param sourcePort output port on the source card
     * @param targetPort input port on the target card
     */
    public record EdgeRoute(
            ProcessEdge edge,
            List<double[]> points,
            PortInfo sourcePort,
            PortInfo targetPort
    ) {}

    /**
     * Describes a satellite mirror-link badge that replaces an ultra-long edge.
     * Two MirrorLinks share the same {@code linkId}: one is the source-side badge
     * (isSource=true, label="→ TargetName") and one is the target-side badge
     * (isSource=false, label="← SourceName").
     *
     * @param linkId       shared pair identifier (both badges in a pair have the same id)
     * @param edge         the original process edge
     * @param hostNodeId   the node this badge is attached to
     * @param remoteNodeId the node on the other end of the link
     * @param label        display text (e.g. "→ EBF Smelting")
     * @param material     material being transferred
     * @param amount       amount being transferred
     * @param badgeX       badge top-left X
     * @param badgeY       badge top-left Y
     * @param isSource     true = source-side (arrow out), false = target-side (arrow in)
     */
    public record MirrorLink(
            int linkId,
            ProcessEdge edge,
            String hostNodeId,
            String remoteNodeId,
            String label,
            String material,
            int amount,
            double badgeX,
            double badgeY,
            boolean isSource
    ) {}
}
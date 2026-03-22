package dev.vfyjxf.cirrus.ui.widget.flowchart.layout;

import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessEdge;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGroup;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;

import java.util.*;

/**
 * Compound-DAG layout engine that wraps SugiyamaLayout.
 * <p>
 * Handles group hierarchy (infinitely nestable) by:
 * <ol>
 *   <li>Recursively laying out each group's interior (bottom-up)</li>
 *   <li>Collapsing each group into a single representative node
 *       with the bounding-box size of its interior content</li>
 *   <li>Running top-level Sugiyama for the global graph</li>
 *   <li>Recursively offsetting interior positions to match the
 *       group's final placement</li>
 *   <li>Computing group bounding boxes with padding</li>
 *   <li>Running final A* edge routing on the fully resolved positions</li>
 * </ol>
 * All coordinates are grid-snapped via {@link SugiyamaLayout#GRID_CELL}.
 */
public class CompoundLayout {

    // ─── config ───

    private int cardWidth = 280;
    /** Width multiplier for the final-target node card. */
    private static final double TARGET_WIDTH_SCALE = 1.5;
    /** Height extra padding for the final-target node card. */
    private static final int TARGET_HEIGHT_EXTRA = 30;

    /** Gap between layers INSIDE a group (compact). */
    private int layerGap = 160;
    /** Gap between nodes INSIDE a group (compact). */
    private int nodeGap = 40;
    /** Gap between layers at the TOP level (groups / ungrouped nodes). */
    private int topLayerGap = 260;
    /** Gap between groups (or ungrouped nodes) vertically at the TOP level. */
    private int topNodeGap = 100;
    private int maxSweeps = 30;

    /** Padding inside a group box (around its child content). */
    private int groupPadX = 50;
    private int groupPadTop = 36;   // extra room for the header label
    private int groupPadBottom = 30;

    // ─── builder ───

    public CompoundLayout cardWidth(int v)    { this.cardWidth = v; return this; }
    public CompoundLayout layerGap(int v)      { this.layerGap = v; return this; }
    public CompoundLayout nodeGap(int v)       { this.nodeGap = v; return this; }
    public CompoundLayout topLayerGap(int v)   { this.topLayerGap = v; return this; }
    public CompoundLayout topNodeGap(int v)    { this.topNodeGap = v; return this; }
    public CompoundLayout maxSweeps(int v)     { this.maxSweeps = v; return this; }
    public CompoundLayout groupPadX(int v)     { this.groupPadX = v; return this; }
    public CompoundLayout groupPadTop(int v)   { this.groupPadTop = v; return this; }
    public CompoundLayout groupPadBottom(int v){ this.groupPadBottom = v; return this; }

    // ═══════════════════ Public entry point ═══════════════════

    /**
     * Lays out the graph with compound-group nesting.
     */
    public SugiyamaLayout.LayoutResult layout(ProcessGraph graph) {
        if (graph.nodeCount() == 0) {
            return new SugiyamaLayout.LayoutResult(
                    Map.of(), List.of(), Map.of(), List.of(), Map.of(), 0, 0);
        }

        // Detect the single final-target node (unique sink)
        List<ProcessNode> sinks = graph.sinks();
        String targetId = sinks.size() == 1 ? sinks.get(0).id() : null;

        // If no groups, delegate directly
        if (graph.groupCount() == 0) {
            SugiyamaLayout flat = topLevelLayout();
            if (targetId != null) {
                injectTargetOverride(flat, graph, targetId);
            }
            return flat.layout(graph);
        }

        // ── Step 1: Build group hierarchy ──
        // Root-level elements: ungrouped nodes + root-level groups
        List<ProcessGroup> rootGroups = new ArrayList<>();
        Set<String> allGroupedNodeIds = new HashSet<>();
        for (ProcessGroup g : graph.groups()) {
            if (g.parentGroupId() == null) rootGroups.add(g);
            allGroupedNodeIds.addAll(collectAllDescendantNodes(g, graph));
        }

        // ── Step 2: Bottom-up layout for each group to get bounding sizes ──
        // groupId → interior layout result (positions relative to group origin)
        Map<String, SugiyamaLayout.LayoutResult> interiorLayouts = new LinkedHashMap<>();
        // groupId → [width, height] of the group's bounding box
        Map<String, int[]> groupSizes = new LinkedHashMap<>();

        // Sort groups bottom-up (leaf groups first)
        List<ProcessGroup> bottomUpOrder = bottomUpSort(graph);

        for (ProcessGroup group : bottomUpOrder) {
            layoutGroupInterior(group, graph, interiorLayouts, groupSizes, targetId);
        }

        // ── Step 3: Build a flattened root-level graph ──
        // Replace each root group with a single virtual node sized to its bbox.
        // Edges between groups use group representatives.
        ProcessGraph rootGraph = buildRootLevelGraph(graph, rootGroups, allGroupedNodeIds);

        Map<String, int[]> rootOverrides = new HashMap<>();
        for (ProcessGroup rg : rootGroups) {
            int[] sz = groupSizes.get(rg.id());
            if (sz != null) {
                rootOverrides.put(rg.id(), sz);
            }
        }

        SugiyamaLayout topLayout = topLevelLayout()
                .nodeSizeOverrides(rootOverrides)
                .skipRouting(true);
        SugiyamaLayout.LayoutResult topResult = topLayout.layout(rootGraph);

        // ── Step 4: Assemble final positions ──
        Map<String, SugiyamaLayout.NodePosition> finalPositions = new LinkedHashMap<>();
        Map<String, SugiyamaLayout.GroupBox> groupBoxes = new LinkedHashMap<>();

        // Place ungrouped nodes (already in topResult directly)
        for (var entry : topResult.positions().entrySet()) {
            String id = entry.getKey();
            SugiyamaLayout.NodePosition pos = entry.getValue();
            if (graph.containsNode(id)) {
                // Real ungrouped node
                finalPositions.put(id, pos);
            }
        }

        // Recursively place group content
        for (ProcessGroup rg : rootGroups) {
            SugiyamaLayout.NodePosition groupSlot = topResult.positions().get(rg.id());
            if (groupSlot == null) continue;
            placeGroupContent(rg, graph, interiorLayouts, groupSizes,
                    groupSlot.x() + groupPadX, groupSlot.y() + groupPadTop,
                    finalPositions, groupBoxes, groupSlot);
        }

        // ── Step 5: Port order (on final positions) + full edge routing ──
        // (routingLayout not used directly — routing done inline below)
        // We can't call the private methods directly, so we do a fresh layout
        // with all positions pre-determined. Instead, let's compute port order
        // and routing on the resolved positions.
        // Rebuild a complete graph view for routing
        Map<String, int[][]> portOrder = computeSimplePortOrder(graph, finalPositions);

        List<SugiyamaLayout.MirrorLink> mirrorLinks = new ArrayList<>();
        // For edges spanning too many layers, create mirror links
        Set<ProcessEdge> satelliteEdges = identifySatelliteEdgesCompound(graph, finalPositions, mirrorLinks);

        List<SugiyamaLayout.EdgeRoute> routes = routeAllEdgesCompound(
                graph, finalPositions, portOrder, satelliteEdges, mirrorLinks, groupBoxes);

        // ── Step 6: Compute bounds ──
        double totalW = 0, totalH = 0;
        for (var pos : finalPositions.values()) {
            totalW = Math.max(totalW, pos.right());
            totalH = Math.max(totalH, pos.bottom());
        }
        for (var gb : groupBoxes.values()) {
            totalW = Math.max(totalW, gb.right());
            totalH = Math.max(totalH, gb.bottom());
        }
        for (var route : routes) {
            for (double[] pt : route.points()) {
                totalW = Math.max(totalW, pt[0]);
                totalH = Math.max(totalH, pt[1]);
            }
        }
        for (var ml : mirrorLinks) {
            totalW = Math.max(totalW, ml.badgeX() + SugiyamaLayout.MIRROR_BADGE_W);
            totalH = Math.max(totalH, ml.badgeY() + SugiyamaLayout.MIRROR_BADGE_H);
        }
        totalW += SugiyamaLayout.GRID_CELL * 5;
        totalH += SugiyamaLayout.GRID_CELL * 5;

        return new SugiyamaLayout.LayoutResult(
                finalPositions, routes, portOrder, mirrorLinks, groupBoxes, totalW, totalH);
    }

    // ═══════════════════════════════════════════════════════════
    //  Interior group layout (bottom-up)
    // ═══════════════════════════════════════════════════════════

    private void layoutGroupInterior(ProcessGroup group, ProcessGraph fullGraph,
                                      Map<String, SugiyamaLayout.LayoutResult> interiorLayouts,
                                      Map<String, int[]> groupSizes,
                                      @org.jetbrains.annotations.Nullable String targetId) {
        // Build a sub-graph containing only this group's direct child nodes.
        // Child groups are represented as virtual nodes with their computed sizes.
        ProcessGraph subGraph = new ProcessGraph();

        // Add direct child nodes
        for (String nid : group.childNodeIds()) {
            ProcessNode node = fullGraph.node(nid);
            if (node != null) subGraph.addNode(node);
        }
        // Add child-group virtual nodes (already sized in previous iterations)
        for (String cgId : group.childGroupIds()) {
            ProcessGroup cg = fullGraph.group(cgId);
            if (cg != null) {
                // Virtual placeholder node
                subGraph.addNode(new ProcessNode(
                        cgId, cg.name(), "Group",
                        List.of(), List.of(), 0, 0, 0));
            }
        }

        // Add internal edges
        Set<String> memberIds = new HashSet<>(group.childNodeIds());
        memberIds.addAll(group.childGroupIds());
        // Collect all transitive descendants for child groups
        Map<String, String> descendantToRepresentative = new HashMap<>();
        for (String cgId : group.childGroupIds()) {
            ProcessGroup cg = fullGraph.group(cgId);
            if (cg != null) {
                for (String desc : collectAllDescendantNodes(cg, fullGraph)) {
                    descendantToRepresentative.put(desc, cgId);
                }
            }
        }

        for (ProcessEdge edge : fullGraph.edges()) {
            String fromRep = liftToGroupMember(edge.fromNodeId(), memberIds, descendantToRepresentative);
            String toRep = liftToGroupMember(edge.toNodeId(), memberIds, descendantToRepresentative);
            if (fromRep != null && toRep != null && !fromRep.equals(toRep)) {
                subGraph.addEdge(new ProcessEdge(fromRep, toRep,
                        edge.materialName(), edge.amount(), edge.type()));
            }
        }

        // Size overrides for child groups (use their already-computed sizes)
        Map<String, int[]> overrides = new HashMap<>();
        for (String cgId : group.childGroupIds()) {
            int[] sz = groupSizes.get(cgId);
            if (sz != null) overrides.put(cgId, sz);
        }

        if (subGraph.nodeCount() == 0) {
            // Empty group
            int w = snap(2 * groupPadX + cardWidth);
            int h = snap(groupPadTop + groupPadBottom + 60);
            groupSizes.put(group.id(), new int[]{w, h});
            interiorLayouts.put(group.id(), new SugiyamaLayout.LayoutResult(
                    Map.of(), List.of(), Map.of(), List.of(), Map.of(), w, h));
            return;
        }

        // If the target node is in this group, enlarge it
        if (targetId != null && group.childNodeIds().contains(targetId)) {
            ProcessNode tn = fullGraph.node(targetId);
            if (tn != null) {
                int tw = snap(cardWidth * TARGET_WIDTH_SCALE);
                int th = snap(SugiyamaLayout.computeCardHeight(tn, tw) + TARGET_HEIGHT_EXTRA);
                overrides.put(targetId, new int[]{tw, th});
            }
        }

        SugiyamaLayout interior = interiorLayout()
                .nodeSizeOverrides(overrides)
                .skipRouting(true);
        SugiyamaLayout.LayoutResult result = interior.layout(subGraph);
        interiorLayouts.put(group.id(), result);

        // Compute bounding box from interior positions
        double maxW = result.totalWidth();
        double maxH = result.totalHeight();
        for (var pos : result.positions().values()) {
            maxW = Math.max(maxW, pos.right());
            maxH = Math.max(maxH, pos.bottom());
        }

        int boxW = snap(maxW + 2 * groupPadX);
        int boxH = snap(maxH + groupPadTop + groupPadBottom);
        groupSizes.put(group.id(), new int[]{boxW, boxH});
    }

    // ═══════════════════════════════════════════════════════════
    //  Recursive placement (top-down)
    // ═══════════════════════════════════════════════════════════

    private void placeGroupContent(ProcessGroup group, ProcessGraph fullGraph,
                                    Map<String, SugiyamaLayout.LayoutResult> interiorLayouts,
                                    Map<String, int[]> groupSizes,
                                    double offsetX, double offsetY,
                                    Map<String, SugiyamaLayout.NodePosition> finalPositions,
                                    Map<String, SugiyamaLayout.GroupBox> groupBoxes,
                                    SugiyamaLayout.NodePosition groupSlot) {
        // Record group bounding box
        groupBoxes.put(group.id(), new SugiyamaLayout.GroupBox(
                group.id(), groupSlot.x(), groupSlot.y(),
                groupSlot.width(), groupSlot.height(),
                group.color(), group.name()));

        SugiyamaLayout.LayoutResult interior = interiorLayouts.get(group.id());
        if (interior == null) return;

        // Place direct child nodes with offset
        for (var entry : interior.positions().entrySet()) {
            String childId = entry.getKey();
            SugiyamaLayout.NodePosition childPos = entry.getValue();

            // Check if it's a child group placeholder
            if (group.childGroupIds().contains(childId)) {
                ProcessGroup childGroup = fullGraph.group(childId);
                if (childGroup != null) {
                    double cx = offsetX + childPos.x();
                    double cy = offsetY + childPos.y();
                    SugiyamaLayout.NodePosition childGroupSlot = new SugiyamaLayout.NodePosition(
                            childId, childPos.layer(), cx, cy,
                            childPos.width(), childPos.height());
                    placeGroupContent(childGroup, fullGraph, interiorLayouts, groupSizes,
                            cx + groupPadX, cy + groupPadTop,
                            finalPositions, groupBoxes, childGroupSlot);
                }
            } else {
                // Real node: apply offset
                finalPositions.put(childId, new SugiyamaLayout.NodePosition(
                        childId, childPos.layer(),
                        offsetX + childPos.x(), offsetY + childPos.y(),
                        childPos.width(), childPos.height()));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Routing (simplified — delegates to flat A* per edge)
    // ═══════════════════════════════════════════════════════════

    /**
     * Simple port ordering by target/source Y for compound layout.
     */
    private Map<String, int[][]> computeSimplePortOrder(
            ProcessGraph graph, Map<String, SugiyamaLayout.NodePosition> positions) {
        Map<String, int[][]> result = new HashMap<>();
        for (ProcessNode node : graph.nodes()) {
            SugiyamaLayout.NodePosition pos = positions.get(node.id());
            if (pos == null) continue;
            int inCount = node.inputs().size();
            int outCount = node.outputs().size();
            int maxSlots = Math.max(inCount, outCount);
            if (maxSlots == 0) continue;
            int[][] order = new int[2][maxSlots];
            for (int i = 0; i < maxSlots; i++) { order[0][i] = i; order[1][i] = i; }

            if (outCount > 1) {
                double[] avgY = new double[outCount];
                int[] cnt = new int[outCount];
                for (ProcessEdge e : graph.outgoingEdges(node.id())) {
                    int si = findSlotIndex(node.outputs(), e.materialName());
                    SugiyamaLayout.NodePosition tp = positions.get(e.toNodeId());
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
            if (inCount > 1) {
                double[] avgY = new double[inCount];
                int[] cnt = new int[inCount];
                for (ProcessEdge e : graph.incomingEdges(node.id())) {
                    int si = findSlotIndex(node.inputs(), e.materialName());
                    SugiyamaLayout.NodePosition sp = positions.get(e.fromNodeId());
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

    private static final int SATELLITE_THRESHOLD = 3;

    private Set<ProcessEdge> identifySatelliteEdgesCompound(
            ProcessGraph graph,
            Map<String, SugiyamaLayout.NodePosition> positions,
            List<SugiyamaLayout.MirrorLink> outLinks) {
        Set<ProcessEdge> satellite = new HashSet<>();
        Map<String, Integer> srcCount = new HashMap<>();
        Map<String, Integer> tgtCount = new HashMap<>();
        int linkId = 0;

        for (ProcessEdge edge : graph.edges()) {
            SugiyamaLayout.NodePosition srcPos = positions.get(edge.fromNodeId());
            SugiyamaLayout.NodePosition tgtPos = positions.get(edge.toNodeId());
            if (srcPos == null || tgtPos == null) continue;
            int layerSpan = Math.abs(tgtPos.layer() - srcPos.layer());
            if (layerSpan <= SATELLITE_THRESHOLD) continue;

            satellite.add(edge);
            ProcessNode srcNode = graph.node(edge.fromNodeId());
            ProcessNode tgtNode = graph.node(edge.toNodeId());
            String srcName = srcNode != null ? srcNode.name() : edge.fromNodeId();
            String tgtName = tgtNode != null ? tgtNode.name() : edge.toNodeId();

            int si = srcCount.getOrDefault(edge.fromNodeId(), 0);
            srcCount.put(edge.fromNodeId(), si + 1);
            int ti = tgtCount.getOrDefault(edge.toNodeId(), 0);
            tgtCount.put(edge.toNodeId(), ti + 1);

            int bw = SugiyamaLayout.MIRROR_BADGE_W;
            int bh = SugiyamaLayout.MIRROR_BADGE_H;
            double gap = 6;
            double srcBX = snap(srcPos.right() - bw);
            double srcBY = snap(srcPos.bottom() + gap + si * (bh + 4));
            double tgtBX = snap(tgtPos.x());
            double tgtBY = snap(tgtPos.bottom() + gap + ti * (bh + 4));

            outLinks.add(new SugiyamaLayout.MirrorLink(linkId, edge,
                    edge.fromNodeId(), edge.toNodeId(),
                    "\u2192 " + tgtName, edge.materialName(), edge.amount(),
                    srcBX, srcBY, true));
            outLinks.add(new SugiyamaLayout.MirrorLink(linkId, edge,
                    edge.toNodeId(), edge.fromNodeId(),
                    "\u2190 " + srcName, edge.materialName(), edge.amount(),
                    tgtBX, tgtBY, false));
            linkId++;
        }
        return satellite;
    }

    /**
     * Routes all non-satellite edges using a city-road-planning model.
     * <p>
     * <b>Main Trunk (主干道)</b>: For each ordered pair of groups (srcGroup → tgtGroup),
     * a shared horizontal "highway" is established in the gap between those groups.
     * All edges within the same pair share this trunk, running in parallel lanes.<br>
     * <b>Branch Road (支路)</b>: Short vertical+horizontal stubs that connect each
     * individual node port to/from its assigned lane on the trunk.<br>
     * <b>Intra-group edges</b> use standard A* routing as before.
     * <p>
     * The result is clean, structured wiring where bundled edges travel together
     * and fan out only near their source/target nodes.
     */
    private List<SugiyamaLayout.EdgeRoute> routeAllEdgesCompound(
            ProcessGraph graph,
            Map<String, SugiyamaLayout.NodePosition> positions,
            Map<String, int[][]> portOrder,
            Set<ProcessEdge> satelliteEdges,
            List<SugiyamaLayout.MirrorLink> mirrors,
            Map<String, SugiyamaLayout.GroupBox> groupBoxes) {

        int G = SugiyamaLayout.GRID_CELL;
        /** Vertical spacing between parallel edge lanes in a trunk bundle. */
        int LANE_SPACING = 2 * G;   // 20 px

        // ── Build node → groupBox lookup ──
        Map<String, SugiyamaLayout.GroupBox> nodeToGroup = new HashMap<>();
        for (SugiyamaLayout.GroupBox gb : groupBoxes.values()) {
            for (var pEntry : positions.entrySet()) {
                var pos = pEntry.getValue();
                if (pos.x() >= gb.x() && pos.right() <= gb.right()
                        && pos.y() >= gb.y() && pos.bottom() <= gb.bottom()) {
                    nodeToGroup.put(pEntry.getKey(), gb);
                }
            }
        }

        // ── Build A* grid for intra-group routing ──
        double maxPx = 0, maxPy = 0;
        for (var p : positions.values()) {
            maxPx = Math.max(maxPx, p.right());
            maxPy = Math.max(maxPy, p.bottom());
        }
        for (var gb : groupBoxes.values()) {
            maxPx = Math.max(maxPx, gb.right());
            maxPy = Math.max(maxPy, gb.bottom());
        }
        for (var ml : mirrors) {
            maxPx = Math.max(maxPx, ml.badgeX() + SugiyamaLayout.MIRROR_BADGE_W);
            maxPy = Math.max(maxPy, ml.badgeY() + SugiyamaLayout.MIRROR_BADGE_H);
        }
        int gridW = (int) (maxPx / G) + 30;
        int gridH = (int) (maxPy / G) + 30;

        boolean[][] blocked = new boolean[gridW][gridH];
        for (var p : positions.values()) {
            int gx0 = Math.max(0, (int)(p.x() / G) - 1);
            int gy0 = Math.max(0, (int)(p.y() / G) - 1);
            int gx1 = Math.min(gridW - 1, (int) Math.ceil(p.right() / G) + 1);
            int gy1 = Math.min(gridH - 1, (int) Math.ceil(p.bottom() / G) + 1);
            for (int gx = gx0; gx <= gx1; gx++)
                for (int gy = gy0; gy <= gy1; gy++)
                    blocked[gx][gy] = true;
        }
        for (var ml : mirrors) {
            int gx0 = Math.max(0, (int)(ml.badgeX() / G) - 1);
            int gy0 = Math.max(0, (int)(ml.badgeY() / G) - 1);
            int gx1 = Math.min(gridW-1, (int) Math.ceil((ml.badgeX() + SugiyamaLayout.MIRROR_BADGE_W) / G) + 1);
            int gy1 = Math.min(gridH-1, (int) Math.ceil((ml.badgeY() + SugiyamaLayout.MIRROR_BADGE_H) / G) + 1);
            for (int gx = gx0; gx <= gx1; gx++)
                for (int gy = gy0; gy <= gy1; gy++)
                    blocked[gx][gy] = true;
        }
        int[][] edgeUsage = new int[gridW][gridH];

        // ═══════════════════════════════════════════════
        //  Classify edges: intra-group vs cross-group
        // ═══════════════════════════════════════════════

        // Precompute port positions for every routable edge
        record EdgePortInfo(
                ProcessEdge edge, ProcessNode srcNode, ProcessNode tgtNode,
                double srcPortX, double srcPortY, double tgtPortX, double tgtPortY,
                int outIdx, int inIdx,
                SugiyamaLayout.GroupBox srcGroup, SugiyamaLayout.GroupBox tgtGroup
        ) {}

        List<EdgePortInfo> intraGroupEdges = new ArrayList<>();
        // Key: "srcGroupId|tgtGroupId"  (use "" for ungrouped)
        Map<String, List<EdgePortInfo>> trunkBuckets = new LinkedHashMap<>();

        for (ProcessEdge edge : graph.edges()) {
            if (satelliteEdges.contains(edge)) continue;
            var srcPos = positions.get(edge.fromNodeId());
            var tgtPos = positions.get(edge.toNodeId());
            if (srcPos == null || tgtPos == null) continue;
            ProcessNode srcNode = graph.node(edge.fromNodeId());
            ProcessNode tgtNode = graph.node(edge.toNodeId());
            if (srcNode == null || tgtNode == null) continue;

            int origOut = findSlotIndex(srcNode.outputs(), edge.materialName());
            int origIn  = findSlotIndex(tgtNode.inputs(), edge.materialName());
            int outIdx  = SugiyamaLayout.reorderedPort(portOrder, edge.fromNodeId(), false, origOut);
            int inIdx   = SugiyamaLayout.reorderedPort(portOrder, edge.toNodeId(), true, origIn);
            int outCnt  = Math.max(srcNode.outputs().size(), 1);
            int inCnt   = Math.max(tgtNode.inputs().size(), 1);

            double srcPortX = snap(srcPos.right());
            double srcPortY = snap(srcPos.y() + SugiyamaLayout.portYOffset(outIdx, outCnt, (int) srcPos.height()));
            double tgtPortX = snap(tgtPos.x());
            double tgtPortY = snap(tgtPos.y() + SugiyamaLayout.portYOffset(inIdx, inCnt, (int) tgtPos.height()));

            SugiyamaLayout.GroupBox srcGroup = nodeToGroup.get(edge.fromNodeId());
            SugiyamaLayout.GroupBox tgtGroup = nodeToGroup.get(edge.toNodeId());

            EdgePortInfo epi = new EdgePortInfo(edge, srcNode, tgtNode,
                    srcPortX, srcPortY, tgtPortX, tgtPortY,
                    outIdx, inIdx, srcGroup, tgtGroup);

            boolean sameGroup = (srcGroup == tgtGroup);
            if (sameGroup) {
                intraGroupEdges.add(epi);
            } else {
                String sId = srcGroup != null ? srcGroup.groupId() : "";
                String tId = tgtGroup != null ? tgtGroup.groupId() : "";
                trunkBuckets.computeIfAbsent(sId + "|" + tId, k -> new ArrayList<>()).add(epi);
            }
        }

        List<SugiyamaLayout.EdgeRoute> routes = new ArrayList<>();

        // ═══════════════════════════════════════════════
        //  Route cross-group edges: Trunk + Branches
        //   — Each trunk is a parallel "line group" (线组)
        //   — Exit branches sorted by srcPortY →  monotone, no crossing
        //   — Entry branches sorted by tgtPortY → monotone, no crossing
        //   — Trunk horizontal lanes are parallel → never cross
        // ═══════════════════════════════════════════════

        /** Horizontal spacing between parallel branch stubs within a trunk. */
        int BRANCH_SPACING = G;   // 10 px

        // Track how many branch lanes have been allocated at each group boundary
        // so that successive trunks from/to the same group are staggered.
        Map<String, Integer> exitLanesUsed  = new HashMap<>();  // groupId → total lanes used
        Map<String, Integer> entryLanesUsed = new HashMap<>();

        for (var bucketEntry : trunkBuckets.entrySet()) {
            List<EdgePortInfo> bucket = bucketEntry.getValue();
            if (bucket.isEmpty()) continue;

            SugiyamaLayout.GroupBox srcGroup = bucket.get(0).srcGroup;
            SugiyamaLayout.GroupBox tgtGroup = bucket.get(0).tgtGroup;
            String srcGid = srcGroup != null ? srcGroup.groupId() : "";
            String tgtGid = tgtGroup != null ? tgtGroup.groupId() : "";

            // ── Sort edges by srcPortY — ensures exit branches are monotone ──
            bucket.sort(Comparator.comparingDouble(epi -> epi.srcPortY));

            int laneCount = bucket.size();

            // ── Compute entry-side column indices sorted by tgtPortY ──
            // entryColIdx[i] = which entry column (0..n-1) edge i should use,
            // assigned so that the edge with the smallest tgtPortY gets column 0
            // (rightmost entry X), etc. This makes entry stubs monotone too.
            Integer[] sortedByTgt = new Integer[laneCount];
            for (int i = 0; i < laneCount; i++) sortedByTgt[i] = i;
            Arrays.sort(sortedByTgt, Comparator.comparingDouble(idx -> bucket.get(idx).tgtPortY));
            int[] entryColIdx = new int[laneCount];
            for (int rank = 0; rank < laneCount; rank++) {
                entryColIdx[sortedByTgt[rank]] = rank;
            }

            // ── Allocate corridor at source group right boundary ──
            int exitCorridorStart = exitLanesUsed.getOrDefault(srcGid, 0);
            exitLanesUsed.put(srcGid, exitCorridorStart + laneCount);

            double exitXBase;
            if (srcGroup != null) {
                exitXBase = snap(srcGroup.right() + G + exitCorridorStart * BRANCH_SPACING);
            } else {
                double maxSrcX = 0;
                for (var epi : bucket) maxSrcX = Math.max(maxSrcX, epi.srcPortX);
                exitXBase = snap(maxSrcX + 2 * G + exitCorridorStart * BRANCH_SPACING);
            }

            // ── Allocate corridor at target group left boundary ──
            int entryCorridorStart = entryLanesUsed.getOrDefault(tgtGid, 0);
            entryLanesUsed.put(tgtGid, entryCorridorStart + laneCount);

            double entryXBase;
            if (tgtGroup != null) {
                entryXBase = snap(tgtGroup.x() - G - entryCorridorStart * BRANCH_SPACING);
            } else {
                double minTgtX = Double.MAX_VALUE;
                for (var epi : bucket) minTgtX = Math.min(minTgtX, epi.tgtPortX);
                entryXBase = snap(minTgtX - 2 * G - entryCorridorStart * BRANCH_SPACING);
            }

            // ── Compute trunk center Y ──
            double ySum = 0;
            for (var epi : bucket) ySum += (epi.srcPortY + epi.tgtPortY) / 2.0;
            double trunkCenterY = snap(ySum / bucket.size());

            double minAllowedY = 0, maxAllowedY = maxPy;
            if (srcGroup != null) {
                minAllowedY = Math.max(minAllowedY, srcGroup.y() + groupPadTop);
                maxAllowedY = Math.min(maxAllowedY, srcGroup.bottom() - groupPadBottom);
            }
            if (tgtGroup != null) {
                minAllowedY = Math.max(minAllowedY, tgtGroup.y() + groupPadTop);
                maxAllowedY = Math.min(maxAllowedY, tgtGroup.bottom() - groupPadBottom);
            }
            if (minAllowedY > maxAllowedY) {
                minAllowedY = 0; maxAllowedY = maxPy;
            }

            // ── Assign lane Y to each edge (by srcPortY order) ──
            double totalBandHeight = (laneCount - 1) * LANE_SPACING;
            double bandTop = snap(trunkCenterY - totalBandHeight / 2.0);
            if (bandTop < minAllowedY) bandTop = snap(minAllowedY);
            if (bandTop + totalBandHeight > maxAllowedY)
                bandTop = snap(Math.max(minAllowedY, maxAllowedY - totalBandHeight));

            for (int i = 0; i < laneCount; i++) {
                EdgePortInfo epi = bucket.get(i);
                double laneY = snap(bandTop + i * LANE_SPACING);

                // Exit column by srcPortY order (i) → monotone exit branches
                double branchExitX  = snap(exitXBase  + i * BRANCH_SPACING);
                // Entry column by tgtPortY order → monotone entry branches
                double branchEntryX = snap(entryXBase - entryColIdx[i] * BRANCH_SPACING);

                // Build clean 5-segment Manhattan path:
                //   srcPort → branchExitX,srcPortY → branchExitX,laneY
                //           → branchEntryX,laneY → branchEntryX,tgtPortY → tgtPort

                List<double[]> path = new ArrayList<>();
                path.add(new double[]{epi.srcPortX, epi.srcPortY});

                // Branch exit: horizontal from port to exit column
                path.add(new double[]{branchExitX, epi.srcPortY});

                // Branch exit: vertical to the lane Y
                if (Math.abs(laneY - epi.srcPortY) > G * 0.5) {
                    path.add(new double[]{branchExitX, laneY});
                }

                // Trunk: straight horizontal from exit column to entry column
                if (Math.abs(branchEntryX - branchExitX) > G * 0.5) {
                    path.add(new double[]{branchEntryX, laneY});
                }

                // Branch entry: vertical from lane Y to target port Y
                if (Math.abs(laneY - epi.tgtPortY) > G * 0.5) {
                    path.add(new double[]{branchEntryX, epi.tgtPortY});
                }

                // Final horizontal into target port
                path.add(new double[]{epi.tgtPortX, epi.tgtPortY});

                List<double[]> cleanPath = cleanUpPath(path);
                markPathCells(cleanPath, edgeUsage, gridW, gridH, G);

                var srcPort = new SugiyamaLayout.PortInfo(
                        epi.edge.fromNodeId(), epi.outIdx, false, epi.srcPortX, epi.srcPortY);
                var tgtPort = new SugiyamaLayout.PortInfo(
                        epi.edge.toNodeId(), epi.inIdx, true, epi.tgtPortX, epi.tgtPortY);
                routes.add(new SugiyamaLayout.EdgeRoute(epi.edge, cleanPath, srcPort, tgtPort));
            }
        }

        // ═══════════════════════════════════════════════
        //  Route intra-group edges: standard A* routing
        // ═══════════════════════════════════════════════

        // Sort intra-group by manhattan distance desc
        intraGroupEdges.sort((a, b) -> {
            double dA = Math.abs(a.tgtPortX - a.srcPortX) + Math.abs(a.tgtPortY - a.srcPortY);
            double dB = Math.abs(b.tgtPortX - b.srcPortX) + Math.abs(b.tgtPortY - b.srcPortY);
            return Double.compare(dB, dA);
        });

        for (EdgePortInfo epi : intraGroupEdges) {
            int sGX = (int)(epi.srcPortX / G) + 2;
            int sGY = (int)(epi.srcPortY / G);
            int eGX = (int)(epi.tgtPortX / G) - 2;
            int eGY = (int)(epi.tgtPortY / G);

            // Compute group bounding box in grid coords so A* prefers staying inside
            int bMinGX = -1, bMinGY = 0, bMaxGX = 0, bMaxGY = 0;
            SugiyamaLayout.GroupBox grp = epi.srcGroup; // srcGroup == tgtGroup for intra
            if (grp != null) {
                bMinGX = (int)(grp.x() / G);
                bMinGY = (int)(grp.y() / G);
                bMaxGX = (int) Math.ceil(grp.right() / G);
                bMaxGY = (int) Math.ceil(grp.bottom() / G);
            }

            List<int[]> gridPath = aStarFind(sGX, sGY, eGX, eGY,
                    blocked, edgeUsage, gridW, gridH, 200,
                    bMinGX, bMinGY, bMaxGX, bMaxGY);

            List<double[]> pixelPath = new ArrayList<>();
            pixelPath.add(new double[]{epi.srcPortX, epi.srcPortY});
            if (gridPath != null && !gridPath.isEmpty()) {
                pixelPath.addAll(simplifyGridPath(gridPath, G));
            } else {
                double midX = snap((epi.srcPortX + epi.tgtPortX) / 2.0);
                // Clamp fallback midpoint inside the group box
                if (grp != null) {
                    midX = Math.max(midX, snap(grp.x() + groupPadX));
                    midX = Math.min(midX, snap(grp.right() - groupPadX));
                }
                pixelPath.add(new double[]{midX, epi.srcPortY});
                pixelPath.add(new double[]{midX, epi.tgtPortY});
            }
            pixelPath.add(new double[]{epi.tgtPortX, epi.tgtPortY});

            List<double[]> cleanPath = cleanUpPath(pixelPath);
            markPathCells(cleanPath, edgeUsage, gridW, gridH, G);

            var srcPort = new SugiyamaLayout.PortInfo(
                    epi.edge.fromNodeId(), epi.outIdx, false, epi.srcPortX, epi.srcPortY);
            var tgtPort = new SugiyamaLayout.PortInfo(
                    epi.edge.toNodeId(), epi.inIdx, true, epi.tgtPortX, epi.tgtPortY);
            routes.add(new SugiyamaLayout.EdgeRoute(epi.edge, cleanPath, srcPort, tgtPort));
        }

        return routes;
    }

    /**
     * Clean up a path: remove consecutive duplicate points AND collinear
     * intermediate points, yielding the minimal set of waypoints for a
     * clean orthogonal polyline with no redundant bends.
     */
    private static List<double[]> cleanUpPath(List<double[]> path) {
        if (path.size() <= 1) return new ArrayList<>(path);
        // Step 1: remove consecutive duplicates
        List<double[]> deduped = new ArrayList<>();
        deduped.add(path.get(0));
        for (int i = 1; i < path.size(); i++) {
            double[] prev = deduped.get(deduped.size() - 1);
            double[] cur = path.get(i);
            if (Math.abs(cur[0] - prev[0]) > 0.5 || Math.abs(cur[1] - prev[1]) > 0.5) {
                deduped.add(cur);
            }
        }
        // Step 2: remove collinear intermediate points
        // A point B between A and C is collinear if all three share the same X or same Y.
        if (deduped.size() <= 2) return deduped;
        List<double[]> clean = new ArrayList<>();
        clean.add(deduped.get(0));
        for (int i = 1; i < deduped.size() - 1; i++) {
            double[] a = clean.get(clean.size() - 1);
            double[] b = deduped.get(i);
            double[] c = deduped.get(i + 1);
            boolean sameX = Math.abs(a[0] - b[0]) < 0.5 && Math.abs(b[0] - c[0]) < 0.5;
            boolean sameY = Math.abs(a[1] - b[1]) < 0.5 && Math.abs(b[1] - c[1]) < 0.5;
            if (!sameX && !sameY) {
                clean.add(b); // genuine bend — keep
            }
            // else: collinear — skip B
        }
        clean.add(deduped.get(deduped.size() - 1));
        return clean;
    }

    // ═══════════════════════════════════════════════════════════
    //  A* pathfinding (compact copy — same logic as SugiyamaLayout)
    // ═══════════════════════════════════════════════════════════

    private static final int BEND_PENALTY = 40;
    private static final int OUT_OF_BOUNDS_PENALTY = 500;
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    /**
     * A* pathfinding with optional soft bounding box.
     * @param boundsMinGX minimum grid X of the preferred region (-1 to disable)
     * @param boundsMinGY minimum grid Y of the preferred region
     * @param boundsMaxGX maximum grid X of the preferred region
     * @param boundsMaxGY maximum grid Y of the preferred region
     *                     When boundsMinGX &lt; 0, bounding is disabled.
     */
    private static List<int[]> aStarFind(int sx, int sy, int tx, int ty,
                                          boolean[][] blocked, int[][] usage,
                                          int gw, int gh, int crossPenalty,
                                          int boundsMinGX, int boundsMinGY,
                                          int boundsMaxGX, int boundsMaxGY) {
        if (sx < 0 || sy < 0 || sx >= gw || sy >= gh) return null;
        if (tx < 0 || ty < 0 || tx >= gw || ty >= gh) return null;
        int states = gw * gh * 4;
        int[] gCost = new int[states];
        int[] from  = new int[states];
        Arrays.fill(gCost, Integer.MAX_VALUE);
        Arrays.fill(from, -1);
        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        for (int d = 0; d < 4; d++) {
            int s = enc(sx, sy, d, gh);
            int g = (d == 0) ? 0 : BEND_PENALTY;
            gCost[s] = g;
            open.add(new long[]{g + manhattan(sx, sy, tx, ty), s});
        }
        int goal = -1;
        while (!open.isEmpty()) {
            long[] top = open.poll();
            int s = (int)top[1]; int f = (int)top[0];
            int cx = s / (gh * 4), cy = (s / 4) % gh, cd = s % 4;
            if (gCost[s] > f - manhattan(cx, cy, tx, ty)) continue;
            if (cx == tx && cy == ty) { goal = s; break; }
            for (int d = 0; d < 4; d++) {
                if (d == ((cd + 2) & 3)) continue;
                int nx = cx + DX[d], ny = cy + DY[d];
                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue;
                if (blocked[nx][ny]) continue;
                int step = 1;
                if (d != cd) step += BEND_PENALTY;
                if (usage[nx][ny] > 0) step += crossPenalty * usage[nx][ny];
                // Soft penalty for leaving the preferred bounding box
                if (boundsMinGX >= 0 &&
                        (nx < boundsMinGX || nx > boundsMaxGX || ny < boundsMinGY || ny > boundsMaxGY)) {
                    step += OUT_OF_BOUNDS_PENALTY;
                }
                int ng = gCost[s] + step;
                int ns = enc(nx, ny, d, gh);
                if (ng < gCost[ns]) { gCost[ns] = ng; from[ns] = s; open.add(new long[]{ng + manhattan(nx, ny, tx, ty), ns}); }
            }
        }
        if (goal < 0) return null;
        List<int[]> path = new ArrayList<>();
        for (int s = goal; s >= 0; s = from[s]) path.add(new int[]{s / (gh * 4), (s / 4) % gh});
        Collections.reverse(path);
        return path;
    }

    private static int enc(int gx, int gy, int d, int gh) { return (gx * gh + gy) * 4 + d; }
    private static int manhattan(int x1, int y1, int x2, int y2) { return Math.abs(x2 - x1) + Math.abs(y2 - y1); }

    private static List<double[]> simplifyGridPath(List<int[]> gp, int G) {
        if (gp.size() <= 1) {
            List<double[]> r = new ArrayList<>();
            for (int[] c : gp) r.add(new double[]{c[0] * (double)G, c[1] * (double)G});
            return r;
        }
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{gp.get(0)[0]*(double)G, gp.get(0)[1]*(double)G});
        for (int i = 1; i < gp.size() - 1; i++) {
            int[] p = gp.get(i-1), c = gp.get(i), n = gp.get(i+1);
            if ((c[0]-p[0]) != (n[0]-c[0]) || (c[1]-p[1]) != (n[1]-c[1]))
                pts.add(new double[]{c[0]*(double)G, c[1]*(double)G});
        }
        int[] last = gp.get(gp.size()-1);
        pts.add(new double[]{last[0]*(double)G, last[1]*(double)G});
        return pts;
    }

    private static void markPathCells(List<double[]> path, int[][] usage,
                                       int gw, int gh, int G) {
        for (int i = 0; i < path.size() - 1; i++) {
            int gx0 = (int)(path.get(i)[0]/G), gy0 = (int)(path.get(i)[1]/G);
            int gx1 = (int)(path.get(i+1)[0]/G), gy1 = (int)(path.get(i+1)[1]/G);
            if (gy0 == gy1) {
                for (int gx = Math.min(gx0,gx1); gx <= Math.max(gx0,gx1); gx++)
                    if (gx >= 0 && gx < gw && gy0 >= 0 && gy0 < gh) usage[gx][gy0]++;
            } else {
                for (int gy = Math.min(gy0,gy1); gy <= Math.max(gy0,gy1); gy++)
                    if (gx0 >= 0 && gx0 < gw && gy >= 0 && gy < gh) usage[gx0][gy]++;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    /** Layout for interior of groups (compact spacing). */
    private SugiyamaLayout interiorLayout() {
        return new SugiyamaLayout()
                .cardWidth(cardWidth)
                .layerGap(layerGap)
                .nodeGap(nodeGap)
                .maxSweeps(maxSweeps);
    }

    /** Layout for the top-level graph (wider spacing between groups). */
    private SugiyamaLayout topLevelLayout() {
        return new SugiyamaLayout()
                .cardWidth(cardWidth)
                .layerGap(topLayerGap)
                .nodeGap(topNodeGap)
                .maxSweeps(maxSweeps);
    }

    private static int snap(double v) {
        return (int)(Math.round(v / SugiyamaLayout.GRID_CELL) * SugiyamaLayout.GRID_CELL);
    }

    /**
     * Collects all transitive descendant node IDs of a group
     * (including nodes in child groups, grandchild groups, etc.).
     */
    private static Set<String> collectAllDescendantNodes(ProcessGroup group, ProcessGraph graph) {
        Set<String> result = new LinkedHashSet<>(group.childNodeIds());
        for (String cgId : group.childGroupIds()) {
            ProcessGroup cg = graph.group(cgId);
            if (cg != null) result.addAll(collectAllDescendantNodes(cg, graph));
        }
        return result;
    }

    /**
     * Returns groups in bottom-up order (leaf groups before parent groups)
     * using iterative DFS post-order traversal.
     */
    private static List<ProcessGroup> bottomUpSort(ProcessGraph graph) {
        Map<String, ProcessGroup> remaining = new LinkedHashMap<>();
        for (ProcessGroup g : graph.groups()) remaining.put(g.id(), g);
        List<ProcessGroup> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ProcessGroup g : graph.groups()) {
            if (!visited.contains(g.id())) postOrder(g, graph, visited, result);
        }
        return result;
    }

    private static void postOrder(ProcessGroup g, ProcessGraph graph,
                                   Set<String> visited, List<ProcessGroup> out) {
        if (!visited.add(g.id())) return;
        for (String cgId : g.childGroupIds()) {
            ProcessGroup cg = graph.group(cgId);
            if (cg != null) postOrder(cg, graph, visited, out);
        }
        out.add(g);
    }

    /**
     * Builds a root-level pseudo-graph where each root group becomes a single virtual
     * node and edges are lifted to the root-level representatives.
     */
    private ProcessGraph buildRootLevelGraph(ProcessGraph fullGraph,
                                              List<ProcessGroup> rootGroups,
                                              Set<String> allGroupedNodeIds) {
        ProcessGraph root = new ProcessGraph();
        // 1) Add ungrouped nodes
        for (ProcessNode n : fullGraph.nodes()) {
            if (!allGroupedNodeIds.contains(n.id())) {
                root.addNode(n);
            }
        }
        // 2) Add root-group virtual nodes
        for (ProcessGroup rg : rootGroups) {
            root.addNode(new ProcessNode(
                    rg.id(), rg.name(), "Group",
                    List.of(), List.of(), 0, 0, 0));
        }
        // 3) Lift edges
        Map<String, String> nodeToRoot = new HashMap<>();
        for (ProcessGroup rg : rootGroups) {
            for (String desc : collectAllDescendantNodes(rg, fullGraph)) {
                nodeToRoot.put(desc, rg.id());
            }
        }
        Set<String> addedEdgeKeys = new HashSet<>();
        for (ProcessEdge edge : fullGraph.edges()) {
            String fromRep = nodeToRoot.getOrDefault(edge.fromNodeId(), edge.fromNodeId());
            String toRep   = nodeToRoot.getOrDefault(edge.toNodeId(), edge.toNodeId());
            if (fromRep.equals(toRep)) continue; // internal to same group
            String key = fromRep + "->" + toRep + ":" + edge.materialName();
            if (addedEdgeKeys.add(key)) {
                root.addEdge(new ProcessEdge(fromRep, toRep,
                        edge.materialName(), edge.amount(), edge.type()));
            }
        }
        return root;
    }

    /**
     * Maps a nodeId to its representative in a group's direct member set.
     * If the node is a direct member, returns it. If it belongs to a child group
     * (represented in descendantToRep), returns the child group id.
     * Otherwise returns null (not in scope).
     */
    private static String liftToGroupMember(String nodeId,
                                             Set<String> memberIds,
                                             Map<String, String> descendantToRep) {
        if (memberIds.contains(nodeId)) return nodeId;
        String rep = descendantToRep.get(nodeId);
        if (rep != null && memberIds.contains(rep)) return rep;
        return null;
    }

    private static int findSlotIndex(List<ProcessNode.MaterialSlot> slots, String materialName) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).materialName().equals(materialName)) return i;
        }
        return 0;
    }

    /**
     * Inject an enlarged size override for the final-target node into a SugiyamaLayout.
     */
    private void injectTargetOverride(SugiyamaLayout layout, ProcessGraph graph, String targetId) {
        ProcessNode tn = graph.node(targetId);
        if (tn == null) return;
        int tw = snap(cardWidth * TARGET_WIDTH_SCALE);
        int th = snap(SugiyamaLayout.computeCardHeight(tn, tw) + TARGET_HEIGHT_EXTRA);
        Map<String, int[]> overrides = new HashMap<>(layout.getNodeSizeOverrides());
        overrides.put(targetId, new int[]{tw, th});
        layout.nodeSizeOverrides(overrides);
    }
}

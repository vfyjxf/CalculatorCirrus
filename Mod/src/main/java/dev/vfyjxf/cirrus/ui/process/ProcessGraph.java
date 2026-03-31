package dev.vfyjxf.cirrus.ui.process;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Directed graph of process nodes connected by material flow edges.
 * <p>
 * Supports adjacency queries, topological iteration, source/sink detection,
 * subgraph extraction, and compound (nested group) structure.
 * Designed for use with the compound Sugiyama layout algorithm.
 */
public class ProcessGraph {

    private final Map<String, ProcessNode> nodes = new LinkedHashMap<>();
    private final List<ProcessEdge> edges = new ArrayList<>();

    // adjacency lists
    private final Map<String, List<ProcessEdge>> outEdges = new HashMap<>();
    private final Map<String, List<ProcessEdge>> inEdges = new HashMap<>();

    // ───────── group support ─────────

    private final Map<String, ProcessGroup> groups = new LinkedHashMap<>();
    /** nodeId → direct parent group id */
    private final Map<String, String> nodeGroupMap = new HashMap<>();
    private @Nullable String preferredRootNodeId;

    // ───────── mutation ─────────

    public ProcessGraph addNode(ProcessNode node) {
        nodes.put(node.id(), node);
        outEdges.putIfAbsent(node.id(), new ArrayList<>());
        inEdges.putIfAbsent(node.id(), new ArrayList<>());
        return this;
    }

    public ProcessGraph addEdge(ProcessEdge edge) {
        edges.add(edge);
        outEdges.computeIfAbsent(edge.fromNodeId(), k -> new ArrayList<>()).add(edge);
        inEdges.computeIfAbsent(edge.toNodeId(), k -> new ArrayList<>()).add(edge);
        return this;
    }

    public ProcessGraph addGroup(ProcessGroup group) {
        groups.put(group.id(), group);
        for (String nid : group.childNodeIds()) {
            nodeGroupMap.put(nid, group.id());
        }
        return this;
    }

    public ProcessGraph preferredRootNodeId(@Nullable String nodeId) {
        this.preferredRootNodeId = nodeId;
        return this;
    }

    // ───────── queries ─────────

    public ProcessNode node(String id) {
        return nodes.get(id);
    }

    public Collection<ProcessNode> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public List<ProcessEdge> edges() {
        return Collections.unmodifiableList(edges);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public @Nullable String preferredRootNodeId() {
        return preferredRootNodeId;
    }

    /**
     * All edges leaving the given node.
     */
    public List<ProcessEdge> outgoingEdges(String nodeId) {
        return outEdges.getOrDefault(nodeId, List.of());
    }

    /**
     * All edges entering the given node.
     */
    public List<ProcessEdge> incomingEdges(String nodeId) {
        return inEdges.getOrDefault(nodeId, List.of());
    }

    /**
     * Successor node ids (nodes reachable via outgoing edges).
     */
    public List<String> successors(String nodeId) {
        return outgoingEdges(nodeId).stream()
                .map(ProcessEdge::toNodeId)
                .distinct()
                .toList();
    }

    /**
     * Predecessor node ids (nodes reachable via incoming edges).
     */
    public List<String> predecessors(String nodeId) {
        return incomingEdges(nodeId).stream()
                .map(ProcessEdge::fromNodeId)
                .distinct()
                .toList();
    }

    /**
     * Source nodes (no incoming edges).
     */
    public List<ProcessNode> sources() {
        return nodes.values().stream()
                .filter(n -> incomingEdges(n.id()).isEmpty())
                .toList();
    }

    /**
     * Sink nodes (no outgoing edges).
     */
    public List<ProcessNode> sinks() {
        return nodes.values().stream()
                .filter(n -> outgoingEdges(n.id()).isEmpty())
                .toList();
    }

    /**
     * All unique material names flowing through this graph.
     */
    public Set<String> allMaterials() {
        var materials = new LinkedHashSet<String>();
        for (var edge : edges) {
            materials.add(edge.materialName());
        }
        return materials;
    }

    /**
     * Check whether the graph contains a node with the given id.
     */
    public boolean containsNode(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    // ───────── group queries ─────────

    /** Returns the group with the given id, or null. */
    public @Nullable ProcessGroup group(String groupId) {
        return groups.get(groupId);
    }

    /** All registered groups. */
    public Collection<ProcessGroup> groups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public int groupCount() {
        return groups.size();
    }

    /** Returns the direct parent group id of a node, or null if ungrouped. */
    public @Nullable String groupOfNode(String nodeId) {
        return nodeGroupMap.get(nodeId);
    }

    /**
     * Lifts a nodeId to its representative element inside {@code parentGroup}.
     * <ul>
     *   <li>If the node is a direct child of parentGroup → returns nodeId</li>
     *   <li>If the node is inside a child group (or deeper descendant) → returns the
     *       top-level child group id that contains it</li>
     *   <li>Otherwise → null (not in scope)</li>
     * </ul>
     */
    public @Nullable String liftToDirectChild(String nodeId, ProcessGroup parentGroup) {
        if (parentGroup.childNodeIds().contains(nodeId)) return nodeId;
        String gid = nodeGroupMap.get(nodeId);
        if (gid == null) return null;
        while (gid != null) {
            if (parentGroup.childGroupIds().contains(gid)) return gid;
            ProcessGroup g = groups.get(gid);
            gid = g != null ? g.parentGroupId() : null;
        }
        return null;
    }

    /**
     * Lifts a nodeId to its root-level representative.
     * Returns nodeId itself if ungrouped, or the root-level group that
     * transitively contains it.
     */
    public String liftToRootLevel(String nodeId) {
        String gid = nodeGroupMap.get(nodeId);
        if (gid == null) return nodeId; // ungrouped
        while (gid != null) {
            ProcessGroup g = groups.get(gid);
            if (g == null || g.parentGroupId() == null) return gid;
            gid = g.parentGroupId();
        }
        return nodeId;
    }

    @Override
    public String toString() {
        return "ProcessGraph{nodes=" + nodes.size()
                + ", edges=" + edges.size()
                + ", groups=" + groups.size() + "}";
    }
}

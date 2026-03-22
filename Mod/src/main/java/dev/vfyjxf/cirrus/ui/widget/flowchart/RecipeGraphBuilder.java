package dev.vfyjxf.cirrus.ui.widget.flowchart;

import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessEdge;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGraph;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessGroup;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode;
import dev.vfyjxf.cirrus.ui.widget.flowchart.model.ProcessNode.MaterialSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link ProcessGraph} from a list of {@link ProcessNode}s by
 * automatically discovering material flow edges.
 * <p>
 * Edge discovery: for each input of node B, if another node A produces a
 * material with the same name, an edge A→B is created. Catalysts are excluded
 * (they're not consumed and don't represent material flow).
 * <p>
 * This serves as the bridge between the Cirrus recipe projection system and
 * the flowchart visualization.
 */
public final class RecipeGraphBuilder {

    private RecipeGraphBuilder() {}

    /**
     * Builds a ProcessGraph from nodes, auto-discovering material edges.
     *
     * @param nodes the process nodes (recipe steps)
     * @return a fully connected ProcessGraph
     */
    public static ProcessGraph build(List<ProcessNode> nodes) {
        return build(nodes, List.of());
    }

    /**
     * Builds a ProcessGraph from nodes with optional grouping.
     *
     * @param nodes  the process nodes (recipe steps)
     * @param groups optional process groups for visual grouping
     * @return a fully connected ProcessGraph
     */
    public static ProcessGraph build(List<ProcessNode> nodes, List<ProcessGroup> groups) {
        ProcessGraph graph = new ProcessGraph();

        for (ProcessNode node : nodes) graph.addNode(node);
        for (ProcessGroup group : groups) graph.addGroup(group);

        // Index: materialName → list of node IDs that produce it
        Map<String, List<String>> producers = new HashMap<>();
        for (ProcessNode node : nodes) {
            for (MaterialSlot out : node.outputs()) {
                producers.computeIfAbsent(out.materialName(), k -> new ArrayList<>())
                         .add(node.id());
            }
        }

        // Create edges: input material → find producer nodes
        for (ProcessNode node : nodes) {
            for (MaterialSlot input : node.inputs()) {
                if (input.isCatalyst()) continue;
                List<String> prods = producers.getOrDefault(input.materialName(), List.of());
                for (String prodId : prods) {
                    if (!prodId.equals(node.id())) {
                        graph.addEdge(new ProcessEdge(
                                prodId, node.id(),
                                input.materialName(), input.amount(),
                                input.type()
                        ));
                    }
                }
            }
        }

        return graph;
    }

    /**
     * Builder for constructing recipe chains step-by-step.
     */
    public static class ChainBuilder {
        private final List<ProcessNode> nodes = new ArrayList<>();
        private final Map<String, List<String>> stageNodes = new LinkedHashMap<>();
        private final List<ProcessGroup> groups = new ArrayList<>();

        public ChainBuilder addNode(ProcessNode node) {
            nodes.add(node);
            return this;
        }

        public ChainBuilder addNode(ProcessNode node, String stage) {
            nodes.add(node);
            stageNodes.computeIfAbsent(stage, k -> new ArrayList<>()).add(node.id());
            return this;
        }

        public ProcessGraph build() {
            // Auto-create groups from stages
            int colorIdx = 0;
            int[] stageColors = {
                    0xFF335577, 0xFF553377, 0xFF775533,
                    0xFF337755, 0xFF773355, 0xFF557733,
                    0xFF335555, 0xFF553355, 0xFF555533
            };
            for (var entry : stageNodes.entrySet()) {
                int color = stageColors[colorIdx % stageColors.length];
                groups.add(new ProcessGroup(
                        "stage_" + colorIdx, entry.getKey(),
                        entry.getValue(), color
                ));
                colorIdx++;
            }
            return RecipeGraphBuilder.build(nodes, groups);
        }
    }
}

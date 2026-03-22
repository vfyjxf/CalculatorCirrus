package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.ResultStepsState;
import dev.vfyjxf.cirrus.ui.calc.result.StepNode;
import dev.vfyjxf.cirrus.ui.calc.result.StepTree;

import java.util.*;

public final class LayeredStepsLayout implements ResultStepsLayout {
    @Override
    public List<StepBlock> build(StepTree tree, ResultStepsState state) {
        if (tree == null || tree.root() == null) return List.of();

        Map<Integer, List<StepNode>> layers = new TreeMap<>();
        Set<StepNode> seen = new HashSet<>();
        var queue = new ArrayDeque<StepNode>();
        queue.add(tree.root());
        while (!queue.isEmpty()) {
            StepNode node = queue.removeFirst();
            if (!seen.add(node)) continue;
            int depth = tree.depthOf(node);
            layers.computeIfAbsent(depth, key -> new ArrayList<>()).add(node);
            for (StepNode child : node.children()) queue.add(child);
        }

        List<StepBlock> blocks = new ArrayList<>();
        for (Map.Entry<Integer, List<StepNode>> entry : layers.entrySet()) {
            int depth = entry.getKey();
            List<StepNode> nodes = entry.getValue();
            blocks.add(StepBlock.layerHeader(depth, nodes.size()));
            nodes.sort(Comparator.comparingInt(n -> n.step() == null ? Integer.MAX_VALUE : n.step().actionIndex()));
            for (StepNode node : nodes) {
                blocks.add(StepBlock.fromNode(node, depth, state.isLayerFolded(node.id())));
            }
        }
        return blocks;
    }
}

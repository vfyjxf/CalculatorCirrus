package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.ResultStepsState;
import dev.vfyjxf.cirrus.ui.calc.result.StepNode;
import dev.vfyjxf.cirrus.ui.calc.result.StepTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeStepsLayout implements ResultStepsLayout {
    @Override
    public List<StepBlock> build(StepTree tree, ResultStepsState state) {
        List<StepBlock> blocks = new ArrayList<>();
        if (tree == null || tree.root() == null) return blocks;
        Set<StepNode> seen = new HashSet<>();
        walk(tree.root(), 0, state, blocks, seen);
        return blocks;
    }

    protected boolean isFolded(ResultStepsState state, StepNode node) {
        return state.isTreeFolded(node.id());
    }

    private void walk(StepNode node, int depth, ResultStepsState state, List<StepBlock> out, Set<StepNode> seen) {
        if (!seen.add(node)) return;
        boolean collapsed = isFolded(state, node);
        out.add(StepBlock.fromNode(node, depth, collapsed));
        if (collapsed) return;
        for (StepNode child : node.children()) {
            walk(child, depth + 1, state, out, seen);
        }
    }
}

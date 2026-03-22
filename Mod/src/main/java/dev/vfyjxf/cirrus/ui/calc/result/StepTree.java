package dev.vfyjxf.cirrus.ui.calc.result;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

public final class StepTree {
    private final StepNode root;
    private final Map<StepNode, Integer> depthMap = new HashMap<>();
    private final Map<String, StepNode> idMap = new HashMap<>();

    public StepTree(StepNode root) {
        this.root = root;
        computeDepths();
    }

    public StepNode root() {
        return root;
    }

    public int depthOf(StepNode node) {
        return depthMap.getOrDefault(node, 0);
    }

    public @Nullable StepNode findById(String id) {
        return idMap.get(id);
    }

    private void computeDepths() {
        if (root == null) return;
        var queue = new ArrayDeque<StepNode>();
        queue.add(root);
        depthMap.put(root, 0);
        idMap.put(root.id(), root);
        while (!queue.isEmpty()) {
            StepNode current = queue.removeFirst();
            int base = depthMap.getOrDefault(current, 0);
            for (StepNode child : current.children()) {
                if (depthMap.containsKey(child)) {
                    continue;
                }
                depthMap.put(child, base + 1);
                idMap.put(child.id(), child);
                queue.add(child);
            }
        }
    }
}

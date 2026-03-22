package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.*;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ListStepsLayout implements ResultStepsLayout {

    @Override
    public List<StepBlock> build(StepTree tree, ResultStepsState state) {
        if (tree == null || tree.root() == null) return List.of();

        // 1. Flatten all nodes via BFS (execution order)
        List<StepNode> flat = flattenBFS(tree);

        // 2. Filter by search query
        String query = state.searchQuery().trim().toLowerCase();
        if (!query.isEmpty()) {
            flat = flat.stream().filter(n -> matchesSearch(n, query)).toList();
        }

        // 3. Sort
        flat = applySort(flat, state.sortMode(), tree);

        // 4. Assign stepIndex (1-based) after sort
        // 5. Group and build blocks
        return buildBlocks(flat, state, tree);
    }

    private List<StepNode> flattenBFS(StepTree tree) {
        List<StepNode> result = new ArrayList<>();
        Set<StepNode> seen = new HashSet<>();
        var queue = new ArrayDeque<StepNode>();
        queue.add(tree.root());
        while (!queue.isEmpty()) {
            StepNode node = queue.removeFirst();
            if (!seen.add(node)) continue;
            if (!node.isTagNode()) {
                result.add(node);
            }
            for (StepNode child : node.children()) {
                queue.add(child);
            }
        }
        return result;
    }

    private boolean matchesSearch(StepNode node, String query) {
        ResultStep step = node.step();
        if (step == null) return false;
        ResourceLocation recipeId = step.recipe().id();
        if (recipeId != null && recipeId.toString().toLowerCase().contains(query)) {
            return true;
        }
        String systemId = step.recipe().systemId().toString().toLowerCase();
        return systemId.contains(query);
    }

    private List<StepNode> applySort(List<StepNode> nodes, SortMode mode, StepTree tree) {
        if (mode == SortMode.EXECUTION_ORDER) return new ArrayList<>(nodes);
        List<StepNode> sorted = new ArrayList<>(nodes);
        sorted.sort(comparatorFor(mode, tree));
        return sorted;
    }

    private Comparator<StepNode> comparatorFor(SortMode mode, StepTree tree) {
        return switch (mode) {
            case EXECUTION_ORDER -> Comparator.comparingInt(n -> actionIndexOf(n));
            case NAME -> Comparator.comparing(n -> recipeIdString(n));
            case DURATION -> Comparator.comparingInt(n -> actionIndexOf(n)); // fallback — no duration data on node
            case ENERGY -> Comparator.comparingInt(n -> actionIndexOf(n));   // fallback — no energy data on node
            case COUNT -> Comparator.<StepNode>comparingInt(n -> timesOf(n)).reversed();
            case DEPENDENCIES -> Comparator.<StepNode>comparingInt(n -> n.children().size()).reversed();
        };
    }

    private static int actionIndexOf(StepNode n) {
        return n.step() == null ? Integer.MAX_VALUE : n.step().actionIndex();
    }

    private static int timesOf(StepNode n) {
        return n.step() == null ? 0 : n.step().times();
    }

    private static String recipeIdString(StepNode n) {
        if (n.step() == null) return "";
        ResourceLocation id = n.step().recipe().id();
        return id == null ? "" : id.toString();
    }

    private List<StepBlock> buildBlocks(List<StepNode> nodes, ResultStepsState state, StepTree tree) {
        GroupMode groupMode = state.groupMode();
        if (groupMode == GroupMode.NONE) {
            return buildFlatBlocks(nodes, state);
        }
        return buildGroupedBlocks(nodes, state, groupMode, tree);
    }

    private List<StepBlock> buildFlatBlocks(List<StepNode> nodes, ResultStepsState state) {
        List<StepBlock> blocks = new ArrayList<>();
        int idx = 1;
        for (StepNode node : nodes) {
            boolean collapsed = state.isCardFolded(node.id());
            blocks.add(StepBlock.fromNode(node, 0, collapsed, idx++));
        }
        return blocks;
    }

    private List<StepBlock> buildGroupedBlocks(
            List<StepNode> nodes, ResultStepsState state, GroupMode groupMode, StepTree tree
    ) {
        Map<String, List<StepNode>> groups = new LinkedHashMap<>();
        for (StepNode node : nodes) {
            String key = groupKey(node, groupMode, tree);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        List<StepBlock> blocks = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<String, List<StepNode>> entry : groups.entrySet()) {
            blocks.add(StepBlock.groupHeader(entry.getKey(), entry.getValue().size()));
            for (StepNode node : entry.getValue()) {
                boolean collapsed = state.isCardFolded(node.id());
                blocks.add(StepBlock.fromNode(node, 0, collapsed, idx++));
            }
        }
        return blocks;
    }

    private String groupKey(StepNode node, GroupMode mode, StepTree tree) {
        ResultStep step = node.step();
        return switch (mode) {
            case NONE -> "";
            case LAYER -> "Layer " + tree.depthOf(node);
            case MACHINE_TYPE -> {
                if (step == null || step.node().machineEntry().isEmpty()) yield "Unknown";
                yield step.node().machineEntry().toItemStack().getHoverName().getString();
            }
            case MOD_SOURCE -> {
                if (step == null) yield "Unknown";
                yield step.recipe().systemId().toString();
            }
            case STATUS -> "OK"; // Placeholder — no real status resolution yet
        };
    }
}

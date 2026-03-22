package dev.vfyjxf.cirrus.ui.calc.result.layout;

import dev.vfyjxf.cirrus.ui.calc.result.StepNode;
import org.jetbrains.annotations.Nullable;

public record StepBlock(
    StepBlockType type,
    String id,
    int depth,
    @Nullable StepNode node,
    boolean collapsed,
    int childCount,
    int actionIndex,
    @Nullable String title,
    int count,
    int stepIndex
) {
    public static StepBlock layerHeader(int depth, int count) {
        return new StepBlock(StepBlockType.LAYER_HEADER, "layer:" + depth, depth, null,
            false, 0, Integer.MIN_VALUE, "Layer " + depth, count, -1);
    }

    public static StepBlock groupHeader(String title, int count) {
        return new StepBlock(StepBlockType.GROUP_HEADER, "group:" + title, 0, null,
            false, 0, Integer.MIN_VALUE, title, count, -1);
    }

    public static StepBlock fromNode(StepNode node, int depth, boolean collapsed) {
        return fromNode(node, depth, collapsed, -1);
    }

    public static StepBlock fromNode(StepNode node, int depth, boolean collapsed, int stepIndex) {
        int actionIndex = node.step() == null ? Integer.MAX_VALUE : node.step().actionIndex();
        StepBlockType type = node.isTagNode() ? StepBlockType.TAG : StepBlockType.STEP;
        return new StepBlock(type, node.id(), depth, node, collapsed,
            node.children().size(), actionIndex, null, 0, stepIndex);
    }
}

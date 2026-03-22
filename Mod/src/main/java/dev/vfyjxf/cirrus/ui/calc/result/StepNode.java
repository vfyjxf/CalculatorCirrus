package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class StepNode {
    private final String id;
    private final @Nullable Namespace tagId;
    private final @Nullable ResultStep step;
    private final List<StepNode> children = new ArrayList<>();

    public StepNode(String id) {
        this(id, null, null);
    }

    public StepNode(String id, @Nullable ResultStep step) {
        this(id, null, step);
    }

    public StepNode(String id, @Nullable Namespace tagId, @Nullable ResultStep step) {
        this.id = id;
        this.tagId = tagId;
        this.step = step;
    }

    public String id() {
        return id;
    }

    public @Nullable Namespace tagId() {
        return tagId;
    }

    public @Nullable ResultStep step() {
        return step;
    }

    public boolean isTagNode() {
        return tagId != null;
    }

    public List<StepNode> children() {
        return children;
    }

    public void addChild(StepNode child) {
        children.add(child);
    }
}
